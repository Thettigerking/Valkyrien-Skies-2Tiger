package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;

import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * Per-frame list of every solid ship voxel, packed as
 * {@code vec4(worldX, worldY, worldZ, padding)} entries into a buffer
 * texture. Mirrors {@link VsShipEmitterList} structurally but populates
 * from solid (full-cube opaque) voxels instead of light-emitting voxels.
 *
 * <p>Used by the world FSH for ship-to-world AO. Iterating voxel centers
 * directly (rather than reading from the world-grid-aligned cell strengths
 * in {@link VsWorldFromShipLightStorage}) lets the AO shadow follow the
 * ship's transform continuously — including rotation — because the world
 * coords are continuous floats, not grid-quantized. With the cell-storage
 * approach, the AO pattern was anchored to world-aligned cells and
 * couldn't truly rotate; voxel-list AO is rotation-aware by construction.
 */
public class VsShipOccluderList {
    /** Cap on occluders tracked per frame. The shader's loop is bounded
     *  too; keep these in sync. 1024 entries × 48 bytes = 48 KB GPU buffer
     *  (3 RGBA32F texels per voxel: position, NN, quaternion). */
    public static final int MAX_OCCLUDERS = 1024;
    /** 12 floats per voxel, three vec4 texels:
     *    [i*3 + 0] = (worldX, worldY, worldZ, shipIndex)
     *    [i*3 + 1] = (nnWorldX, nnWorldY, nnWorldZ, nnDist3D)   — written
     *                by {@link #computeNearestNeighbors}
     *    [i*3 + 2] = (qx, qy, qz, qw)                            — ship
     *                rotation, used so each voxel's octagon stays oriented
     *                with the ship instead of becoming a world-axis box.
     *  Without the quaternion, voxels of a rotated ship rendered as
     *  separate world-aligned octagons offset diagonally and looked like
     *  multiple disconnected shapes; with it, the polygon test runs in
     *  ship frame so the merged AO follows the hull's orientation. */
    private static final int BYTES_PER_OCCLUDER = 48;
    /** Match D_HIGH in the world FSH: dPair beyond this never produces a
     *  bridge, so we don't need to record an NN past this radius. Cell
     *  units in the shader collapse to world units 1:1 (1 cell = 1 block).
     *  Currently 2.5 cells — bridges fade out half a cell past octagon-
     *  touching distance (D_LOW = 2*HALF = 2). */
    private static final float NN_MAX_DIST = 2.5f;
    private static final float NN_MAX_DIST_SQ = NN_MAX_DIST * NN_MAX_DIST;

    private final long arenaPtr;
    private int count = 0;

    private int buffer = 0;
    private int texture = 0;
    private int currentByteSize = 0;

    private final Vector3d scratch = new Vector3d();
    private final Quaterniond scratchQuat = new Quaterniond();
    private final BlockPos.MutableBlockPos scratchBlockPos = new BlockPos.MutableBlockPos();

    /** Per-frame map from ship.getId() → small dense index used as the
     *  shader's per-voxel ship tag. Reset every frame in beginFrame; index
     *  0 is reserved for "no ship" so the ship FSH can compare against
     *  -1 / unset values without false matches. */
    private final Long2IntMap shipIdToIndex = new Long2IntOpenHashMap();
    private int nextShipIndex = 1;

    public VsShipOccluderList() {
        arenaPtr = MemoryUtil.nmemAlloc((long) MAX_OCCLUDERS * BYTES_PER_OCCLUDER);
    }

    public void delete() {
        if (arenaPtr != 0L) MemoryUtil.nmemFree(arenaPtr);
        if (buffer != 0) { GL15.glDeleteBuffers(buffer); buffer = 0; }
        if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
    }

    public void beginFrame() {
        count = 0;
        shipIdToIndex.clear();
        nextShipIndex = 1;
    }

    public int size() {
        return count;
    }

