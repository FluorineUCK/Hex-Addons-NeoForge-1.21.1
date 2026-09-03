package org.eu.net.pool.hexic.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccess {
    @Accessor("litTime")
    int hexic$getLitTime();

    @Accessor("litTime")
    void hexic$setLitTime(int value);

    @Accessor("litDuration")
    int hexic$getLitDuration();

    @Accessor("litDuration")
    void hexic$setLitDuration(int value);
}
