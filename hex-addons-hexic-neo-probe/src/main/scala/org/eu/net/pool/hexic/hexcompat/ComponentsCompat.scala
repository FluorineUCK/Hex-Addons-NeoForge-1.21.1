package org.eu.net.pool.hexic.hexcompat

import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo, Tag}
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.util.WeakHashMap
import java.util.function.Function as JFunction
import java.util.function.Consumer
import scala.annotation.targetName
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

trait HexComponent:
  def readFromNbt(tag: CompoundTag): Unit
  def writeToNbt(tag: CompoundTag): Unit

trait AutoSyncedHexComponent extends HexComponent

enum ComponentCopyPolicy:
  case Always, LosslessOnly, Never

final class ComponentKey[C <: HexComponent](val id: String, val copyPolicy: ComponentCopyPolicy, val factory: AnyRef => C):
  ComponentStore.registerKey(this)
  override def toString: String = s"hexic:$id"

object ComponentKey:
  def apply[C <: HexComponent](id: String)(factory: AnyRef => C): ComponentKey[C] =
    new ComponentKey(id, ComponentCopyPolicy.LosslessOnly, factory)
  def apply[C <: HexComponent](id: String, copyPolicy: ComponentCopyPolicy)(factory: AnyRef => C): ComponentKey[C] =
    new ComponentKey(id, copyPolicy, factory)

