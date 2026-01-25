package org.eu.net.pool.iotaworks.mixin;

import org.eu.net.pool.iotaworks.HexPatternAccessor;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.nbt.NbtCompound;

@Mixin(HexPattern.class)
public class HexPatternMixin implements HexPatternAccessor {
    private int depth = 0;
    public int depth() { return depth; }
    public void depth_$eq(int depth) { this.depth = depth; }

    @Shadow @Final @Mutable public static Codec CODEC; // we don't actually use this field, but we need to @Mutable it so the wrapop can use it

    @WrapOperation(method = "<clinit>", at = @At(value = "FIELD", opcode = 179, ordinal = 1))
    private static void wrapCodec(Codec<HexPattern> codec, Operation<Void> original) {
        original.call(Codec.<HexPattern, HexPattern>either(RecordCodecBuilder.create(b ->
            b.<HexPattern, Integer>group(
                codec.fieldOf("parent").forGetter(p -> p),
                Codec.INT.fieldOf("level").forGetter(p -> ((HexPatternMixin) (Object) p).depth)
            ).apply(b, (p, l) -> {
                ((HexPatternMixin) (Object) p).depth = l;
                return p;
            })
        ), codec).xmap(e -> e.left().or(e::right).get(), Either::left));
    }

    @WrapMethod(method = "serializeToNBT")
    private NbtCompound wrapSerialize(Operation<NbtCompound> original) {
        // ideally we'd just shove it into the compound, but codecs can't do that
        NbtCompound c = new NbtCompound();
        c.put("parent", original.call());
        c.putInt("level", depth);
        return c;
    }
}
