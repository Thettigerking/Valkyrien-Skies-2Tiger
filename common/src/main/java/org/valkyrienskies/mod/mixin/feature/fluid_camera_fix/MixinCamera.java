package org.valkyrienskies.mod.mixin.feature.fluid_camera_fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.ref.WeakReference;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    public abstract Vec3 getPosition();

    @Unique
    private boolean isShipWater = false;

    @Unique
    private WeakReference<Level> vs$cachedLevel = null;
    @Unique
    private long vs$cachedGameTime = Long.MIN_VALUE;
    @Unique
    private double vs$cachedCamX;
    @Unique
    private double vs$cachedCamY;
    @Unique
    private double vs$cachedCamZ;
    @Unique
    private FluidState vs$cachedResult = null;
    @Unique
    private boolean vs$cachedIsShipWater = false;

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getFluidInCamera"
    )
    private FluidState getFluidInCamera(final BlockGetter instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState vanillaFluid = getFluidState.call(instance, blockPos);
        isShipWater = false;
        if (!vanillaFluid.isEmpty() || !(instance instanceof final Level level)) {
            return vanillaFluid;
        }

        final Vec3 cameraPos = this.getPosition();
        final double origX = cameraPos.x;
        final double origY = cameraPos.y;
        final double origZ = cameraPos.z;
        final long gameTime = level.getGameTime();

        final Level cachedLevel = vs$cachedLevel != null ? vs$cachedLevel.get() : null;
        if (vs$cachedResult != null && cachedLevel == level && vs$cachedGameTime == gameTime
            && vs$cachedCamX == origX && vs$cachedCamY == origY && vs$cachedCamZ == origZ) {
            isShipWater = vs$cachedIsShipWater;
            return vs$cachedResult;
        }

        FluidState result = vanillaFluid;

        final AABBd cameraAABB = new AABBd(origX - 1, origY - 1, origZ - 1, origX + 1, origY + 1, origZ + 1);
        final boolean anyShipsNearCamera =
            VSGameUtilsKt.getShipsIntersecting(level, cameraAABB).iterator().hasNext()
                || VSGameUtilsKt.getShipManagingPos(level, origX, origY, origZ) != null;

        if (anyShipsNearCamera) {
            final FluidState[] fluidState = {vanillaFluid};
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 1,
                (x, y, z) -> {
                    fluidState[0] = instance.getBlockState(BlockPos.containing(x, y, z))
                        .getFluidState();
                    if (!fluidState[0].isEmpty()) {
                        isShipWater = true;
                    }
                });
            result = fluidState[0];
        }

        if (cachedLevel != level) {
            vs$cachedLevel = new WeakReference<>(level);
        }
        vs$cachedGameTime = gameTime;
        vs$cachedCamX = origX;
        vs$cachedCamY = origY;
        vs$cachedCamZ = origZ;
        vs$cachedResult = result;
        vs$cachedIsShipWater = isShipWater;
        return result;
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "getFluidInCamera"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2,
        final Operation<Float> getHeight) {
        if (!instance.isEmpty()) {
            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }
        }
        return getHeight.call(instance, arg, arg2);
    }
}
