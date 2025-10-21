//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction}
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapInvalidIota, MishapOthersName}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.HexUtils
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents
import com.chocohead.mm.api.ClassTinkerers
import com.ibm.icu.util.MeasureUnit
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.{LiteralArgumentBuilder, RequiredArgumentBuilder}
import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.{Codec, DynamicOps}
import com.samsthenerd.inline.api.data.ItemInlineData
import com.sun.nio.file.ExtendedOpenOption
import dev.kineticcat.hexportation.fabric.api.casting.iota.{ConduitIota, StorageViewIota}
import kotlin.text.Charsets
import miyucomics.hexical.features.dyes.DyeIota
import miyucomics.hexical.features.hopper
import miyucomics.hexical.features.hopper.targets.SidedInventoryEndpoint
import miyucomics.hexical.features.hopper.{HopperDestination, HopperEndpoint, HopperEndpointRegistry, HopperEndpointResolver, HopperSource}
import miyucomics.hexical.features.pigments.PigmentIota
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.transfer.v1.fluid.{FluidConstants, FluidVariant}
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.Bootstrap
import net.minecraft.command.argument.{EntityArgumentType, NbtElementArgumentType}
import net.minecraft.command.{CommandException, EntitySelector}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluid
import net.minecraft.inventory.SidedInventory
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.*
import net.minecraft.nbt.visitor.StringNbtWriter
import net.minecraft.registry.tag.TagKey
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.{HoverEvent, LiteralTextContent, MutableText, Style, Text, TextContent}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.Vec3d
import net.minecraft.util.{DyeColor, Formatting, Hand, Identifier, Rarity, Uuids}
import net.minecraft.world.World
import org.eu.net.pool.hexic
import org.objectweb.asm.{ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, InsnList}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.hexal.api.casting.iota.{GateIota, MoteIota}
import ram.talia.moreiotas.api.casting.iota.{EntityTypeIota, IotaTypeIota, ItemStackIota, ItemTypeIota, MatrixIota, StringIota}
import sun.misc.Unsafe

import java.io.{File, FileNotFoundException, IOException}
import java.lang.invoke.MethodHandles
import java.lang.reflect.{Constructor, Field, Member, Method}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{experimental, showAsInfix, tailrec, targetName, unused}
import scala.collection.convert.ImplicitConversions.*
import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.jdk.CollectionConverters.*
import scala.language.experimental.{macros, saferExceptions}
import scala.language.{dynamics, existentials, implicitConversions, postfixOps, reflectiveCalls}
import scala.reflect.{ClassTag, classTag}
import scala.tools.asm.tree.MethodNode
import scala.util.{NotGiven, TupledFunction, boundary}
import scala.util.chaining.given

given Logger = LoggerFactory.getLogger("hexic")

extension (i: Iota)
  def asIotaType[T <: Iota: ClassTag](idx: Int, expected: => Text): T = i match
    case i: T => i
    case _ => throw MishapInvalidIota(i, idx, expected)
  def asIotaType[T <: Iota: ClassTag: IotaType](idx: Int): T = i.asIotaType[T](idx, summon[IotaType[T]].typeName)
  def asValue[T: FromIota](idx: Int, expected: => Text): T = summon[FromIota[T]].convert(i).getOrElse(throw MishapInvalidIota(i, idx, expected))

extension (c: NbtCompound)
  def iota(using ServerWorld): Iota = IotaType.deserializeIota(c, summon)

given Conversion[(HexDir, String), HexPattern] = t => HexPattern.fromAngles(t._2, t._1)

case class Box[T](var value: T)

trait Gives[C[_]]:
  type T: C
  def value: T

given [C[_], T_](using C[T_]): Conversion[T_, Gives[C]] with
  override def apply(x: T_) : Gives[C] = new Gives[C]:
    type T = T_
    def value: T_ = x

trait Outcome[-T]:
  def apply(res: OperationResult, value: T): OperationResult
object Outcome:
  def apply(xs: Gives[Outcome]*): Gives[Outcome] = ???
  def apply[T: Outcome](xs: T*): OperationResult => OperationResult = res => res.->(xs*)
extension (op: OperationResult)
  def ->[T: Outcome](xs: T*): OperationResult =
    xs.foldLeft(op)(summon[Outcome[T]](_, _))

given Outcome[OperationResult => OperationResult] = (res, f) => f(res)
given [T: Outcome]: Outcome[Seq[T]] = (res, value) => res -> Outcome(value*)

//given Outcome[Seq[Iota]]:
//  override def ->:(res: OperationResult): OperationResult = ???
//given Outcome[Iota]:
//  override def ->:

extension [T] (r: Registry[T])
  def update(key: Identifier, value: T) =
    Registry.register(r, key, value)

case class :?[T, G](value: T)(using val proof: G)
object Proven:
  given wrap[T, G](using G): Conversion[T, T :? G] = x => :?(x)
  given unwrap[T, G]: Conversion[T :? G, T] = _.value
  given prove[T, G]: Conversion[(G ?=> T) :? G, T] = x => x.value(using x.proof)
  given unneeded[T, G]: Conversion[T, G ?=> T] with
    override def apply(x: T): G ?=> T = _ ?=> x
import Proven.given

object Patterns:
  def mkAction(body: (CastingEnvironment, ServerWorld) ?=> (CastingImage, SpellContinuation) => (CastingImage, SpellContinuation, EvalSound, Seq[OperatorSideEffect])): Action =
    (env: CastingEnvironment, image: CastingImage, cont: SpellContinuation) =>
      val ret: (CastingImage, SpellContinuation, EvalSound, Seq[OperatorSideEffect]) = body(using env, env.getWorld)(image, cont)
      OperationResult(newImage = ret._1, sideEffects = ret._4, newContinuation = ret._2, sound = ret._3)
  def mkConstAction(argc: Int, mediaCost: Long = 0)(body: (CastingEnvironment, ServerWorld) ?=> Seq[Iota] => Seq[Iota]): Action =
    new ConstMediaAction:
      import ConstMediaAction.DefaultImpls => d
      override def getArgc: Int = argc
      override def getMediaCost: Long = mediaCost
      override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
        body(using castingEnvironment, castingEnvironment.getWorld)(list.toSeq)
      override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
      override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  def mkLiteral(value: (CastingEnvironment, ServerWorld) ?=> Iota): Action =
    mkConstAction(0): (args: Seq[Iota]) =>
      args :+ value
  def register(id: Identifier, pattern: HexPattern)(body: Action): Unit =
    HexRegistries.ACTION(id) = ActionRegistryEntry(pattern, body)

inline def unsafe(using u: Unsafe) = u

