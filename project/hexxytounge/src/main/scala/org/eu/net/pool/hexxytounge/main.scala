package org.eu.net.pool.hexxytounge

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.{IotaType, NullIota}
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.eu.net.pool.phlib.{Events => PhEvents, _}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota
import scala.collection._

private[hexxytounge] given Logger = LoggerFactory.getLogger("hexxytounge")
private[hexxytounge] given Conversion[String, Identifier] = Identifier.of("hexxytounge", _)

val murmurCache = mutable.WeakHashMap[ServerPlayerEntity, String]()

def init() =
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral: (env, _) ?=>
      env.getCastingEntity match
        case murmurCache(str) => StringIota.make(str)
        case _: ServerPlayerEntity => NullIota()
        case _ => throw MishapBadCaster()
  ServerPlayNetworking.registerGlobalReceiver("murmur", (_, player, _, buf, _) =>
    if buf.readBoolean() then
      murmurCache(player) = buf.readString()
    else
      murmurCache -= player)