package org.valkyrienskies.mod.fabric.mixin.feature.crash_report;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSCrashReportHeader;

@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Inject(method = "getFriendlyReport", at = @At(value = "INVOKE",
        target = "Ljava/lang/StringBuilder;append(Ljava/lang/String;)Ljava/lang/StringBuilder;", ordinal = 0))
    private void addTsToCrashReportHeader(CallbackInfoReturnable<String> cir, @Local(name = "stringBuilder")
    StringBuilder stringBuilder){

        VSCrashReportHeader.addCrashReportHeader(stringBuilder);
    }
//fabric, smh my head
}
