package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import net.minecraft.server.world.ServerWorld;
import org.eu.net.pool.hexic.Interop;
import org.eu.net.pool.hexic.metatableHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternIota.class)
public class PatternIotaMixin {
    @Inject(at = @At(value = "INVOKE", ordinal = 0, target = "Lat/petrak/hexcasting/api/casting/iota/PatternIota;getPattern()Lat/petrak/hexcasting/api/casting/math/HexPattern;", shift = At.Shift.BEFORE), method = "execute", cancellable = true)
    void execute(CastingVM vm, ServerWorld world, SpellContinuation continuation, CallbackInfoReturnable<CastResult> cir) {
        // ignore IDE error here; it works at compile-time
        Interop.callScalaReturnable(cir, l -> metatableHook.executeHook((PatternIota) (Object) this, l, world, vm, continuation));
    }
}