object ComponentStore:
  private val log = LoggerFactory.getLogger("hexic")
  private val persistentRootKey = "hexic_components"
  private val playerFileSuffix = "hexic_components"
  private val registeredKeys = mutable.LinkedHashMap.empty[String, ComponentKey[? <: HexComponent]]
  private val values =
    java.util.Collections.synchronizedMap(
      new WeakHashMap[AnyRef, mutable.Map[ComponentKey[?], HexComponent]]()
    )

  private val newBucket = new JFunction[AnyRef, mutable.Map[ComponentKey[?], HexComponent]]:
    override def apply(owner: AnyRef): mutable.Map[ComponentKey[?], HexComponent] =
      mutable.Map.empty

  def registerKey(key: ComponentKey[? <: HexComponent]): Unit =
    registeredKeys.synchronized:
      registeredKeys.getOrElseUpdate(key.id, key)
    ()

  def registerLifecycle(bus: IEventBus): Unit =
    bus.addListener(classOf[PlayerEvent.LoadFromFile], new Consumer[PlayerEvent.LoadFromFile]:
      override def accept(event: PlayerEvent.LoadFromFile): Unit =
        loadPlayer(event)
    )
    bus.addListener(classOf[PlayerEvent.SaveToFile], new Consumer[PlayerEvent.SaveToFile]:
      override def accept(event: PlayerEvent.SaveToFile): Unit =
        savePlayer(event)
    )
    bus.addListener(classOf[PlayerEvent.Clone], new Consumer[PlayerEvent.Clone]:
      override def accept(event: PlayerEvent.Clone): Unit =
        copyPlayer(event.getOriginal, event.getEntity, event.isWasDeath)
    )

  def get[C <: HexComponent](owner: AnyRef, key: ComponentKey[C]): C =
    values.computeIfAbsent(owner, newBucket)
      .getOrElseUpdate(key, {
        val component = key.factory(owner)
        readComponent(owner, key, component)
        component
      })
      .asInstanceOf[C]

  def maybe[C <: HexComponent](owner: AnyRef, key: ComponentKey[C]): Option[C] =
    try Some(get(owner, key))
    catch case _: Throwable => None

  def sync[C <: HexComponent](owner: AnyRef, key: ComponentKey[C]): Unit =
    values.synchronized:
      Option(values.get(owner)).flatMap(_.get(key)).foreach: component =>
        val tag = writeComponent(owner, key, component)
        component match
          case _: AutoSyncedHexComponent =>
            owner match
              case player: ServerPlayer if player.connection != null =>
                NetworkCompat.sendComponent(player, key.id, tag.copy())
              case _ =>
          case _ =>

  def clear(owner: AnyRef): Unit =
    values.remove(owner)
    ()

  def hasPersistentComponent(owner: AnyRef, key: ComponentKey[? <: HexComponent]): Boolean =
    persistentRoot(owner, create = false).exists(_.contains(key.id, Tag.TAG_COMPOUND))

  def applySync(owner: AnyRef, keyId: String, tag: CompoundTag): Boolean =
    persistentRoot(owner, create = true) match
      case Some(root) =>
        root.put(keyId, tag.copy())
        registeredKeys.synchronized:
          registeredKeys.get(keyId).foreach(key => readRegistered(owner, key, tag.copy()))
        true
      case None => false

  private def persistentRoot(owner: AnyRef, create: Boolean): Option[CompoundTag] =
    owner match
      case entity: Entity =>
        val data = entity.getPersistentData
        if data.contains(persistentRootKey, Tag.TAG_COMPOUND) then
          Some(data.getCompound(persistentRootKey))
        else if create then
          val root = CompoundTag()
          data.put(persistentRootKey, root)
          Some(root)
        else
          None
      case _ => None

  private def setPersistentRoot(owner: AnyRef, tag: CompoundTag): Unit =
    owner match
      case entity: Entity =>
        val data = entity.getPersistentData
        if tag.isEmpty then data.remove(persistentRootKey)
        else data.put(persistentRootKey, tag.copy())
      case _ =>

  private def readComponent[C <: HexComponent](owner: AnyRef, key: ComponentKey[C], component: C): Unit =
    persistentRoot(owner, create = false)
      .filter(_.contains(key.id, Tag.TAG_COMPOUND))
      .foreach(root => component.readFromNbt(root.getCompound(key.id)))

  private def writeComponent(owner: AnyRef, key: ComponentKey[?], component: HexComponent): CompoundTag =
    val tag = CompoundTag()
    component.writeToNbt(tag)
    persistentRoot(owner, create = true).foreach: root =>
      root.put(key.id, tag)
    tag

  private def serializeOwner(owner: AnyRef): CompoundTag =
    val root = persistentRoot(owner, create = false).map(_.copy()).getOrElse(CompoundTag())
    Option(values.get(owner)).foreach: bucket =>
      bucket.foreach: (key, component) =>
        val tag = CompoundTag()
        component.writeToNbt(tag)
        root.put(key.id, tag)
    root

  private def deserializeOwner(owner: AnyRef, root: CompoundTag): Unit =
    setPersistentRoot(owner, root)
    registeredKeys.synchronized:
      for key <- registeredKeys.values do
        if root.contains(key.id, Tag.TAG_COMPOUND) then
          readRegistered(owner, key, root.getCompound(key.id))

  private def readRegistered(owner: AnyRef, key: ComponentKey[? <: HexComponent], tag: CompoundTag): Unit =
    val component = get(owner, key.asInstanceOf[ComponentKey[HexComponent]])
    component.readFromNbt(tag)
    writeComponent(owner, key, component)

  private def shouldCopy(key: ComponentKey[?], wasDeath: Boolean): Boolean =
    key.copyPolicy match
      case ComponentCopyPolicy.Always => true
      case ComponentCopyPolicy.LosslessOnly => !wasDeath
      case ComponentCopyPolicy.Never => false

  private def copyPlayer(original: AnyRef, clone: AnyRef, wasDeath: Boolean): Unit =
    val source = serializeOwner(original)
    val target = CompoundTag()
    registeredKeys.synchronized:
      for key <- registeredKeys.values do
        if shouldCopy(key, wasDeath) && source.contains(key.id, Tag.TAG_COMPOUND) then
          target.put(key.id, source.getCompound(key.id).copy())
    deserializeOwner(clone, target)

  private def loadPlayer(event: PlayerEvent.LoadFromFile): Unit =
    val file = event.getPlayerFile(playerFileSuffix).toPath
    if Files.exists(file) then
      try
        deserializeOwner(event.getEntity, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()))
      catch
        case NonFatal(t) =>
          log.warn("Failed to load Hexic player component data from {}", file, t)

  private def savePlayer(event: PlayerEvent.SaveToFile): Unit =
    val file = event.getPlayerFile(playerFileSuffix).toPath
    try
      val tag = serializeOwner(event.getEntity)
      setPersistentRoot(event.getEntity, tag)
      if tag.isEmpty then Files.deleteIfExists(file)
      else NbtIo.writeCompressed(tag, file)
    catch
      case NonFatal(t) =>
        log.warn("Failed to save Hexic player component data to {}", file, t)

extension (owner: AnyRef)
  def component[C <: HexComponent](using key: ComponentKey[C]): C =
    ComponentStore.get(owner, key)

  def getComponent[C <: HexComponent](key: ComponentKey[C]): C =
    ComponentStore.get(owner, key)

  def syncComponent[C <: HexComponent](key: ComponentKey[C]): Unit =
    ComponentStore.sync(owner, key)

  @targetName("syncComponentByType")
  def syncComponent[C <: HexComponent]()(using key: ComponentKey[C]): Unit =
    ComponentStore.sync(owner, key)
