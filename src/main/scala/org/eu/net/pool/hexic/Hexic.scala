//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType, Vec3Iota}
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.common.lib.HexRegistries
import com.mojang.serialization.{Codec, DynamicOps}
import net.minecraft.nbt.*
import net.minecraft.registry.{Registry, RegistryKey}
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.{MutableText, Text}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.Vec3d
import net.minecraft.util.{Formatting, Identifier}
import net.minecraft.world.World
import org.eu.net.pool.hexic
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import java.{lang, util}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.chaining.given

given Logger = LoggerFactory.getLogger("hexic")

extension (i: Iota)
  def asIotaType[T <: Iota: IotaType: ClassTag](idx: Int, expected: => Text): T = i match
    case i: T => i
    case _ => throw MishapInvalidIota(i, idx, expected)

def init(): Unit =
  Registry.register(HexRegistries.IOTA_TYPE, "location": Identifier, LocationIota)
  Registry.register(HexRegistries.IOTA_TYPE, "text": Identifier, TextIota)
  Registry.register(HexRegistries.IOTA_TYPE, "nbt": Identifier, NbtIota)
  Registry.register(HexRegistries.ACTION, "serialize": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaq", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] = util.List.of(NbtIota(list.get(0)))
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "deserialize": Identifier, ActionRegistryEntry(HexPattern.fromAngles("qawde", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] = util.List.of(list.get(0).asIotaType[NbtIota](0, Text.literal("an ").append(Text.literal("NBT tag").styled(_.withColor(NbtIota.color)))))
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ARITHMETIC, "nbt": Identifier, new Arithmetic:
    import Arithmetic.*
    override def arithName(): String = "NBT"
    private def ops = Map[HexPattern, util.List[Iota] => util.List[Iota]](
      ADD -> ???
    )

    override def opTypes(): lang.Iterable[HexPattern] = ops.keys.asJava

    override def getOperator(pattern: HexPattern): Operator =
      val f = ops(pattern)
      ???
  )

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

given IotaType[TextIota] = TextIota
given IotaType[LocationIota] = LocationIota
given IotaType[NbtIota] = NbtIota

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
  def display(e: NbtElement): Text =
    given Conversion[String, MutableText] = Text.literal
    e match
      case b: NbtByte => s"Byte Tag: ${b.byteValue}"
      case c: NbtCompound => s"Compound Tag [${c.getSize}]"
      case a: NbtByteArray => s"Byte Array Tag [${a.size}]"
      case d: NbtDouble => s"Double Tag: ${d.doubleValue}"
      case _: NbtEnd => (s"End Tag": MutableText).styled(_.withObfuscated(true))
      case f: NbtFloat => s"Float Tag: ${f.floatValue}"
      case i: NbtInt => s"Int Tag: ${i.intValue}"
      case i: NbtIntArray => s"Int Array Tag [${i.size}]"
      case l: NbtList => s"List Tag [${l.size}]"
      case l: NbtLong => s"Long Tag: ${l.longValue}"
      case i: NbtLongArray => s"Long Array Tag [${i.size}]"
      case s: NbtShort => s"Short Tag: ${s.shortValue}"
      case s: NbtString => s"String Tag: ${s.asString}"
object TextIota extends IotaType[TextIota]:
  def color(): Int = Vec3Iota.TYPE.color()
  def deserialize(using NbtElement, ServerWorld): TextIota = TextIota(summon[NbtElement])
  def display(using NbtElement): Text =
    given ServerWorld = null
    deserialize.text