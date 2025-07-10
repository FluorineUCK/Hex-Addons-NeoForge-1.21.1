//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.{DoubleIota, EntityIota, Iota, IotaType, Vec3Iota}
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{MishapInvalidIota, MishapOthersName}
import at.petrak.hexcasting.common.lib.HexRegistries
import com.mojang.serialization.{Codec, DynamicOps}
import net.minecraft.Bootstrap
import net.minecraft.entity.{EntityType, LightningEntity}
import net.minecraft.entity.damage.{DamageSource, DamageSources}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.*
import net.minecraft.nbt.visitor.StringNbtWriter
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
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.convert.ImplicitConversions.given
import scala.jdk.CollectionConverters.*
import scala.language.dynamics
import scala.reflect.ClassTag
import scala.reflect.api.TypeTags
import scala.util.chaining.given

given Logger = LoggerFactory.getLogger("hexic")

extension (i: Iota)
  def asIotaType[T <: Iota: IotaType: ClassTag](idx: Int, expected: => Text): T = i match
    case i: T => i
    case _ => throw MishapInvalidIota(i, idx, expected)

extension (c: NbtCompound)
  def iota(using ServerWorld): Iota = IotaType.deserializeIota(c, summon)

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
  Registry.register(HexRegistries.ACTION, "deserialize": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqa", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      given ServerWorld = castingEnvironment.getWorld
      val iota = list.get(0).asIotaType[NbtIota](0, Text.literal("an ").append(Text.literal("NBT compound").styled(_.withColor(NbtIota.color)))).data.asInstanceOf[NbtCompound].iota
      iota match
        case p: EntityIota => p.getEntity match
          case t: PlayerEntity if t != castingEnvironment.getCastingEntity =>
            throw MishapOthersName(t)
          case _ =>
        case _ =>
      util.List.of(iota)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ARITHMETIC, "nbt": Identifier, {
    import Arithmetic.*
    given Conversion[NbtIota, NbtElement] = _.data
    given Conversion[NbtElement, NbtIota] = NbtIota(_)
    arith("nbt",
      ADD -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: NbtDouble, b: AbstractNbtNumber) => NbtDouble.of(a.doubleValue + b.doubleValue)
            case (a: AbstractNbtNumber, b: NbtDouble) => NbtDouble.of(a.doubleValue + b.doubleValue)
            case (a: NbtFloat, b: AbstractNbtNumber) => NbtFloat.of(a.floatValue + b.floatValue)
            case (a: AbstractNbtNumber, b: NbtFloat) => NbtFloat.of(a.floatValue + b.floatValue)
            case (a: NbtLong, b: AbstractNbtNumber) => NbtLong.of(a.longValue + b.longValue)
            case (a: AbstractNbtNumber, b: NbtLong) => NbtLong.of(a.longValue + b.longValue)
            case (a: NbtInt, b: AbstractNbtNumber) => NbtInt.of(a.intValue + b.intValue)
            case (a: AbstractNbtNumber, b: NbtInt) => NbtInt.of(a.intValue + b.intValue)
            case (a: NbtShort, b: AbstractNbtNumber) => NbtShort.of((a.shortValue + b.shortValue).toShort)
            case (a: AbstractNbtNumber, b: NbtShort) => NbtShort.of((a.shortValue + b.shortValue).toShort)
            case (a: NbtByte, b: AbstractNbtNumber) => NbtByte.of((a.byteValue + b.byteValue).toByte)
            case (a: AbstractNbtNumber, b: NbtByte) => NbtByte.of((a.byteValue + b.byteValue).toByte)
      ),
      SUB -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: NbtDouble, b: AbstractNbtNumber) => NbtDouble.of(a.doubleValue - b.doubleValue)
            case (a: AbstractNbtNumber, b: NbtDouble) => NbtDouble.of(a.doubleValue - b.doubleValue)
            case (a: NbtFloat, b: AbstractNbtNumber) => NbtFloat.of(a.floatValue - b.floatValue)
            case (a: AbstractNbtNumber, b: NbtFloat) => NbtFloat.of(a.floatValue - b.floatValue)
            case (a: NbtLong, b: AbstractNbtNumber) => NbtLong.of(a.longValue - b.longValue)
            case (a: AbstractNbtNumber, b: NbtLong) => NbtLong.of(a.longValue - b.longValue)
            case (a: NbtInt, b: AbstractNbtNumber) => NbtInt.of(a.intValue - b.intValue)
            case (a: AbstractNbtNumber, b: NbtInt) => NbtInt.of(a.intValue - b.intValue)
            case (a: NbtShort, b: AbstractNbtNumber) => NbtShort.of((a.shortValue - b.shortValue).toShort)
            case (a: AbstractNbtNumber, b: NbtShort) => NbtShort.of((a.shortValue - b.shortValue).toShort)
            case (a: NbtByte, b: AbstractNbtNumber) => NbtByte.of((a.byteValue - b.byteValue).toByte)
            case (a: AbstractNbtNumber, b: NbtByte) => NbtByte.of((a.byteValue - b.byteValue).toByte)
      ),
      MUL -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: NbtDouble, b: AbstractNbtNumber) => NbtDouble.of(a.doubleValue * b.doubleValue)
            case (a: AbstractNbtNumber, b: NbtDouble) => NbtDouble.of(a.doubleValue * b.doubleValue)
            case (a: NbtFloat, b: AbstractNbtNumber) => NbtFloat.of(a.floatValue * b.floatValue)
            case (a: AbstractNbtNumber, b: NbtFloat) => NbtFloat.of(a.floatValue * b.floatValue)
            case (a: NbtLong, b: AbstractNbtNumber) => NbtLong.of(a.longValue * b.longValue)
            case (a: AbstractNbtNumber, b: NbtLong) => NbtLong.of(a.longValue * b.longValue)
            case (a: NbtInt, b: AbstractNbtNumber) => NbtInt.of(a.intValue * b.intValue)
            case (a: AbstractNbtNumber, b: NbtInt) => NbtInt.of(a.intValue * b.intValue)
            case (a: NbtShort, b: AbstractNbtNumber) => NbtShort.of((a.shortValue * b.shortValue).toShort)
            case (a: AbstractNbtNumber, b: NbtShort) => NbtShort.of((a.shortValue * b.shortValue).toShort)
            case (a: NbtByte, b: AbstractNbtNumber) => NbtByte.of((a.byteValue * b.byteValue).toByte)
            case (a: AbstractNbtNumber, b: NbtByte) => NbtByte.of((a.byteValue * b.byteValue).toByte)
      ),
      DIV -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: NbtDouble, b: AbstractNbtNumber) => NbtDouble.of(a.doubleValue / b.doubleValue)
            case (a: AbstractNbtNumber, b: NbtDouble) => NbtDouble.of(a.doubleValue / b.doubleValue)
            case (a: NbtFloat, b: AbstractNbtNumber) => NbtFloat.of(a.floatValue / b.floatValue)
            case (a: AbstractNbtNumber, b: NbtFloat) => NbtFloat.of(a.floatValue / b.floatValue)
            case (a: NbtLong, b: AbstractNbtNumber) => NbtLong.of(a.longValue / b.longValue)
            case (a: AbstractNbtNumber, b: NbtLong) => NbtLong.of(a.longValue / b.longValue)
            case (a: NbtInt, b: AbstractNbtNumber) => NbtInt.of(a.intValue / b.intValue)
            case (a: AbstractNbtNumber, b: NbtInt) => NbtInt.of(a.intValue / b.intValue)
            case (a: NbtShort, b: AbstractNbtNumber) => NbtShort.of((a.shortValue / b.shortValue).toShort)
            case (a: AbstractNbtNumber, b: NbtShort) => NbtShort.of((a.shortValue / b.shortValue).toShort)
            case (a: NbtByte, b: AbstractNbtNumber) => NbtByte.of((a.byteValue / b.byteValue).toByte)
            case (a: AbstractNbtNumber, b: NbtByte) => NbtByte.of((a.byteValue / b.byteValue).toByte)
      ),
      INDEX -> ((a: NbtIota, b: DoubleIota | StringIota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: AbstractNbtList[? <: NbtElement], b: DoubleIota) => a.get(b.asIntOrThrow(0))
            case (a: NbtCompound, b: NbtString) => a.get(b.asString)
      ),
      SLICE -> ((a: NbtIota, f: DoubleIota | StringIota, t: DoubleIota) =>
        Seq[NbtIota]:
          (a.data, f, t) match
            case (a: NbtByteArray, b: DoubleIota, c: DoubleIota) => (a: Array[Byte]).slice(b `asIntOrThrow` 1, c `asIntOrThrow` 2): NbtByteArray
            case (a: NbtIntArray, b: DoubleIota, c: DoubleIota) => (a: Array[Int]).slice(b `asIntOrThrow` 1, c `asIntOrThrow` 2): NbtIntArray
            case (a: NbtLongArray, b: DoubleIota, c: DoubleIota) => (a: Array[Long]).slice(b `asIntOrThrow` 1, c `asIntOrThrow` 2): NbtLongArray
            case (a: NbtList, b: DoubleIota, c: DoubleIota) =>
              val l = NbtList()
              a.slice(b `asIntOrThrow` 1, c `asIntOrThrow` 2).foreach(l.add)
              l
            case (a: NbtCompound, b: StringIota, c: StringIota) =>
              NbtCompound().tap: r =>
                a.getKeys.collect:
                  case k if k <= b.getString && k > c.getString => r(k) = a(k)
        ),
      INDEX_OF -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: AbstractNbtList[? <: NbtElement], b: NbtElement) => NbtInt.of(a.indexOf(b))
            case (a: NbtCompound, b: NbtElement) =>
              val list = NbtList()
              a.getKeys.foreach: k =>
                if a.get(k) == b then
                  list.add(NbtString.of(k))
              NbtIota(list)
      ),
      APPEND -> ((a: NbtIota, b: NbtIota) =>
        Seq[NbtIota]:
          (a.data, b.data) match
            case (a: AbstractNbtList[t], b) if b.isInstanceOf[t] =>
              a.copy().tap:
                case c: AbstractNbtList[t] =>
                  c.add(b.asInstanceOf[t])
      ),
      UNAPPEND -> ((a: NbtIota) =>
        a.data match
          case a: AbstractNbtList[?] =>
            val s = a.asScala
            Seq[NbtIota](NbtList().tap(_.addAll(s.init)), s.last)
          case c: NbtCompound =>
            val k = c.getKeys.asScala.toBuffer
            Seq[NbtIota](
              NbtCompound().tap: d =>
                k.init.foreach: k =>
                  d(k) = c(k),
              NbtString.of(k.last),
              c(k.last),
            )
      ),
      CONS -> ((a: NbtIota, b: Iota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (nbtList(Tagged(a: AbstractNbtList[t], given _)), b) =>
              b match
                case iotaLike[t](b) => a.copy.asInstanceOf[AbstractNbtList[t]].tap(_.add(0, b))
        ),
      UNCONS -> ((a: NbtIota) =>
        a.data match
          case a: AbstractNbtList[?] =>
            val s = a.asScala
            Seq[NbtIota](NbtList().tap(_.addAll(s.tail)), s.head)
          case c: NbtCompound =>
            val k = c.getKeys.asScala.toBuffer
            Seq[NbtIota](
              NbtCompound().tap: d =>
                k.tail.foreach: k =>
                  d(k) = c(k),
              NbtString.of(k.head),
              c(k.head),
            )
        ),
      REMOVE -> ((a: NbtIota, key: Iota) =>
        a match
          case nbtList(Tagged(l: AbstractNbtList[t], given _)) =>
            key match
              case iotaLike(i: Int) =>
                val c: l.type = l.copy.asInstanceOf
                c.remove(i)
                Seq(NbtIota(c))
              case _ => throw MishapInvalidIota.of(key, 0, "int")
        )
    )
  })

