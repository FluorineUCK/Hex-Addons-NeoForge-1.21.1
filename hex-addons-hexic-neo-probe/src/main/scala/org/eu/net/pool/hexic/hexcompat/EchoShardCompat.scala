package org.eu.net.pool
package hexic.hexcompat

import at.petrak.hexcasting.api.casting.iota.{GarbageIota, Iota, PatternIota}
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.{CompoundTag, ListTag, Tag}
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.{InteractionHand, InteractionResult}
import net.minecraft.world.item.{ItemStack, Items}
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

import java.util.function.Consumer
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object EchoShardCompat:
  val MemoryKey = "hexic:memory"

  def appendPattern(stack: ItemStack, pattern: HexPattern): Unit =
    if stack.is(Items.ECHO_SHARD) then
      CustomData.update(
        DataComponents.CUSTOM_DATA,
        stack,
        new Consumer[CompoundTag]:
          override def accept(tag: CompoundTag): Unit =
            val patterns =
              if tag.contains(MemoryKey, Tag.TAG_LIST) then
                tag.getList(MemoryKey, Tag.TAG_COMPOUND)
              else
                ListTag()
            serializeIota(PatternIota(pattern)) match
              case encoded: CompoundTag =>
                patterns.add(encoded)
                tag.put(MemoryKey, patterns)
              case encoded =>
                throw IllegalStateException(
                  s"Hex Casting encoded PatternIota as ${encoded.getClass.getName}, expected CompoundTag"
                )
      )

  def memoryCount(stack: ItemStack): Int =
    memoryTags(stack).size

  def hasMemory(stack: ItemStack): Boolean =
    memoryCount(stack) > 0

  def appendTooltip(stack: ItemStack, tooltip: java.util.List[Component]): Unit =
    if stack.is(Items.ECHO_SHARD) && hasMemory(stack) then
      tooltip.add(
        Component.translatable("hexic.spell_memory")
          .withStyle(style => style.withColor(0xfc77be))
      )

  def onRightClick(event: PlayerInteractEvent.RightClickItem): Unit =
    val stack = event.getItemStack
    if stack.is(Items.ECHO_SHARD) && hasMemory(stack) then
      val level = event.getLevel
      event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide))
      event.setCanceled(true)
      if !level.isClientSide then
        event.getEntity match
          case player: ServerPlayer =>
            castMemory(player, event.getHand)
          case _ =>

  /**
   * Execute the shard's stored patterns against the staffcast image belonging
   * to the hand that used the shard. Returns true only when a stored payload
   * was consumed.
   */
  def castMemory(player: ServerPlayer, hand: InteractionHand): Boolean =
    val stack = player.getItemInHand(hand)
    val tags = memoryTags(stack)
    if !stack.is(Items.ECHO_SHARD) || tags.isEmpty then
      false
    else
      val replacement = stack.copyWithCount(1)
      removeMemory(replacement)
      val iotas = tags.map: tag =>
        try Option(deserializeIota(tag)).getOrElse(GarbageIota())
        catch case NonFatal(_) => GarbageIota()

      val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(player, hand)
      try
        vm.queueExecuteAndWrapIotas(iotas.asJava, player.serverLevel())
        IXplatAbstractions.INSTANCE.setStaffcastImage(player, vm.getImage)
      finally
        stack.shrink(1)
        if stack.isEmpty then
          player.setItemInHand(hand, replacement)
        else if !player.getInventory.add(replacement) then
          player.drop(replacement, false)
      true

  private def memoryTags(stack: ItemStack): Vector[CompoundTag] =
    Option(stack.get(DataComponents.CUSTOM_DATA))
      .map(_.copyTag())
      .filter(_.contains(MemoryKey, Tag.TAG_LIST))
      .map(_.getList(MemoryKey, Tag.TAG_COMPOUND).asScala.collect:
        case compound: CompoundTag => compound.copy()
      .toVector)
      .getOrElse(Vector.empty)

  private def removeMemory(stack: ItemStack): Unit =
    Option(stack.get(DataComponents.CUSTOM_DATA)) match
      case Some(data) =>
        val tag = data.copyTag()
        tag.remove(MemoryKey)
        if tag.isEmpty then stack.remove(DataComponents.CUSTOM_DATA)
        else CustomData.set(DataComponents.CUSTOM_DATA, stack, tag)
      case None =>
