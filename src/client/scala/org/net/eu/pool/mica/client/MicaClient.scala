package org.net.eu.pool.mica.client

import com.google.gson.{JsonArray, JsonElement, JsonObject}
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.{BlockRenderLayer, RenderLayers, TexturedRenderLayers, VertexConsumer}
import net.minecraft.client.texture.{Sprite, SpriteAtlasTexture, SpriteLoader}
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.{BlockPos, Direction}
import org.net.eu.pool.mica.{AbstractRuneStorage, EmptyRune, EndQuoteRune, HasRegistry, registryFor, QuoteRune, Rune, RuneShift, given}
import net.minecraft.client.data.{BlockStateModelGenerator, ItemModelGenerator, ItemModels, Model, ModelIds, ModelSupplier}
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.util.math.Direction.Axis

import scala.util.chaining.scalaUtilChainingOps

def blockAtlas = MinecraftClient.getInstance.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(_)

type V3 = (x: Float, y: Float, z: Float)
type UV = (u: Float, v: Float)
type I2 = (Int, Int)
type RGB = (r: Float, g: Float, b: Float, a: Float)

case class Vertex(pos: V3, uv: UV, color: RGB, light: I2, overlay: I2)

given runeExt: AnyRef with
	extension (r: Rune)
		def spriteTexture: Identifier = registryFor[Rune].getId(r).withPrefixedPath("block/rune_")
		def sprite: Sprite = blockAtlas(r.spriteTexture)
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
			matrices.translate(ctx.camera.getPos.negate)
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

class ModelBuilder extends ModelSupplier:
	private val elements = JsonArray()
	private val textures = JsonObject()
	opaque type Key = String
	class Element private[ModelBuilder](faces: JsonObject):
		def face(dir: Direction, texture: Key, uv: (from: UV, to: UV) = null, cullface: Direction = null, rotation: 0 | 90 | 180 | 270 = 0, tintindex: Int = -1): Unit =
			if faces.has(dir.toString) then
				throw IllegalArgumentException(s"Duplicate face $dir in element")
			val faceObj = JsonObject()
			faces.addProperty("texture", s"%$texture")
			if uv != null then faces.add("uv", fromTuple((uv.from.u: Float, uv.from.v: Float, uv.to.u: Float, uv.to.v: Float)))
			if rotation != 0 then faces.addProperty("rotation", rotation)
			if tintindex >= 0 then faces.addProperty("tintindex", tintindex)
			faces.add(dir.toString, faceObj)
	private def fromTuple(t: V3): JsonArray =
		val ary = JsonArray()
		ary.add(t.x)
		ary.add(t.y)
		ary.add(t.z)
		ary
	private def fromTuple(t: UV): JsonArray =
		val ary = JsonArray()
		ary.add(t.u)
		ary.add(t.v)
		ary
	private def fromTuple(t: (Float, Float, Float, Float)): JsonArray =
		val ary = JsonArray()
		ary.add(t._1)
		ary.add(t._2)
		ary.add(t._3)
		ary.add(t._4)
		ary
	def texture(name: String, value: Identifier): Key =
		if textures.has(name) then
			throw IllegalArgumentException(s"Duplicate texture $name")
		textures.addProperty(name, value.toString)
		name
	def element(name: String = null, from: V3, to: V3, rotation: (origin: V3, axis: Axis, angle: Float, rescale: Boolean) = null): Element =
		val el = JsonObject()
		if name != null then el.addProperty("name", name)
		el.add("from", fromTuple(from))
		el.add("to", fromTuple(to))
		if rotation != null then
			val rotObject = JsonObject()
			rotObject.add("origin", fromTuple(rotation.origin))
			rotObject.addProperty("axis", rotation.axis.toString)
			rotObject.addProperty("angle", rotation.angle)
			rotObject.addProperty("rescale", rotation.rescale)
			el.add("rotation", rotObject)
		val facesObj = JsonObject()
		elements.add(el)
		Element(el)

	override def get: JsonElement =
		val obj = JsonObject()
		obj.add("textures", textures)
		obj.add("elements", elements)
		obj

def datagenRune(rune: Rune)(using pack: FabricDataGenerator#Pack) =
	pack.addProvider(
		new FabricModelProvider(_) {
			override def getName: String = s"${super.getName} for rune ${registryFor[Rune].getId(rune)}"
			override def generateBlockStateModels(using gen: BlockStateModelGenerator): Unit = ()
			override def generateItemModels(using gen: ItemModelGenerator): Unit =
				gen.register(rune.item.value)
				gen.modelCollector.accept(ModelIds.getItemModelId(rune.item.value), {
					val m = ModelBuilder()
					val surface = m.texture("surface", rune.surfaceSprite)
					val sprite = m.texture("sprite", rune.spriteTexture)
					val bottom = m.element(from = (4, 0, 4), to = (8, 2, 8))
					bottom.face(Direction.UP, texture = surface, uv = (from = (0, 0), to = (1, 1)))
					m
				})
		}
	)

def datagen(using gen: FabricDataGenerator): Unit =
	given FabricDataGenerator#Pack = gen.createPack()
	datagenRune(EmptyRune)
	datagenRune(QuoteRune)
	datagenRune(EndQuoteRune)