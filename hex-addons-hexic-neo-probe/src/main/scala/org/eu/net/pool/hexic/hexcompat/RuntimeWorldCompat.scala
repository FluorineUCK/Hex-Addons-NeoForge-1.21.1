package org.eu.net.pool.hexic.hexcompat.runtimeworld

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.{AABB, Vec3}
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.eu.net.pool.hexic.JavaPlaneAccess
import org.eu.net.pool.hexic.hexcompat.getSavePath

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util as ju
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

/**
 * Handle for one real runtime dimension.
 *
 * Unlike the old pre2 compatibility surrogate, `asWorld` has the same unique
 * key as `asKey`; local plane coordinates therefore require no translation.
 */
class RuntimeWorldHandle private[runtimeworld] (
  private val manager: FantasyManager,
  private val server: MinecraftServer,
  val id: ResourceLocation,
  private val world: ServerLevel
):
  private val metadataRoot: Path =
    server.getSavePath(LevelResource.ROOT)
      .resolve("hexic-runtimeworld")
      .resolve(id.getNamespace)
  private val metadataPath: Path =
    metadataRoot.resolve(s"${id.getPath}.parent")
  private val legacyOriginPath: Path =
    metadataRoot.resolve(s"${id.getPath}.origin")
  private var parent: Option[(ResourceKey[Level], BlockPos)] = readParent()

  val asKey: ResourceKey[Level] =
    ResourceKey.create(Registries.DIMENSION, id)

  val origin: BlockPos = BlockPos.ZERO

  /** Kept for source compatibility; it now means a dedicated unique level. */
  val usesDedicatedCellLevel: Boolean =
    world.dimension() == asKey

  def usesTrueRuntimeLevel: Boolean =
    world.dimension() == asKey && Option(server.getLevel(asKey)).contains(world)

  def asWorld: ServerLevel = world

  def planePos(local: BlockPos): BlockPos = local.immutable()

  def bounds: AABB =
    AABB(0.0, 0.0, 0.0, 11.0, 11.0, 11.0)

  def entities: Seq[Entity] =
    world.getAllEntities.asScala.toSeq

  def parentInfo: Option[(ResourceKey[Level], BlockPos)] = parent

  def parentInfo_=(value: Option[(ResourceKey[Level], BlockPos)]): Unit =
    parent = value
    writeParent()

  def unload(): Unit =
    evacuatePlayers()
    manager.enqueue(this, deleteFiles = false)

  def delete(): Unit =
    removeResidualEntities()
    Files.deleteIfExists(metadataPath)
    Files.deleteIfExists(legacyOriginPath)
    manager.enqueue(this, deleteFiles = true)

  private[runtimeworld] def finishUnload(deleteFiles: Boolean): Unit =
    RuntimeWorldFactory.unload(server, world, deleteFiles)

  private[hexic] def probeEvacuateResidualPlayer(player: Player): Unit =
    evacuateResidualPlayer(player)

  private def removeResidualEntities(): Unit =
    entities.foreach:
      case player: Player =>
        evacuateResidualPlayer(player)
      case living: LivingEntity =>
        // Match Hexic's original deleteworld semantics: let living entities
        // run their normal death/removal path, then discard anything that a
        // modded implementation leaves alive.
        living.kill()
        if !living.isRemoved then
          living.discard()
      case entity =>
        entity.discard()

  private def evacuatePlayers(): Unit =
    world.players().asScala.toSeq.foreach(evacuateResidualPlayer)

  private def evacuateResidualPlayer(player: Player): Unit =
    val (outer, pos) = parentInfo
      .flatMap: (key, boundPos) =>
        Option(server.getLevel(key)).map(_ -> Vec3.atBottomCenterOf(boundPos))
      .getOrElse:
        server.overworld() -> Vec3.atBottomCenterOf(server.overworld().getSharedSpawnPos)
    JavaPlaneAccess.shatterDemiplanePlayer(player, outer, pos)

  private def readParent(): Option[(ResourceKey[Level], BlockPos)] =
    if Files.exists(metadataPath) then
      val values = Files.readAllLines(metadataPath, StandardCharsets.UTF_8).asScala.toSeq
      for
        dimension <- values.lift(0).flatMap(value => Option(ResourceLocation.tryParse(value)))
        x <- values.lift(1).flatMap(value => Try(Integer.parseInt(value)).toOption)
        y <- values.lift(2).flatMap(value => Try(Integer.parseInt(value)).toOption)
        z <- values.lift(3).flatMap(value => Try(Integer.parseInt(value)).toOption)
      yield ResourceKey.create(Registries.DIMENSION, dimension) -> BlockPos(x, y, z)
    else
      None

  private def writeParent(): Unit =
    parent match
      case Some((dimension, pos)) =>
        Files.createDirectories(metadataPath.getParent)
        Files.write(
          metadataPath,
          Seq(
            dimension.location().toString,
            pos.getX.toString,
            pos.getY.toString,
            pos.getZ.toString
          ).asJava,
          StandardCharsets.UTF_8
        )
      case None =>
        Files.deleteIfExists(metadataPath)

