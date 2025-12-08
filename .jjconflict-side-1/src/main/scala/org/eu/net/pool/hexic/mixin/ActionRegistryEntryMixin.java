package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.llamalad7.mixinextras.sugar.Local;
import org.eu.net.pool.hexic.PatternRemapper$;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ActionRegistryEntry.class)
public class ActionRegistryEntryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static HexPattern replacePattern(HexPattern value, @Local(argsOnly = true) Action action) {
        return PatternRemapper$.MODULE$.remap(value, action);
    }
}
