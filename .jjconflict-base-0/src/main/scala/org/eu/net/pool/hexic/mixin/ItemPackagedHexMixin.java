package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex;
import net.minecraft.item.ItemStack;
import org.eu.net.pool.hexic.PigmentHolderItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemPackagedHex.class)
public abstract class ItemPackagedHexMixin implements PigmentHolderItem {
    @Override @Shadow public abstract FrozenPigment getPigment(ItemStack stack);
    @Shadow @Final public static String TAG_PIGMENT;

    @Override
    public void setPigment(ItemStack stack, FrozenPigment pigment) {
        stack.getOrCreateNbt().put(TAG_PIGMENT, pigment.serializeToNBT());
    }
}