object RuntimeWorldHandle:
  /** Static data-driven template only; demiplanes no longer live in this level. */
  val CellLevelId: ResourceLocation =
    ResourceLocation.fromNamespaceAndPath("hexic", "cell")

  val CellLevelKey: ResourceKey[Level] =
    ResourceKey.create(Registries.DIMENSION, CellLevelId)

class RuntimeWorldConfig:
  private[runtimeworld] var dimensionType: Option[ResourceKey[?]] = None
  private[runtimeworld] var generator: Option[Any] = None

  def setDimensionType(key: ResourceKey[?]): RuntimeWorldConfig =
    dimensionType = Some(key)
    this

  def setGenerator(value: Any): RuntimeWorldConfig =
    generator = Some(value)
    this

/**
 * Source-compatible constructor marker retained for the original Hexic call
 * site. RuntimeWorldFactory uses the `hexic:cell` data-pack generator.
 */
class VoidChunkGenerator(registry: Any)

class FantasyManager private[runtimeworld] (private val server: MinecraftServer):
  private val handles = mutable.Map.empty[ResourceLocation, RuntimeWorldHandle]
  private val pending = mutable.LinkedHashMap.empty[ResourceLocation, (RuntimeWorldHandle, Boolean)]

  def getOrOpenPersistentWorld(id: ResourceLocation, config: RuntimeWorldConfig): RuntimeWorldHandle =
    handles.get(id) match
      case Some(handle) if handle.usesTrueRuntimeLevel =>
        pending.remove(id)
        handle
      case _ =>
        pending.remove(id)
        val world = RuntimeWorldFactory.open(server, id)
        val handle = RuntimeWorldHandle(this, server, id, world)
        handles(id) = handle
        handle

  private[runtimeworld] def enqueue(handle: RuntimeWorldHandle, deleteFiles: Boolean): Unit =
    handles.remove(handle.id)
    pending(handle.id) = handle -> deleteFiles

  private[runtimeworld] def tick(): Unit =
    val work = pending.values.toSeq
    pending.clear()
    work.foreach: (handle, deleteFiles) =>
      handle.finishUnload(deleteFiles)

  private[runtimeworld] def stop(): Unit =
    pending.clear()
    handles.clear()

  /** Deterministic seam for server-start probes, where no next tick is available. */
  private[hexic] def drainPendingForProbe(): Unit = tick()

object Fantasy:
  private val managers = ju.WeakHashMap[MinecraftServer, FantasyManager]()
  private var lifecycleRegistered = false

  def get(server: MinecraftServer): FantasyManager =
    managers.synchronized:
      managers.computeIfAbsent(server, FantasyManager(_))

  def registerLifecycle(bus: IEventBus): Unit =
    Fantasy.synchronized:
      if !lifecycleRegistered then
        lifecycleRegistered = true
        bus.addListener((event: ServerTickEvent.Pre) =>
          managers.synchronized:
            Option(managers.get(event.getServer)).foreach(_.tick())
        )
        bus.addListener((event: ServerStoppingEvent) =>
          managers.synchronized:
            Option(managers.remove(event.getServer)).foreach(_.stop())
        )
