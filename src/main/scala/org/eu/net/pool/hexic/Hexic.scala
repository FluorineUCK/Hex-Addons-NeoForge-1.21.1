//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{MishapInvalidIota, MishapOthersName}
import at.petrak.hexcasting.api.utils.HexUtils
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents
import com.mojang.brigadier.builder.{LiteralArgumentBuilder, RequiredArgumentBuilder}
import com.mojang.serialization.{Codec, DynamicOps}
import com.samsthenerd.inline.api.data.ItemInlineData
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant
import net.minecraft.Bootstrap
import net.minecraft.command.argument.{EntityArgumentType, NbtElementArgumentType}
import net.minecraft.command.{CommandException, EntitySelector}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluid
import net.minecraft.item.Item
import net.minecraft.nbt.*
import net.minecraft.nbt.visitor.StringNbtWriter
import net.minecraft.registry.{Registries, Registry, RegistryKey}
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.{HoverEvent, MutableText, Text}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.Vec3d
import net.minecraft.util.{Formatting, Hand, Identifier, Uuids}
import net.minecraft.world.World
import org.eu.net.pool.hexic
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import java.util.UUID
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{experimental, showAsInfix, tailrec, unused}
import scala.collection.convert.ImplicitConversions.*
import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.jdk.CollectionConverters.*
import scala.language.experimental.{macros, saferExceptions}
import scala.language.{dynamics, existentials, implicitConversions, reflectiveCalls}
import scala.reflect.ClassTag
import scala.util.boundary
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

