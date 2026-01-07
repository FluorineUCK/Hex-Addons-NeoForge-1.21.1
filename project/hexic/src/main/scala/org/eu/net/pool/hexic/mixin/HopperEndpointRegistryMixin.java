package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import miyucomics.hexical.features.hopper.HopperEndpointRegistry;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(HopperEndpointRegistry.class)
public class HopperEndpointRegistryMixin {
    @WrapOperation(method = "init$lambda$0", at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;getCastingEntity()Lnet/minecraft/entity/LivingEntity;"))
    private static LivingEntity modifyCaster(CastingEnvironment instance, Operation<LivingEntity> original, @Local(argsOnly = true) Iota iota) {
        if (iota instanceof EntityIota entityIota && entityIota.getEntity() instanceof LivingEntity entity)
            return entity;
        else
            return original.call(instance);
    }
}
