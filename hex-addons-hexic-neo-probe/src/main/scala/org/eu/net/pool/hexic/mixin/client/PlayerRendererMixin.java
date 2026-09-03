package org.eu.net.pool.hexic.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.eu.net.pool.hexic.hexcompat.CatMorphCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
        method = {"renderRightHand", "renderLeftHand"},
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexic$hideFirstPersonHands(
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        AbstractClientPlayer player,
        CallbackInfo callback
    ) {
        if (CatMorphCompat.hidesPlayerHands(player)) {
            callback.cancel();
        }
    }
}
