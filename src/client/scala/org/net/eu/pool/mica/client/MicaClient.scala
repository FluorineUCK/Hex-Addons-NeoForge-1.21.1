package org.net.eu.pool.mica.client

import com.google.gson.{JsonArray, JsonElement, JsonObject}
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.brigadier.builder.{ArgumentBuilder, LiteralArgumentBuilder}
import net.fabricmc.fabric.api.client.command.v2.{ClientCommandManager, ClientCommandRegistrationCallback, FabricClientCommandSource}
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.{WorldRenderContext, WorldRenderEvents}
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricAdvancementProvider, FabricLootTableProvider, SimpleFabricLootTableProvider}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper
import net.minecraft.advancement.criterion.{Criteria, ImpossibleCriterion}
import net.minecraft.advancement.{Advancement, AdvancementCriterion, AdvancementDisplay, AdvancementDisplays, AdvancementEntry, AdvancementFrame, AdvancementRequirements, AdvancementRewards}
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.{BlockRenderLayer, OverlayTexture, RenderLayer, RenderLayers, Tessellator, TexturedRenderLayers, VertexConsumer, VertexConsumerProvider, VertexFormats}
import net.minecraft.client.texture.{Sprite, SpriteAtlasTexture, SpriteLoader}
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.{ActionResult, AssetInfo, Formatting, Hand, Identifier}
import net.minecraft.util.math.{BlockPos, Direction, MathHelper}
import org.net.eu.pool.mica.{*, given}
import net.minecraft.client.data.{BlockStateModelGenerator, ItemModelGenerator, ItemModels, Model, ModelIds, ModelSupplier}
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.data.DataWriter
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.loot.{LootPool, LootTable}
import net.minecraft.network.packet.CustomPayload
import net.minecraft.registry.{Registries, RegistryKey, RegistryKeys, RegistryWrapper}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Direction.Axis
import net.minecraft.world.World
import org.joml.{Matrix3f, Quaternionf, Quaternionfc}
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

import java.nio.file.{Files, Path}
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.{BiConsumer, Consumer}
import scala.annotation.targetName
import scala.util.chaining.scalaUtilChainingOps
import scala.collection.convert.ImplicitConversions.given
import scala.util.boundary.Label

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

class Renderer(val buffer: VertexConsumer, val matrices: MatrixStack):
  var sprite: Option[Sprite] = None

  def forSprite(uv: UV): UV =
    sprite match
      case Some(value) => (
        u = MathHelper.lerp(uv.u, value.getMinU, value.getMaxU),
        v = MathHelper.lerp(uv.v, value.getMinV, value.getMaxV),
      )
      case None => uv

  def vert(vertex: Vertex, normal: V3): Unit =
    val Vertex(pos, uv, color, light, overlay) = vertex
    val newUV = forSprite(uv)
    buffer.vertex(matrices.peek, pos.x, pos.y, pos.z)
      .texture(newUV.u, newUV.v)
      .color(color.r, color.g, color.b, color.a)
      .light(light._1, light._2)
      .overlay(overlay._1, overlay._2)
      .normal(matrices.peek, normal.x, normal.y, normal.z)

  // vtx0 ─── vtx1
  //   │       │
  // vtx3 ─── vtx2
  def quad(vtx0: Vertex, vtx1: Vertex, vtx2: Vertex, vtx3: Vertex, normal: V3): Unit =
    vert(vtx0, normal)
    vert(vtx1, normal)
    vert(vtx2, normal)
    vert(vtx3, normal)

  def rect(vtx0: Vertex, vtx1: Vertex, vtx2: Vertex, normal: V3): Unit =
    // vtx0 ─── vtx1
    //   │       │
    // vtx3 ─── vtx2
    val vtx3 = Vertex(pos = vtx0.pos + (vtx2.pos - vtx1.pos), uv = vtx0.uv + (vtx2.uv - vtx1.uv), color = vtx1.color, light = vtx1.light, overlay = vtx1.overlay)
    quad(vtx0, vtx1, vtx2, vtx3, normal = normal)
