package org.eu.net.pool.hexic.hexcompat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.eu.net.pool.hexic.CastingEngine$;
import org.eu.net.pool.hexic.ChiselTable$;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;
import scala.collection.immutable.BitSet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Client-only renderer bridge kept out of common classloading paths. */
public final class HexicBlockEntityRenderers {
    private static final Logger LOGGER = LoggerFactory.getLogger("hexic");
    private static final ResourceLocation WHITE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("hexic", "textures/block/border.png");
    private static final RenderType CHISEL_PIXEL_RENDER_TYPE =
        RenderType.entityTranslucent(WHITE_TEXTURE, false);

    private HexicBlockEntityRenderers() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        BlockEntityType<BlockEntity> engineType = (BlockEntityType) CastingEngine$.MODULE$.entityType();
        BlockEntityType<BlockEntity> chiselTableType = (BlockEntityType) ChiselTable$.MODULE$.entityType();
        event.registerBlockEntityRenderer(engineType, context -> new CastingEngineRenderer());
        event.registerBlockEntityRenderer(chiselTableType, context -> new ChiselTableRenderer());
        if (Boolean.getBoolean("hexic.probe.validateClientHooks")) {
            LOGGER.info("[HEXIC-PROBE] block_entity_renderers=PASS engine=true chisel_table=true");
        }
    }

    private static final class CastingEngineRenderer implements BlockEntityRenderer<BlockEntity> {
        private static final ClassValue<Method> SIMULATE_PHYSICS = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    return type.getMethod("simulatePhysics", float.class);
                } catch (NoSuchMethodException exception) {
                    throw new IllegalStateException("Hexic casting engine lost simulatePhysics(float)", exception);
                }
            }
        };

        @Override
        public void render(
            BlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
        ) {
            float angle = simulatedAngle(blockEntity, partialTick);
            poseStack.pushPose();
            try {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.mulPose(Axis.YP.rotation(angle));
                Minecraft.getInstance().getItemRenderer().renderStatic(
                    new ItemStack(CastingEngine$.MODULE$.delegate()),
                    ItemDisplayContext.NONE,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    buffers,
                    blockEntity.getLevel(),
                    0
                );
            } finally {
                poseStack.popPose();
            }
        }

        @SuppressWarnings("unchecked")
        private static float simulatedAngle(BlockEntity blockEntity, float partialTick) {
            try {
                Tuple2<Object, Object> physics = (Tuple2<Object, Object>) SIMULATE_PHYSICS
                    .get(blockEntity.getClass())
                    .invoke(blockEntity, partialTick);
                return ((Number) physics._1()).floatValue();
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to simulate Hexic casting engine rendering", exception);
            }
        }
    }

    /**
     * Restores the original 14×14 field of animated, individually chiseled
     * amethyst pixels. The static block model is only the table body; without
     * this renderer every filled/cut pattern is invisible on the client.
     */
    private static final class ChiselTableRenderer implements BlockEntityRenderer<BlockEntity> {
        private static final ClassValue<Method> BITS = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    return type.getMethod("bits");
                } catch (NoSuchMethodException exception) {
                    throw new IllegalStateException("Hexic chisel table lost bits()", exception);
                }
            }
        };

        @Override
        public void render(
            BlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
        ) {
            BitSet bits = bits(blockEntity);
            if (bits.isEmpty()) {
                return;
            }

            VertexConsumer consumer = buffers.getBuffer(CHISEL_PIXEL_RENDER_TYPE);
            double time = (blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime()) + partialTick;
            for (int bit = 0; bit < 14 * 16; bit++) {
                if (!bits.contains(bit)) {
                    continue;
                }
                int x = bit / 16;
                int z = bit % 16;
                if (x >= 14 || z >= 14) {
                    continue;
                }

                float phase = x * 3.0F + z - 5.0F;
                float lighten = (float) Math.sin(phase / 20.0F + time / 400.0);
                float darken = (float) Math.sin(phase / 24.0F + time / 400.0);
                float red = 1.0F - Math.max(darken, 0.0F) * 0.08F;
                float green = 0.6F + lighten * 0.125F;
                float blue = 1.0F + Math.min(darken, 0.0F) * 0.08F;

                float minX = (x + 1) / 16.0F;
                float minY = 12.0F / 16.0F;
                float minZ = (z + 1) / 16.0F;
                float maxX = (x + 2) / 16.0F;
                float maxY = 13.0F / 16.0F;
                float maxZ = (z + 2) / 16.0F;
                renderCube(
                    consumer,
                    poseStack.last(),
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    red,
                    green,
                    blue,
                    packedLight,
                    packedOverlay
                );
            }
        }

        private static BitSet bits(BlockEntity blockEntity) {
            try {
                return (BitSet) BITS.get(blockEntity.getClass()).invoke(blockEntity);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to read Hexic chisel-table pixels", exception);
            }
        }
    }

    private static void renderCube(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        float red,
        float green,
        float blue,
        int packedLight,
        int packedOverlay
    ) {
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, 0, 1, 0,
            minX, maxY, maxZ, 0, 0,
            maxX, maxY, maxZ, 1, 0,
            maxX, maxY, minZ, 1, 1,
            minX, maxY, minZ, 0, 1);
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, 0, -1, 0,
            minX, minY, minZ, 0, 0,
            maxX, minY, minZ, 1, 0,
            maxX, minY, maxZ, 1, 1,
            minX, minY, maxZ, 0, 1);
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, 0, 0, -1,
            maxX, minY, minZ, 0, 1,
            minX, minY, minZ, 1, 1,
            minX, maxY, minZ, 1, 0,
            maxX, maxY, minZ, 0, 0);
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, 0, 0, 1,
            minX, minY, maxZ, 0, 1,
            maxX, minY, maxZ, 1, 1,
            maxX, maxY, maxZ, 1, 0,
            minX, maxY, maxZ, 0, 0);
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, -1, 0, 0,
            minX, minY, minZ, 0, 1,
            minX, minY, maxZ, 1, 1,
            minX, maxY, maxZ, 1, 0,
            minX, maxY, minZ, 0, 0);
        quad(consumer, pose, red, green, blue, packedLight, packedOverlay, 1, 0, 0,
            maxX, minY, maxZ, 0, 1,
            maxX, minY, minZ, 1, 1,
            maxX, maxY, minZ, 1, 0,
            maxX, maxY, maxZ, 0, 0);
    }

    private static void quad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float red,
        float green,
        float blue,
        int packedLight,
        int packedOverlay,
        float normalX,
        float normalY,
        float normalZ,
        float x1,
        float y1,
        float z1,
        float u1,
        float v1,
        float x2,
        float y2,
        float z2,
        float u2,
        float v2,
        float x3,
        float y3,
        float z3,
        float u3,
        float v3,
        float x4,
        float y4,
        float z4,
        float u4,
        float v4
    ) {
        vertex(consumer, pose, x1, y1, z1, u1, v1, red, green, blue, packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(consumer, pose, x2, y2, z2, u2, v2, red, green, blue, packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(consumer, pose, x3, y3, z3, u3, v3, red, green, blue, packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(consumer, pose, x4, y4, z4, u4, v4, red, green, blue, packedLight, packedOverlay, normalX, normalY, normalZ);
    }

    private static void vertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float x,
        float y,
        float z,
        float u,
        float v,
        float red,
        float green,
        float blue,
        int packedLight,
        int packedOverlay,
        float normalX,
        float normalY,
        float normalZ
    ) {
        consumer
            .addVertex(pose, x, y, z)
            .setColor(red, green, blue, 1.0F)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(pose, normalX, normalY, normalZ);
    }
}
