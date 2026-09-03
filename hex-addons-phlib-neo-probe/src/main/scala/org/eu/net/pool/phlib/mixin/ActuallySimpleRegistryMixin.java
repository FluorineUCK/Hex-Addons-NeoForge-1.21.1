package org.eu.net.pool.phlib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.eu.net.pool.phlib.AllocationTracked;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(MappedRegistry.class)
public class ActuallySimpleRegistryMixin<T> {
    @Shadow @Nullable private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @ModifyExpressionValue(method = "freeze", at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalStateException;", ordinal = 1))
    private IllegalStateException phlib$addAllocationSites(IllegalStateException original) {
        if (unregisteredIntrusiveHolders == null) {
            return original;
        }
        for (var entry: unregisteredIntrusiveHolders.entrySet()) {
            if (entry.getKey() instanceof AllocationTracked t) original.addSuppressed(t.phlib$createdAt());
        }
        return original;
    }
}
