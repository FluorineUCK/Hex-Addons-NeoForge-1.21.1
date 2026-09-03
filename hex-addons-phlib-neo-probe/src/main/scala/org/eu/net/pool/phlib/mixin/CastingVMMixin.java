package org.eu.net.pool.phlib.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.eu.net.pool.phlib.FinalizedSpell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(CastingVM.class)
public abstract class CastingVMMixin {
    @Shadow public abstract CastingEnvironment getEnv();

    @WrapMethod(method = "performSideEffects")
    void wrapSideEffects(List<? extends OperatorSideEffect> sideEffects, Operation<Void> original) throws Throwable {
        Throwable exc = null;
        try {
            original.call(sideEffects);
        } catch (Throwable e) {
            exc = e;
        }
        for (var effect: sideEffects)
            if (effect instanceof OperatorSideEffect.AttemptSpell && ((OperatorSideEffect.AttemptSpell) effect).getSpell() instanceof FinalizedSpell spell)
                try {
                    spell.postCast(getEnv());
                } catch (Throwable e) {
                    if (exc == null)
                        exc = e;
                    else
                        exc.addSuppressed(e);
                }
        if (exc != null) throw exc;
    }
}
