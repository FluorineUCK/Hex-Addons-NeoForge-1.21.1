package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.eu.net.pool.hexic.CatHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// rephrased from trickster
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void morphDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity disguise = (CatEntity) CatHolder.getCat(this);
        if (disguise != null) cir.setReturnValue(disguise.getDimensions(pose));
    }

    @WrapOperation(method = {"playHurtSound", "onDamaged"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHurtSound(Lnet/minecraft/entity/damage/DamageSource;)Lnet/minecraft/sound/SoundEvent;"))
    private SoundEvent morphHurtSound(LivingEntity instance, DamageSource source, Operation<SoundEvent> original) {
        LivingEntity disguise = (CatEntity) CatHolder.getCat(this);
        return disguise != null ? SoundEvents.ENTITY_CAT_HURT : original.call(instance, source);
    }

    @ModifyExpressionValue(method = {"computeFallDamage", "canBreatheInWater"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getType()Lnet/minecraft/entity/EntityType;"))
    private EntityType<?> modifyEntityType(EntityType<?> original) {
        LivingEntity disguise = (CatEntity) CatHolder.getCat(this);
        return disguise != null ? disguise.getType() : original;
    }
}
