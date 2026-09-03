package org.eu.net.pool.hexic.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eu.net.pool.hexic.hexcompat.StackCountCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes ItemStack's 1.21 packet codec accept signed counts and clamps packet
 * values through the same range used by persistent serialization.
 */
@Mixin(targets = "net.minecraft.world.item.ItemStack$1")
public abstract class ItemStackStreamCodecMixin {
    @Inject(
        method = "decode(Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexic$decodeConfiguredCount(
        RegistryFriendlyByteBuf buffer,
        CallbackInfoReturnable<ItemStack> callback
    ) {
        int count = StackCountCompat.clamp(buffer.readVarInt());
        if (count == 0) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }

        StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> itemCodec =
            ByteBufCodecs.holderRegistry(Registries.ITEM);
        Holder<Item> item = itemCodec.decode(buffer);
        DataComponentPatch components = DataComponentPatch.STREAM_CODEC.decode(buffer);
        callback.setReturnValue(new ItemStack(item, count, components));
    }

    @Inject(
        method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexic$encodeConfiguredCount(
        RegistryFriendlyByteBuf buffer,
        ItemStack stack,
        CallbackInfo callback
    ) {
        if (stack == ItemStack.EMPTY || stack.getItem() == net.minecraft.world.item.Items.AIR) {
            buffer.writeVarInt(0);
            callback.cancel();
            return;
        }

        int count = StackCountCompat.clamp(stack.getCount());
        buffer.writeVarInt(count);
        if (count != 0) {
            StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> itemCodec =
                ByteBufCodecs.holderRegistry(Registries.ITEM);
            itemCodec.encode(buffer, stack.getItemHolder());
            DataComponentPatch.STREAM_CODEC.encode(buffer, stack.getComponentsPatch());
        }
        callback.cancel();
    }
}
