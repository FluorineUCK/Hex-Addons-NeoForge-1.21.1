package org.eu.net.pool.hexic.hexcompat

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import top.theillusivec4.curios.api.{CuriosApi, SlotContext}
import top.theillusivec4.curios.api.`type`.capability.{ICurio, ICurioItem}

object CuriosCompat:
  private val preferredSlots = Seq("hexic_mediaweave", "charm", "necklace", "curio")

  def equippedStacks(entity: LivingEntity): Seq[ItemStack] =
    val curios = CuriosApi.getCuriosInventory(entity)
    if !curios.isPresent then Seq.empty
    else
      val handler = curios.get().getEquippedCurios
      for
        idx <- 0 until handler.getSlots
        stack = handler.getStackInSlot(idx)
        if stack != null && !stack.isEmpty
      yield stack

  def insertIntoFirstEmptySlot(entity: LivingEntity, stack: ItemStack): Boolean =
    val curios = CuriosApi.getCuriosInventory(entity)
    if !curios.isPresent || stack == null || stack.isEmpty then false
    else
      val handler = curios.get()
      val slots =
        (preferredSlots ++ handler.getCurios.keySet().toArray(Array.empty[String])).distinct
      slots.exists: slotId =>
        val stacks = handler.getStacksHandler(slotId)
        stacks.isPresent &&
          (0 until stacks.get().getStacks.getSlots).exists: idx =>
            val slot = stacks.get().getStacks
            val context = SlotContext(slotId, entity, idx, false, true)
            if slot.getStackInSlot(idx).isEmpty && CuriosApi.isStackValid(context, stack) then
              slot.setStackInSlot(idx, stack)
              true
            else false

  def insertOneIntoFirstEmptySlot(entity: LivingEntity, source: ItemStack): Boolean =
    val curios = CuriosApi.getCuriosInventory(entity)
    if !curios.isPresent || source == null || source.isEmpty then false
    else
      val handler = curios.get()
      val slots =
        (preferredSlots ++ handler.getCurios.keySet().toArray(Array.empty[String])).distinct
      slots.exists: slotId =>
        val stacks = handler.getStacksHandler(slotId)
        stacks.isPresent &&
          (0 until stacks.get().getStacks.getSlots).exists: idx =>
            val slot = stacks.get().getStacks
            val candidate = source.copyWithCount(1)
            val context = SlotContext(slotId, entity, idx, false, true)
            if slot.getStackInSlot(idx).isEmpty && CuriosApi.isStackValid(context, candidate) then
              slot.setStackInSlot(idx, source.split(1))
              true
            else false

  def registerLockingItem(item: Item): Unit =
    CuriosApi.registerCurio(item, lockingItem)

  private object lockingItem extends ICurioItem:
    override def canUnequip(context: SlotContext, stack: ItemStack): Boolean =
      lockTag(stack).forall(_.get("lock") == null)

    override def getDropRule(context: SlotContext, source: net.minecraft.world.damagesource.DamageSource, recentlyHit: Boolean, stack: ItemStack): ICurio.DropRule =
      dropRule(stack)

    override def getDropRule(context: SlotContext, source: net.minecraft.world.damagesource.DamageSource, lootingLevel: Int, recentlyHit: Boolean, stack: ItemStack): ICurio.DropRule =
      dropRule(stack)

    private def dropRule(stack: ItemStack): ICurio.DropRule =
      if lockTag(stack).exists(_.get("lock") != null) then
        ICurio.DropRule.ALWAYS_KEEP
      else
        ICurio.DropRule.DEFAULT

    private def lockTag(stack: ItemStack) =
      Option(stack)
        .map(_.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
