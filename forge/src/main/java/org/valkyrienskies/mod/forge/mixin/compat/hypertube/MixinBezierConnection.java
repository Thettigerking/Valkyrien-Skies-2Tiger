package org.valkyrienskies.mod.forge.mixin.compat.hypertube;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pedrorok.hypertube.core.connection.BezierConnection;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BezierConnection.class)
public abstract class MixinBezierConnection {
    @Shadow
    @Final
    private int detailLevel;

    @WrapMethod(
        method = "calculateRelativeBezierPoints",
        remap = false
    )
    private List<Vec3> result(Operation<List<Vec3>> original){
        if(detailLevel > 1000) return List.of();
        else return original.call();
    }
}
