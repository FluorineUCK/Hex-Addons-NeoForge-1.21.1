package org.net.eu.pool.mica.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.modfest.fireblanket.Fireblanket;
import net.modfest.fireblanket.config.ConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(Fireblanket.class)
public class FireblanketMixin {
    @WrapOperation(method = "onInitialize", at = @At(value = "INVOKE", target = "Lnet/modfest/fireblanket/config/FireblanketConfig;get(Lnet/modfest/fireblanket/config/ConfigSpec;)Ljava/lang/Object;", ordinal = 1))
    Object noZstdForYou(ConfigSpec<Boolean> spec, Operation<Boolean> original) { return true; }
}
