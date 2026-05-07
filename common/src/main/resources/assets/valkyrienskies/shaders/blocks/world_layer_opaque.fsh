#version 330 core

#import <sodium:include/fog.glsl>

// VS-modified copy of sodium's stock chunk FSH. The only effect added on top of
// vanilla world rendering is "ship lights brighten the world": each ship-emitter
// is fed in as an entry in u_VsShipEmitters (vec4 = worldPos + lightLevel) and
// we max-merge the distance-attenuated contribution into the world's lightmap
// UV. No occlusion (sky shadowing or wall attenuation) — those approximations
// were causing visible artifacts so they're removed.
//
// Sub-block precision: emitter world coords are stored as floats, so as a ship
// moves smoothly the lit area on the ground tracks it continuously. The old
// BFS-over-block-grid approach quantized emitter positions to floor() and made
// the lit region jump per-block.

in vec4 v_Color;            // RGB = chunk-mesher tinted color, .a = pure vanilla AO (no shade)
in vec2 v_TexCoord;
in vec2 v_LightCoord;       // _vert_tex_light_coord (vanilla world lightmap UV)
in vec3 v_CameraRelWorldPos;// world pos relative to camera; + u_VsRenderOrigin = absolute
// Decoded face data from the VSH (see VsVertexFlagPacker). Flat
// interpolated, since face slot is shared by all 4 vertices of a quad.
flat in vec3 v_WorldNormal; // exact world-space face normal, axis-aligned ±X/±Y/±Z
flat in int v_IsShaded;     // 0 for fluids and emissive/fullbright quads
in float v_FragDistance;
in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex;
uniform sampler2D u_LightTex;
uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;

uniform ivec3 u_VsRenderOrigin;
// Buffer texture (RGBA32F) — TWO texels per ship emitter:
//   texel 2i:   vec4(worldX, worldY, worldZ, lightLevel)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation quaternion
// The quaternion's inverse rotates the world-frame fragment-to-emitter
// offset into the emitter's owning ship local frame, so the Manhattan
// light bubble visibly rotates with the hull.
uniform samplerBuffer u_VsShipEmitters;
uniform int u_VsShipEmitterCount;

// Per-frame list of solid ship voxel CENTERS in world space, paired
// with the voxel's owning-ship rotation quaternion. Two RGBA32F
// texels per voxel:
//   texel 2i:   vec4(worldX, worldY, worldZ, shipIndex)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation
// The shader applies the inverse rotation to the fragment-to-voxel
// offset so the polygon test runs in the voxel's ship-local frame —
// each voxel's octagonal shadow rotates with its ship instead of
// staying world-axis-aligned.
uniform samplerBuffer u_VsShipOccluders;
uniform int u_VsShipOccluderCount;

// External-world fluid culling for ship air pockets. These uniforms are populated by
// ShipWaterPocketExternalWaterCull when this shader is used for Sodium's translucent fluid pass.
uniform float ValkyrienAir_CullEnabled;
uniform float ValkyrienAir_IsShipPass;
uniform vec3 ValkyrienAir_CameraWorldPos;
uniform sampler2D ValkyrienAir_FluidMask;

uniform vec4 ValkyrienAir_ShipAabbMin0;
uniform vec4 ValkyrienAir_ShipAabbMax0;
uniform vec4 ValkyrienAir_GridSize0;
uniform mat4 ValkyrienAir_WorldToShip0;
uniform sampler2D ValkyrienAir_Mask0;

uniform vec4 ValkyrienAir_ShipAabbMin1;
uniform vec4 ValkyrienAir_ShipAabbMax1;
uniform vec4 ValkyrienAir_GridSize1;
uniform mat4 ValkyrienAir_WorldToShip1;
uniform sampler2D ValkyrienAir_Mask1;

uniform vec4 ValkyrienAir_ShipAabbMin2;
uniform vec4 ValkyrienAir_ShipAabbMax2;
uniform vec4 ValkyrienAir_GridSize2;
uniform mat4 ValkyrienAir_WorldToShip2;
uniform sampler2D ValkyrienAir_Mask2;

