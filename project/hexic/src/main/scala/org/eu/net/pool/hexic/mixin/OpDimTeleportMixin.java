package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(OpDimTeleport.class)
public class OpDimTeleportMixin {
    @ModifyReturnValue(at = @At("RETURN"), method = "execute")
    SpellAction.Result modifyCost(SpellAction.Result result, @Local(name = "origin") ServerWorld origin, @Local(name = "destination") ServerWorld destination) {
        Identifier sourceID = origin.getRegistryKey().getValue();
        boolean fromDemiplane = sourceID.getNamespace().equals("hexic") && sourceID.getPath().startsWith("fresh-");
        Identifier targetID = destination.getRegistryKey().getValue();
        boolean toDemiplane = targetID.getNamespace().equals("hexic") && targetID.getPath().startsWith("fresh-");
        if (fromDemiplane && toDemiplane) ((SpellAction$ResultAccessor) (Object) result).setCost(0);
        else if (fromDemiplane || toDemiplane) ((SpellAction$ResultAccessor) (Object) result).setCost(5 * MediaConstants.SHARD_UNIT);
        return result;
    }
}
