package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.eu.net.pool.hexic.HexicKt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IotaType.class, remap = false)
public class IotaTypeMixin {
    @WrapMethod(method = "deserializeIota")
    private static Iota wrapDeserialize(NbtCompound tag, ServerWorld world, Operation<Iota> original) {
        return HexicKt.deserializeHook(tag, world, original);
    }
    
    @WrapMethod(method = "serialize")
    private static NbtCompound wrapSerialize(Iota iota, Operation<NbtCompound> original) {
        return HexicKt.serializeHook(iota, original);
    }
}
