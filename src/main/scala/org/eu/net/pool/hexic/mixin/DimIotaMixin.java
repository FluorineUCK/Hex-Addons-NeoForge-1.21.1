package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.beholderface.oneironaut.casting.iotatypes.DimIota;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
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

    @WrapMethod(method = "deserialize(Lnet/minecraft/nbt/NbtElement;Lnet/minecraft/server/world/ServerWorld;)Lnet/beholderface/oneironaut/casting/iotatypes/DimIota;")
    DimIota wrapDeserialize(NbtElement tag, ServerWorld world, Operation<DimIota> original) {
        DimIota orig = original.call(tag, world);
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            System.out.printf("Dimension iota %s returns world %s%n", orig, orig.toWorld(world.getServer()));
        }
        if (orig.toWorld(world.getServer()) == null) {
            return null;
        } else {
            return orig;
        }
        // okay, what just happened
    }
}
