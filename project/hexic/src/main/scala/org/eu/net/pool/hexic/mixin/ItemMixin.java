package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static net.minecraft.item.Items.ECHO_SHARD;

@Mixin(Item.class)
public class ItemMixin {
    @Inject(method = "appendTooltip", at = @At("TAIL"))
    void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context, CallbackInfo ci) {
        if ((Object) this != ECHO_SHARD) return; {
            NbtCompound tag = stack.getNbt();
            if (tag == null) return;
            if (tag.contains("hexic:memory", NbtElement.LIST_TYPE))
                tooltip.add(Text.translatable("hexic.spell_memory").styled(s -> s.withColor(0xfc77be)));
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    void use(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if ((Object) this != ECHO_SHARD) return;
        ItemStack stack = user.getStackInHand(hand);
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return;
        NbtList patterns = nbt.getList("hexic:memory", NbtElement.COMPOUND_TYPE);
        if (patterns.isEmpty() || world.isClient || !(world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity serverPlayer)) return;
        CastingVM staffcast = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, hand);
        stack.decrement(1);
        NbtCompound newNbt = nbt.copy();
        newNbt.remove("hexic:memory");
        ItemStack newStack = new ItemStack(ECHO_SHARD);
        if (!newNbt.isEmpty()) newStack.setNbt(newNbt);
        try {
            staffcast.queueExecuteAndWrapIotas(patterns.stream().map(e -> e instanceof NbtCompound c ? IotaType.deserialize(c, serverWorld) : new GarbageIota()).toList(), serverWorld);
            IXplatAbstractions.INSTANCE.setStaffcastImage(serverPlayer, staffcast.getImage());
        } finally {
            if (stack.isEmpty()) {
                cir.setReturnValue(TypedActionResult.consume(newStack));
            } else {
                user.getInventory().offerOrDrop(newStack);
                cir.setReturnValue(TypedActionResult.consume(stack));
            }
        }
    }
}