extension (ctx: StringContext) def ifModLoaded(`then`: => Unit, `else`: => Unit = {}): Unit =
  if FabricLoader.getInstance.isModLoaded(ctx.parts(0)) then
    `then`
  else
    `else`

class Pointer[T](val address: Long) extends AnyVal:
  inline def cast[R]: Pointer[R] = Pointer(address)
  inline def alloc(newSize: Long) = Pointer(unsafe.reallocateMemory(address, newSize))
  inline def free(): Unit = unsafe.freeMemory(address)
  inline transparent def value: T =
    import scala.compiletime._
    summonFrom:
      case ev: (Pointer[t] =:= T) => ev(Pointer(unsafe.getAddress(address)))
      case ev: (Int =:= T) => ev(unsafe.getInt(address))
      case ev: (Long =:= T) => ev(unsafe.getLong(address))
      case ev: (Float =:= T) => ev(unsafe.getFloat(address))
      case ev: (Double =:= T) => ev(unsafe.getDouble(address))
      case ev: (Char =:= T) => ev(unsafe.getChar(address))
      case ev: (Short =:= T) => ev(unsafe.getShort(address))
      case ev: (Byte =:= T) => ev(unsafe.getByte(address))
      case _ => error("Cannot use non-primitive types with pointers")
  inline def value_=(value: T): Unit =
    import scala.compiletime._
    summonFrom:
      case ev: (T =:= Pointer[t]) => unsafe.putAddress(address, ev(value).address)
      case ev: (T =:= Int) => unsafe.putInt(address, ev(value))
      case ev: (T =:= Long) => unsafe.putLong(address, ev(value))
      case ev: (T =:= Float) => unsafe.putFloat(address, ev(value))
      case ev: (T =:= Double) => unsafe.putDouble(address, ev(value))
      case ev: (T =:= Char) => unsafe.putChar(address, ev(value))
      case ev: (T =:= Short) => unsafe.putShort(address, ev(value))
      case ev: (T =:= Byte) => unsafe.putByte(address, ev(value))
      case _ => error("Cannot use non-primitive types with pointers")
  inline def +(offset: Long): Pointer[T] = Pointer(address + offset * Pointer.sizeOf[T])
object Pointer:
  inline def alloc[T](count: Long): Pointer[T] = Pointer(unsafe.allocateMemory(size * sizeOf[T]))
  inline def size: Long = unsafe.addressSize
  inline def sizeOf[T]: Long =
    import scala.compiletime._
    inline erasedValue[T] match
      case ev: Pointer[t] => Pointer.size
      case ev: Int => 4
      case ev: Long => 8
      case ev: Float => 4
      case ev: Double => 8
      case ev: Char => 4
      case ev: Short => 2
      case ev: Byte => 1
      case _ => error("Cannot use non-primitive types with pointers")

def bullshit(bytes: Array[Byte]): Unit =
  MethodHandles.lookup.defineHiddenClass(bytes, true)
def bullshit(node: ClassNode): Unit =
  val writer = ClassWriter(0)
  node accept writer
  bullshit(writer toByteArray)

def runInstrs(instrs: InsnList) =
  val node = ClassNode()
  node.superName = classOf[Runnable].getName
  val method = tree.MethodNode(Member.PUBLIC, "run", "()V", null, Array.empty)
  node.methods.add(method)
  val writer = ClassWriter(0)
  node accept writer
  val id = "_runnable" + UUID.randomUUID().toString.replace("-", "")
  ClassTinkerers.define(id, writer toByteArray)
  classNamed(id).get.runtimeClass.newInstance.asInstanceOf[Runnable].run()

