package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import kotlin.Pair;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Tuple2;

@Mixin(CastingVM.class)
class CastingVMMixin {
    @Inject(method = "handleParentheses", at = @At("HEAD"), cancellable = true)
    void handleParentheses(Iota iota, CallbackInfoReturnable<Pair<CastingImage, ResolvedPatternType>> cir) {
        if (Extern.handleParentheses((CastingVM) (Object) this, iota) instanceof scala.Some<Tuple2<CastingImage,ResolvedPatternType>> result) {
            cir.setReturnValue(new Pair(result.get()._1(), result.get()._2()));
        }
    }
}