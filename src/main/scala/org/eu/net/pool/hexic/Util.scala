package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType}
import at.petrak.hexcasting.common.lib.HexRegistries
import com.mojang.serialization.{Codec, DynamicOps}
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.item.Item
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.{Registries, Registry, RegistryKey, SimpleRegistry}
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier

import scala.annotation.{showAsInfix, targetName}
import scala.util.NotGiven

given [T]: Conversion[RegistryKey[Registry[T]], ? <: Registry[T]] = (Registries.REGISTRIES.asInstanceOf[Registry[Registry[T]]]).get(_)
given Conversion[String, Identifier] = Identifier.of("hexic", _)

class Registrar[T](val id: Identifier):
  val key: RegistryKey[Registry[T]] = RegistryKey.ofRegistry[T](id)
  lazy val registry: SimpleRegistry[T] = FabricRegistryBuilder.createSimple(key).buildAndRegister()

object Registrar:
  given [T]: Conversion[Registrar[T], RegistryKey[Registry[T]]] = _.key
  given [T]: Conversion[Registrar[T], Registry[T]] = _.registry

given [T]: Conversion[RegistryEntry[T], T] = _.value()
given [T]: Conversion[Registry[T], RegistryKey[? <: Registry[T]]] = _.getKey

given [T <: Iota]: Conversion[T, NbtCompound] = IotaType.serialize(_)
given ServerWorld => Conversion[NbtCompound, Iota | Null] = IotaType.deserializeIota(_, summon)

given [T: Codec, R: DynamicOps]: Conversion[T, R] = summon[Codec[T]].encodeStart(summon, _).getOrThrow(false, _ => {})
given [T: Codec, R: DynamicOps]: Conversion[R, T] = summon[Codec[T]].decode(summon, _).getOrThrow(false, _ => {}).getFirst
given [T: DynamicOps, U: DynamicOps]: Conversion[T, U] = summon[DynamicOps[T]].convertTo(summon, _)