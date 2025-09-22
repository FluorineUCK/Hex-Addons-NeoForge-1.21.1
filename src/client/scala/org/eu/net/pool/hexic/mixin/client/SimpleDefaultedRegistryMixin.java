package org.eu.net.pool.hexic.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.SimpleDefaultedRegistry;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.eu.net.pool.hexic.Mediaweave;
import org.eu.net.pool.hexic.Mediaweave$;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SimpleDefaultedRegistry.class)
public class SimpleDefaultedRegistryMixin {
    private Mediaweave hexic$preferred() {
        long n = MinecraftClient.getInstance().getSession().getUuidOrNull().getLeastSignificantBits();
        n %= 16;
        if (n < 0) n += 16;
        return Mediaweave$.MODULE$.colors().apply(DyeColor.values()[(int) n]);
    }

    @Inject(method = "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    void getHook(@Nullable Identifier par1, CallbackInfoReturnable cir) {
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("preferred_mediaweave")) cir.setReturnValue(hexic$preferred());
    }

    @Inject(method = "getOrEmpty", at = @At("HEAD"), cancellable = true)
    void getOrEmptyHook(@Nullable Identifier id, CallbackInfoReturnable<Optional> cir) {
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("preferred_mediaweave"))
            cir.setReturnValue(Optional.of(hexic$preferred()));
    }
}
