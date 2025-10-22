package org.eu.net.pool.hexic
package client

import at.petrak.hexcasting.api.item.PigmentItem
import at.petrak.hexcasting.api.pigment.FrozenPigment
import com.google.gson.reflect.TypeToken
import com.google.gson.{Gson, JsonObject}
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricLanguageProvider, FabricModelProvider, FabricRecipeProvider, FabricTagProvider}
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.minecraft.advancement.criterion.InventoryChangedCriterion
import net.minecraft.client.MinecraftClient
import net.minecraft.client.color.item.ItemColorProvider
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.network.{ClientPlayNetworkHandler, ClientPlayerEntity}
import net.minecraft.client.render.model.json
import net.minecraft.data.client.{BlockStateModelGenerator, ItemModelGenerator, ModelIds, Models, TextureKey, TextureMap}
import net.minecraft.data.server.recipe.{RecipeJsonProvider, ShapedRecipeJsonBuilder}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.recipe.book.RecipeCategory
import net.minecraft.registry.{MutableRegistry, Registries, RegistryKeys, RegistryWrapper}
import net.minecraft.screen.slot.Slot
import net.minecraft.text.{CharacterVisitor, OrderedText, Style}
import net.minecraft.util.DyeColor
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.Vec3d
import org.eu.net.pool.common_curses.{HotbarRendering, SlotAccess, TextManipulator}
import org.eu.net.pool.common_curses.client.CommonCursesClientKt
import org.eu.net.pool.hexic.mixin.client.ChatScreenAccess

import java.io.{InputStreamReader, Reader}
import java.util.function.Consumer
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.language.experimental.{macros, saferExceptions}
import scala.util.boundary
import scala.util.chaining.scalaUtilChainingOps

given client: MinecraftClient = MinecraftClient.getInstance

inline def foldLocalPlayer[R](default: => R)(ifPresent: ClientPlayerEntity => R): R =
  client.player match
    case null => default
    case player => ifPresent(player)

var lastMurmur: Option[String] = None

object Hooks:
  def clientTick(): Unit =
    val currentMurmur = client.currentScreen match
      case null => None
      case c: ChatScreenAccess => Some(c.getChatField.getText)
      case _ => None
    if currentMurmur != lastMurmur then
      if isDev then println(s"Sending murmur: ${currentMurmur}")
      lastMurmur = currentMurmur
      val buf = PacketByteBufs.create()
      buf.writeBoolean(currentMurmur.isDefined)
      currentMurmur.foreach(buf.writeString)
      ClientPlayNetworking.send("murmur", buf)
  def provideRenderText(string: String, firstCharacterIndex: Int, field: TextFieldWidget, original: OrderedText): OrderedText =
    foldLocalPlayer(original): p =>
      val c = p.getComponent(PlayerInfoComponent.key)
      boundary[OrderedText]:
        if c.rightWeave.hasCustomName && c.rightWeave.getItem.isInstanceOf[Mediaweave] then
          val wake = c.rightWeave.getName.getString.toLowerCase
          if field.getText.toLowerCase.startsWith(s"$wake:") then
            boundary.break[OrderedText]: v =>
              original.accept: (idx, style, p) =>
                v.accept(idx, if idx + firstCharacterIndex <= wake.length then style.withColor(c.rightWeave.getItem.asInstanceOf[Mediaweave].color.getSignColor) else style, p)
        if c.leftWeave.hasCustomName && c.leftWeave.getItem.isInstanceOf[Mediaweave] then
          val wake = c.leftWeave.getName.getString.toLowerCase
          if field.getText.toLowerCase.startsWith(s"$wake:") then
            boundary.break[OrderedText]: v =>
              original.accept: (idx, style, p) =>
                v.accept(idx, if idx + firstCharacterIndex <= wake.length then style.withColor(c.leftWeave.getItem.asInstanceOf[Mediaweave].color.getSignColor) else style, p)
        original
  def interceptSendMessage(handler: ClientPlayNetworkHandler, msg: String): Boolean =
    foldLocalPlayer(false): p =>
      boundary[Boolean]:
        val c = p.getComponent(PlayerInfoComponent.key)
        val (left, text) = boundary[(Boolean, String)]:
          if c.rightWeave.hasCustomName && c.rightWeave.getItem.isInstanceOf[Mediaweave] then
            val wake = c.rightWeave.getName.getString.toLowerCase
            if msg.toLowerCase.startsWith(s"$wake:") then
              boundary.break((false, msg.substring(wake.length + 1)))
          if c.leftWeave.hasCustomName && c.leftWeave.getItem.isInstanceOf[Mediaweave] then
            val wake = c.leftWeave.getName.getString.toLowerCase
            if msg.toLowerCase.startsWith(s"$wake:") then
              boundary.break((true, msg.substring(wake.length + 1)))
          boundary.break(false)
        val buf = PacketByteBufs.create()
        buf.writeByte(if left then 12 else 8)
        buf.writeString(text.trim)
        ClientPlayNetworking.send("sync_mediaweave", buf)
        true

