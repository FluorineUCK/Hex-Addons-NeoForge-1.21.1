package org.net.eu.pool.mica.client

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper
import net.minecraft.client.render.{BlockRenderLayer, RenderLayers, TexturedRenderLayers, VertexConsumer}
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.util.math.MatrixStack

case class Vertex(pos: (x: Float, y: Float, z: Float), uv: (u: Float, v: Float), color: (r: Float, g: Float, b: Float, a: Float), light: (Int, Int), overlay: (Int, Int))

def vert(vertex: Vertex, normal: (x: Float, y: Float, z: Float))(using c: VertexConsumer, stack: MatrixStack): Unit =
	val Vertex(pos, uv, color, light, overlay) = vertex
	c.vertex(stack.peek, pos.x, pos.y, pos.z)
	.texture(uv.u, uv.v)
	.color(color.r, color.g, color.b, color.a)
	.light(light._1, light._2)
	.overlay(overlay._1, overlay._1)
	.normal(stack.peek, normal.x, normal.y, normal.z)

def quad(vtx0: Vertex, vtx1: Vertex, vtx2: Vertex, vtx3: Vertex, normal: (x: Float, y: Float, z: Float))(using VertexConsumer, MatrixStack) =
	vert(vtx0, normal)
	vert(vtx1, normal)
	vert(vtx2, normal)
	vert(vtx3, normal)

def init(): Unit =
	WorldRenderEvents.BEFORE_ENTITIES.register: ctx =>
		given buf: VertexConsumer = ctx.consumers.getBuffer(TexturedRenderLayers.getEntitySolid)
		given matrices: MatrixStack = ctx.matrixStack()
		matrices.push()
		try
			matrices.translate(ctx.camera.getPos.negate)
			quad(
				Vertex(pos = (0, 0, 0), uv = (0, 0), color = (1, 1, 1, 1), light = (15, 15), overlay = (0, 0)),
				Vertex(pos = (16, 0, 0), uv = (1, 0), color = (1, 1, 1, 1), light = (15, 15), overlay = (0, 0)),
				Vertex(pos = (16, 0, 16), uv = (1, 1), color = (1, 1, 1, 1), light = (15, 15), overlay = (0, 0)),
				Vertex(pos = (0, 0, 16), uv = (0, 1), color = (1, 1, 1, 1), light = (15, 15), overlay = (0, 0)),
				normal = (0, 1, 0)
			)
		finally
			matrices.pop()

def datagen(using FabricDataGenerator): Unit =
	println("Hello, datagen!")