package org.eu.net.pool.hexic
package client

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricLanguageProvider, FabricModelProvider, FabricRecipeProvider, FabricTagProvider}
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.network.{ClientPlayNetworkHandler, ClientPlayerEntity}
import net.minecraft.data.client.{BlockStateModelGenerator, ItemModelGenerator, Models}
import net.minecraft.data.server.recipe.RecipeJsonProvider
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.registry.{MutableRegistry, Registries, RegistryKeys, RegistryWrapper}
import net.minecraft.screen.slot.Slot
import net.minecraft.text.{CharacterVisitor, OrderedText, Style}
import net.minecraft.util.DyeColor
import net.minecraft.util.collection.DefaultedList
import org.eu.net.pool.common_curses.{HotbarRendering, SlotAccess, TextManipulator}
import org.eu.net.pool.common_curses.client.CommonCursesClientKt
import org.eu.net.pool.hexic.mixin.client.ChatScreenAccess

import java.util.function.Consumer
import scala.language.experimental.{macros, saferExceptions}
import scala.util.boundary
import scala.util.chaining.scalaUtilChainingOps

inline def foldLocalPlayer[R](default: => R)(ifPresent: ClientPlayerEntity => R): R =
  MinecraftClient.getInstance().player match
    case null => default
    case player => ifPresent(player)

var lastMurmur: Option[String] = None

object Hooks:
  def clientTick(): Unit =
    val currentMurmur = MinecraftClient.getInstance.currentScreen match
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
          "metatable" -> "Metatable Exaltation",
          "murmur" -> "Murmur Reflection",
          "reveal" -> "Greater Reveal",
        ) do gen.add(s"hexcasting.action.hexic:$action", name)
        for (ty, name) <- Vector(
          "tripwire" -> "Tripwire",
          "nbt" -> "Tag",
          "variant" -> "Object Variant",
          "stack" -> "Object Stack",
          // infinite hexxy
          "jvm/class" -> "Class",
          "jvm/pointer" -> "Address",
        ) do gen.add(s"hexcasting.iota.hexic:$ty", name)
        gen.add("hexic.bad_metatable", "Expected a map in the §a%s§r property but got %s")

        for (color, item) <- Mediaweave.colors do
          gen.add(item, s"${color.humanName} Mediaweave")
        for (_, item) <- stringworms do
          gen.add(item, s"Stringworm")
        for item <- MediaBundle.items do
          gen.add(item, item.size match
            case 6 => s"${item.color.humanName} Media Pouch"
            case 12 => s"Large ${item.color.humanName} Media Pouch"
            case _ => s"How Did You Get This ${item.color.humanName} Media Pouch")
        gen.add("tag.item.hexic.mediaweaves", "Mediaweave")
        gen.add("hexic.media_bundle.items", "%s/%s")
        gen.add("hexic.media.infinite", "%s: %s")
        gen.add("hexic.media.finite", "%s: %s/%s (%s)")
        gen.add("hexic.media.external", "Media")
        gen.add("hexic.media.internal", "Trinkets")
        gen.add(wizard, "Wizard")
        gen.add("hexdoc.hexic.title", "Hexic")
        gen.add("hexdoc.hexic.description", "Miscellaneous neat features and QoL patterns for Hex Casting")
  pack.addProvider:
    new FabricRecipeProvider(_):
      override def generate(consumer: Consumer[RecipeJsonProvider]): Unit =
        ;
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