    /** Returns the per-frame dense index assigned to {@code shipId} during
     *  populateFromShip, or 0 if the ship wasn't populated this frame.
     *  Used by setupShipShaderState to tell the ship FSH which voxels in
     *  u_VsShipOccluders belong to the currently rendering ship so they
     *  can be skipped (their AO is already baked into v_Color.a). */
    public int getShipIndex(long shipId) {
        return shipIdToIndex.getOrDefault(shipId, 0);
    }

    /** Assigns a fresh per-frame dense index to {@code shipId} if it doesn't
     *  already have one, and returns it. Callers populating occluders from
     *  outside {@link #populateFromShip} (e.g. {@link VsWorldFromShipLightStorage})
     *  use this to tag voxels with the ship they belong to. */
    public int assignShipIndex(long shipId) {
        return shipIdToIndex.computeIfAbsent(shipId, id -> nextShipIndex++);
    }

    /** Walk a ship's voxels, find solid blocks, transform centers to world. */
    public void populateFromShip(LevelAccessor level, ClientShip ship) {
        AABBic shipyardAabb = ship.getShipAABB();
        if (shipyardAabb == null) return;

        ShipTransform xform = ship.getRenderTransform();
        Matrix4dc shipToWorld = xform.getShipToWorld();
        // Pull the rotation out of the ship's transform once per ship —
        // every voxel of this ship shares the same quaternion. The shader
        // applies its inverse to the fragment-to-voxel offset to express
        // the SDF in the ship's local frame, so the Manhattan tent
        // (octagonal shadow) rotates with the ship.
        shipToWorld.getNormalizedRotation(scratchQuat);
        float qx = (float) scratchQuat.x;
        float qy = (float) scratchQuat.y;
        float qz = (float) scratchQuat.z;
        float qw = (float) scratchQuat.w;

        // Assign or look up this ship's per-frame dense index. Stored in
        // voxel.w so the ship FSH can compare against u_VsCurrentShipIndex
        // and skip same-ship voxels (their AO is already baked into
        // v_Color.a — counting them again double-darkens self-shadows).
        long shipId = ship.getId();
        int shipIdx = shipIdToIndex.computeIfAbsent(shipId, id -> nextShipIndex++);
        float shipIndexFloat = (float) shipIdx;

        int xMin = shipyardAabb.minX();
        int yMin = shipyardAabb.minY();
        int zMin = shipyardAabb.minZ();
        int xMax = shipyardAabb.maxX();
        int yMax = shipyardAabb.maxY();
        int zMax = shipyardAabb.maxZ();

        for (int sy = yMin; sy <= yMax; sy++) {
            for (int sz = zMin; sz <= zMax; sz++) {
                for (int sx = xMin; sx <= xMax; sx++) {
                    if (count >= MAX_OCCLUDERS) return;
                    scratchBlockPos.set(sx, sy, sz);
                    BlockState state = level.getBlockState(scratchBlockPos);
                    if (state.isAir()) continue;
                    boolean isSolid = state.canOcclude()
                            && state.isCollisionShapeFullBlock(level, scratchBlockPos);
                    if (!isSolid) continue;

                    // Voxel center → world coords. Float precision preserved
                    // through the matrix multiply, so a rotating ship's voxel
                    // centers move along smooth arcs rather than snapping
                    // cell-to-cell.
                    scratch.set(sx + 0.5, sy + 0.5, sz + 0.5);
                    shipToWorld.transformPosition(scratch);

                    appendOccluder(scratch.x, scratch.y, scratch.z, shipIndexFloat, qx, qy, qz, qw);
                }
            }
        }
    }

