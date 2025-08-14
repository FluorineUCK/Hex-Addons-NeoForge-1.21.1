package org.eu.net.pool.hexic.client

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider
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