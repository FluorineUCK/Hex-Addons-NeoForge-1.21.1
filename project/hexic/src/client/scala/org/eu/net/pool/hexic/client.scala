package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.item.PigmentItem
import at.petrak.hexcasting.api.mod.HexTags
import at.petrak.hexcasting.api.pigment.FrozenPigment
import com.google.gson.reflect.TypeToken
import com.google.gson.{Gson, JsonArray, JsonObject}
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import dev.emi.trinkets.api.{TrinketComponent, TrinketsApi}
import kotlin.jvm.JvmField
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricLanguageProvider, FabricModelProvider, FabricRecipeProvider, FabricTagProvider}
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.minecraft.advancement.criterion.InventoryChangedCriterion
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.client.MinecraftClient
import net.minecraft.client.color.item.ItemColorProvider
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.network.{ClientPlayNetworkHandler, ClientPlayerEntity}
import net.minecraft.client.render.block.entity.{BlockEntityRenderer, BlockEntityRendererFactories}
import net.minecraft.client.render.model.json
import net.minecraft.client.render.*
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.data.client.*
import net.minecraft.data.server.recipe.{RecipeJsonProvider, ShapedRecipeJsonBuilder}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.recipe.book.RecipeCategory
import net.minecraft.registry.{MutableRegistry, Registries, RegistryKeys, RegistryWrapper}
import net.minecraft.screen.slot.Slot
import net.minecraft.text.{CharacterVisitor, OrderedText, Style}
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.{Direction, MathHelper, Vec3d}
import net.minecraft.util.{DyeColor, Identifier}

import java.io.{InputStreamReader, Reader}
import java.util.function.Consumer
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.immutable.BitSet
import scala.language.experimental.{macros, saferExceptions}
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.boundary
import scala.util.boundary.Label
import scala.util.chaining.scalaUtilChainingOps
import phlib.{*, given}

given client: MinecraftClient = MinecraftClient.getInstance

inline def foldLocalPlayer[R](default: => R)(ifPresent: ClientPlayerEntity => R): R =
  client.player match
    case null => default
    case player => ifPresent(player)

object ClientHooks:
  def interceptSendMessage(handler: ClientPlayNetworkHandler, msg: String): Boolean =
    foldLocalPlayer(false): p =>
      if p.validMediaweave.isDefined then
        val buf = PacketByteBufs.create()
        buf.writeByte(0)
        buf.writeString(msg)
        ClientPlayNetworking.send("message", buf)
        true
      else false