def init(): Unit =
  WarCrime.thoughtWorld = RegistryKey.of(RegistryKeys.WORLD, "thought")
  val funny = allocUninitializedInstance[String]
  println(funny)
  println("I will now print 24 integers.")
  val p = Pointer.alloc[Int](24)
  for i <- 0 until 24 do
    println(s"${i}: ${(p + i).value}")
  p.free()
  try
    System.getProperties.load(Files.newBufferedReader(Path.of("config/jvm.properties"), Charsets.UTF_8))
  catch
    case _: FileNotFoundException => ;
    case i: IOException =>
      summon[Logger].warn("Failed to read properties", i)
  HexRegistries.IOTA_TYPE("location") = LocationIota
  HexRegistries.IOTA_TYPE("text") = TextIota
  HexRegistries.IOTA_TYPE("nbt") = NbtIota
  HexRegistries.IOTA_TYPE("variant") = VariantIota
  HexRegistries.IOTA_TYPE("stack") = StackIota
  HexRegistries.IOTA_TYPE("map") = MapIota
  HexRegistries.IOTA_TYPE("jvm/class") = ClassIota
  HexRegistries.IOTA_TYPE("jvm/pointer") = PointerIota
  Registries.ITEM("echo") = EchoItem
  ifModLoaded"hexical${
    for
      HopperEndpointRegistry <- classNamed("miyucomics.hexical.features.hopper.HopperEndpointRegistry")
      ConduitIota <- classNamed("dev.kineticcat.hexportation.fabric.api.casting.iota.ConduitIota")
      registerHopperEndpoint <- classNamed("org.eu.net.pool.registerHopperEndpoint").asInstanceOf[Option[ClassTag[? <: (() => Unit)]]]
    do
      registerHopperEndpoint.runtimeClass.newInstance().asInstanceOf[() => Unit]()
  }"
  def mkNbtLiftAction[T: FromIota](lift: T => NbtElement, expected: Identifier) =
    Patterns.mkConstAction(1):
      case Seq(iotaLike[T](x)) => Seq(NbtIota(lift(x)))
      case Seq(x) => throw MishapInvalidIota.of(x, 0, expected.toString)
  def mkNbtLiftArrayAction[T: FromIota](lift: T => NbtElement, liftArray: Array[T] => NbtElement, expected: Identifier)(using FromIota[Array[T]]) =
    Patterns.mkConstAction(1):
      case Seq(iotaLike[T](x)) => Seq(NbtIota(lift(x)))
      case Seq(iotaLike[Array[T]](x)) => Seq(NbtIota(liftArray(x)))
      case Seq(x) => throw MishapInvalidIota.of(x, 0, expected.toString)
  Patterns.register("nbt/lift1", (HexDir.NORTH_WEST, "edwaqw")):
    mkNbtLiftArrayAction[Byte](NbtByte.of, (b => NbtByteArray(b)): Array[Byte] => NbtElement, "byte")
  Patterns.register("nbt/lift2", (HexDir.NORTH_WEST, "edwaqww")):
    mkNbtLiftAction[Short](NbtShort.of, "short")
  Patterns.register("nbt/lift4", (HexDir.NORTH_WEST, "edwaqwww")):
    mkNbtLiftArrayAction[Int](NbtInt.of, (b: Array[Int]) => NbtIntArray(b), "int")
  Patterns.register("nbt/lift8", (HexDir.NORTH_WEST, "edwaqwwww")):
    mkNbtLiftArrayAction[Long](NbtLong.of, (b: Array[Long]) => NbtLongArray(b), "long")
  Patterns.register("nbt/liftf", (HexDir.NORTH_WEST, "edwaqwaa")):
    mkNbtLiftAction[Float](NbtFloat.of, "float")
  Patterns.register("nbt/liftd", (HexDir.NORTH_WEST, "edwaqwwww")):
    mkNbtLiftAction[Double](NbtDouble.of, "double")
  Patterns.register("nbt/literal/collection", (HexDir.EAST, "qqddqdewqaeaaee")):
    Patterns.mkLiteral(NbtIota(NbtCompound()))
  Patterns.register("nbt/literal/list", (HexDir.EAST, "eedwaqq")):
    Patterns.mkLiteral(NbtIota(NbtList()))
  Patterns.register("nbt/literal/array1", (HexDir.EAST, "eedwaqqe")):
    Patterns.mkLiteral(NbtIota(NbtByteArray(Array[Byte]())))
  Patterns.register("nbt/literal/array2", (HexDir.EAST, "eedwaqqew")):
    Patterns.mkLiteral(NbtIota(NbtIntArray(Array[Int]())))
  Patterns.register("nbt/literal/array4", (HexDir.EAST, "eedwaqqewww")):
    Patterns.mkLiteral(NbtIota(NbtLongArray(Array[Long]())))
  Patterns.register("empty_map", (HexDir.EAST, "dqdwdqd")):
    Patterns.mkLiteral(MapIota())
  Patterns.register("nbt/serialize", nw"edwaq"):
    Patterns.mkConstAction(1):
      case Seq(x: Iota) => Seq(IotaType.serialize(x))
  Patterns.register("nbt/deserialize", (HexDir.NORTH_WEST, "edwaqa")):
    Patterns.mkConstAction(1):
      case Seq(data: NbtIota) =>
        val env = summon[CastingEnvironment]
        given ServerWorld = env.getWorld
        val iota = data.data.asInstanceOf[NbtCompound].iota
        iota match
          case p: EntityIota => p.getEntity match
            case t: PlayerEntity if t != env.getCastingEntity =>
              throw MishapOthersName(t)
            case _ =>
          case _ =>
        Seq(iota)
      case Seq(x) =>
        throw MishapInvalidIota(x, 0, Text.literal("an ").append(Text.literal("NBT compound").styled(_.withColor(NbtIota.color))))
  Registry.register(HexRegistries.ARITHMETIC, "nbt": Identifier, {
    import Arithmetic.*
    given Conversion[NbtIota, NbtElement] = _.data
    given Conversion[NbtElement, NbtIota] = NbtIota(_)
    arith("nbt",
      ADD -> ((a: NbtIota, b: Iota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: NbtDouble, iotaLike[Double](b)) => NbtDouble.of(a.doubleValue + b)
            case (a: NbtFloat, iotaLike[Float](b)) => NbtFloat.of(a.floatValue + b)
            case (a: NbtLong, iotaLike[Long](b)) => NbtLong.of(a.longValue + b)
            case (a: NbtInt, iotaLike[Int](b)) => NbtInt.of(a.intValue + b)
            case (a: NbtShort, iotaLike[Short](b)) => NbtShort.of((a.shortValue + b).toShort)
            case (a: NbtByte, iotaLike[Byte](b)) => NbtByte.of((a.byteValue + b).toByte)
            case (a: NbtByteArray, iotaLike[Seq[Byte]](b)) => NbtByteArray(a.getByteArray ++ b)
            case (a: NbtIntArray, iotaLike[Seq[Int]](b)) => NbtIntArray(a.getIntArray ++ b)
            case (a: NbtLongArray, iotaLike[Seq[Long]](b)) => NbtLongArray(a.getLongArray ++ b)
            case (a: NbtList, iotaLike[Seq[NbtElement]](b)) => NbtList().tap: l =>
              l.addAll(a)
              l.addAll(b)
            case (a: NbtCompound, iotaLike[NbtCompound](b)) => NbtCompound().tap: c =>
              c.copyFrom(a)
              c.copyFrom(b)
      ),
      SUB -> ((a: NbtIota, b: Iota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: NbtDouble, iotaLike[Double](b)) => NbtDouble.of(a.doubleValue - b)
            case (a: NbtFloat, iotaLike[Float](b)) => NbtFloat.of(a.floatValue - b)
            case (a: NbtLong, iotaLike[Long](b)) => NbtLong.of(a.longValue - b)
            case (a: NbtInt, iotaLike[Int](b)) => NbtInt.of(a.intValue - b)
            case (a: NbtShort, iotaLike[Short](b)) => NbtShort.of((a.shortValue - b).toShort)
            case (a: NbtByte, iotaLike[Byte](b)) => NbtByte.of((a.byteValue - b).toByte)
      ),
      MUL -> ((a: NbtIota, b: Iota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: NbtDouble, iotaLike[Double](b)) => NbtDouble.of(a.doubleValue * b)
            case (a: NbtFloat, iotaLike[Float](b)) => NbtFloat.of(a.floatValue * b)
            case (a: NbtLong, iotaLike[Long](b)) => NbtLong.of(a.longValue * b)
            case (a: NbtInt, iotaLike[Int](b)) => NbtInt.of(a.intValue * b)
            case (a: NbtShort, iotaLike[Short](b)) => NbtShort.of((a.shortValue * b).toShort)
            case (a: NbtByte, iotaLike[Byte](b)) => NbtByte.of((a.byteValue * b).toByte)
      ),
      DIV -> ((a: NbtIota, b: Iota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: NbtDouble, iotaLike[Double](b)) => NbtDouble.of(a.doubleValue / b)
            case (a: NbtFloat, iotaLike[Float](b)) => NbtFloat.of(a.floatValue / b)
            case (a: NbtLong, iotaLike[Long](b)) => NbtLong.of(a.longValue / b)
            case (a: NbtInt, iotaLike[Int](b)) => NbtInt.of(a.intValue / b)
            case (a: NbtShort, iotaLike[Short](b)) => NbtShort.of((a.shortValue / b).toShort)
            case (a: NbtByte, iotaLike[Byte](b)) => NbtByte.of((a.byteValue / b).toByte)
      ),
      INDEX -> ((a: NbtIota, b: DoubleIota | StringIota) =>
        Seq[NbtIota]:
          (a.data, b) match
            case (a: AbstractNbtList[? <: NbtElement], b: DoubleIota) => a.get(b.asIntOrThrow(0))
            case (a: NbtCompound, iotaLike[String](b)) => a.get(b)
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
              case iotaLike[Int](i) =>
                val c: l.type = l.copy.asInstanceOf
                c.remove(i)
                Seq(NbtIota(c))
              case _ => throw MishapInvalidIota.of(key, 0, "int")
        ),
      REPLACE -> ((t: NbtIota, k: Iota, v: NbtIota) =>
        Seq:
          NbtIota:
            t.data.copy.tap:
              case c: NbtCompound =>
                c(k.asValue[String](1, StringIota.TYPE.typeName)) = v.data
              case l: AbstractNbtList[t] =>
                given ClassTag[t] = elementTag(l)
                l(k.asValue[Int](1, Text.translatable("hexcasting.iota.int"))) = ???
        ),
    )
  })
  Registry.register(HexRegistries.ARITHMETIC, "text": Identifier, {
    import Arithmetic.*
    arith("text",
      ADD -> ((a: TextIota, b: TextIota) => TextIota(a.text + b.text)),
      APPEND -> ((a: TextIota, b: TextIota) => TextIota(a.text + b.text)),
      CONS -> ((a: TextIota, b: TextIota) => TextIota(b.text + a.text)),
      INDEX -> ((a: TextIota, idx: DoubleIota) =>
        val ord = a.text.asOrderedText
        val n = idx `asIntOrThrow` 0
        boundary:
          ord.accept: (i, s, c) =>
            if i == n then
              boundary break TextIota(Text.literal(c.toChar.toString) setStyle s)
            else
              true
          NullIota()
      ),
      SLICE -> ((a: TextIota, min: DoubleIota, max: DoubleIota) =>
        val ord = a.text.asOrderedText
        val range = min.asIntOrThrow(1) to max.asIntOrThrow(0)
        val out = mutable.ArrayBuffer[(Style, Char)]()
        boundary:
          ord.accept: (i, s, c) =>
            if range contains i then
              out.add(s -> c.toChar)
            else
              true
          NullIota()
      ),
    )
  })
  Registry.register(HexRegistries.ARITHMETIC, "maps": Identifier, {
    import Arithmetic.*
    arith("map",
      ADD -> ((a: MapIota, b: MapIota) => a ++ b),
      SUB -> ((a: MapIota, b: MapIota) => a -- b),
      ABS -> ((a: MapIota) => DoubleIota(a.map.size)),
      INDEX -> ((a: MapIota, k: Iota) => a(k)),
      UNAPPEND -> ((a: MapIota) => a.last.pipe(p => Seq(p._1, p._2)).prepended(a.init)),
      INDEX_OF -> ((a: MapIota, v: Iota) =>
        val c = IotaType.serialize(v)
        a.update(_.filter(_._2 == c))),
      REMOVE -> ((a: MapIota, k: Iota) => a - k),
      REPLACE -> ((a: MapIota, k: Iota, v: Iota) => a + (k -> v)),
      UNCONS -> ((a: MapIota) => a.head.pipe(p => Seq(p._1, p._2)).prepended(a.tail)),
      AND -> ((a: MapIota, b: MapIota) => a & b),
      OR -> ((a: MapIota, b: MapIota) => b ++ a),
      XOR -> ((a: MapIota, b: MapIota) => a ^ b),
      GREATER -> ((a: MapIota, b: MapIota) => a.map.containsAll(b.map) && a.map != b.map),
      LESS -> ((a: MapIota, b: MapIota) => b.map.containsAll(a.map) && a.map != b.map),
      GREATER_EQ -> ((a: MapIota, b: MapIota) => a.map.containsAll(b.map)),
      LESS_EQ -> ((a: MapIota, b: MapIota) => b.map.containsAll(a.map)),
    )
  })
  HexRegistries.ARITHMETIC("null_abs") = arith("null_abs", Arithmetic.ABS -> ((_: NullIota) => DoubleIota(0)))
  CommandRegistrationCallback.EVENT.register: (d, r, e) =>
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("gimmeiota")
      .requires(_.hasPermissionLevel(2))
      .`then`(RequiredArgumentBuilder.argument("type", WarCrime.reat(r, HexRegistries.IOTA_TYPE))
        .`then`(RequiredArgumentBuilder.argument[ServerCommandSource, NbtElement]("data", NbtElementArgumentType.nbtElement())
          .executes(c =>
            val t = WarCrime.gre[IotaType[?]](c, "type", HexRegistries.IOTA_TYPE)
            val d = NbtElementArgumentType.getNbtElement(c, "data")
            val p = c.getSource.getPlayer
            if p == null then
              throw CommandException("Command must be run by a player")
            try
              t.value.deserialize(d, c.getSource.getWorld) match
                case null => throw CommandException("Iota did not accept the given data")
                case r: Iota =>
                  p.gimmeIota(r)
                  c.getSource.sendFeedback(() => Text.translatable("Pushed %s to stack", try r.display catch case x: (Exception | Error) => x.getMessage), true)
                  1
                case x => throw CommandException(s"${x} is not an iota")
            catch
              case x: IllegalArgumentException => throw CommandException(x.getMessage)
          ).build()
        ).build()
      ).`then`(
        RequiredArgumentBuilder.argument[ServerCommandSource, EntitySelector]("entity", EntityArgumentType.entity())
          .executes(c =>
            val p = c.getSource.getPlayer
            if p == null then
              throw CommandException("Command must be run by a player")
            val r = EntityIota(EntityArgumentType.getEntity(c, "entity")).tap(p.gimmeIota)
            c.getSource.sendFeedback(() => Text.translatable("Pushed %s to stack", r.display), true)
            1
          ).build()
      ).build())
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("property").pipe: c =>
      c.`then`(LiteralArgumentBuilder.literal("get")
        .`then`(RequiredArgumentBuilder.argument("property", StringArgumentType.string())
          .executes((c: CommandContext[ServerCommandSource]) =>
            val prop = StringArgumentType.getString(c, "property")
            System.getProperty(prop) match
              case null => throw CommandException(t"Property ${prop} is not set")
              case s =>
                c.getSource.sendFeedback(() => t"Property ${prop} is set to ${s}", false)
                1
          )
          .build())
        .build()
      )
      c.`then`(LiteralArgumentBuilder.literal("set")
        .`then`(RequiredArgumentBuilder.argument("property", StringArgumentType.string())
          .`then`(RequiredArgumentBuilder.argument("value", StringArgumentType.string())
            .executes((c: CommandContext[ServerCommandSource]) =>
              val prop = StringArgumentType.getString(c, "property")
              val value = StringArgumentType.getString(c, "value")
              System.setProperty(prop, value)
              c.getSource.sendFeedback(() => t"Changed the value of property ${prop}", true)
              1
            )
            .build())
          .build())
        .build()
      )
      c.`then`(LiteralArgumentBuilder.literal("remove")
        .`then`(RequiredArgumentBuilder.argument("property", StringArgumentType.string())
          .build())
        .build()
      )
      c.`then`(LiteralArgumentBuilder.literal[ServerCommandSource]("reload")
        .executes(c =>
          val out = Files.newBufferedReader(Path.of("config/jvm.properties"), Charsets.UTF_8)
          try
            System.getProperties.load(out)
          catch
            case _: FileNotFoundException => throw CommandException("Properties file does not exist")
          finally
            out.close()
          c.getSource.sendFeedback(() => "Reloaded properties from file", true)
          1
        ).build()
      )
      c.`then`(LiteralArgumentBuilder.literal[ServerCommandSource]("flush")
        .executes(c =>
          val out = Files.newBufferedWriter(Path.of("config/jvm.properties"), Charsets.UTF_8)
          try
            System.getProperties.store(out, null)
          finally
            out.close()
          c.getSource.sendFeedback(() => "Saved properties to file", true)
          1
        ).build()
      )
      c.build())
  extension (ctx: StringContext) def jvm() = e"aqqqqqdeeweweweweeaaedeqedee${ctx.s()}"
  Patterns.register("jvm/class_of_iota", jvm"aeeee"):
    Patterns.mkConstAction(1):
      case Seq(x: Iota) => Seq(ClassIota()(using ClassTag(x.getClass)))
  Patterns.register("jvm/class_of_payload", jvm"dqqqq"):
    Patterns.mkConstAction(1):
      case Seq(x: Iota) =>
        val f = classOf[Iota].getDeclaredField("payload")
        f.setAccessible(true)
        val payload = f.get(x)
        Seq(ClassIota()(using ClassTag(payload.getClass)))
  Patterns.register("jvm/newinstance_unboxed", jvm"aeeeedw"):
    Patterns.mkConstAction(1):
      case Seq(x@ClassIota()) =>
        Seq(allocUninitializedInstance[x.T](using x.tag).asInstanceOf[Iota])
  Patterns.register("jvm/newinstance_boxed", jvm"dqqqqaw"):
    Patterns.mkConstAction(1):
      case Seq(x@ClassIota()) =>
        Seq(ObjectIota(allocUninitializedInstance[x.T](using x.tag).asInstanceOf[AnyRef]))
  Patterns.register("malloc", jvm"wwaa"):
    Patterns.mkConstAction(1):
      case Seq(x: DoubleIota) =>
        Seq(PointerIota(Pointer.alloc[Byte](x.getDouble.round)))
  Patterns.register("free", jvm"wwdd"):
    Patterns.mkConstAction(1):
      case Seq(PointerIota(p)) =>
        p.free()
        Seq()

