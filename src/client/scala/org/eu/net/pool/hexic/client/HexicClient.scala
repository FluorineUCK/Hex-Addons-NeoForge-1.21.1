package org.eu.net.pool.hexic.client

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.{FabricLanguageProvider, FabricModelProvider}
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.data.client.{BlockStateModelGenerator, ItemModelGenerator}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.collection.DefaultedList
import org.eu.net.pool.common_curses.{HotbarRendering, SlotAccess, TextManipulator}
import org.eu.net.pool.common_curses.client.CommonCursesClientKt
import org.eu.net.pool.hexic.*

import scala.language.experimental.{macros, saferExceptions}
import scala.util.boundary

inline def foldLocalPlayer[R](default: => R)(ifPresent: ClientPlayerEntity => R): R =
  MinecraftClient.getInstance().player match
    case null => default
    case player => ifPresent(player)

def init(): Unit =
  HotbarRendering.Companion.getEvent.register: () =>
    foldLocalPlayer(HotbarRendering.ALL):
      _.getComponent(PlayerWispComponent.key).wispMedia.fold(HotbarRendering.ALL): _ =>
        HotbarRendering.NONE

def datagen(gen: FabricDataGenerator): Unit =
  val pack = gen.createPack()
  pack.addProvider: out =>
    new FabricModelProvider(out) {
      override def generateBlockStateModels(gen: BlockStateModelGenerator): Unit =
        ;
      override def generateItemModels(gen: ItemModelGenerator): Unit =
        gen.register()
    }
  pack.addProvider:
    new FabricLanguageProvider(_) {
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
          "metatable" -> "Metatable Exaltation",
        ) do gen.add(s"hexcasting.action.hexic:$action", name)
        gen.add("hexic.bad_metatable", "Expected a map in the §a%s§r property but got %s")
    }

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