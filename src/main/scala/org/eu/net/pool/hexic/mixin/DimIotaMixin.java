package org.eu.net.pool.hexic.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import at.petrak.hexcasting.api.casting.iota.IotaType;

@Mixin(targets = "net.beholderface.oneironaut.casting.iotatypes.DimIota$1")
abstract class DimIotaMixin {
    @WrapOperation(method = "display", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;of(Ljava/lang/String;)Lnet/minecraft/text/Text;"))
    Text getName(String dim, Operation<Text> original) {
        return dim.startsWith("hexic:fresh_") ? org.eu.net.pool.hexic.Extern.getPocketName(dim) : original.call(dim);
    }
}
