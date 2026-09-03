package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport;
import net.minecraft.server.level.ServerLevel;
import org.eu.net.pool.hexic.hexcompat.DemiplaneCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Restores Hexic's original Oneironaut demiplane media-cost rules. */
@Pseudo
@Mixin(OpDimTeleport.class)
public abstract class OpDimTeleportMixin {
    @ModifyReturnValue(method = "execute", at = @At("RETURN"))
    private SpellAction.Result hexic$adjustDemiplaneCost(SpellAction.Result original) {
        if (!(original.getEffect() instanceof OpDimTeleportSpellAccess spell)) {
            return original;
        }

        ServerLevel origin = spell.hexic$getOrigin();
        ServerLevel destination = spell.hexic$getDestination();
        boolean fromDemiplane = DemiplaneCompat.isDemiplane(origin);
        boolean toDemiplane = DemiplaneCompat.isDemiplane(destination);
        if (!fromDemiplane && !toDemiplane) {
            return original;
        }

        long cost = fromDemiplane && toDemiplane
                ? 0L
                : 5L * MediaConstants.SHARD_UNIT;
        return original.copy(
                original.getEffect(),
                cost,
                original.getParticles(),
                original.getOpCount()
        );
    }

}