def init(): Unit =
  BlockEntityRendererFactories.register(
    Registries.BLOCK_ENTITY_TYPE("chisel_table").asInstanceOf[BlockEntityType[? <: BlockEntity { val bits: BitSet }]],
    ctx => (tbl: BlockEntity { val bits: BitSet }, dt, mats, bufs, light, overlay) =>
      given MatrixStack = mats
      given buf: VertexConsumer = bufs.getBuffer(RenderLayer.getTranslucent)
      for bit <- tbl.bits do
        val x = bit / 16
        val y = bit % 16
        val n = x * 3f + y - 5f
        val time = tbl.getWorld.getTime + dt
        val lighten = Math.sin(n / 20f + time / 400f)
        val darken = Math.sin(n / 24f + time / 400f)
        val color = ((1.0f - (darken max 0) * 0.08f).toFloat, (0.6f + lighten * 0.125).toFloat, (1.0f + (darken min 0) * 0.08f).toFloat, 1.0f) // season to taste
        if color._1 > 1 || color._2 > 1 || color._3 > 1 || color._4 > 1 then
          given_Logger.error(s"Out-of-bounds pixel color! x=$x y=$y n=$n time=$time lighten=$lighten darken=$darken color=$color")
        else
          given Lighting = Lighting(light, overlay, color = color)
          cuboid(
            ((x+1)/16f, 12/16f, (y+1)/16f) -> ((x+2)/16f, 13/16f, (y+2)/16f),
            // TODO
            Direction.values.map(_ -> (null, (0f, 0f) -> (1f, 1f)))*
          )
  )
  ColorProviderRegistry.ITEM.register((stack, idx) => boundary:
    val nbt = stack.getSubNbt("pigment")
    if nbt == null then boundary.break(0xFFFFFFFF)
    val prov = FrozenPigment.fromNBT(nbt).getColorProvider
    prov.getColor(client.world.getTime + client.getTickDelta, Vec3d.fromPolar(idx * 360/32, 0))
  , dyedStringworm)
  for (color, item) <- Pen.instances do
    ColorProviderRegistry.ITEM.register((_, idx) => if idx == 1 then color.getSignColor else 0xFFFFFFF, item)
  for i <- 0 until 32 do
    val k = s"layer$i"
    if !json.ItemModelGenerator.LAYERS.contains(k) then
      json.ItemModelGenerator.LAYERS.add(k)
  phlib.Events.registryLookup.register:
    val preferredColor = DyeColor.values()(client.getSession.getUuidOrNull.getLeastSignificantBits.abs.%(16).toInt)
    val preferredStringworm = stringworms(Stringworm.flavors(client.getSession.getUuidOrNull.getLeastSignificantBits.abs.%(48).*(7).%(4).toInt))
    val preferredMediaweave = Mediaweave.colors(preferredColor)
    val preferredPen = Pen.instances(preferredColor)
    val preferredPouch = memo(MediaBundle(preferredColor, _))
    {
      case (Registries.ITEM, id) if id == ("preferred_mediaweave": Identifier) => preferredMediaweave
      case (Registries.ITEM, id) if id == ("small_preferred_bundle": Identifier) => preferredPouch(6)
      case (Registries.ITEM, id) if id == ("large_preferred_bundle": Identifier) => preferredPouch(12)
      case (Registries.ITEM, id) if id == ("preferred_stringworm": Identifier) => preferredStringworm
      case (Registries.ITEM, id) if id == ("preferred_pen": Identifier) => preferredPen
    }
  ClientPlayNetworking.registerGlobalReceiver("msg", (_, handler, buf, _) =>
    val s = buf.readString
    if s.startsWith("/") then
      handler.sendChatCommand(s.drop(1))
    else
      handler.sendChatMessage(s))

extension (s: DyeColor) def humanName: String = s.getName.split('_').map(_.capitalize).mkString(" ")

inline def pushMatrices[T](using stack: MatrixStack)(body: => T): T =
  stack.push()
  try
    body
  finally
    stack.pop()

case class Lighting(light: Int | (Int, Int), overlay: Int | (Int, Int) = (255, 255) /* trial-and-error with no effect */, color: (Float, Float, Float, Float) = (1, 1, 1, 1)):
  def writeLight()(using buf: VertexConsumer) =
    light match
      case (i, j) => buf.light(i, j)
      case i: Int => buf.light(i)
  def writeOverlay()(using buf: VertexConsumer) =
    overlay match
      case (i, j) => buf.overlay(i, j)
      case i: Int => buf.overlay(i)
  def writeColor()(using buf: VertexConsumer) =
    buf.color(color._1, color._2, color._3, color._4)

def vert(using buf: VertexConsumer, mats: MatrixStack, light: Lighting)(pos: (Float, Float, Float), normal: (Float, Float, Float), uv: (Float, Float)) =
  buf.vertex(mats.peek.getPositionMatrix, pos._1, pos._2, pos._3)
  light.writeColor()
  buf.texture(uv._1 / 48, uv._2 / 32)
  light.writeLight()
  light.writeOverlay()
  buf.normal(mats.peek.getNormalMatrix, normal._1, normal._2, normal._3)
  buf.next()

def verts(using VertexConsumer, MatrixStack, Lighting)(verts: Seq[((Float, Float, Float), (Float, Float))], normal: (Float, Float, Float)) =
  for (pos, uv) <- verts yield
    vert(pos, normal, uv)

