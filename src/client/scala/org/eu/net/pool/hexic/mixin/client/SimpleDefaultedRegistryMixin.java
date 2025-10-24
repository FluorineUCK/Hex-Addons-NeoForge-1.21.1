package org.eu.net.pool.hexic.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.SimpleDefaultedRegistry;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.eu.net.pool.hexic.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SimpleDefaultedRegistry.class)
public class SimpleDefaultedRegistryMixin {
    private DyeColor hexic$preferred() {
        long n = MinecraftClient.getInstance().getSession().getUuidOrNull().getLeastSignificantBits();
        n %= 16;
        if (n < 0) n += 16;
        return DyeColor.values()[(int) n];
    }

    private Mediaweave hexic$preferredMediaweave() {
        return Mediaweave$.MODULE$.colors().apply(hexic$preferred());
    }
    private MediaBundle hexic$preferredBundle(int size) {
        return MediaBundle.apply(hexic$preferred(), size);
    }
    private Pen hexic$preferredPen() {
        return Pen.apply(hexic$preferred());
    }

    private Item hexic$preferredStringworm() {
        long n = MinecraftClient.getInstance().getSession().getUuidOrNull().getLeastSignificantBits();
        n %= 48;
        n *= 7;
        n %= 4;
        if (n < 0) n += 4;
        return Extern.getStringworm((int) n);
    }

    @Inject(method = "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    void getHook(@Nullable Identifier par1, CallbackInfoReturnable cir) {
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("preferred_mediaweave")) cir.setReturnValue(hexic$preferredMediaweave());
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("small_preferred_bundle")) cir.setReturnValue(hexic$preferredBundle(6));
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("large_preferred_bundle")) cir.setReturnValue(hexic$preferredBundle(12));
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("preferred_stringworm")) cir.setReturnValue(hexic$preferredStringworm());
        if (par1 != null && par1.getNamespace().equals("hexic") && par1.getPath().equals("preferred_pen")) cir.setReturnValue(hexic$preferredPen());
    }

    @Inject(method = "getOrEmpty", at = @At("HEAD"), cancellable = true)
    void getOrEmptyHook(@Nullable Identifier id, CallbackInfoReturnable<Optional> cir) {
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("preferred_mediaweave"))
            cir.setReturnValue(Optional.of(hexic$preferredMediaweave()));
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("small_preferred_bundle"))
            cir.setReturnValue(Optional.of(hexic$preferredBundle(6)));
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("large_preferred_bundle"))
            cir.setReturnValue(Optional.of(hexic$preferredBundle(12)));
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("preferred_stringworm"))
            cir.setReturnValue(Optional.of(hexic$preferredStringworm()));
        if (id != null && id.getNamespace().equals("hexic") && id.getPath().equals("preferred_pen"))
            cir.setReturnValue(Optional.of(hexic$preferredPen()));
    }
}
