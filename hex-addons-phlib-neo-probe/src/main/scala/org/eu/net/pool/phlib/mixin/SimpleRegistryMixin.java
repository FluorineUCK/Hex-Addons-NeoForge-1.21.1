package org.eu.net.pool.phlib.mixin;

import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.eu.net.pool.phlib.Events;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Tuple2;

@Mixin({MappedRegistry.class, DefaultedMappedRegistry.class})
public abstract class SimpleRegistryMixin<T> implements Registry<T> {
    @Inject(
        at = @At("HEAD"),
        method = "get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
        cancellable = true
    )
    private void phlib$preGet(ResourceLocation id, CallbackInfoReturnable<T> cir) {
        Events.registryLookup().invoker().unapply(new Tuple2<>(this, id)).foreach(x -> { cir.setReturnValue((T) x); return null; });
    }
}
