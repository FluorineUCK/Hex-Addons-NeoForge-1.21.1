package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.eu.net.pool.hexic.cfg;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scala.util.CommandLineParser;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Shadow private int count;

    @Inject(method = "<init>(Lnet/minecraft/nbt/NbtCompound;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtCompound;getByte(Ljava/lang/String;)B", shift = At.Shift.BY, by = 2))
    void intSurprise(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("Count", NbtElement.INT_TYPE)) {
            count = nbt.getInt("Count");
        }
        int minSize = cfg.apply("hexic.min_stack_size", Integer::parseInt).getOrElse(() -> 0);
        int maxSize = cfg.apply("hexic.max_stack_size", Integer::parseInt).getOrElse(() -> Integer.MAX_VALUE);
        if (count < minSize) count = minSize;
        if (count > maxSize) count = maxSize;
    }

    @WrapOperation(method = {"writeNbt", "method_7953"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtCompound;putByte(Ljava/lang/String;B)V"))
    void eatsYourByteCutely(NbtCompound instance, String key, byte value, Operation<Void> original) {
        instance.putInt(key, count);
    }
    
    @WrapOperation(method = "isEmpty", at = @At(value = "FIELD", target = "Lnet/minecraft/item/ItemStack;count:I"))
    int gaslightCount(ItemStack instance, Operation<Integer> original) {
        int i = original.call(instance);
        if (i < 0) i = 1;
        return i;
    }
}
