package org.net.eu.pool.mica.client

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper
import net.minecraft.client.render.{BlockRenderLayer, RenderLayers, TexturedRenderLayers}
import net.minecraft.client.texture.SpriteAtlasTexture

def init(): Unit =
	WorldRenderEvents.BEFORE_ENTITIES.register: ctx =>
		val buf = ctx.consumers.getBuffer(TexturedRenderLayers.getEntitySolid)
		buf.vertex(0, 0, 0, 0xFFFFFFFF, 0, 0, 0, 15, 0, 1, 0)
		buf.vertex(0, 0, 16, 0xFFFFFFFF, 0, 1, 0, 15, 0, 1, 0)
		buf.vertex(16, 0, 16, 0xFFFFFFFF, 1, 1, 0, 15, 0, 1, 0)
		buf.vertex(16, 0, 0, 0xFFFFFFFF, 1, 0, 0, 15, 0, 1, 0)

def datagen(using FabricDataGenerator): Unit =
	println("Hello, datagen!")