uniform vec4 ValkyrienAir_ShipAabbMin3;
uniform vec4 ValkyrienAir_ShipAabbMax3;
uniform vec4 ValkyrienAir_GridSize3;
uniform mat4 ValkyrienAir_WorldToShip3;
uniform sampler2D ValkyrienAir_Mask3;

uniform vec4 ValkyrienAir_ShipAabbMin4;
uniform vec4 ValkyrienAir_ShipAabbMax4;
uniform vec4 ValkyrienAir_GridSize4;
uniform mat4 ValkyrienAir_WorldToShip4;
uniform sampler2D ValkyrienAir_Mask4;

uniform vec4 ValkyrienAir_ShipAabbMin5;
uniform vec4 ValkyrienAir_ShipAabbMax5;
uniform vec4 ValkyrienAir_GridSize5;
uniform mat4 ValkyrienAir_WorldToShip5;
uniform sampler2D ValkyrienAir_Mask5;

uniform vec4 ValkyrienAir_ShipAabbMin6;
uniform vec4 ValkyrienAir_ShipAabbMax6;
uniform vec4 ValkyrienAir_GridSize6;
uniform mat4 ValkyrienAir_WorldToShip6;
uniform sampler2D ValkyrienAir_Mask6;

uniform vec4 ValkyrienAir_ShipAabbMin7;
uniform vec4 ValkyrienAir_ShipAabbMax7;
uniform vec4 ValkyrienAir_GridSize7;
uniform mat4 ValkyrienAir_WorldToShip7;
uniform sampler2D ValkyrienAir_Mask7;

uniform vec4 ValkyrienAir_ShipAabbMin8;
uniform vec4 ValkyrienAir_ShipAabbMax8;
uniform vec4 ValkyrienAir_GridSize8;
uniform mat4 ValkyrienAir_WorldToShip8;
uniform sampler2D ValkyrienAir_Mask8;
// World-section storage for per-fragment world-voxel scanning inside
// ws_shipAo. Same layout the ship FSH uses (block_layer_opaque.fsh):
// a flat solid-bit + light-byte buffer plus an index LUT. Exposed to
// the world shader so the X-X corner rule can fold in world blocks
// alongside ship voxels — without it, the SDF only sees ship voxels
// (in u_VsShipOccluders) and vanilla baked AO from sodium handles
// the world side, producing two separate shadow shapes that don't
// combine cleanly. Bound from WorldThing.java.
uniform usamplerBuffer u_VsLightSections;
uniform usamplerBuffer u_VsLightLut;

// Inverse-rotate v by quaternion q (i.e., apply q^-1 = (-q.xyz, q.w) to v).
// Used to express world-frame offsets in the owning ship's local frame so
// the SDF / distance metrics line up with the ship's axes.
vec3 vs_quatRotateInv(vec4 q, vec3 v) {
    vec3 qNeg = -q.xyz;
    return v + 2.0 * cross(qNeg, cross(qNeg, v) + q.w * v);
}

// ===== Section-storage helpers (mirrored from ship FSH) =====
// Layout: each section is [solid bits 732B][light bytes 5832B] = 6564B = 1641 ints.
const uint VS_BLOCKS_PER_SECTION = 18u * 18u * 18u;
const uint VS_LIGHT_SIZE_BYTES = VS_BLOCKS_PER_SECTION;
const uint VS_SOLID_SIZE_BYTES = ((VS_BLOCKS_PER_SECTION + 31u) / 32u) * 4u;
const uint VS_SOLID_START_INTS = 0u;
const uint VS_SECTION_SIZE_INTS = (VS_SOLID_SIZE_BYTES + VS_LIGHT_SIZE_BYTES) / 4u;

uint vs_indexLut(uint i) { return texelFetch(u_VsLightLut, int(i)).r; }
uint vs_indexLight(uint i) { return texelFetch(u_VsLightSections, int(i)).r; }