def init(): Unit =
  HotbarRendering.Companion.getEvent.register: () =>
    foldLocalPlayer(HotbarRendering.ALL):
      _.getComponent(PlayerInfoComponent.key).wispMedia.fold(HotbarRendering.ALL)(_ => HotbarRendering.NONE)
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

extension (s: DyeColor) def humanName: String = s.getName.split('_').map(_.capitalize).mkString(" ")

def datagen(gen: FabricDataGenerator): Unit =
  val pack = gen.createPack()
  pack.addProvider:
    new FabricModelProvider(_):
      override def generateBlockStateModels(gen: BlockStateModelGenerator): Unit =
        ;
      override def generateItemModels(gen: ItemModelGenerator): Unit =
        for (_, item) <- Mediaweave.colors do gen.register(item, Models.GENERATED)
        for (_, item) <- stringworms do gen.register(item, Models.GENERATED)
        for item <- MediaBundle.items do gen.register(item, Models.GENERATED)
        for (_, item) <- Pen.instances do
          gen.writer.accept(ModelIds.getItemModelId(item), () => JsonObject().tap: j =>
            j.addProperty("parent", "minecraft:item/generated")
            j.add("textures", JsonObject().tap: j =>
              j.addProperty("layer0", "hexic:item/pen_back")
              j.addProperty("layer1", "hexic:item/pen_cover")
              j.addProperty("layer2", "hexic:item/pen_overlay")
            )
          )
        gen.writer.accept(ModelIds.getItemModelId(dyedStringworm), () => JsonObject().tap: j =>
          j.addProperty("parent", "minecraft:item/generated")
          j.add("textures", JsonObject().tap: j =>
            for i <- 0 until 32 do
              j.addProperty(s"layer$i", s"hexic:item/stringworm_tinted_$i")
          )
        )
        gen.register(wizard, Models.GENERATED)
  pack.addProvider:
    new FabricLanguageProvider(_):
      override def generateTranslations(gen: FabricLanguageProvider.TranslationBuilder): Unit =
        for (action, name) <- Vector(
          "nbt/lift1" -> "Secretary's Purification: Byte",
          "nbt/lift2" -> "Secretary's Purification: Short",
          "nbt/lift4" -> "Secretary's Purification: Integer",
          "nbt/lift8" -> "Secretary's Purification: Long",
          "nbt/liftf" -> "Secretary's Purification: Float",
          "nbt/liftd" -> "Secretary's Purification: Double",
          "nbt/literal/collection" -> "Secretary's Reflection: Collection",
          "nbt/literal/list" -> "Secretary's Reflection: Vacant List",
          "nbt/literal/array1" -> "Secretary's Reflection: Vacant Byte Array",
          "nbt/literal/array2" -> "Secretary's Reflection: Vacant Short Array",
          "nbt/literal/array4" -> "Secretary's Reflection: Vacant Integer Array",
          "empty_map" -> "Vacant Reflection: Map",
          "nbt/serialize" -> "Exporter's Purification",
          "tripwire" -> "Tripwire Reflection",
          "nbt/deserialize" -> "Importer's Purification",
          "jvm/class_of_iota" -> "Classifier Purification II",
          "jvm/class_of_payload" -> "Classifier Purification I",
          "jvm/newinstance_unboxed" -> "Constructor Purification II",
          "jvm/newinstance_boxed" -> "Constructor Purification I",
          "malloc" -> "Allocator's Purification",
          "free" -> "Deallocator's Gambit",
          "staffcast_factory" -> "Lani's Greater Gambit",
          "staffcast_factory/lazy" -> "Lani's Lesser Gambit",
          "metatable" -> "Patchwork Exaltation",
          "murmur" -> "Murmur Reflection",
          "reveal" -> "Greater Reveal",
          "dye_offhand" -> "Apply Pigment",
          "rotate" -> "Ferris Distillation",
          "take" -> "Retention Distillation",
          "drop" -> "Rejection Distillation",
          "whatthefuck" -> "Suffering",
        ) do gen.add(s"hexcasting.action.hexic:$action", name)
        for (klass, name) <- Vector(
          "int_or_list" -> "§aint§r or §5[§aint§5]§r",
        ) do gen.add(s"hexcasting.mishap.invalid_value.class.hexic:$klass", name)
        for (ty, name) <- Vector(
          "tripwire" -> "Tripwire",
          "nbt" -> "Tag",
          "variant" -> "Concept",
          // infinite hexxy
          "jvm/class" -> "Class",
          "jvm/pointer" -> "Address",
        ) do gen.add(s"hexcasting.iota.hexic:$ty", name)
        gen.add("itemGroup.hexic.group", "Hexic")
        gen.add("hexic.bad_metatable", "Expected a map in the §a%s§r property but got %s")

        for (color, item) <- Mediaweave.colors do
          gen.add(item, s"${color.humanName} Mediaweave")
        for (color, item) <- Pen.instances do
          gen.add(item, s"${color.humanName} Pen")
        for (_, item) <- stringworms do
          gen.add(item, s"Stringworm")
        for item <- MediaBundle.items do
          gen.add(item, item.size match
            case 6 => s"${item.color.humanName} Media Pouch"
            case 12 => s"Large ${item.color.humanName} Media Pouch"
            case _ => s"How Did You Get This ${item.color.humanName} Media Pouch")
        val hexLang = Gson().fromJson(InputStreamReader(getClass.getResourceAsStream("/assets/hexcasting/lang/en_us.json")), new TypeToken[java.util.Map[String, String]]() {}).asScala
        Registries.ITEM.forEach:
          case p: PigmentItem => gen.add("item.hexic.stringworm." + p.getTranslationKey, "Shimmering " + hexLang(p.getTranslationKey).replace("Pigment", "Stringworm"))
          case e => println(e)
        gen.add("tag.item.hexic.mediaweaves", "Mediaweave")
        gen.add("hexic.media_bundle.items", "%s/%s")
        gen.add("hexic.media.infinite", "%s: %s")
        gen.add("hexic.media.finite", "%s: %s/%s (%s)")
        gen.add("hexic.media.external", "Media")
        gen.add("hexic.media.internal", "Trinkets")
        gen.add("text.hexic.pigment_holder_item", "an item storing a pigment")
        gen.add(wizard, "Wizard")
        gen.add("hexdoc.hexic.title", "Hexic")
        gen.add("hexdoc.hexic.description", "Miscellaneous neat features and QoL patterns for Hex Casting")
  pack.addProvider:
    new FabricRecipeProvider(_):
      override def generate(consumer: Consumer[RecipeJsonProvider]): Unit =
        for case item@MediaBundle(color, 6) <- MediaBundle.items do
          ShapedRecipeJsonBuilder(RecipeCategory.TOOLS, item, 1)
            .group(" s ")
            .group("waw")
            .group(" w ")
            .input('s', Items.STRING)
            .input('w', Mediaweave.colors(color))
            .input('a', Items.AMETHYST_SHARD)
            .criterion("recipe", InventoryChangedCriterion.Conditions.items(Mediaweave.colors(color)))
  pack.addProvider:
    new FabricTagProvider[Item](_, RegistryKeys.ITEM, _):
      override def configure(lookup: RegistryWrapper.WrapperLookup): Unit =
        getOrCreateTagBuilder(Mediaweave.tag).add(Mediaweave.colors.values.toSeq*)

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
