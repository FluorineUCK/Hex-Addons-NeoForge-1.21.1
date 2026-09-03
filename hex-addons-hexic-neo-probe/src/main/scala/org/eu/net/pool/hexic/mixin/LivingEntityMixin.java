package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cat;
import org.eu.net.pool.hexic.CatHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the gameplay-facing half of the instant-cat disguise. Rendering is
 * isolated in the client-only mixin configuration.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void hexic$catDimensions(
        Pose pose,
        CallbackInfoReturnable<EntityDimensions> callback
    ) {
        Cat disguise = CatHolder.getCat((Entity) (Object) this);
        if (disguise != null) {
            callback.setReturnValue(disguise.getDimensions(pose));
        }
    }

    @WrapOperation(
        method = {"playHurtSound", "handleDamageEvent"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;"
        )
    )
    private SoundEvent hexic$catHurtSound(
        LivingEntity entity,
        DamageSource source,
        Operation<SoundEvent> original
    ) {
        return CatHolder.getCat(entity) != null
            ? SoundEvents.CAT_HURT
            : original.call(entity, source);
    }

    @ModifyExpressionValue(
        method = {"calculateFallDamage", "canBreatheUnderwater"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getType()Lnet/minecraft/world/entity/EntityType;"
        )
    )
    private EntityType<?> hexic$catEntityType(EntityType<?> original) {
        Cat disguise = CatHolder.getCat((Entity) (Object) this);
        return disguise != null ? disguise.getType() : original;
    }
}