type subtypes[T, R <: T] = T

val fadedScrolls: TagKey[ActionRegistryEntry] = TagKey.of(HexRegistries.ACTION, "faded_scrolls")

extension (ctx: StringContext)
  def ne(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_EAST)
  def e(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.EAST)
  def se(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_EAST)
  def nw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_WEST)
  def w(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.WEST)
  def sw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_WEST)

extension (text: Text)
  def +(other: Text): MutableText = Text.literal("").append(text).append(other)
  def uncons: Option[(Text, Text)] =
    boundary:
      if !text.getContent.empty then
        val contentText = text.copy
        val siblingsText = Text.literal("")
        siblingsText.setStyle(text.getStyle)
        siblingsText.getSiblings ++= contentText.getSiblings
        contentText.getSiblings.clear()
        boundary.break(Some((contentText, siblingsText)))
      for sibling <- text.getSiblings do
        for p <- sibling.uncons do
          boundary.break(Some(p both(_.copy.styled(_.withParent(text.getStyle)))))
      None
  def unsnoc: Option[(Text, Text)] =
    boundary:
      for sibling <- text.getSiblings do
        for p <- sibling.unsnoc do
          boundary.break(Some(p both(_.copy.styled(_.withParent(text.getStyle)))))
      if !text.getContent.empty then
        boundary.break(Some((text.copyContentOnly, Text.literal("")) both(_.setStyle(text.getStyle))))
      None
