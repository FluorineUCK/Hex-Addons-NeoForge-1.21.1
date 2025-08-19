package org.net.eu.pool.mica.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private BufferBuilderStorage bufferBuilders;
    @Shadow private @Nullable ClientWorld world;
    @Unique private final WorldRenderContextImpl mica_context = new WorldRenderContextImpl();

    @Inject(method = "render", at = @At("HEAD"))
    private void beforeRender(ObjectAllocator objectAllocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, GpuBufferSlice slice, Vector4f skyColor, boolean thinFog, CallbackInfo ci) {
        mica_context.prepare((WorldRenderer) (Object) this, tickCounter, renderBlockOutline, camera, this.client.gameRenderer, positionMatrix, projectionMatrix, bufferBuilders.getEntityVertexConsumers(), MinecraftClient.isFabulousGraphicsOrBetter(), world);
    }

        @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderEntities(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/RenderTickCounter;Ljava/util/List;)V"
            )
    )
    private void afterTerrainSolid(CallbackInfo ci, @Local MatrixStack matrixStack) {
        mica_context.setMatrixStack(matrixStack);
        org.net.eu.pool.mica.client.MicaClient$package.renderRunes(mica_context);
    }
}
