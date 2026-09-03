package org.eu.net.pool.hexic.hexcompat

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState

import java.util
import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*

/**
 * NeoForge replacement for Hexic 2.1.0's Fabric array-backed
 * `InventoryView.Events` extension points.
 */
object HexicViewApi:
  @FunctionalInterface
  trait Registration extends AutoCloseable:
    override def close(): Unit

  trait Handler:
    def contents(server: MinecraftServer): util.List[Iota] = util.List.of()

    def available(server: MinecraftServer, variant: Iota): Long =
      extract(server, variant, Long.MaxValue, simulate = true)

    def remaining(server: MinecraftServer, variant: Iota): Long =
      insert(server, variant, Long.MaxValue, simulate = true)

    def extract(
      server: MinecraftServer,
      variant: Iota,
      amount: Long,
      simulate: Boolean
    ): Long = 0L

    def insert(
      server: MinecraftServer,
      variant: Iota,
      amount: Long,
      simulate: Boolean
    ): Long = 0L

    def entities(server: MinecraftServer): util.List[Entity] = util.List.of()

    def teleportEntity(server: MinecraftServer, entity: Entity): Boolean = false

  @FunctionalInterface
  trait BlockProvider:
    def create(
      level: ServerLevel,
      pos: BlockPos,
      state: BlockState
    ): util.List[? <: Handler]

  @FunctionalInterface
  trait EntityProvider:
    def create(level: ServerLevel, entity: Entity): util.List[? <: Handler]

  @FunctionalInterface
  trait IotaResolver:
    /**
     * Returns a `BoxedView.Instance`, or null when this resolver does not
     * recognize the iota. The Iota return type keeps this public adapter
     * independent from Scala's nested binary class names.
     */
    def resolve(environment: CastingEnvironment, iota: Iota): Iota | Null

  private val blockProviders = CopyOnWriteArrayList[BlockProvider]()
  private val entityProviders = CopyOnWriteArrayList[EntityProvider]()
  private val iotaResolvers = CopyOnWriteArrayList[IotaResolver]()

  def registerBlockProvider(provider: BlockProvider): Registration =
    blockProviders.add(provider)
    () => blockProviders.remove(provider)

  def registerEntityProvider(provider: EntityProvider): Registration =
    entityProviders.add(provider)
    () => entityProviders.remove(provider)

  def registerIotaResolver(resolver: IotaResolver): Registration =
    iotaResolvers.add(resolver)
    () => iotaResolvers.remove(resolver)

  def blockHandlers(
    level: ServerLevel,
    pos: BlockPos,
    state: BlockState
  ): util.List[Handler] =
    blockProviders.asScala
      .flatMap(provider => Option(provider.create(level, pos, state)).toSeq.flatMap(_.asScala))
      .toList
      .asJava

  def entityHandlers(level: ServerLevel, entity: Entity): util.List[Handler] =
    entityProviders.asScala
      .flatMap(provider => Option(provider.create(level, entity)).toSeq.flatMap(_.asScala))
      .toList
      .asJava

  def resolve(environment: CastingEnvironment, iota: Iota): Iota | Null =
    iotaResolvers.asScala.iterator
      .map(_.resolve(environment, iota))
      .find(_ != null)
      .orNull

  def blockProviderCount(): Int = blockProviders.size()
  def entityProviderCount(): Int = entityProviders.size()
  def iotaResolverCount(): Int = iotaResolvers.size()
