package org.eu.net.pool.iotaworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.nbt.NbtCompound;

@Mixin(HexPattern.class)
public class HexPatternMixin {
    private int depth = 0;
    @WrapOperation(method = "<clinit>", at = @At(value = "FIELD", opcode = 179))
    private static void wrapCodec(Codec<HexPattern> codec, Operation<Void> original) {
        original.call(Codec.<HexPattern, HexPattern>either(RecordCodecBuilder.create(b ->
            b.<HexPattern, Integer>group(
                codec.fieldOf("parent").forGetter(p -> p),
                Codec.INT.fieldOf("level").forGetter(p -> ((HexPatternMixin) (Object) p).depth)
            ).apply(b, (p, l) -> {
                ((HexPatternMixin) (Object) p).depth = l;
                return p;
            })
        ), codec).xmap(e -> {
            HexPattern value[] = new HexPattern[1];
            e.ifLeft(v -> { value[0] = v; });
            e.ifRight(v -> { value[0] = v; });
            return value[0];
        }, Either::left));
    }
}