def cuboid(using VertexConsumer, MatrixStack, Lighting)(span: ((Float, Float, Float), (Float, Float, Float)), faces: (Direction, (Sprite | Null, ((Float, Float), (Float, Float))))*) =
  val (from, to) = span
  val (x1, y1, z1) = (from._1 min to._1, from._2 min to._2, from._3 min to._3)
  val (x2, y2, z2) = (from._1 max to._1, from._2 max to._2, from._3 max to._3)
  for (dir, (sprite, (uv1, uv2))) <- faces do
    val ((minU, minV), (maxU, maxV)) = sprite match
      case null => (0f, 0f) -> (1f, 1f)
      case s: Sprite => (s.getMinU, s.getMinV) -> (s.getMaxU, s.getMaxV)
    val u1 = MathHelper.lerp(uv1._1, minU, maxU)
    val v1 = MathHelper.lerp(uv1._2, minV, maxV)
    val u2 = MathHelper.lerp(uv2._1, minU, maxU)
    val v2 = MathHelper.lerp(uv2._2, minV, maxV)
    // the remainder of this function has been generated by a qwen3-coder:480b since I'm too lazy to write all this by hand
    val vertsSeq = dir match
      case Direction.UP =>
        Seq(
          (x1, y2, z2) -> (u1, v1),
          (x2, y2, z2) -> (u2, v1),
          (x2, y2, z1) -> (u2, v2),
          (x1, y2, z1) -> (u1, v2),
        )
      case Direction.DOWN =>
        Seq(
          (x1, y1, z1) -> (u1, v1),
          (x2, y1, z1) -> (u2, v1),
          (x2, y1, z2) -> (u2, v2),
          (x1, y1, z2) -> (u1, v2),
        )
      case Direction.NORTH =>
        Seq(
          (x2, y1, z1) -> (u1, v2),
          (x1, y1, z1) -> (u2, v2),
          (x1, y2, z1) -> (u2, v1),
          (x2, y2, z1) -> (u1, v1),
        )
      case Direction.SOUTH =>
        Seq(
          (x1, y1, z2) -> (u1, v2),
          (x2, y1, z2) -> (u2, v2),
          (x2, y2, z2) -> (u2, v1),
          (x1, y2, z2) -> (u1, v1),
        )
      case Direction.WEST =>
        Seq(
          (x1, y1, z1) -> (u1, v2),
          (x1, y1, z2) -> (u2, v2),
          (x1, y2, z2) -> (u2, v1),
          (x1, y2, z1) -> (u1, v1),
        )
      case Direction.EAST =>
        Seq(
          (x2, y1, z2) -> (u1, v2),
          (x2, y1, z1) -> (u2, v2),
          (x2, y2, z1) -> (u2, v1),
          (x2, y2, z2) -> (u1, v1),
        )

    val normal = dir match
      case Direction.UP    => (0f, 1f, 0f)
      case Direction.DOWN  => (0f, -1f, 0f)
      case Direction.NORTH => (0f, 0f, -1f)
      case Direction.SOUTH => (0f, 0f, 1f)
      case Direction.WEST  => (-1f, 0f, 0f)
      case Direction.EAST  => (1f, 0f, 0f)
    verts(vertsSeq, normal)

