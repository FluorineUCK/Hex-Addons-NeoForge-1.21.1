package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import net.minecraft.server.level.ServerLevel;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import scala.Option;
import scala.Tuple2;

import java.util.Collections;

/**
 * Restores Hexic's custom parenthesis patterns at the real CastingVM seam.
 *
 * <p>The compatibility implementation lives in {@link Extern}; this Mixin is
 * deliberately limited to translating Scala's optional tuple into the
 * {@link CastResult} used by Hex Casting pre-39. Redirecting both execution
 * branches keeps Hex Casting's escape and simulate handling around the custom
 * result instead of returning early from {@code executeInner}.</p>
 */
@Mixin(CastingVM.class)
public final class CastingVMMixin {
    @Redirect(
        method = "executeInner",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/iota/Iota;execute(Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;Lnet/minecraft/server/level/ServerLevel;Lat/petrak/hexcasting/api/casting/eval/vm/SpellContinuation;)Lat/petrak/hexcasting/api/casting/eval/CastResult;"
        )
    )
    private CastResult hexic$executeOrHandle(
        Iota iota,
        CastingVM vm,
        ServerLevel world,
        SpellContinuation continuation
    ) {
        CastResult custom = hexic$customParenthesisResult(iota, vm, continuation);
        return custom != null ? custom : iota.execute(vm, world, continuation);
    }

    @Redirect(
        method = "executeInner",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/iota/Iota;executeInParens(Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;Lnet/minecraft/server/level/ServerLevel;Lat/petrak/hexcasting/api/casting/eval/vm/SpellContinuation;)Lat/petrak/hexcasting/api/casting/eval/CastResult;"
        )
    )
    private CastResult hexic$executeInParensOrHandle(
        Iota iota,
        CastingVM vm,
        ServerLevel world,
        SpellContinuation continuation
    ) {
        CastResult custom = hexic$customParenthesisResult(iota, vm, continuation);
        return custom != null ? custom : iota.executeInParens(vm, world, continuation);
    }

    private static CastResult hexic$customParenthesisResult(
        Iota iota,
        CastingVM vm,
        SpellContinuation continuation
    ) {
        Option<Tuple2<CastingImage, ResolvedPatternType>> result =
            Extern.handleParentheses(vm, iota);
        if (result.isEmpty()) {
            return null;
        }

        Tuple2<CastingImage, ResolvedPatternType> value = result.get();
        return new CastResult(
            iota,
            continuation,
            value._1(),
            Collections.emptyList(),
            value._2(),
            HexEvalSounds.NORMAL_EXECUTE.get()
        );
    }
}