bool vs_nextLut(uint base, int coord, out uint next) {
    int start = int(vs_indexLut(base));
    uint size = vs_indexLut(base + 1u);
    int idx = coord - start;
    if (idx < 0 || idx >= int(size)) return true;
    next = vs_indexLut(base + 2u + uint(idx));
    return false;
}

bool vs_chunkCoordToSectionIndex(ivec3 sectionPos, out uint index) {
    uint first;
    if (vs_nextLut(0u, sectionPos.y, first) || first == 0u) return true;
    uint second;
    if (vs_nextLut(first, sectionPos.x, second) || second == 0u) return true;
    uint sectionIndex;
    if (vs_nextLut(second, sectionPos.z, sectionIndex) || sectionIndex == 0u) return true;
    index = sectionIndex - 1u;
    return false;
}

bool vs_isSolid(uint sectionOffset, uvec3 blockInSectionPos) {
    uint bitOffset = blockInSectionPos.x + blockInSectionPos.z * 18u + blockInSectionPos.y * 18u * 18u;
    uint uintOffset = bitOffset >> 5u;
    uint bitInWordOffset = bitOffset & 31u;
    uint word = vs_indexLight(sectionOffset + VS_SOLID_START_INTS + uintOffset);
    return (word & (1u << bitInWordOffset)) != 0u;
}

uint vs_fetchSolid3x3x3(uint sectionOffset, ivec3 blockInSectionPos) {
    uint ret = 0u;
    #define VS_FETCH_SOLID(x, y, z, i) { \
        bool flag = vs_isSolid(sectionOffset, uvec3(blockInSectionPos + ivec3(x, y, z))); \
        ret |= uint(flag) << uint(i); \
    }
    VS_FETCH_SOLID(-1, -1, -1, 0)  VS_FETCH_SOLID(0, -1, -1, 1)  VS_FETCH_SOLID(1, -1, -1, 2)
    VS_FETCH_SOLID(-1, -1,  0, 3)  VS_FETCH_SOLID(0, -1,  0, 4)  VS_FETCH_SOLID(1, -1,  0, 5)
    VS_FETCH_SOLID(-1, -1,  1, 6)  VS_FETCH_SOLID(0, -1,  1, 7)  VS_FETCH_SOLID(1, -1,  1, 8)
    VS_FETCH_SOLID(-1,  0, -1, 9)  VS_FETCH_SOLID(0,  0, -1,10)  VS_FETCH_SOLID(1,  0, -1,11)
    VS_FETCH_SOLID(-1,  0,  0,12)  VS_FETCH_SOLID(0,  0,  0,13)  VS_FETCH_SOLID(1,  0,  0,14)
    VS_FETCH_SOLID(-1,  0,  1,15)  VS_FETCH_SOLID(0,  0,  1,16)  VS_FETCH_SOLID(1,  0,  1,17)
    VS_FETCH_SOLID(-1,  1, -1,18)  VS_FETCH_SOLID(0,  1, -1,19)  VS_FETCH_SOLID(1,  1, -1,20)
    VS_FETCH_SOLID(-1,  1,  0,21)  VS_FETCH_SOLID(0,  1,  0,22)  VS_FETCH_SOLID(1,  1,  0,23)
    VS_FETCH_SOLID(-1,  1,  1,24)  VS_FETCH_SOLID(0,  1,  1,25)  VS_FETCH_SOLID(1,  1,  1,26)
    return ret;
}

out vec4 fragColor;

const float WS_UV_MIN = 1.0 / 32.0;
const float WS_UV_MAX = 31.0 / 32.0;

// Loop bound for the per-fragment ship-occluder scan. Should match
// VsShipOccluderList.MAX_OCCLUDERS — 1024 fits but is excessive per
// fragment; 128 covers typical scenes (a single mid-size ship's solid
// voxels), excess entries beyond this cap are silently ignored.
const int VS_OCCLUDER_LOOP_CAP = 128;
const int VA_MASK_TEX_WIDTH_SHIFT = 12;
const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;
const int VA_SUB = 8;
const int VA_OCC_WORDS_PER_VOXEL = 16;
const float VA_WORLD_SAMPLE_EPS = 0.0001;