class SyspropConfig(prefix: String) extends Dynamic:
  def selectDynamic(k: String): String =
    System.getProperty(if prefix == "" then k else s"$prefix.$k", "")
  def updateDynamic(k: String, v: String): Unit =
    System.setProperty(if prefix == "" then k else s"$prefix.$k", v)

type Config = SyspropConfig {
  def accelerateEntities: String
}
def config = SyspropConfig("hexic").asInstanceOf[Config]

def eq[T: ClassTag, U: ClassTag] = summon[ClassTag[T]] == summon[ClassTag[U]]

object iotaLike:
  def unapply[T: ClassTag](iota: Iota): Option[T] =
    (iota, summon[ClassTag[T]]) match
      case (s: StringIota, _: ClassTag[String]) => Some(s.getString)
      case (NbtIota(s: NbtString), _: ClassTag[String]) => Some(s.asString)
      case (d: DoubleIota, _: ClassTag[Double]) => Some(d.getDouble)
      case (d: DoubleIota, _: ClassTag[Int]) =>
        val i: Int = d.getDouble.round.toInt
        if ((i - d.getDouble).abs > DoubleIota.TOLERANCE)
          None
        else
          Some(i)
      case _ => None

object nbtList:
  def unapply(l: NbtElement): Option[Tagged[AbstractNbtList, NbtElement]] =
    l match
      case c: NbtList => Some(Tagged(c))
      case c: NbtIntArray => Some(Tagged(c))
      case c: NbtByteArray => Some(Tagged(c))
      case c: NbtLongArray => Some(Tagged(c))
      case _ => None

