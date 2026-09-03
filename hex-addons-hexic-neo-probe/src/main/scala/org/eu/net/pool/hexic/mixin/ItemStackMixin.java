package org.eu.net.pool.hexic.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eu.net.pool.hexic.hexcompat.StackCountCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * 1.21 codec equivalent of Hexic 2.1.0's ItemStack NBT mixin.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow
    private int count;

    @Shadow
    @Final
    @Nullable
    private Item item;

    @Redirect(
        method = "method_57371",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;",
            ordinal = 0
        )
    )
    private static Codec<Integer> hexic$useConfiguredIntCountCodec(int vanillaMin, int vanillaMax) {
        return StackCountCompat.codec();
    }

    /**
     * Preserve the original signed-stack behavior: a negative count remains a
     * real stack in memory. With the default minimum of zero it is clamped only
     * when serialized, exactly like the 2.1.0 implementation.
     */
    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void hexic$negativeCountsAreNotEmpty(CallbackInfoReturnable<Boolean> callback) {
        if (this.count < 0
            && (Object)this != ItemStack.EMPTY
            && this.item != null
            && this.item != Items.AIR) {
            callback.setReturnValue(false);
        }
    }
}
