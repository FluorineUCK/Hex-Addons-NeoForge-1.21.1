package org.eu.net.pool.hexic.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.eu.net.pool.hexic.CatHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// rephrased from trickster
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    private void stealsYourArmsCutely(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                   AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (CatHolder.getCat(player) != null) ci.cancel();
    }
}
