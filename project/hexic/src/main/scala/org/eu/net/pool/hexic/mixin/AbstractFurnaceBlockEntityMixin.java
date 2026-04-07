package org.eu.net.pool.hexic.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.eu.net.pool.hexic.AbstractFurnaceBlockEntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements AbstractFurnaceBlockEntityAccess {
    @Override
    @Accessor("burnTime")
    public abstract int burnTime();

    @Override
    @Accessor("burnTime")
    public abstract void burnTime_$eq(int burnTime);

    @Override
    @Accessor("fuelTime")
    public abstract int fuelTime();

    @Override
    @Accessor("fuelTime")
    public abstract void fuelTime_$eq(int fuelTime);
}
