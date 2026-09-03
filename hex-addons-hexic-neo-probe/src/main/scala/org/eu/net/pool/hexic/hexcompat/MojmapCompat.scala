package org.eu.net.pool.hexic.hexcompat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.dispenser.BlockSource
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.stats.Stat
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

import java.nio.file.Path
import java.util.function.Predicate
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

def classNamed(name: String): Option[ClassTag[?]] =
  try Some(ClassTag(Class.forName(name)))
  catch case _: ClassNotFoundException => None

extension (server: MinecraftServer)
  def getOverworld: ServerLevel = server.overworld()
  def getWorld(key: ResourceKey[Level]): ServerLevel | Null = server.getLevel(key)
  def getWorlds: Iterable[ServerLevel] = server.getAllLevels.asScala
  def getSavePath(resource: LevelResource): Path = server.getWorldPath(resource)

extension (key: ResourceKey[?])
  def getValue: ResourceLocation = key.location()

extension (level: Level)
  def getRegistryKey: ResourceKey[Level] = level.dimension()
  def isClient: Boolean = level.isClientSide
  def getTime: Long = level.getGameTime
  def setBlockState(pos: BlockPos, state: BlockState, flags: Int): Boolean =
    level.setBlock(pos, state, flags)

extension (level: ServerLevel)
  def getEntitiesByClass[T <: Entity](cls: Class[T], box: AABB, predicate: Predicate[? >: T]): java.util.List[T] =
    level.getEntitiesOfClass(cls, box, predicate)

extension (pos: BlockPos)
  def offset(direction: Direction): BlockPos = pos.relative(direction)

extension (entity: Entity)
  def getWorld: Level = entity.level()
  def getPos: Vec3 = entity.position()
  def getYaw: Float = entity.getYRot
  def getPitch: Float = entity.getXRot
  def getBlockPos: BlockPos = entity.blockPosition()

extension (itemEntity: ItemEntity)
  def getStack: ItemStack = itemEntity.getItem

extension (player: Player)
  def getStackInHand(hand: InteractionHand): ItemStack = player.getItemInHand(hand)
  def incrementStat(stat: Stat[?]): Unit = player.awardStat(stat)

extension (vec: Vec3)
  def getX: Double = vec.x
  def getY: Double = vec.y
  def getZ: Double = vec.z

extension (source: BlockSource)
  def getPos: BlockPos = source.pos()
  def getWorld: ServerLevel = source.level()
  def getBlockState: BlockState = source.state()

extension (block: Block)
  def getDefaultState: BlockState = block.defaultBlockState()

extension (state: BlockState)
  def get[T <: Comparable[T]](property: Property[T]): T = state.getValue(property)

extension (stack: ItemStack)
  def isOf(item: Item): Boolean = stack.is(item)
  def decrement(amount: Int): Unit = stack.shrink(amount)

extension (slot: Slot)
  def getStack: ItemStack = slot.getItem
  def setStack(stack: ItemStack): Unit = slot.set(stack)