extension (content: TextContent)
  def empty = content match
    case l: LiteralTextContent => l.string == ""
    case _ => false

object EchoItem extends Item(FabricItemSettings().rarity(Rarity.RARE))

case class Nonce(id: UUID):
  def this() = this(UUID.randomUUID())
object Nonce:
  given Codec[Nonce] = Uuids.CODEC.xmap(Nonce(_), _.id)
  given Conversion[Nonce, Text] = _.id.toString.takeRight(6).pipe(Text.literal).styled(_.withFont(Identifier("minecraft:illageralt")))

extension (p: ServerPlayerEntity) def gimmeIota(iota: Iota): Unit =
  val m = p.getComponent(HexCardinalComponents.STAFFCAST_IMAGE)
  m.setImage(m.getVM(Hand.MAIN_HAND).getImage.withStack(_ ++ Vector(iota)))

object elementTag:
  def apply[T <: NbtElement](l: AbstractNbtList[T]): ClassTag[T] =
    l match
      case _: NbtByteArray => summon[ClassTag[NbtByte]]
      case _: NbtIntArray => summon[ClassTag[NbtInt]]
      case _: NbtLongArray => summon[ClassTag[NbtLong]]
      case _: NbtList => summon[ClassTag[NbtElement]]
  def unapply[T <: NbtElement](l: AbstractNbtList[T]): Some[ClassTag[T]] = Some(elementTag(l))

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
  def unapply[T: FromIota](iota: Iota): Option[T] = summon[FromIota[T]].convert(iota)

object itsGiving:
  inline transparent def unapply[T](x: Any): Option[(x.type, T)] =
    summonFrom {
      case y: T => Some((x, y))
      case _ => None
    }

//noinspection UnstableApiUsage
object MediaVariant extends TransferVariant[MediaVariant.type]:
  def getNbt = NbtCompound()
  def getObject: MediaVariant.type = MediaVariant
  def isBlank: Boolean = false
  def toNbt = NbtCompound()
  def toPacket(buf: net.minecraft.network.PacketByteBuf): Unit = ()

trait FromIota[T]:
  def convert(iota: Iota): Option[T]
