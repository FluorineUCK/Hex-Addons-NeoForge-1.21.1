//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType, Vec3Iota}
import at.petrak.hexcasting.common.lib.HexRegistries
import com.mojang.serialization.{Codec, DynamicOps}
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.nbt.scanner.NbtCollector
import net.minecraft.nbt.{AbstractNbtList, AbstractNbtNumber, NbtCompound, NbtElement, NbtInt, NbtOps, NbtString}
import net.minecraft.registry.{Registry, RegistryKey}
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.{Formatting, Identifier}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.eu.net.pool.hexic
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.chaining.given

given Logger = LoggerFactory.getLogger("hexic")

def init(): Unit =
  Registry.register(HexRegistries.IOTA_TYPE, "location": Identifier, LocationIota)
  Registry.register(HexRegistries.IOTA_TYPE, "text": Identifier, TextIota)
  Registry.register(HexRegistries.IOTA_TYPE, "nbt": Identifier, NbtIota)

trait Selector[-T, R]:
  def apply(target: T): R
  def update(target: T, value: R): Unit

//extension [T, R] (target: T)
//  def apply(selector: Selector[T, R]): R = selector(target)
//  def update(selector: Selector[T, R], value: R): Unit = selector(target) = value

given Conversion[Iota, IotaDuck] = _.asInstanceOf
given Conversion[IotaDuck, Iota] = _.asInstanceOf

def copy[T <: Iota](iota: T)(using ServerWorld): T | Null = iota.getType.deserialize(iota, summon[ServerWorld]).asInstanceOf[T | Null]

class IotaComponent[R: Codec](val id: Identifier):
  def apply(target: Iota): Option[R] =
    val data: NbtCompound = target
    if (data.contains(id.toString))
      try
        Some(data.get(id.toString))
      catch
        case _: RuntimeException => None
    else
      None
  def update(target: Iota, value: Option[R])(using ServerWorld): target.type =
    val data: NbtCompound = target
    value.fold(data.remove(id.toString))(data.put(id.toString, _))
    (data: Iota | Null) match {
      case iota: target.type => iota
      case _ => panic("Iota changed types or became null during serialization")
    }

@tailrec
def panic(reason: String): Nothing =
  System.err.println(s"thread '${Thread.currentThread.getName}' panicked at '${reason}'")
  System.err.flush()
  Runtime.getRuntime.halt(101)
  panic(reason)

case class LocationIota(vec: Vec3d, dim: Option[RegistryKey[World]]) extends Vec3Iota(vec), IotaTypeHint:
  override def serialize: NbtElement = NbtCompound().tap(_.put("vec", super.serialize())).tap(n => dim.map(v => n.putString("dim", v.getValue.toString)))
  override def hexic$iotaType(): IotaType[?] = LocationIota

object LocationIota extends IotaType[LocationIota]:
  override def color: Int = Vec3Iota.TYPE.color()
  override def deserialize(using NbtElement, ServerWorld): LocationIota = ???
  override def display(d: NbtElement): Text = d match
    case d: NbtCompound => Vec3Iota.TYPE.display(d.get("vec"))
    case _ => null

given Codec[Text] = Codecs.TEXT
given DynamicOps[NbtElement] = NbtOps.INSTANCE

case class TextIota(text: Text) extends Iota(TextIota, text):
  override def isTruthy = true
  override def toleratesOther(i: Iota): Boolean = i match
    case t: TextIota => text == t.text
    case t: StringIota => text.getString == t.getString
    case _ => false
  override def serialize: NbtElement = text

class NbtIota(val data: NbtElement) extends Iota(NbtIota, data):
  override def isTruthy: Boolean = data match
    case d: AbstractNbtNumber => d.numberValue != 0
    case a: AbstractNbtList[?] => a.size != 0
    case s: NbtString => s.asString != ""
    case c: NbtCompound => c.getSize != 0
    case _ => true
  override def toleratesOther(that: Iota): Boolean = that match
    case that: NbtIota => this.data == that.data
    case _ => this.data == that
  override def serialize: NbtElement = data
object NbtIota extends IotaType[NbtIota]:
  def color: Int = Formatting.DARK_AQUA.getColorValue
  def deserialize(using NbtElement, ServerWorld): NbtIota = NbtIota(summon)
  def display(e: NbtElement): Text = ???

object TextIota extends IotaType[TextIota]:
  def color(): Int = Vec3Iota.TYPE.color()
  def deserialize(using NbtElement, ServerWorld): TextIota = ???
  def display(using NbtElement): Text =
    given ServerWorld = null
    deserialize.text