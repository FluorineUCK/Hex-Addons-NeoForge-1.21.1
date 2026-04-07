package org.eu.net.pool.phlib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import org.eu.net.pool.phlib.AllocationTracked;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SimpleRegistry.class)
public class ActuallySimpleRegistryMixin<T> {
    @Shadow private Map<T, RegistryEntry.Reference<T>> intrusiveValueToEntry;

    @ModifyExpressionValue(method = "freeze", at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalStateException;", ordinal = 1))
    IllegalStateException addSuppressed(IllegalStateException original) {
        for (var entry: intrusiveValueToEntry.entrySet()) {
            if (entry.getKey() instanceof AllocationTracked t) original.addSuppressed(t.phlib$createdAt());
        }
        return original;
    }
}