bool va_isFluidUv(vec2 uv) {
    return texture(ValkyrienAir_FluidMask, uv).r > 0.5;
}

uint va_fetchWord(sampler2D tex, int wordIndex) {
    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);
    vec4 raw = texelFetch(tex, coord, 0) * 255.0;
    uvec4 bytes = uvec4(round(raw));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

bool va_testAir(sampler2D mask, int voxelIdx, ivec3 isize) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int wordIndex = occBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_testOcc(sampler2D mask, int voxelIdx, int subIdx) {
    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);
    int bit = subIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_shouldDiscardForShip(vec3 worldPos, vec4 aabbMin, vec4 aabbMax, vec4 gridSize, mat4 worldToShip, sampler2D mask) {
    if (gridSize.x <= 0.0) return false;
    if (worldPos.x < aabbMin.x || worldPos.x > aabbMax.x) return false;
    if (worldPos.y < aabbMin.y || worldPos.y > aabbMax.y) return false;
    if (worldPos.z < aabbMin.z || worldPos.z > aabbMax.z) return false;

    vec3 localPos = (worldToShip * vec4(worldPos, 1.0)).xyz;
    vec3 size = gridSize.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(localPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(mask, voxelIdx, subIdx)) return true;
    if (va_testAir(mask, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardFluid(vec3 worldPos) {
    return va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin0, ValkyrienAir_ShipAabbMax0, ValkyrienAir_GridSize0, ValkyrienAir_WorldToShip0, ValkyrienAir_Mask0) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin1, ValkyrienAir_ShipAabbMax1, ValkyrienAir_GridSize1, ValkyrienAir_WorldToShip1, ValkyrienAir_Mask1) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin2, ValkyrienAir_ShipAabbMax2, ValkyrienAir_GridSize2, ValkyrienAir_WorldToShip2, ValkyrienAir_Mask2) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin3, ValkyrienAir_ShipAabbMax3, ValkyrienAir_GridSize3, ValkyrienAir_WorldToShip3, ValkyrienAir_Mask3) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin4, ValkyrienAir_ShipAabbMax4, ValkyrienAir_GridSize4, ValkyrienAir_WorldToShip4, ValkyrienAir_Mask4) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin5, ValkyrienAir_ShipAabbMax5, ValkyrienAir_GridSize5, ValkyrienAir_WorldToShip5, ValkyrienAir_Mask5) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin6, ValkyrienAir_ShipAabbMax6, ValkyrienAir_GridSize6, ValkyrienAir_WorldToShip6, ValkyrienAir_Mask6) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin7, ValkyrienAir_ShipAabbMax7, ValkyrienAir_GridSize7, ValkyrienAir_WorldToShip7, ValkyrienAir_Mask7) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin8, ValkyrienAir_ShipAabbMax8, ValkyrienAir_GridSize8, ValkyrienAir_WorldToShip8, ValkyrienAir_Mask8);
}

