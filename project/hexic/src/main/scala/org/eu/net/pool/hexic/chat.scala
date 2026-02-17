package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType, ListIota, NullIota}
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentAccess, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.entity.{EntityComponentFactoryRegistry, EntityComponentInitializer, RespawnCopyStrategy}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.nbt.{NbtCompound, NbtElement}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.eu.net.pool.phlib.{Events as PhEvents, *, given}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import scala.collection.immutable.*
import scala.language.implicitConversions

private[hexic] case class MurmurCache(var value: Option[String]) extends Component:
  override def readFromNbt(tag: NbtCompound): Unit =
    if tag.getBoolean("active") then
      value = Some(tag.getString("value"))
    else
      value = None
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putBoolean("active", false)
    for value <- value do
      tag.putBoolean("active", true)
      tag.putString("value", value)
private[hexic] object MurmurCache:
  given ComponentKey[MurmurCache] = ComponentRegistry.getOrCreate("murmur", classOf[MurmurCache])

private[hexic] case class RevealComponent(var lines: Seq[Text]) extends AutoSyncedComponent:
  override def readFromNbt(tag: NbtCompound): Unit =
    lines = for n <- 0 until tag.getInt("lineCount") yield Text.Serializer.fromJson(tag(s"line$n")) // nbt is json apparently?
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putInt("lineCount", lines.size)
    for (line, n) <- lines.zipWithIndex do tag(s"line$n") = Text.Serializer.toJsonTree(line)
private[hexic] object RevealComponent:
  given ComponentKey[RevealComponent] = ComponentRegistry.getOrCreate("reveal", classOf[RevealComponent])

def keyOf[C <: Component: ComponentKey as key] = key

extension (c: ComponentAccess)
  def component[C <: Component: ComponentKey as key]: C = c.getComponent(key)
  def syncComponent[C <: Component: ComponentKey as key](): Unit = c.syncComponent(key)

object hasComponent:
  def unapply[C <: Component: ComponentKey](ctx: ComponentAccess): Option[C] =
    try
      Some(ctx.component[C])
    catch case _: NoSuchElementException =>
      None

def initChat() =
  Patterns.register("reveal", ne"deqed" ):
    Patterns.mkConstAction(1, 0):
      case Seq(iota: Iota) =>
        locally(summon[CastingEnvironment]).getCastingEntity match
          case null => throw MishapBadCaster()
          case p: ServerPlayerEntity =>
            p.component[RevealComponent].lines = iota match
              case s: ListIota => s.getList.map(_.display).toSeq
              //case m: MapIota => m.map.toSeq.map(p => IotaType.getDisplay(p._1) -> IotaType.getDisplay(p._2)).sortBy(_._1.getString)
              case _: NullIota => Seq()
              case _ => Seq(iota.display)
            p.syncComponent[RevealComponent]()
            Seq()
          case _ => throw MishapBadCaster()
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral: (env, _) ?=>
      hasComponent.unapply[MurmurCache](env.getCastingEntity).fold(throw MishapBadCaster())(_.value.fold(NullIota())(StringIota.make))
  ServerPlayNetworking.registerGlobalReceiver("murmur", { case (_, hasComponent[MurmurCache](c), _, buf, _) => c.value = Option.when(buf.readBoolean())(buf.readString()) }: ServerPlayNetworking.PlayChannelHandler)