given Conversion[Array[Byte], NbtByteArray] = NbtByteArray(_)
given Conversion[Array[Int], NbtIntArray] = NbtIntArray(_)
given Conversion[Array[Long], NbtLongArray] = NbtLongArray(_)
given Conversion[NbtByteArray, Array[Byte]] = _.getByteArray
given Conversion[NbtIntArray, Array[Int]] = _.getIntArray
given Conversion[NbtLongArray, Array[Long]] = _.getLongArray

trait Tagged[+F[_ <: U @uncheckedVariance], +U]:
  type T <: U: ClassTag
  val value: F[T]
object Tagged:
  def apply[F[_ <: R], R: ClassTag](v: F[R]): Tagged[F, R] =
    new Tagged:
      type T = R
      val value: F[R] = v
  def unapply[F[_ <: R], R](v: Tagged[F, R]): (F[v.T], ClassTag[v.T]) = (v.value, summon)

extension [T](l: util.AbstractList[T])
  def apply(n: Int): T = l.get(n)
  def update(n: Int, x: T): Unit = l.set(n, x)
extension (c: NbtCompound)
  def apply(k: String): NbtElement | Null = c.get(k)
  def update(k: String, v: NbtElement | Null): Unit = c.put(k, v)

given Conversion[Double, DoubleIota] = DoubleIota(_)
given Conversion[Int, DoubleIota] = DoubleIota(_)
given Conversion[DoubleIota, Double] = _.getDouble
extension (d: DoubleIota) def asIntOrThrow(idx: Int): Int =
  val v = d.getDouble
  if (v.round - v).abs > DoubleIota.TOLERANCE then
    throw MishapInvalidIota.of(d, idx, "int")
  v.round.intValue