// Per-fragment ship AO via voxel-position iteration.
//
// For each ship voxel center (in world coords, stored as a continuous
// float — so the position smoothly tracks the ship's transform including
// rotation), compute its contribution to this fragment's AO based on:
//   • d_n (component along the face normal): how far the voxel is in
//     the outward direction. Voxels in the half-space behind the face
//     (d_n <= 0) are skipped.
//   • d_p (length of the in-plane component): how far the voxel is
//     laterally from the fragment's projected position. Voxels too far
//     to one side don't shadow this fragment.>
// Smooth falloff in both directions; sum contributions, clamp to 1.
//
// This replaces the cell-storage-based AO that operated on grid-aligned
// world cells — that approach quantized voxel positions to cells and
// the AO pattern could only morph between cell-aligned configs. With
// the voxel list, every voxel's exact transformed position contributes,
// so the AO shape rotates and translates continuously with the ship.
float ws_shipAo(vec3 worldPosWorld, vec3 nf, out bool anyCardinal) {
    int n = min(u_VsShipOccluderCount, VS_OCCLUDER_LOOP_CAP);

    // Octagonal AO footprint per voxel: Manhattan distance from the
    // 1×1 block, with the CENTER part (where ≥ 2 cardinal-flagged
    // voxels reach) expanded perpendicular to the cardinal axis.
    // Expansion magnitude scales with neighbour proximity — max at
    // vanilla distance 1 (immediately adjacent), tapering to nothing
    // by distance 2.5. We accumulate two sums and pick at the end:
    //   • totalNormal: plain REACH=1 octagon (fallback for fragments
    //     with < 2 flagged voxels).
    //   • totalStretched: per-voxel stretched contribution, used
    //     when the fragment is in the merged centre.
    float totalNormal   = 0.0;
    float totalStretched = 0.0;
    int cardReachCount = 0;
    bool anyCenter = false;

    for (int i = 0; i < n; i++) {
        vec4 voxel = texelFetch(u_VsShipOccluders, i * 2);
        vec4 q     = texelFetch(u_VsShipOccluders, i * 2 + 1);

        vec3 d_ship  = vs_quatRotateInv(q, voxel.xyz - worldPosWorld);
        vec3 nf_ship = vs_quatRotateInv(q, nf);

        float d_n = dot(d_ship, nf_ship);
        if (d_n <= 0.0 || d_n >= 1.5) continue;
        // Linear vertical falloff matching vanilla's per-vertex AO
        // bilinear interpolation: full strength at d_n=0.5 (block
        // resting on the shaded face), fading linearly to 0 at
        // d_n=1.5 (block one over from that). Same window vanilla
        // uses to decide which neighbour blocks contribute AO.
        float fn = clamp(1.5 - d_n, 0.0, 1.0);

        // Ship-local tangent basis so the footprint stays oriented
        // with the ship.
        vec3 uRef = vec3(1.0, 0.0, 0.0);
        vec3 uAxisShip = uRef - dot(uRef, nf_ship) * nf_ship;
        if (length(uAxisShip) < 0.1) {
            uRef = vec3(0.0, 0.0, 1.0);
            uAxisShip = uRef - dot(uRef, nf_ship) * nf_ship;
        }
        uAxisShip = normalize(uAxisShip);
        vec3 vAxisShip = cross(nf_ship, uAxisShip);
        float du = dot(d_ship, uAxisShip);
        float dv = dot(d_ship, vAxisShip);

        // CPU-baked per-axis cardinal-neighbour flags in bits
        // 16/17/18 of voxel.w; bits 19-22 hold a 4-bit closest-
        // neighbour distance (1..15 linear over [0, 2.5], or 0 if
        // no neighbour).
        int flags = floatBitsToInt(voxel.w);
        bool cardX = (flags & 0x10000) != 0;
        bool cardY = (flags & 0x20000) != 0;
        bool cardZ = (flags & 0x40000) != 0;
        int distBits = (flags >> 19) & 0xF;
        float closestDist = float(distBits) * (2.5 / 15.0);

        // Determine which face axis the cardinal pair lies along.
        // We expand the OPPOSITE face axis (perpendicular to the
        // pair). Stretch magnitude tapers from a max at distance 1
        // (vanilla touching) to 0 by distance 2.5.
        bool pairAlongU = (cardX && abs(uAxisShip.x) > 0.5)
                       || (cardY && abs(uAxisShip.y) > 0.5)
                       || (cardZ && abs(uAxisShip.z) > 0.5);
        bool pairAlongV = (cardX && abs(vAxisShip.x) > 0.5)
                       || (cardY && abs(vAxisShip.y) > 0.5)
                       || (cardZ && abs(vAxisShip.z) > 0.5);
        // Linear taper: at dist=1 → factor 1.0 (full extra reach),
        // at dist=2.5 → factor 0; clamped to [0, 1].
        float closeness = (distBits == 0)
                ? 0.0
                : clamp((2.5 - closestDist) / 1.5, 0.0, 1.0);
        // Perpendicular expansion adds up to +1.0 on top of the
        // base reach (so REACH_PERP ranges 1.0 to 2.0).
        float reachU = pairAlongV ? (1.0 + closeness) : 1.0;
        float reachV = pairAlongU ? (1.0 + closeness) : 1.0;

        float mu = max(0.0, abs(du) - 0.5);
        float mv = max(0.0, abs(dv) - 0.5);
        float fpNormal    = max(0.0, 1.0 - mu - mv);
        float fpStretched = max(0.0, 1.0 - mu / reachU - mv / reachV);
        totalNormal   += fn * fpNormal;
        totalStretched += fn * fpStretched;
        if (fpNormal > 0.0 && (cardX || cardY || cardZ)) {
            cardReachCount++;
            if (mu < 0.001 && mv < 0.001) anyCenter = true;
        }
    }
    // Blue indicator: ≥ 2 cardinal-flagged voxels reach the fragment
    // AND none has it as its CENTER. That's the merged-interior
    // (gap-block) region between paired voxels — excludes the
    // columns directly under each voxel.
    anyCardinal = (cardReachCount >= 2) && !anyCenter;

    // Inside the merged centre (≥ 2 flagged voxels reach): use
    // the perpendicular-stretched sum. Otherwise the plain octagon.
    float totalFn = (cardReachCount >= 2) ? totalStretched : totalNormal;
    float occlusion = clamp(totalFn * 0.25, 0.0, 1.0);
    return mix(0.2, 1.0, 1.0 - occlusion);
}
// Loop bound for the per-fragment emitter scan. Should match
// VsShipEmitterList.MAX_EMITTERS — 1024 entries fit but is excessive per
// fragment; 128 is plenty for typical scenes (ships rarely have that many
// torches in the inner radius). Excess emitters in the buffer beyond this
// cap are silently ignored at fragment time.
const int VS_EMITTER_LOOP_CAP = 128;

