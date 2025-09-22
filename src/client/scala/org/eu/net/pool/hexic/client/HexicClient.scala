package org.eu.net.pool.hexic.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricLanguageProvider, FabricModelProvider, FabricRecipeProvider, FabricTagProvider}
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.data.client.{BlockStateModelGenerator, ItemModelGenerator, Models}
import net.minecraft.data.server.recipe.RecipeJsonProvider
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.registry.{MutableRegistry, Registries, RegistryKeys, RegistryWrapper}
import net.minecraft.screen.slot.Slot
import net.minecraft.util.collection.DefaultedList
import org.eu.net.pool.common_curses.{HotbarRendering, SlotAccess, TextManipulator}
import org.eu.net.pool.common_curses.client.CommonCursesClientKt
import org.eu.net.pool.hexic.{*, given}
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

def init(): Unit =
  HotbarRendering.Companion.getEvent.register: () =>
    foldLocalPlayer(HotbarRendering.ALL):
      _.getComponent(PlayerInfoComponent.key).wispMedia.fold(HotbarRendering.ALL)(_ => HotbarRendering.NONE)

def datagen(gen: FabricDataGenerator): Unit =
  val pack = gen.createPack()
  pack.addProvider:
    new FabricModelProvider(_):
      override def generateBlockStateModels(gen: BlockStateModelGenerator): Unit =
        ;
      override def generateItemModels(gen: ItemModelGenerator): Unit =
        for (_, item) <- Mediaweave.colors do gen.register(item, Models.GENERATED)
        gen.register(wizard, Models.GENERATED)
  pack.addProvider:
    new FabricLanguageProvider(_):
      override def generateTranslations(gen: FabricLanguageProvider.TranslationBuilder): Unit =
        for (action, name) <- Vector(
          "nbt/lift1" -> "Byte Purification",
          "nbt/lift2" -> "Short Purification",
          "nbt/lift4" -> "Integer Purification",
          "nbt/lift8" -> "Long Purification",
          "nbt/liftf" -> "Float Purification",
          "nbt/liftd" -> "Double Purification",
          "nbt/literal/collection" -> "Vacant Reflection: Collection",
          "nbt/literal/list" -> "Vacant Reflection: List",
          "nbt/literal/array1" -> "Vacant Reflection: Byte Array",
          "nbt/literal/array2" -> "Vacant Reflection: Short Array",
          "nbt/literal/array4" -> "Vacant Reflection: Integer Array",
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
          "staffcast_factory" -> "Lani's Lesser Gambit",
          "staffcast_factory/lazy" -> "Lani's Greater Gambit",
          "metatable" -> "Metatable Exaltation",
        ) do gen.add(s"hexcasting.action.hexic:$action", name)
        gen.add("hexic.bad_metatable", "Expected a map in the §a%s§r property but got %s")

        for (color, item) <- Mediaweave.colors do
          gen.add(item, s"${color.getName.split('_').map(_.capitalize).mkString(" ")} Mediaweave")
        gen.add("tag.item.hexic.mediaweaves", "Mediaweave")
        gen.add(wizard, "Wizard")
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