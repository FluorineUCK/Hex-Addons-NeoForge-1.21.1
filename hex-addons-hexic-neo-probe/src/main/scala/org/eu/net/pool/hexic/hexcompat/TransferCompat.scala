package org.eu.net.pool.hexic.hexcompat.transfer

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.item.component.CustomData

trait TransactionContext

class Transaction extends TransactionContext

abstract class SnapshotParticipant[T]:
  def createSnapshot(): T
  def readSnapshot(snapshot: T): Unit
  protected def updateSnapshots(tx: TransactionContext): Unit = ()

case class ItemVariant(stack: ItemStack):
  def toStack(count: Int): ItemStack = stack.copyWithCount(count)

object ItemVariant:
  def of(stack: ItemStack): ItemVariant = ItemVariant(stack.copy())

  def of(item: Item, tag: CompoundTag): ItemVariant =
    val stack = ItemStack(item)
    if !tag.isEmpty then stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    ItemVariant(stack)
