package org.valkyrienskies.mod.forge.mixin.compat.hypertube;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pedrorok.hypertube.core.travel.ClientTravelPathMover;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ClientTravelPathMover.PathData.class)
public class MixinClientTravelPathMover {
    @Shadow
    private Vec3 currentLogicalPos;

    @WrapMethod(
        method = "getCurrentDirection",
        remap = false
    )
    private Vec3 applyShipTransform(Operation<Vec3> original){
        Vec3 result = original.call();
        Ship ship = VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, currentLogicalPos);
        if(ship != null) {
            Vector3d resultJOML = ship.getShipToWorld().transformDirection(result.x, result.y, result.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(resultJOML);
        }
        return result;
    }
}
