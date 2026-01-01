package org.eu.net.pool.phlib.mixin;

import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleDefaultedRegistry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;
import org.eu.net.pool.phlib.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Tuple2;

@Mixin({SimpleRegistry.class, SimpleDefaultedRegistry.class})
public abstract class SimpleRegistryMixin<T> implements Registry<T> {
    @Inject(at = @At("HEAD"), method = "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;", cancellable = true)
    void preGet(Identifier id, CallbackInfoReturnable<T> cir) {
        Events.registryLookup().invoker().unapply(new Tuple2<>(this, id)).foreach(x -> { cir.setReturnValue((T) x); return null; });
    }
}
