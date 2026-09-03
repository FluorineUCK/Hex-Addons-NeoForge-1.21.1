package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import miyucomics.hexcellular.action.OpObserveProperty;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpObserveProperty.class)
public class OpObservePropertyMixin {
    @WrapOperation(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lmiyucomics/hexcellular/PropertyIotaKt;getProperty(Ljava/util/List;II)Ljava/lang/String;"
        )
    )
    private String hexic$acceptPropertyStream(
        List<? extends Iota> args,
        int idx,
        int argc,
        Operation<String> original,
        @Cancellable CallbackInfoReturnable<List<Iota>> cir
    ) {
        return Extern.observePropertyHook(
            args,
            idx,
            argc,
            () -> original.call(args, idx, argc),
            cir
        );
    }
}
