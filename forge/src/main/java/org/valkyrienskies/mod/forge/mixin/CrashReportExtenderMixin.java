package org.valkyrienskies.mod.forge.mixin;

import net.minecraft.CrashReport;
import net.minecraftforge.logging.CrashReportExtender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ValkyrienSkiesCrashReporter;

@Mixin(CrashReportExtender.class)
public class CrashReportExtenderMixin {

    @Inject(method = "addCrashReportHeader", at = @At("HEAD"), remap = false)
    private static void addConnectorCrashReportHeader(StringBuilder stringbuilder, CrashReport crashReport, CallbackInfo ci) {
        ValkyrienSkiesCrashReporter.addCrashReportHeader(stringbuilder);
    }

}