// Distance-attenuated max ship-emitter contribution at this fragment's
// world position. Manhattan distance in the emitter's owning-ship frame
// so the octahedral light bubble visibly rotates with the hull.
float vs_shipEmitterLight(vec3 worldPos) {
    float maxLight = 0.0;
    int n = min(u_VsShipEmitterCount, VS_EMITTER_LOOP_CAP);
    for (int i = 0; i < n; i++) {
        vec4 e = texelFetch(u_VsShipEmitters, i * 2);
        vec4 q = texelFetch(u_VsShipEmitters, i * 2 + 1);
        vec3 offset_ship = vs_quatRotateInv(q, worldPos - e.xyz);
        float dist = abs(offset_ship.x) + abs(offset_ship.y) + abs(offset_ship.z);
        float light = max(0.0, e.w - dist);
        maxLight = max(maxLight, light);
    }
    return maxLight;
}

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    vec2 lightCoord = v_LightCoord;
    vec3 worldPos = v_CameraRelWorldPos + vec3(u_VsRenderOrigin);

    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5 && va_isFluidUv(v_TexCoord)) {
        vec3 cullWorldPos = v_CameraRelWorldPos + floor(ValkyrienAir_CameraWorldPos) + vec3(0.0, -VA_WORLD_SAMPLE_EPS, 0.0);
        if (va_shouldDiscardFluid(cullWorldPos)) {
            discard;
        }
    }

    // Ship emitters: max-merge their distance-attenuated contribution into the
    // block-light UV. Sub-block-precise because the emitter coords are floats.
    float shipLight = vs_shipEmitterLight(worldPos);
    if (shipLight > 0.0) {
        // MC packs block-light at U = (lightLevel + 0.5) / 16.
        float shipLightUv = (shipLight + 0.5) / 16.0;
        lightCoord.x = max(lightCoord.x, shipLightUv);
    }

    vec4 lightSample = texture(u_LightTex, clamp(lightCoord, vec2(WS_UV_MIN), vec2(WS_UV_MAX)));

    // Tint × lightmap. AO and shade are applied below as a single combined
    // multiplier so vanilla world AO and ship-to-world AO stack the way
    // vanilla's per-vertex averaging would, instead of multiplying
    // independently.
    diffuseColor.rgb *= v_Color.rgb * lightSample.rgb;

    // Combined AO + shade. v_Color.a is PURE vanilla AO (no shade);
    // ship AO comes from per-fragment ws_shipAo() with manhattan
    // tent + smooth-gated cornerExtra. Combine the two sources
    // additively in occlusion-loss space (matching vanilla's per-
    // vertex averaging compounding rule). Floor 0.2 matches sodium's
    // deepest opaque AO. Face shade applied after; slot 6/7
    // (unshaded/fullbright) skips both AO and shade.
    float ao = v_Color.a;
    bool dbgAnyCardinal = false;
    float shipAo = (v_IsShaded == 1)
            ? ws_shipAo(v_CameraRelWorldPos + vec3(u_VsRenderOrigin), v_WorldNormal, dbgAnyCardinal)
            : 1.0;
    if (v_IsShaded == 1) {
        float combined = max(0.2, ao - (1.0 - shipAo));
        float shade = 1.0;
        if (v_WorldNormal.y < -0.5)       shade = 0.5; // DOWN
        else if (abs(v_WorldNormal.y) > 0.5) shade = 1.0; // UP
        else if (abs(v_WorldNormal.x) > 0.5) shade = 0.6; // EAST / WEST
        else                                  shade = 0.8; // NORTH / SOUTH
        diffuseColor.rgb *= combined * shade;
    } else {
        diffuseColor.rgb *= ao;
    }

    // DEBUG: visualize ship AO loss vs vanilla AO loss as separate channels
    // so a real solid block (vanilla AO baked into v_Color.a) and a ship
    // voxel (per-fragment ws_shipAo()) can be placed side-by-side and
    // compared.
    //
    // RED   — ship AO loss (1 - shipAo, scaled).
    // GREEN — vanilla world AO loss (1 - v_Color.a, scaled).
    //
    // If the two formulas produce the same darkening, equivalent setups
    // produce visually identical shapes. Where they disagree, you'll see
    // pure red (ship darker) or pure green (vanilla darker).
    //
    // Tiny +lightSample.rgb keeps u_LightTex alive against GLSL dead-code
    // elimination — without it the compiler strips the texture sample
    // and sodium's bindUniform throws NPE at link time.
    {
        // DEBUG: red = total combined AO loss (ship + vanilla
        // merged via the same formula main rendering uses). V_x,
        // x_x, V_V, V_V_V all show at consistent brightness when
        // sodium would treat them equivalently.
        float combinedDbg = (v_IsShaded == 1)
                ? max(0.2, v_Color.a - (1.0 - shipAo))
                : v_Color.a;
        float dbgTotalLoss = clamp((1.0 - combinedDbg) * 1.25, 0.0, 1.0);
        // The *1e-30 references keep u_VsLightSections and
        // u_VsLightLut alive against GLSL dead-code elimination.
        float keepAlive =
              float(texelFetch(u_VsLightSections, 0).r) * 1e-30
            + float(texelFetch(u_VsLightLut, 0).r) * 1e-30;
        // BLUE — fragment is reached by at least one ship voxel
        // that has a cardinal neighbour (i.e. the stretch path is
        // active). Helps verify the CPU-side flag computation.
        float dbgBlue = dbgAnyCardinal ? 0.5 : 0.0;
        diffuseColor.rgb = vec3(dbgTotalLoss + keepAlive, 0.0, dbgBlue)
                + lightSample.rgb * 1e-3;
    }

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}
