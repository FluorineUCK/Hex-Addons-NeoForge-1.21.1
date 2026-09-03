package org.eu.net.pool.phlib.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import net.minecraft.server.level.ServerLevel;
import scala.Tuple4;

import org.eu.net.pool.phlib.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternIota.class)
public class PatternIotaMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, require = 1)
    private void phlib$beforePatternExecute(CastingVM vm, ServerLevel world, SpellContinuation continuation,
                                            CallbackInfoReturnable<CastResult> cir) {
        // OLD: Interop.callScalaReturnable(cir, l -> metatableHook.executeHook((PatternIota) (Object) this, l, world, vm, continuation));
        if (Events.beforePatternExecute().invoker().unapply(new Tuple4<>((PatternIota) (Object) this, vm, world, continuation)) instanceof scala.Some<CastResult> result) {
            cir.setReturnValue(result.value());
        }
    }
}
