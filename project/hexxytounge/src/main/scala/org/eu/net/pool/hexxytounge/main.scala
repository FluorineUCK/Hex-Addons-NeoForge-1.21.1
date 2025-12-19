package org.eu.net.pool.hexxytounge

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.{IotaType, NullIota}
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentAccess, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.entity.{EntityComponentFactoryRegistry, EntityComponentInitializer, RespawnCopyStrategy}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.eu.net.pool.phlib.{Events as PhEvents, *}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import scala.collection.*

private[hexxytounge] given Logger = LoggerFactory.getLogger("hexxytounge")
private[hexxytounge] given Conversion[String, Identifier] = Identifier.of("hexxytounge", _)

private[hexxytounge] case class MurmurCache(var value: Option[String]) extends Component:
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
private[hexxytounge] object MurmurCache:
  given ComponentKey[MurmurCache] = ComponentRegistry.getOrCreate("murmur", classOf[MurmurCache])

class Components extends EntityComponentInitializer:
  override def registerEntityComponentFactories(registry: EntityComponentFactoryRegistry): Unit =
    registry.registerForPlayers(summon[ComponentKey[MurmurCache]], _ => MurmurCache(None), RespawnCopyStrategy.ALWAYS_COPY)

object hasComponent:
  def unapply[C <: Component: ComponentKey as key](ctx: ComponentAccess): Option[C] =
    try
      Some(ctx.getComponent(key))
    catch case _: NoSuchElementException =>
      None

def init() =
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral: (env, _) ?=>
      hasComponent.unapply[MurmurCache](env.getCastingEntity).fold(throw MishapBadCaster())(_.value.fold(NullIota())(StringIota.make))
  ServerPlayNetworking.registerGlobalReceiver("murmur", { case (_, hasComponent[MurmurCache](c), _, buf, _) => c.value = Option.when(buf.readBoolean())(buf.readString()) }: ServerPlayNetworking.PlayChannelHandler)