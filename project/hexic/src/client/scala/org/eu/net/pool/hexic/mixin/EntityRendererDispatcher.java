package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.CatEntity;
import org.eu.net.pool.hexic.CatHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRendererDispatcher {
    @Inject(
            method = "render", at = @At("HEAD")
    )
    private void replaceYouWithCat(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci, @Local(argsOnly = true) LocalRef<Entity> entityRef) {
        LivingEntity disguise = (CatEntity) CatHolder.getSyncedCat(entity);
        if (disguise != null) entityRef.set(disguise);
    }
}
