package org.valkyrienskies.mod.forge.mixin.compat.hypertube;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pedrorok.hypertube.core.travel.TravelPathMover;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(TravelPathMover.class)
public abstract class MixinTravelPathMover {

    @Shadow
    public abstract BlockPos getLastPos();

    @Shadow
    @Final
    private LivingEntity entity;

    @WrapOperation(
        method = "tickEntity",
        at = @At(value = "INVOKE",
            target = "Lcom/pedrorok/hypertube/core/travel/TravelPathMover;handleEntityDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)V"),
        remap = false
    )
    private void applyShipTransform(LivingEntity livingEntity, Vec3 direction, Operation<Void> original){
        Ship ship = VSGameUtilsKt.getShipManagingPos(livingEntity.level(), getLastPos());
        if(ship != null) {
            Vector3d resultJOML = ship.getShipToWorld().transformDirection(direction.x, direction.y, direction.z, new Vector3d());
            original.call(livingEntity, VectorConversionsMCKt.toMinecraft(resultJOML));
        } else original.call(livingEntity, direction);
    }

    @WrapMethod(
        method = "getLastDir",
        remap = false
    )
    private Vec3 lastDirOnShip(Operation<Vec3> original) {
        Vec3 result = original.call();
        Ship ship = VSGameUtilsKt.getShipManagingPos(entity.level(), getLastPos());
        if(ship != null) {
            Vector3d resultJOML = ship.getShipToWorld().transformDirection(result.x, result.y, result.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(resultJOML);
        }
        return result;
    }
}