def init(): Unit =
  Registry.register(HexRegistries.IOTA_TYPE, "location": Identifier, LocationIota)
  Registry.register(HexRegistries.IOTA_TYPE, "text": Identifier, TextIota)
  Registry.register(HexRegistries.IOTA_TYPE, "nbt": Identifier, NbtIota)
  Registry.register(HexRegistries.IOTA_TYPE, "variant": Identifier, VariantIota)
  Registry.register(HexRegistries.IOTA_TYPE, "stack": Identifier, StackIota)
  Registry.register(HexRegistries.IOTA_TYPE, "map": Identifier, MapIota)
  Registry.register(HexRegistries.ACTION, "nbt/serialize": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaq", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] = util.List.of(NbtIota(list.get(0)))
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/lift1": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqw", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Byte](b) => NbtIota(NbtByte.of(b))
          case iotaLike[Array[Byte]](b) => NbtIota(NbtByteArray(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("byte": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/lift2": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqww", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Short](b) => NbtIota(NbtShort.of(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("short": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/lift4": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqwww", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Int](b) => NbtIota(NbtInt.of(b))
          case iotaLike[Array[Int]](b) => NbtIota(NbtIntArray(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("int": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/lift8": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqwwww", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Long](b) => NbtIota(NbtLong.of(b))
          case iotaLike[Array[Long]](b) => NbtIota(NbtLongArray(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("long": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/liftf": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqwaa", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Float](b) => NbtIota(NbtFloat.of(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("float": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  Registry.register(HexRegistries.ACTION, "nbt/liftd": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqwaawaa", HexDir.NORTH_WEST), new ConstMediaAction:
    import ConstMediaAction.DefaultImpls => d
    override def getArgc: Int = 1
    override def getMediaCost: Long = 0
    override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
      util.List.of:
        list(0) match
          case iotaLike[Double](b) => NbtIota(NbtDouble.of(b))
          case iota => throw MishapInvalidIota.of(iota, 0, ("double": Identifier).toString)
    override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
    override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  ))
  def literal(id: Identifier, pattern: HexPattern, value: ServerWorld?=> Iota) =
    Registry.register(HexRegistries.ACTION, id, ActionRegistryEntry(pattern, new ConstMediaAction:
      import ConstMediaAction.DefaultImpls => d
      override def getArgc: Int = 0
      override def getMediaCost: Long = 0
      override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
        given ServerWorld = castingEnvironment.getWorld
        util.List.of(value)
      override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
      override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
    ))
  literal("nbt/literal/collection", HexPattern.fromAngles("qqddqdewqaeaaee", HexDir.EAST), NbtIota(NbtCompound()))
  literal("nbt/literal/list", HexPattern.fromAngles("eedwaqq", HexDir.EAST), NbtIota(NbtList()))
  literal("nbt/literal/array1", HexPattern.fromAngles("eedwaqqe", HexDir.EAST), NbtIota(NbtByteArray(Array[Byte]())))
  literal("nbt/literal/array2", HexPattern.fromAngles("eedwaqqew", HexDir.EAST), NbtIota(NbtIntArray(Array[Int]())))
  literal("nbt/literal/array4", HexPattern.fromAngles("eedwaqqewww", HexDir.EAST), NbtIota(NbtLongArray(Array[Long]())))
  literal("empty_map", HexPattern.fromAngles("dqdwdqd", HexDir.EAST), MapIota())
  Registry.register(HexRegistries.ACTION, "nbt/deserialize": Identifier, ActionRegistryEntry(HexPattern.fromAngles("edwaqa", HexDir.NORTH_WEST), new ConstMediaAction:
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
                  c.getSource.sendFeedback(() => Text.translatable("debug print jumpscare"), true)
                  println("before")
                  println(s"before2 ${r}")
                  p.gimmeIota(r)
                  println("after")
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
      ).build()
    )

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

case class StackIota[T](stack: VariantIota[T], count: Long) extends Iota(StackIota, stack):
  given ClassTag[T] = stack.tag
  override def isTruthy: Boolean = ???
  override def toleratesOther(that: Iota): Boolean = that match
    case s: StackIota[?] => stack == s.stack && count == s.count
    case _ => false
  override def serialize: NbtElement = stack.serialize |- (_.asInstanceOf[NbtCompound].putLong("n", count))
object StackIota extends IotaType[StackIota[?]]:
  def color: Int = 0xa34646
  def deserialize(using NbtElement, ServerWorld): StackIota[?] =
    val c: NbtCompound = HexUtils.downcast(summon, NbtCompound.TYPE)
    StackIota(VariantIota.deserialize, c.getLong("n"))
  def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    VariantIota.display(c).copy.tap: t =>
      t.append("x" + expNotation(c.getLong("n")))
inline def repeat[T](inline value: T, inline cond: T => Boolean)(inline body: T => T): T =
  var current = value
  while (cond(current)) current = body(current)
  current

def expNotation(n: Long) =
  if n >= 1000000 then
    // someone needs to stop you
    var d = 0
    val r =
      repeat(n, _ >= 1000): n =>
        d += 1
        n / 10
    f"${r.toDouble / 100}%.2fx10${d.toString.map(c => "⁰¹²³⁴⁵⁶⁷⁸⁹"(c - '0'))}"
  else
    n.toString

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
  def tag: ClassTag[T] = summon
  override def isTruthy: Boolean = true
  override def toleratesOther(that: Iota): Boolean =
    that match
      case v: VariantIota[T] => tag == v.tag && key == v.key && data == v.data
      case _ => false
  override def serialize: NbtElement =
    data.toNbt
    |- (_.putString("type", key.getValue.toString))
//noinspection UnstableApiUsage
object VariantIota extends IotaType[VariantIota[?]], Registrar[VariantIota.Reader]("transfer_variants"):
  type Reader = NbtCompound => Option[VariantIota.TaggedVariant]
  trait TaggedVariant:
    type T: ClassTag
    def tag: ClassTag[T] = summon
    def variant: TransferVariant[T]
    def display: Text
  def color: Int = 0x720a0a
  private def parseVariant(c: NbtCompound): Option[(TaggedVariant, RegistryKey[Reader])] =
    Identifier.tryParse(c.getString("type")) match
      case null => None
      case i => Option.fromNullable(registry.get(i)).flatMap(_(c)).map((_, RegistryKey.of(VariantIota, i)))
  end parseVariant
  def deserialize(using NbtElement, ServerWorld): VariantIota[?] | Null =
    summon[NbtElement] match
      case c: NbtCompound =>
        parseVariant(c) match
          case Some((t, k)) =>
            given ClassTag[t.T] = t.tag
            VariantIota(t.variant, k)
          case None => null
      case _ => null
  end deserialize
  override def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    parseVariant(c).fold(NullIota.DISPLAY)(_._1.display)
  end display
  Registry.register(registry, "item", c =>
    val s = ItemVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Item
        def variant: TransferVariant[Item] = s
        def display: Text = t"${s.getItem.getName(s.toStack)}: ${ItemInlineData(s.toStack).asText(true)}"
          .styled(_.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(s.toStack))))
  )
  Registry.register(registry, "fluid", c =>
    val s = FluidVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Fluid
        def variant: TransferVariant[Fluid] = s
        def display: Text = t"${s.getFluid.getDefaultState.getBlockState.getBlock.getName}: ${ItemInlineData.make(s.getFluid.getBucketItem.getDefaultStack)}"
  )
  Registry.register(registry, "media": Identifier, c =>
    Some(new TaggedVariant:
      type T = MediaVariant.type
      def variant: MediaVariant.type = MediaVariant
      def display: Text = Text.literal("Media").styled(_.withColor(0x74b3f2)))
  )
extension [T] (t: T) def |>[U](f: T => U): U = t.pipe(f)
extension [T] (t: T) def |-[U](f: T => U): T = t.tap(f)
extension [A, B] (p: (A, B))
  infix def both[R, S](f: (A => R) & (B => S)) = (f(p._1), f(p._2))
case class MapIota(map: Map[NbtCompound, NbtCompound] = Map())(using val world: ServerWorld) extends Iota(MapIota, map):
  def get(key: Iota): Option[Iota] = map.get(IotaType.serialize(key)).map(IotaType.deserializeIota(_, summon))
  def apply(key: Iota): Iota = get(key) getOrElse NullIota()
  def -(keys: Iota*): MapIota = MapIota(map removedAll (keys map IotaType.serialize))
  def --(other: MapIota): MapIota = MapIota(map -- other.map.keys)
  def +(pairs: (Iota, Iota)*): MapIota = MapIota(map ++ pairs.map(_ both IotaType.serialize))
  def ++(other: MapIota): MapIota = MapIota(map ++ other.map)
  def update(f: map.type => Map[NbtCompound, NbtCompound]): MapIota = MapIota(map|>f)
  def head: (Iota, Iota) = map.head both(IotaType.deserializeIota(_, summon))
  def tail: MapIota = MapIota(map.tail)
  def init: MapIota = MapIota(map.init)
  def last: (Iota, Iota) = map.last both(IotaType.deserializeIota(_, summon))
  def &(other: MapIota) = MapIota(map.filter(_._1|>other.map.contains))
  def ^(other: MapIota) = mutable.Map[NbtCompound, NbtCompound]().tap(m =>
    m.addAll(map)
    for ((k, v) <- other.map) do
      if m contains k then
        m -= k
      else
        m += (k -> v)
  ) |> (_.toMap) |> (new MapIota(_))
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  override def serialize(): NbtElement = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) |- l.add)
object MapIota extends IotaType[MapIota]:
  def color: Int = 0xb0641c
  def deserialize(using data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    def o = HexUtils.downcast(_, NbtCompound.TYPE)
    l.map(o)
      .map(c => ((c("k")|>o) -> (c("v")|>o)))
      .toMap[NbtCompound, NbtCompound]
      .pipe(new MapIota(_))
  def display(data: NbtElement): Text =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    val p: MutableText = "["
    p.styled(_.withColor(color))
    if l.nonEmpty then
      def o = HexUtils.downcast(_, NbtCompound.TYPE)
      val i = l.map(o).iterator
      def r(c: NbtCompound) =
        p.append(IotaType.getDisplay(c("k")|>o))
        p.append(" → ")
        p.append(IotaType.getDisplay(c("v")|>o))
      r(i.next())
      while (i.hasNext) do
        p.append(", ")
        r(i.next())
    p.append("]")
    p
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
