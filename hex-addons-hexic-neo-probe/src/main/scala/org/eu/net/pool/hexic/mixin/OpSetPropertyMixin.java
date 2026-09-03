package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import miyucomics.hexcellular.action.OpSetProperty;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpSetProperty.class)
public class OpSetPropertyMixin {
    @WrapOperation(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lmiyucomics/hexcellular/PropertyIotaKt;getProperty(Ljava/util/List;II)Ljava/lang/String;"
        )
    )
    private String hexic$acceptPropertyWriter(
        List<? extends Iota> args,
        int idx,
        int argc,
        Operation<String> original,
        @Cancellable CallbackInfoReturnable<List<Iota>> cir
    ) {
        return Extern.writePropertyHook(
            args,
            idx,
            argc,
            () -> original.call(args, idx, argc),
            cir
        );
    }
}