    public void appendOccluder(final double worldX, final double worldY, final double worldZ, final float shipIndex,
        final float qx, final float qy, final float qz, final float qw) {
        if (count >= MAX_OCCLUDERS) return;

        long offset = arenaPtr + (long) count * BYTES_PER_OCCLUDER;
        // Texel 0: position + ship index
        MemoryUtil.memPutFloat(offset,        (float) worldX);
        MemoryUtil.memPutFloat(offset + 4,    (float) worldY);
        MemoryUtil.memPutFloat(offset + 8,    (float) worldZ);
        MemoryUtil.memPutFloat(offset + 12,   shipIndex);
        // Texel 1: NN slot — overwritten by computeNearestNeighbors. Init
        // to "no neighbour" so a forgotten NN call is safe (shader skips
        // bridge when nnDist >= D_HIGH).
        MemoryUtil.memPutFloat(offset + 16,   0f);
        MemoryUtil.memPutFloat(offset + 20,   0f);
        MemoryUtil.memPutFloat(offset + 24,   0f);
        MemoryUtil.memPutFloat(offset + 28,   Float.MAX_VALUE);
        // Texel 2: quaternion
        MemoryUtil.memPutFloat(offset + 32,   qx);
        MemoryUtil.memPutFloat(offset + 36,   qy);
        MemoryUtil.memPutFloat(offset + 40,   qz);
        MemoryUtil.memPutFloat(offset + 44,   qw);
        count++;
    }

    /** Brute-force O(N²) nearest-neighbour pass. For each occluder, finds
     *  the closest other occluder within {@link #NN_MAX_DIST} and writes
     *  it into texel 1 (offset +16). Texel 0 (position) and texel 2
     *  (quaternion) are untouched. Bounded scenes (count up to
     *  MAX_OCCLUDERS=1024) make this ~1 M comparisons per frame, well
     *  under a millisecond on any modern CPU. Must be called after every
     *  populateFromShip / appendOccluder for the frame and before
     *  {@link #upload()}. */
    public void computeNearestNeighbors() {
        for (int i = 0; i < count; i++) {
            long iOffset = arenaPtr + (long) i * BYTES_PER_OCCLUDER;
            float ix = MemoryUtil.memGetFloat(iOffset);
            float iy = MemoryUtil.memGetFloat(iOffset + 4);
            float iz = MemoryUtil.memGetFloat(iOffset + 8);

            float bestDistSq = NN_MAX_DIST_SQ;
            float bestX = 0f, bestY = 0f, bestZ = 0f;
            for (int j = 0; j < count; j++) {
                if (j == i) continue;
                long jOffset = arenaPtr + (long) j * BYTES_PER_OCCLUDER;
                float jx = MemoryUtil.memGetFloat(jOffset);
                float jy = MemoryUtil.memGetFloat(jOffset + 4);
                float jz = MemoryUtil.memGetFloat(jOffset + 8);
                float dx = jx - ix, dy = jy - iy, dz = jz - iz;
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestX = jx;
                    bestY = jy;
                    bestZ = jz;
                }
            }

            float bestDist = bestDistSq < NN_MAX_DIST_SQ
                    ? (float) Math.sqrt(bestDistSq)
                    : Float.MAX_VALUE;
            MemoryUtil.memPutFloat(iOffset + 16, bestX);
            MemoryUtil.memPutFloat(iOffset + 20, bestY);
            MemoryUtil.memPutFloat(iOffset + 24, bestZ);
            MemoryUtil.memPutFloat(iOffset + 28, bestDist);
        }
    }

    public void upload() {
        ensureGlObjects();
        int needed = Math.max(BYTES_PER_OCCLUDER, count * BYTES_PER_OCCLUDER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        boolean orphaned = currentByteSize != needed;
        if (orphaned || count > 0) {
            GL15.nglBufferData(GL31.GL_TEXTURE_BUFFER, needed, arenaPtr, GL15.GL_DYNAMIC_DRAW);
            currentByteSize = needed;
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        if (orphaned) {
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }

    public void bind(int textureUnit) {
        ensureGlObjects();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void ensureGlObjects() {
        if (buffer == 0) buffer = GL15.glGenBuffers();
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }
}