object FromIota:
  def lift[T](f: PartialFunction[Iota, T]): FromIota[T] = (iota: Iota) => f.lift(iota)
  def liftFlat[T](f: PartialFunction[Iota, Option[T]]): FromIota[T] = (iota: Iota) => f.lift(iota).flatten
given FromIota[Iota] = Some(_)
given FromIota[String] = FromIota.lift:
  case s: StringIota => s.getString
  case NbtIota(s: NbtString) => s.asString
given FromIota[Boolean] = FromIota.lift:
  case b: BooleanIota => b.getBool
  case NbtIota(n: AbstractNbtNumber) => n.longValue != 0
given [T: ClassTag](using elems: FromIota[T]): FromIota[Seq[T]] = FromIota.liftFlat:
  case l: ListIota =>
    boundary:
      val b = mutable.Seq.empty[T]
      l.getList.map(elems.convert).collect:
        case Some(p) => b.add(p)
        case None => boundary.break(None)
      Some(b.toSeq)
given arrayByteFromIota: FromIota[Array[Byte]] = FromIota.lift:
  case NbtIota(t: NbtByteArray) => t.getByteArray
given arrayIntFromIota: FromIota[Array[Int]] = FromIota.lift:
  case NbtIota(t: NbtIntArray) => t.getIntArray
given arrayLongFromIota: FromIota[Array[Long]] = FromIota.lift:
  case NbtIota(t: NbtLongArray) => t.getLongArray
given [T <: NbtElement: ClassTag]: FromIota[T] = FromIota.lift:
  case NbtIota(data: T) => data
given FromIota[Double] = FromIota.lift:
  case d: DoubleIota => d.getDouble
