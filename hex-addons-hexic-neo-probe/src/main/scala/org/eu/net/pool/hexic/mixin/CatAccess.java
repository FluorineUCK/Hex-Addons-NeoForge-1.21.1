package org.eu.net.pool.hexic.mixin;

import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Cat.class)
public interface CatAccess {
    @Invoker("setCollarColor")
    void hexic$setCollarColor(DyeColor color);
}