def datagen(gen: FabricDataGenerator): Unit =
  val pack = gen.createPack()
  pack.addProvider:
    new FabricModelProvider(_):
      override def generateBlockStateModels(gen: BlockStateModelGenerator): Unit =
        gen.registerSimpleCubeAll(Registries.BLOCK("border"))
        gen.registerSimpleState(Registries.BLOCK("chisel_table"))
        gen.modelCollector.accept(ModelIds.getBlockModelId(Registries.BLOCK("chisel_table")), () =>
          new JsonObject().tap: j =>
            j.addProperty("parent", "minecraft:block/block")
            j.add("textures", new JsonObject().tap: j =>
              j.addProperty("particle", "hexcasting:block/slate")
            )
            j.add("elements", new JsonArray().tap: j =>
              def elem(name: String, from: (Float, Float, Float), to: (Float, Float, Float), config: JsonObject ?=> Unit, faces: (Direction, JsonObject ?=> Label[Unit] ?=> Unit)*) =
                j.add(new JsonObject().tap: j =>
                  j.addProperty("name", name)
                  j.add("from", JsonArray().tap(_.add(from._1)).tap(_.add(from._2)).tap(_.add(from._3)))
                  j.add("to", JsonArray().tap(_.add(to._1)).tap(_.add(to._2)).tap(_.add(to._3)))
                  j.add("faces", JsonObject().tap: j =>
                    for (face, action) <- if faces.nonEmpty then faces else Direction.values.toSeq.map { _ -> {} } do
                      boundary:
                        j.add(face.asString, JsonObject().tap:
                          case given JsonObject =>
                            val j = summon[JsonObject]
                            if face == Direction.WEST && from._1 == 0 then j.addProperty("cullface", "west")
                            if face == Direction.DOWN && from._2 == 0 then j.addProperty("cullface", "down")
                            if face == Direction.NORTH && from._3 == 0 then j.addProperty("cullface", "north")
                            if face == Direction.EAST && to._1 == 16 then j.addProperty("cullface", "east")
                            if face == Direction.UP && to._2 == 16 then j.addProperty("cullface", "up")
                            if face == Direction.SOUTH && to._3 == 16 then j.addProperty("cullface", "south")
                            config; action
                        )
                  )
                )
              // TODO: we can optimize this later
              elem("small_leg", (0, 0, 0), (4, 8, 4), j ?=> j.addProperty("texture", "#particle"))
              elem("big_leg", (12, 0, 12), (16, 8, 16), j ?=> j.addProperty("texture", "#particle"))
              elem("surface", (0, 8, 0), (16, 12, 16), j ?=> j.addProperty("texture", "#particle"))
            )
        )
      override def generateItemModels(gen: ItemModelGenerator): Unit =
        for (_, item) <- Mediaweave.colors do gen.register(item, Models.GENERATED)
        for (_, item) <- stringworms do gen.register(item, Models.GENERATED)
        for item <- MediaBundle.items do gen.register(item, Models.GENERATED)
        for (_, item) <- Pen.instances do
          gen.writer.accept(ModelIds.getItemModelId(item), () => JsonObject().tap: j =>
            j.addProperty("parent", "minecraft:item/generated")
            j.add("textures", JsonObject().tap: j =>
              j.addProperty("layer0", "hexic:item/pen_back")
              j.addProperty("layer1", "hexic:item/pen_overlay")
              j.addProperty("layer2", "hexic:item/pen_cover")
            )
          )
        gen.writer.accept(ModelIds.getItemModelId(dyedStringworm), () => JsonObject().tap: j =>
          j.addProperty("parent", "minecraft:item/generated")
          j.add("textures", JsonObject().tap: j =>
            for i <- 0 until 32 do
              j.addProperty(s"layer$i", s"hexic:item/stringworm_tinted_$i")
          )
        )
        gen.register(Registries.ITEM("chisel"), Models.GENERATED)
        gen.register(wizard, Models.GENERATED)
  pack.addProvider:
    new FabricLanguageProvider(_):
      override def generateTranslations(gen: FabricLanguageProvider.TranslationBuilder): Unit =
        for action -> name <- Vector(
          "blind" -> "Hidden Sun's Nadir",
          "deleteworld" -> "Shatter Demiplane",
          "drop" -> "Rejection Distillation",
          "dye_offhand" -> "Apply Pigment",
          "erase" -> "Erase Block",
          "extract" -> "Excisor's Gambit",
          "findview" -> "Reflection Purification",
          "fox" -> "Vulpine Gambit",
          "free" -> "Deallocator's Gambit",
          "get_other_caster" -> "Dual's Reflection",
          "grep" -> "Refinement Distillation",
          "make_cme" -> "Thoth's Pseudogambit",
          "makeworld" -> "Conjure Demiplane",
          "malloc" -> "Allocator's Purification",
          "modulo" -> "Modulus Distillation II",
          "moveconcept" -> "Transfer Substance",
          "moveentity" -> "Transfer Creature",
          "murmur" -> "Murmur Reflection",
          "omni_close" -> "Cessation",
          "omni_open" -> "Resumption",
          "reveal" -> "Greater Reveal",
          "rotate" -> "Ferris Distillation",
          "staffcast_factory" -> "Lani's Greater Gambit",
          "staffcast_factory/lazy" -> "Lani's Lesser Gambit",
          "take" -> "Retention Distillation",
          "unfox" -> "Vulpine Expulsion",
          "where" -> "Deductive Purification",
        ) do gen.add(s"hexcasting.action.hexic:$action", name)
        gen.add("hexcasting.special.hexic:tuple", "Coupler's Gambit")
        gen.add("hexcasting.special.hexic:tuple.n", "Coupler's Gambit %s")
        for klass ->name<- Vector(
          "erase" -> "an item entity or vector",
          "int_or_list" -> "§aint§r or §5[§aint§5]§r",
        ) do gen.add(s"hexcasting.mishap.invalid_value.class.hexic:$klass", name)
        for ty ->name<- Vector(
          "nbt" -> "Tag",
          "variant" -> "Concept",
        ) do gen.add(s"hexcasting.iota.hexic:$ty", name)
        gen.add("hexcasting.mishap.bad_block.hexic:erase", "a block holding a casting item or acting as an iota holder")
        gen.add("hexcasting.mishap.bad_item.hexic:erase", "a casting item or iota holder")
        gen.add("hexic.bad_metatable", "Expected a map in the §a%s§r property but got %s")
        gen.add("itemGroup.hexic.group", "Hexic")
        gen.add("text.hexic.or_map", "%s or map")

        for (color, item) <- Mediaweave.colors do
          gen.add(item, s"${color.humanName} Mediaweave")
        for (color, item) <- Pen.instances do
          gen.add(item, s"${color.humanName} Pen")
        for (_, item) <- stringworms do
          gen.add(item, s"Stringworm")
        for item <- MediaBundle.items do
          gen.add(item, item.size match
            case 6 => s"${item.color.humanName} Casting Pouch"
            case 12 => s"Large ${item.color.humanName} Casting Pouch"
            case s => throw IllegalStateException(s"Unhandled bundle size $s"))
        gen.add(wizard, "Wizard")
        val hexLang = Seq("hexcasting", "oneironaut").flatMap(mod => Gson().fromJson(InputStreamReader(getClass.getResourceAsStream(s"/assets/$mod/lang/en_us.json")), new TypeToken[java.util.Map[String, String]]() {}).asScala).toMap
        Registries.ITEM.forEach:
          case p: PigmentItem => gen.add("item.hexic.stringworm." + p.getTranslationKey, "Shimmering " + hexLang(p.getTranslationKey).replace("Pigment", "Stringworm"))
          case e => println(e)
        for page -> text <- Vector(
          "dye_offhand" -> "Imbues the item held in my offhand (e.g. a $(l:items/hexcasting)$(item)casting item/$) with the given pigment.",
          "erase" -> "Erases the _Hex or iota contained within a dropped item or block. Costs one dust per item.",
          "get_other_caster" -> "Adds the closest sentient being, excluding me, to the stack.",
          "modulo" -> "Similar to Modulus, but differs for negative numbers: -8 %%₁ 3 = -2, but -8 %%₂ 3 = 1.",
          "murmur" -> "Finds the region of my mind known as the 'chat box' and adds its contents to the stack. If it cannot be found, adds Null instead.",
          "blind" -> "Inflicts $(thing)blindness/$. Base cost is one $(l:items/amethyst)$(item)Amethyst Dust/$ per 10 seconds.",
        ) do gen.add(s"book.hexic.page.$page", text)
        gen.add("hexdoc.hexic.description", "Miscellaneous neat features and QoL patterns for Hex Casting")
        gen.add("hexdoc.hexic.title", "Hexic")
        gen.add("hexic.media.external", "Media")
        gen.add("hexic.media.finite", "%s: %s/%s (%s)")
        gen.add("hexic.media.infinite", "%s: %s")
        gen.add("hexic.media.internal", "Trinkets")
        gen.add("hexic.media_bundle.items", "%s/%s")
        gen.add("hexic.spell_memory", "Hex memorized")
        gen.add("tag.item.hexic.mediaweaves", "Mediaweave")
        gen.add("text.hexic.pigment_holder_item", "an item storing a pigment")
        gen.add("trinkets.slot.chest.hexic_mediaweave", "Mediaweave")
  pack.addProvider:
    new FabricRecipeProvider(_):
      override def generate(consumer: Consumer[RecipeJsonProvider]): Unit =
        for case item@MediaBundle(color, 6) <- MediaBundle.items do
          ShapedRecipeJsonBuilder(RecipeCategory.TOOLS, item, 1)
            .pattern(" s ")
            .pattern("waw")
            .pattern(" w ")
            .group("hexic:media_pouch")
            .input('s', Items.STRING)
            .input('w', Mediaweave.colors(color))
            .input('a', Items.AMETHYST_SHARD)
            .criterion("recipe", InventoryChangedCriterion.Conditions.items(Mediaweave.colors(color)))
            .offerTo(consumer, Registries.ITEM.getId(item))
        for case (color, item) <- Pen.instances do
          ShapedRecipeJsonBuilder(RecipeCategory.TOOLS, item, 1)
            .pattern("w")
            .pattern("a")
            .pattern("i")
            .group("hexic:pen")
            .input('i', Items.GOLD_NUGGET)
            .input('w', Mediaweave.colors(color))
            .input('a', Items.AMETHYST_SHARD)
            .criterion("recipe", InventoryChangedCriterion.Conditions.items(Mediaweave.colors(color)))
            .offerTo(consumer, Registries.ITEM.getId(item))
  pack.addProvider:
    new FabricTagProvider[Item](_, RegistryKeys.ITEM, _):
      override def configure(lookup: RegistryWrapper.WrapperLookup): Unit =
        getOrCreateTagBuilder(Mediaweave.tag).add(Mediaweave.colors.values.toSeq*)
        getOrCreateTagBuilder(HexTags.Items.STAVES).add(Pen.instances.values.toSeq*)

object inventory_??? extends Inventory:
  override def size(): Int = ???
  override def isEmpty: Boolean = ???
  override def getStack(slot: Int): ItemStack = ???
  override def removeStack(slot: Int, amount: Int): ItemStack = ???
  override def removeStack(slot: Int): ItemStack = ???
  override def setStack(slot: Int, stack: ItemStack): Unit = ???
  override def markDirty(): Unit = ???
  override def canPlayerUse(player: PlayerEntity): Boolean = ???
  override def clear(): Unit = ???

case class FilterSlot(s: DefaultedList[ItemVariant], idx: Int, pos: (Int, Int))
  extends Slot(inventory_???, idx, pos._1, pos._2):
  override def getStack: ItemStack = s(idx).toStack
  override def setStackNoCallbacks(stack: ItemStack): Unit = s(idx) = ItemVariant.of(stack)
  override def markDirty(): Unit = ()
  override def getMaxItemCount: Int = 0
  override def takeStack(amount: Int): ItemStack =
    s(idx) = ItemVariant.blank()
    ItemStack.EMPTY