given FromIota[Float] = FromIota.lift:
  case d: DoubleIota if (d.getDouble.round.toFloat - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toFloat
  case NbtIota(n: NbtFloat) => n.floatValue
given FromIota[Byte] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Byte.MaxValue && d.getDouble > Byte.MinValue && (d.getDouble.round.toByte - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toByte
  case NbtIota(n: AbstractNbtNumber) => n.byteValue
given FromIota[Short] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Short.MaxValue && d.getDouble > Short.MinValue && (d.getDouble.round.toShort - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toShort
  case NbtIota(n: AbstractNbtNumber) => n.shortValue
given FromIota[Int] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Int.MaxValue && d.getDouble > Int.MinValue && (d.getDouble.round.toInt - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toInt
  case NbtIota(n: AbstractNbtNumber) => n.intValue
given FromIota[Long] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Long.MaxValue && d.getDouble > Long.MinValue && (d.getDouble.round - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round
  case NbtIota(n: AbstractNbtNumber) => n.longValue

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

case class MishapWrongDimensionKiddo(val dim1: RegistryKey[World], val dim2: RegistryKey[World]) extends Mishap:
  override def accentColor(using CastingEnvironment, Mishap.Context): FrozenPigment = dyeColor(DyeColor.MAGENTA)
  override def errorMessage(using CastingEnvironment, Mishap.Context): Text =
    val env = summon[CastingEnvironment]
    val color = env.getPigment.getColorProvider.getColor(0, Vec3d.ZERO)
    val mind = t"Mind".styled(_ `withColor` color)
    t"My ${mind} cannot think in ${dim1.getValue `toTranslationKey` "dimension"} and ${dim2.getValue `toTranslationKey` "dimension"} at once"
  override def execute(using CastingEnvironment, Mishap.Context, util.List[Iota]): Unit = ???

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

case class StackIota[T](stack: VariantIota[T], count: BigInt) extends Iota(StackIota, stack):
  export stack.given ClassTag[T]
  override def isTruthy: Boolean = ???
  override def toleratesOther(that: Iota): Boolean = that match
    case s: StackIota[?] => stack == s.stack && count == s.count
    case _ => false
  override def serialize: NbtElement = stack.serialize tap (_.asInstanceOf[NbtCompound].putByteArray("n", count.toByteArray))
object StackIota extends IotaType[StackIota[?]]:
  def color: Int = 0xa34646
  def deserialize(using NbtElement, ServerWorld): StackIota[?] =
    val c: NbtCompound = HexUtils.downcast(summon, NbtCompound.TYPE)
    StackIota(VariantIota.deserialize, c.getByteArray("n") pipe (BigInt(_)))
  def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    VariantIota.parseVariant(c).fold(NullIota.DISPLAY)(t => BigInt(c.getByteArray("n")) pipe t._1.display)
inline def repeat[T](inline value: T, inline cond: T => Boolean)(inline body: T => T): T =
  var current = value
  while (cond(current)) current = body(current)
  current

def toExp[T](value: T)(using num: Integral[T])(trigger: T = num.fromInt(1000000), max: T = num.fromInt(1000)): (T, Option[Int]) =
  import num.given
  if value >= trigger then
    // someone needs to stop you
    var d = 0
    val r =
      repeat(value, _ >= max): n =>
        d += 1
        n / num.fromInt(10)
    (r, Some(d))
  else
    (value, None)

def x10[T: Numeric](power: T) = "x10" + (power.toString: String).map((c: Char) => "⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '0'))

//noinspection UnstableApiUsage
trait MediaContainer:
  def -=(using Transaction)(amount: Long): Boolean
  def +=(using Transaction)(amount: Long): Boolean
  def current(using Transaction): Long
  def max(using Transaction): Long
trait MediaContainerProvider:
  @targetName("hexic$MediaContainerProvider$Context")
  type Context: ClassTag;
  @targetName("hexic$MediaContainerProvider$getMediaContainer")
  def getMediaContainer(c: Context): Option[MediaContainer]

given Unsafe =
  val klass = classNamed("sun.misc.Unsafe").get.runtimeClass
  val field = klass.getDeclaredField("theUnsafe")
  field.setAccessible(true)
  field.get(null).asInstanceOf[Unsafe]

def allocUninitializedInstance[T: ClassTag](using u: Unsafe) = u.allocateInstance(summon[ClassTag[T]].runtimeClass).asInstanceOf[T]

private def normalize(obj: Any)(using u: Unsafe): Long =
  if u.arrayIndexScale(classOf[Array[Object]]) == 4 then
    u.getInt(obj, 4L).toLong & 0xFFFFFFFFL
  else
    u.getLong(obj, 8L)

//noinspection UnstableApiUsage
inline def transaction[R](body: Transaction ?=> R): R =
  given tx: Transaction = summonFrom:
    case outer: Transaction => Transaction.openNested(outer)
    case _ if !Transaction.isOpen => Transaction.openOuter()
  try
    body
  finally
    tx.close()

def test(): Unit =
  var t: Transaction = null
  transaction:
    t = summon
  println(t)

def expNotation[T](n: T)(using num: Integral[T]) =
  toExp(n)() match
    case (v, Some(d)) => s"${v}${x10(d)}"
    case (v, None) => v.toString

given Codec[Text] = Codecs.TEXT
given DynamicOps[NbtElement] = NbtOps.INSTANCE

given IotaType[DoubleIota] = DoubleIota.TYPE
given IotaType[StringIota] = StringIota.TYPE
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

case class ClassIota[_T: ClassTag]() extends Iota(ClassIota, summon[ClassTag[_T]]):
  type T = _T
  def tag: ClassTag[T] = summon[ClassTag[T]]
  def runtimeClass: Class[T] = tag.runtimeClass.asInstanceOf
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtString.of(runtimeClass.getName)
object ClassIota extends IotaType[ClassIota[?]]:
  override def color: Int = Formatting.GOLD.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): ClassIota[?] | Null = HexUtils.downcast(element, NbtString.TYPE).asString match
    case "void" => ClassIota()(using ClassTag(java.lang.Void.TYPE))
    case "byte" => ClassIota()(using ClassTag(java.lang.Byte.TYPE))
    case "short" => ClassIota()(using ClassTag(java.lang.Short.TYPE))
    case "int" => ClassIota()(using ClassTag(java.lang.Integer.TYPE))
    case "long" => ClassIota()(using ClassTag(java.lang.Long.TYPE))
    case "boolean" => ClassIota()(using ClassTag(java.lang.Boolean.TYPE))
    case "float" => ClassIota()(using ClassTag(java.lang.Float.TYPE))
    case "double" => ClassIota()(using ClassTag(java.lang.Double.TYPE))
    case "char" => ClassIota()(using ClassTag(java.lang.Character.TYPE))
    case s => classNamed(s).fold(null)(ClassIota()(using _))
  override def display(element: NbtElement): Text =
    val klass = deserialize(element, null).runtimeClass
    (klass.getSimpleName: MutableText).styled(_.withColor(if klass.isPrimitive then Formatting.RED else Formatting.GOLD))

given Conversion[String, NbtString] = NbtString.of
given Conversion[NbtString, String] = _.asString

case class FieldIota(field: Field | Method | Constructor[?]) extends Iota(FieldIota, field):
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtCompound().tap: c =>
    c("c") = field.getDeclaringClass.getName
    field match
      case f: Field =>
        c("f") = NbtByte.of(0: Byte)
        c("n") = field.getName
      case m: Method =>
        c("f") = NbtByte.of(1: Byte)
        c("n") = field.getName
      case k: Constructor[?] =>
        c("f") = NbtByte.of(2: Byte)
        c("k") = NbtList().tap: p =>
          for t <- k.getParameterTypes do
            p.add(t.getName)
object FieldIota extends IotaType[FieldIota]:
  override def color: Int = Formatting.YELLOW.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): FieldIota | Null =
    boundary:
      val c = HexUtils.downcast(element, NbtCompound.TYPE)
      val klass = classNamed(c("c").downcast[NbtString]).getOrElse(boundary.break(null)).runtimeClass
      lazy val name = c("n").downcast[NbtString]
      try
        FieldIota:
          c("f").downcast[NbtByte].byteValue() match
            case 0 => klass.getDeclaredField(name)
            case 1 => klass.getDeclaredMethod(name)
            case 2 => klass.getDeclaredConstructor(c("k").downcast[NbtList].map(m => classNamed(m.downcast[NbtString]).getOrElse(boundary.break(null)).runtimeClass).toSeq*)
            case _ => boundary.break(null)
      catch
        case _: (NoSuchFieldException | NoSuchMethodException) => null
  override def display(element: NbtElement): Text = t"Pointer: 0x${f"${HexUtils.downcast(element, NbtLong.TYPE).longValue}%x"}".styled(_.withColor(color))

extension (e: NbtElement)
  def downcast[T <: NbtElement: NbtType] = HexUtils.downcast(e, summon[NbtType[T]])

given NbtType[NbtString] = NbtString.TYPE
given NbtType[NbtByte] = NbtByte.TYPE
given NbtType[NbtShort] = NbtShort.TYPE
given NbtType[NbtInt] = NbtInt.TYPE
given NbtType[NbtLong] = NbtLong.TYPE
given NbtType[NbtFloat] = NbtFloat.TYPE
given NbtType[NbtDouble] = NbtDouble.TYPE
given NbtType[NbtByteArray] = NbtByteArray.TYPE
given NbtType[NbtIntArray] = NbtIntArray.TYPE
given NbtType[NbtLongArray] = NbtLongArray.TYPE
given NbtType[NbtList] = NbtList.TYPE
given NbtType[NbtCompound] = NbtCompound.TYPE
given NbtType[NbtEnd] = NbtEnd.TYPE

case class ObjectIota[T](obj: AnyRef) extends Iota(ObjectIota, obj):
  override def isTruthy: Boolean = !obj.isInstanceOf[Unit]
  override def toleratesOther(iota: Iota): Boolean = false
  override def serialize(): NbtElement = NbtCompound()
  override def display(): Text = obj.toString
object ObjectIota extends IotaType[ObjectIota[?]]:
  override def color: Int = Formatting.RED.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): ObjectIota[?] = null
  override def display(element: NbtElement): Text = t"Object"

case class PointerIota[T](pointer: Pointer[T]) extends Iota(PointerIota, pointer):
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtLong.of(pointer.address)
object PointerIota extends IotaType[PointerIota[?]]:
  override def color: Int = Formatting.RED.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): PointerIota[?] = PointerIota(Pointer(HexUtils.downcast(element, NbtLong.TYPE).longValue))
  override def display(element: NbtElement): Text = t"Pointer: 0x${f"${HexUtils.downcast(element, NbtLong.TYPE).longValue}%x"}".styled(_.withColor(color))

case class NbtIota(data: NbtElement) extends Iota(NbtIota, data):
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
//noinspection UnstableApiUsage
case class VariantIota[T: ClassTag](data: TransferVariant[T], key: RegistryKey[VariantIota.Reader]) extends Iota(VariantIota, data):
  override def isTruthy: Boolean = true
  override def toleratesOther(that: Iota): Boolean =
    that match
      case v: VariantIota[T] => key == v.key && data == v.data
      case _ => false
  override def serialize: NbtElement =
    data.toNbt tap(_.putString("type", key.getValue.toString))
//noinspection UnstableApiUsage
object VariantIota extends IotaType[VariantIota[?]], Registrar[VariantIota.Reader]("transfer_variants"):
  type Reader = NbtCompound => Option[VariantIota.TaggedVariant]
  trait TaggedVariant:
    type T: ClassTag
    def variant: TransferVariant[T]
    def display: Text
    def display(count: BigInt): Text =
      t"${display}x${expNotation(count)}"
  def color: Int = 0x720a0a
  private[hexic] def parseVariant(c: NbtCompound): Option[(TaggedVariant, RegistryKey[Reader])] =
    Identifier.tryParse(c.getString("type")) match
      case null => None
      case i => Option.fromNullable(registry.get(i)).flatMap(_(c)).map((_, RegistryKey.of(VariantIota, i)))
  end parseVariant
  def deserialize(using NbtElement, ServerWorld): VariantIota[?] | Null =
    summon[NbtElement] match
      case c: NbtCompound =>
        parseVariant(c) match
          case Some((t, k)) =>
            import t.given
            VariantIota(t.variant, k)
          case None => null
      case _ => null
  end deserialize
  override def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    parseVariant(c).fold(NullIota.DISPLAY)(_._1.display)
  end display
  registry(Identifier("item")) = c =>
    val s = ItemVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Item
        def variant: TransferVariant[Item] = s
        def display: Text = t"${s.getItem.getName(s.toStack)}: ${ItemInlineData(s.toStack).asText(true)}"
          .styled(_.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(s.toStack))))
  registry(Identifier("fluid")) = c =>
    val s = FluidVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Fluid
        def variant: TransferVariant[Fluid] = s
        def display: MutableText = t"${s.getFluid.getDefaultState.getBlockState.getBlock.getName}: ${ItemInlineData.make(s.getFluid.getBucketItem.getDefaultStack)}"
        override def display(count: BigInt): Text =
          val buckets = count / FluidConstants.BUCKET
          // small quantities of liquid value using millibuckets
          if buckets < 100 then
            display.append("x").append(f"${count.toDouble / FluidConstants.BUCKET.toDouble}%.3f")
          else
            // normal exp-notation bucket count
            display.append("x").append(expNotation(buckets))
  registry("media") = c =>
    Some(new TaggedVariant:
      type T = MediaVariant.type
      def variant: MediaVariant.type = MediaVariant
      def display: Text = Text.literal("Media").styled(_.withColor(0x74b3f2)))

