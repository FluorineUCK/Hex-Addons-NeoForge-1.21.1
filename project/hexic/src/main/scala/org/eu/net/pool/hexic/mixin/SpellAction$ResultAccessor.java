package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpellAction.Result.class)
public interface SpellAction$ResultAccessor {
    @Accessor @Mutable void setCost(long cost);
}