object Renderer:
  def get(using VertexConsumer, MatrixStack): Renderer = Renderer(summon, summon)
  def forLayer(layer: RenderLayer)(using p: VertexConsumerProvider, m: MatrixStack) = Renderer(p.getBuffer(layer), m)

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

inline def withMatrices[T](body: => T)(using m: MatrixStack): T =
  m.push()
  try
    body
  finally
    m.pop()

def renderRunes(ctx: WorldRenderContext): Unit =
  given matrices: MatrixStack = ctx.matrixStack
  given VertexConsumerProvider = ctx.consumers
  given r: Renderer = Renderer.forLayer(TexturedRenderLayers.getEntityCutout)
  withMatrices:
    matrices.translate(ctx.camera.getPos.negate)
    var cur = BlockPos.Mutable()
    RenderSystem.teardownOverlayColor()
    for (k, i) <- AbstractRuneStorage.keys.zipWithIndex do
      val c: AbstractRuneStorage = ctx.world.getComponent(k)
      withMatrices:
        val shift = RuneShift(i)
        matrices.translate(shift.x / 4.0, shift.y / 8.0, shift.z / 4.0)
        c.contents.forEach: (pos, rune) =>
          cur.set(pos)
          // TODO: more quads
          withMatrices:
            matrices.translate(cur.getX, cur.getY, cur.getZ)
            val color: RGB = (1, 1, 1, 1)
            val light: I2 = (255, 0)
            val overlay: I2 = (0, 10) // magic number owo scary
            r.sprite = Some(rune.surface)
            r.quad(
              Vertex(pos = (-0.25f, 0.125f, -0.25f), uv = (0, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (-0.25f, 0.125f, 0.25f), uv = (0, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.25f, 0.125f, 0.25f), uv = (1, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.25f, 0.125f, -0.25f), uv = (1, 0), color = color, light = light, overlay = overlay),
              normal = (0, 1, 0),
            )
            r.quad(
              Vertex(pos = (-0.249f, 0f, -0.249f), uv = (0.5f, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (-0.249f, 0.125f, -0.249f), uv = (0.25f, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.249f, 0.125f, -0.249f), uv = (0.25f, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.249f, 0f, -0.249f), uv = (0.5f, 1), color = color, light = light, overlay = overlay),
              normal = (0, 0, -1)
            )
            r.quad(
              Vertex(pos = (0.249f, 0f, 0.249f), uv = (0.25f, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.249f, 0.125f, 0.249f), uv = (0.5f, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (-0.249f, 0.125f, 0.249f), uv = (0.5f, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (-0.249f, 0f, 0.249f), uv = (0.25f, 1), color = color, light = light, overlay = overlay),
              normal = (0, 0, 1)
            )
            withMatrices:
              matrices.peek.getPositionMatrix.mul(0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1)
              matrices.peek.getNormalMatrix.mul(Matrix3f(0, 0, -1, 0, 1, 0, 1, 0, 0))
              r.quad(
                Vertex(pos = (-0.249f, 0f, -0.249f), uv = (0.5f, 0), color = color, light = light, overlay = overlay),
                Vertex(pos = (0.249f, 0f, -0.249f), uv = (0.5f, 1), color = color, light = light, overlay = overlay),
                Vertex(pos = (0.249f, 0.125f, -0.249f), uv = (0.25f, 1), color = color, light = light, overlay = overlay),
                Vertex(pos = (-0.249f, 0.125f, -0.249f), uv = (0.25f, 0), color = color, light = light, overlay = overlay),
                normal = (0, 0, -1)
              )
              r.quad(
                Vertex(pos = (0.249f, 0f, 0.249f), uv = (0.25f, 0), color = color, light = light, overlay = overlay),
                Vertex(pos = (-0.249f, 0f, 0.249f), uv = (0.25f, 1), color = color, light = light, overlay = overlay),
                Vertex(pos = (-0.249f, 0.125f, 0.249f), uv = (0.5f, 1), color = color, light = light, overlay = overlay),
                Vertex(pos = (0.249f, 0.125f, 0.249f), uv = (0.5f, 0), color = color, light = light, overlay = overlay),
                normal = (0, 0, 1)
              )
            r.sprite = Some(rune.sprite)
            r.quad(
              Vertex(pos = (-0.25f, 0.126f, -0.25f), uv = (0, 0), color = color, light = light, overlay = overlay),
              Vertex(pos = (-0.25f, 0.126f, 0.25f), uv = (0, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.25f, 0.126f, 0.25f), uv = (1, 1), color = color, light = light, overlay = overlay),
              Vertex(pos = (0.25f, 0.126f, -0.25f), uv = (1, 0), color = color, light = light, overlay = overlay),
              normal = (0, 1, 0),
            )
  ()

def debugAxes(using matrices: MatrixStack, consumers: VertexConsumerProvider)(): Unit =
  val buf = consumers.getBuffer(RenderLayer.LINES)
  RenderSystem.lineWidth(2.0f)
  val matrix = matrices.peek
  def axis(x: Float, y: Float, z: Float) =
    buf.vertex(matrix, 0.0f, 0.0f, 0.0f).color(x, y, z, 1f).overlay(0, 15).normal(0, 0, 0)
    buf.vertex(matrix, x, y, z).color(x, y, z, 1f).overlay(0, 15).normal(0, 0, 0)
  axis(1, 0, 0)
  axis(0, 1, 0)
  axis(0, 0, 1)

class ModelBuilder extends ModelSupplier:
  private var parent: Option[Identifier] = None
  private val elements = JsonArray()
  private val textures = JsonObject()
  private val display = JsonObject()
  opaque type Key = String
  class Element private[ModelBuilder](faces: JsonObject):
    def face(dir: Direction, texture: Key, uv: (from: UV, to: UV) = null, cullface: Direction = null, rotation: 0 | 90 | 180 | 270 = 0, tintindex: Int = -1): Unit =
      if faces.has(dir.toString) then
        throw IllegalArgumentException(s"Duplicate face $dir in element")
      val faceObj = JsonObject()
      faceObj.addProperty("texture", s"#$texture")
      if uv != null then faceObj.add("uv", fromTuple((uv.from.u: Float, uv.from.v: Float, uv.to.u: Float, uv.to.v: Float)))
      if rotation != 0 then faceObj.addProperty("rotation", rotation)
      if tintindex >= 0 then faceObj.addProperty("tintindex", tintindex)
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
    el.add("faces", facesObj)
    elements.add(el)
    Element(facesObj)
  def parent(name: Identifier): Unit =
    parent match
      case None => parent = Some(name)
      case Some(value) => throw IllegalArgumentException(s"Attempt to change parent to '$name', but it is already specified as '$value'")

  def display(name: String, translation: V3, rotation: V3, scale: V3): Unit =
    if display.has(name) then
      throw IllegalArgumentException(s"Duplicate display $name")
    val obj = JsonObject()
    obj.add("rotation", fromTuple(rotation))
    obj.add("translation", fromTuple(translation))
    obj.add("scale", fromTuple(scale))
    display.add(name, obj)

  override def get: JsonElement =
    val obj = JsonObject()
    obj.add("textures", textures)
    obj.add("elements", elements)
    obj.add("display", display)
    parent.foreach(i => obj.addProperty("parent", i.toString))
    obj

trait EarlyBlockInteractionHandler:
  this: Item =>
  @targetName("mica$earlyUseOnBlock") def earlyUseOnBlock(using ServerPlayerEntity, World, Label[ActionResult])(stack: ItemStack, hand: Hand, hitResult: BlockHitResult): Unit

def datagenRune(rune: Rune)(using pack: FabricDataGenerator#Pack) =
  pack.addProvider(
    new FabricModelProvider(_) {
      override def getName: String = s"${super.getName} for rune ${registryFor[Rune].getId(rune)}"
      override def generateBlockStateModels(using gen: BlockStateModelGenerator): Unit = ()
      override def generateItemModels(using gen: ItemModelGenerator): Unit =
        gen.register(rune.item.value)
        gen.modelCollector.accept(ModelIds.getItemModelId(rune.item.value), {
          val m = ModelBuilder()
          m.parent(Identifier.ofVanilla("block/stone_pressure_plate"))
          val surface = m.texture("surface", rune.surfaceSprite)
          val sprite = m.texture("sprite", rune.spriteTexture)
          val bottom = m.element(from = (4, 0, 4), to = (12, 2, 12))
          bottom.face(Direction.UP, texture = surface, uv = (from = (0, 0), to = (16, 16)))
          m
        })
    }
  )
  val projectRoot = System.getenv("PROJECT_ROOT")
  if projectRoot != null then
    val texture = rune.spriteTexture
    val textureFile = Path.of(s"$projectRoot/src/client/resources/assets/${texture.getNamespace}/textures/${texture.getPath}.png")
    if !Files.exists(textureFile) then Files.createFile(textureFile)

given ServerExecutor:
  override def run(body: ClientExecutor ?=> Unit): Unit =
    val packet = SidedExecutePacket[ClientExecutor](body(using _))
    ClientPlayNetworking.send(packet)

def datagen(using gen: FabricDataGenerator): Unit =
  given pack: FabricDataGenerator#Pack = gen.createPack()
  registryFor[Rune].forEach(datagenRune(_))
  pack.addProvider(
    new FabricAdvancementProvider(_, _) {
      override def generateAdvancement(wrapperLookup: RegistryWrapper.WrapperLookup, consumer: Consumer[AdvancementEntry]): Unit =
        val rootId = Identifier.of("mica", "without_me")
        consumer.accept(AdvancementEntry(
          rootId,
          Advancement(
            parent = Optional.empty,
            display = Optional.of(AdvancementDisplay(
              icon = ItemStack(Items.ENDER_EYE),
              title = Text.literal("Guess Who\'s Back, Back Again"),
              description = Text.literal("Return to a Teleport Slate from over 32 blocks away"),
              background = Optional.empty,
              frame = AdvancementFrame.CHALLENGE,
              showToast = true,
              announceToChat = true,
              hidden = true,
            )),
            rewards = AdvancementRewards.NONE,
            criteria = java.util.Map.of("mojang_won\'t_let_me_be", AdvancementCriterion(Criteria.IMPOSSIBLE, ImpossibleCriterion.Conditions())),
            requirements = AdvancementRequirements.allOf(Seq("mojang_won\'t_let_me_be")),
            sendsTelemetryEvent = false
          )
        ))
    }
  )

def init() =
  ClientPlayNetworking.registerGlobalReceiver[SidedExecutePacket[ServerExecutor]](SidedExecutePacket.id, (p, ctx) => p.payload(summon))
  ClientCommandRegistrationCallback.EVENT.register: (d, reg) =>
    d.register:
      val p = LiteralArgumentBuilder.literal[FabricClientCommandSource]("mica")
      p.`then`:
        val p = LiteralArgumentBuilder.literal[FabricClientCommandSource]("youLikeRunes")
        p.requires(_.getPlayer hasPermissionLevel 2)
        p.executes: ctx =>
          registryFor[Rune].forEach: r =>
            try
              val id = Registries.ITEM.getId(r.item.value)
              runOnServer:
                relevantPlayer.getInventory.offerOrDrop(ItemStack(Registries.ITEM.get(id)))
            catch case e: Exception =>
              ctx.getSource.sendFeedback(Text literal e.getMessage styled (_ withColor Formatting.RED))
          0
        p.build()