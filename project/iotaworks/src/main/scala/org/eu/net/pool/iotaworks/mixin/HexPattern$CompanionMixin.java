package org.eu.net.pool.iotaworks.mixin;

import org.eu.net.pool.iotaworks.HexPatternAccessor;

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

@Mixin(HexPattern.Companion.class)
class HexPattern$CompanionMixin {
    @WrapMethod(method = "fromNBT")
    public HexPattern fromNBT(NbtCompound c, Operation<HexPattern> original) {
        // see stupid reasoning in HexPatternMixin
        if (c.get("parent") instanceof NbtCompound c1) {
            HexPattern p = original.call(c1);
            ((HexPatternAccessor) (Object) p).depth_$eq(c.getInt("level"));
            return p;
        } else {
            return original.call(c);
        }
    }
}