def classNamed(name: String): Option[ClassTag[?]] =
  try
    Some(ClassTag(Class.forName(name)))
  catch
    case _: ClassNotFoundException => None

object registerHopperEndpoint extends (() => Unit):
  def apply(): Unit =
    HopperEndpointRegistry.INSTANCE.register: (iota: Iota, env: CastingEnvironment, slot: Integer) =>
      given world: ServerWorld = env.getWorld
      iota match
        case c: ConduitIota =>
          val conduit = c.getConduit
          val source: Option[HopperSource] = world.getBlockEntity(conduit.source()) match
            case s: SidedInventory => Some(SidedInventoryEndpoint(s, conduit.sourceDir()))
            case _ => None
          val dest: Option[HopperDestination] = world.getBlockEntity(conduit.sink()) match
            case s: SidedInventory => Some(SidedInventoryEndpoint(s, conduit.sourceDir()))
            case _ => None
          (source, dest) match
            case (None, None) => null
            case (Some(s), None) => new HopperSource:
              export s.{getItems, withdraw}
            case (None, Some(d)) => new HopperDestination:
              export d.{deposit, simulateDeposit}
            case (Some(s), Some(d)) => new HopperSource with HopperDestination:
              export s.{getItems, withdraw}
              export d.{deposit, simulateDeposit}
        case _ => null

extension [A, B] (p: (A, B))
  infix def both[R, S](f: (A => R) & (B => S)): (R, S) = (f(p._1), f(p._2))
case class MapIota(map: Map[NbtCompound, NbtCompound] = Map())(using val world: ServerWorld) extends Iota(MapIota, map):
  def get(key: Iota): Option[Iota] = map.get(IotaType.serialize(key)).map(IotaType.deserializeIota(_, summon))
  def apply(key: Iota): Iota = get(key) getOrElse NullIota()
  def -(keys: Iota*): MapIota = MapIota(map -- (keys map IotaType.serialize))
  def --(other: MapIota): MapIota = MapIota(map -- other.map.keys)
  def +(pairs: (Iota, Iota)*): MapIota = MapIota(map ++ pairs.map(_ both IotaType.serialize))
  def ++(other: MapIota): MapIota = MapIota(map ++ other.map)
  def update(f: map.type => Map[NbtCompound, NbtCompound]): MapIota = MapIota(map pipe f)
  def head: (Iota, Iota) = map.head both(IotaType.deserializeIota(_, summon))
  def tail: MapIota = MapIota(map.tail)
  def init: MapIota = MapIota(map.init)
  def last: (Iota, Iota) = map.last both(IotaType.deserializeIota(_, summon))
  def &(other: MapIota): MapIota = MapIota(map.filter(_._1 pipe other.map.contains))
  def ^(other: MapIota): MapIota = mutable.Map[NbtCompound, NbtCompound]().tap(m =>
    m.addAll(map)
    for ((k, v) <- other.map) do
      if m contains k then
        m -= k
      else
        m += (k -> v)
  ) pipe (_.toMap) pipe (new MapIota(_))
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  override def serialize(): NbtElement = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) tap l.add)
object MapIota extends IotaType[MapIota]:
  def color: Int = 0xb0641c
  def deserialize(using data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    def o = HexUtils.downcast(_, NbtCompound.TYPE)
    l.map(o)
      .map(c => ((c("k") pipe o) -> (c("v") pipe o)))
      .toMap[NbtCompound, NbtCompound]
      .pipe(new MapIota(_))
  def display(data: NbtElement): Text =
    val items = HexUtils.downcast(data, NbtList.TYPE)
    val output: MutableText = "["
    output.styled(_.withColor(color))
    if items.nonEmpty then
      def castToCompound = HexUtils.downcast(_, NbtCompound.TYPE)
      val itemPair = items.map(castToCompound).iterator
      def writePair(pair: NbtCompound) =
        output.append(IotaType.getDisplay(pair("k") pipe castToCompound))
        output.append(" → ")
        output.append(IotaType.getDisplay(pair("v") pipe castToCompound))
      writePair(itemPair.next())
      while itemPair.hasNext do
        output.append(", ")
        writePair(itemPair.next())
    output.append("]")
    output
trait IotaCoercion[T]:
  typ: IotaType[I] =>
  // need _root_ path, since `typ` could theoretically have these as members
  type I <: _root_.at.petrak.hexcasting.api.casting.iota.Iota
  def foo = ???
def downcast[R: ClassTag](t: Any): Option[R] = t match
  case r: R => Some(r)
  case _ => None
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
