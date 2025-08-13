package org.net.eu.pool.mica.client

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.{BlockRenderLayer, RenderLayers, TexturedRenderLayers, VertexConsumer}
import net.minecraft.client.texture.{Sprite, SpriteAtlasTexture, SpriteLoader}
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.net.eu.pool.mica.{AbstractRuneStorage, HasRegistry, Rune, RuneShift}

import scala.util.chaining.scalaUtilChainingOps

def blockAtlas = MinecraftClient.getInstance.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(_)

type V3 = (x: Float, y: Float, z: Float)
type UV = (u: Float, v: Float)
type I2 = (Int, Int)
type RGB = (r: Float, g: Float, b: Float, a: Float)

case class Vertex(pos: V3, uv: UV, color: RGB, light: I2, overlay: I2)

given runeExt: AnyRef with
	extension (r: Rune)
		def sprite: Sprite = blockAtlas(summon[HasRegistry[Rune]].registry.getId(r).withPrefixedPath("block/rune_"))
		def surface: Sprite = blockAtlas(r.surfaceSprite)

given v3Ext: AnyRef with
	extension (v: V3)
		def +(other: V3): V3 =
			(x = v.x + other.x, y = v.y + other.y, z = v.z + other.z)
		def -(other: V3): V3 =
			(x = v.x - other.x, y = v.y - other.y, z = v.z - other.z)
given uvExt: AnyRef with
	extension (v: UV)
		def +(other: UV): UV =
			(u = v.u + other.u, v = v.v + other.v)
		def -(other: UV): UV =
			(u = v.u - other.u, v = v.v - other.v)

def vert(vertex: Vertex, normal: V3)(using c: VertexConsumer, stack: MatrixStack): Unit =
	val Vertex(pos, uv, color, light, overlay) = vertex
	c.vertex(stack.peek, pos.x, pos.y, pos.z)
	.texture(uv.u, uv.v)
	.color(color.r, color.g, color.b, color.a)
	.light(light._1, light._2)
	.overlay(overlay._1, overlay._1)
	.normal(stack.peek, normal.x, normal.y, normal.z)

// vtx0 ─── vtx1
//   │       │
// vtx3 ─── vtx2
def quad(vtx0: Vertex, vtx1: Vertex, vtx2: Vertex, vtx3: Vertex, normal: V3)(using VertexConsumer, MatrixStack): Unit =
	vert(vtx0, normal)
	vert(vtx1, normal)
	vert(vtx2, normal)
	vert(vtx3, normal)

def rect(vtx0: Vertex, vtx1: Vertex, vtx2: Vertex, normal: V3)(using VertexConsumer, MatrixStack): Unit =
	// vtx0 ─── vtx1
	//   │       │
	// vtx3 ─── vtx2
	val vtx3 = Vertex(pos = vtx0.pos + (vtx2.pos - vtx1.pos), uv = vtx0.uv + (vtx2.uv - vtx1.uv), color = vtx1.color, light = vtx1.light, overlay = vtx1.overlay)
	quad(vtx0, vtx1, vtx2, vtx3, normal = normal)

def withMatrices[T](body: => T)(using m: MatrixStack): T =
	m.push()
	try
		body
	finally
		m.pop()

def init(): Unit =
	WorldRenderEvents.BEFORE_ENTITIES.register: ctx =>
		given buf: VertexConsumer = ctx.consumers.getBuffer(TexturedRenderLayers.getEntityCutout)
		given matrices: MatrixStack = ctx.matrixStack()
		withMatrices:
			matrices.translate(ctx.camera.getPos.negate)-
			var cur = BlockPos.Mutable()
			for (k, i) <- AbstractRuneStorage.keys.zipWithIndex do
				val c: AbstractRuneStorage = ctx.world.getComponent(k)
				withMatrices:
					val shift = RuneShift(i)
					matrices.translate(shift.x / 4.0 - 0.5, shift.y / 8.0 - 0.5, shift.z / 4.0 - 0.5)
					c.contents.forEach: (pos, rune) =>
						cur.set(pos)
						// TODO: more quads
						quad(
							Vertex(pos = (cur.getX + 0.25f, cur.getY + 0.625f, cur.getZ + 0.25f), uv = (rune.surface.getMinU, rune.surface.getMinV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.25f, cur.getY + 0.625f, cur.getZ + 0.75f), uv = (rune.surface.getMinU, rune.surface.getMaxV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.75f, cur.getY + 0.625f, cur.getZ + 0.75f), uv = (rune.surface.getMaxU, rune.surface.getMaxV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.75f, cur.getY + 0.625f, cur.getZ + 0.25f), uv = (rune.surface.getMaxU, rune.surface.getMinV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							normal = (0, 1, 0),
						)
						quad(
							Vertex(pos = (cur.getX + 0.25f, cur.getY + 0.626f, cur.getZ + 0.25f), uv = (rune.sprite.getMinU, rune.sprite.getMinV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.25f, cur.getY + 0.626f, cur.getZ + 0.75f), uv = (rune.sprite.getMinU, rune.sprite.getMaxV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.75f, cur.getY + 0.626f, cur.getZ + 0.75f), uv = (rune.sprite.getMaxU, rune.sprite.getMaxV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							Vertex(pos = (cur.getX + 0.75f, cur.getY + 0.626f, cur.getZ + 0.25f), uv = (rune.sprite.getMaxU, rune.sprite.getMinV), color = (1, 1, 1, 1), light = (255, 255), overlay = (0, 15)),
							normal = (0, 1, 0),
						)

def datagen(using FabricDataGenerator): Unit =
	println("Hello, datagen!")