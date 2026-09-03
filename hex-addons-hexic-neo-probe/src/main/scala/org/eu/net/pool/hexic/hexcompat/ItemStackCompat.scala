package org.eu.net.pool.hexic.hexcompat

import net.minecraft.core.component.DataComponents
import net.minecraft.core.{HolderLookup, RegistryAccess}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

extension (stack: ItemStack)
  def getNbt: CompoundTag | Null =
    val data = stack.get(DataComponents.CUSTOM_DATA)
    if data == null then null else data.getUnsafe

  def setNbt(tag: CompoundTag | Null): Unit =
    if tag == null || tag.isEmpty then
      stack.remove(DataComponents.CUSTOM_DATA)
    else
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

  def getOrCreateNbt(): CompoundTag =
    val existing = getNbt
    if existing != null then existing
    else
      val created = CompoundTag()
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(created))
      stack.get(DataComponents.CUSTOM_DATA).getUnsafe

  def getSubNbt(key: String): CompoundTag | Null =
    val root = getNbt
    if root == null || !root.contains(key, Tag.TAG_COMPOUND) then null else root.getCompound(key)

  def getOrCreateSubNbt(key: String): CompoundTag =
    val root = getOrCreateNbt()
    if root.contains(key, Tag.TAG_COMPOUND) then
      root.getCompound(key)
    else
      val child = CompoundTag()
      root.put(key, child)
      child

  def copyAndEmpty(): ItemStack =
    stack.copyAndClear()

  def writeNbt(registries: HolderLookup.Provider, target: CompoundTag): CompoundTag =
    stack.save(registries, target) match
      case compound: CompoundTag => compound
      case other => throw IllegalStateException(s"Expected ItemStack CompoundTag, got ${other.getClass.getName}")

def itemStackFromNbt(registries: HolderLookup.Provider, tag: CompoundTag): ItemStack =
  ItemStack.parseOptional(registries, tag)

private lazy val builtinRegistryAccess =
  RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)

def itemStackFromNbt(tag: CompoundTag): ItemStack =
  if !tag.contains("id", Tag.TAG_STRING) then
    ItemStack.EMPTY
  else
    val normalized =
      if tag.contains("Count", Tag.TAG_ANY_NUMERIC) || tag.contains("tag", Tag.TAG_COMPOUND) then
        val migrated = CompoundTag()
        migrated.putString("id", tag.getString("id"))
        val legacyCount =
          if tag.contains("Count", Tag.TAG_ANY_NUMERIC) then tag.getInt("Count")
          else 1
        migrated.putInt("count", math.max(1, legacyCount))
        val components =
          if tag.contains("components", Tag.TAG_COMPOUND) then tag.getCompound("components").copy()
          else CompoundTag()
        if tag.contains("tag", Tag.TAG_COMPOUND) then
          components.put("minecraft:custom_data", tag.getCompound("tag").copy())
        if !components.isEmpty then migrated.put("components", components)
        migrated
      else
        tag
    ItemStack.parseOptional(builtinRegistryAccess, normalized)

extension (player: Player)
  def dropItem(stack: ItemStack, throwRandomly: Boolean, retainOwnership: Boolean): ItemEntity =
    player.drop(stack, throwRandomly, retainOwnership)