extension (i: CastingImage)
  def withStack(m: Seq[Iota] => Seq[Iota]): CastingImage = i.copy(util.ArrayList(m(i.getStack.asScala.toSeq).asJavaCollection), i.getParenCount, i.getParenthesized, i.getEscapeNext, i.getOpsConsumed, i.getUserData)
extension (o: OperationResult)
  def withStack(m: Seq[Iota] => Seq[Iota]): OperationResult = o.copy(o.getNewImage.withStack(m), o.getSideEffects, o.getNewContinuation, o.getSound)

inline def arith(name: String, inline ops: (HexPattern, AnyRef)*) = ${ arithImpl('name, 'ops) }

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
  Bootstrap.SYSOUT.println(s"thread '${Thread.currentThread.getName}' panicked at '$reason'")
  Bootstrap.SYSOUT.flush()
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

case class NbtIota(val data: NbtElement) extends Iota(NbtIota, data):
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
given Conversion[String, MutableText] = Text.literal
object NbtIota extends IotaType[NbtIota]:
  def name: Text = ("NBT": MutableText).styled(_.withColor(color))
  def color: Int = Formatting.DARK_AQUA.getColorValue
  def deserialize(using NbtElement, ServerWorld): NbtIota = NbtIota(summon)
  def display(e: NbtElement): Text = Text.literal(StringNbtWriter()(e)).styled(_.withColor(color))
//object NbtIotaButOpaque extends IotaType[NbtIotaButOpaque]:
//  def name: Text = ("NBT": MutableText).styled(_.withColor(color))
//  def color: Int = Formatting.DARK_AQUA.getColorValue
//  def deserialize(using NbtElement, ServerWorld): NbtIota = NbtIota(summon)
//  def display(e: NbtElement): Text = Text.literal(StringNbtWriter()(e)).styled(_.withColor(color))
object TextIota extends IotaType[TextIota]:
  def color(): Int = Vec3Iota.TYPE.color()
  def deserialize(using NbtElement, ServerWorld): TextIota = TextIota(summon[NbtElement])
  def display(using NbtElement): Text =
    given ServerWorld = null
    deserialize.text
