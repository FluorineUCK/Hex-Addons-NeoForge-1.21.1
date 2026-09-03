package org.eu.net.pool
package iotaworks

import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.iota.{GarbageIota, Iota, IotaType, PatternIota, DoubleIota, NullIota, Vec3Iota}
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapInvalidIota}
import at.petrak.hexcasting.api.casting.mishaps.Mishap.Context
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.TreeList
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import com.google.gson.JsonElement
import com.mojang.datafixers.util.Either
import com.mojang.serialization.{Codec, JsonOps, MapCodec}
import miyucomics.hexcellular.{PropertyIota, StateStorage}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.world.item.DyeColor
import org.eu.net.pool.phlib.{Events as PhEvents, *, given}
import org.slf4j.{Logger, LoggerFactory}

import java.{lang, util, util as ju}
import scala.jdk.CollectionConverters.given
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame
import kotlin.Pair
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type
import at.petrak.hexcasting.api.casting.eval.sideeffects.EvalSound
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds

private[iotaworks] given Logger = LoggerFactory.getLogger("iotaworks")
private[iotaworks] given Conversion[String, Identifier] = Identifier.of("iotaworks", _)
given IotaType[PropertyIota] = PropertyIota.TYPE
given IotaType[PatternIota] = PatternIota.TYPE

private[iotaworks] def iotaInt(iota: Iota, reverseIdx: Int): Int =
  iota match
    case d: DoubleIota =>
      val n = d.getDouble
      val i = n.toInt
      if (i - n).abs > DoubleIota.TOLERANCE then
        throw MishapInvalidIota.ofType(iota, reverseIdx, "int")
      i
    case _ =>
      throw MishapInvalidIota.ofType(iota, reverseIdx, "int")

abstract case class AbstractMetatableIota(iotaType: MetatableIotaType & Singleton, userdata: Iota, displayText: Text, metatable: String, readonlyMetatable: Boolean) extends Iota(() => iotaType):
  override def subIotas(): lang.Iterable[Iota] = util.List.of(userdata)
  override def toleratesOther(that: Iota): Boolean = that match
    case AbstractMetatableIota(_, u, _, m, _) => metatable == m && Iota.tolerates(userdata, u)
    case _ => Iota.tolerates(userdata, that)
  override def display(): Text = displayText
  def toTag: NbtCompound = NbtCompound().tap: c =>
    c.put("userdata", serialize(userdata))
    c.put("display", Codecs.TEXT.encodeStart(NbtOps.INSTANCE, displayText).getOrThrow())
    c.putString("metatable", metatable)
    c.putBoolean("ro", readonlyMetatable)
  def meta(using world: ServerWorld): MapIota =
    StateStorage.Companion.getProperty(world, metatable) match
      case m: MapIota => m
      case i => throw MishapBadMetatable(metatable, i, readonlyMetatable)
  def meta_=(using world: ServerWorld)(x: MapIota): Unit =
    StateStorage.Companion.setProperty(world, metatable, x)
  infix def mro(key: HexPattern)(using world: ServerWorld): Option[Iota] =
    meta.get(PatternIota(key)).orElse:
      userdata match
        case m: AbstractMetatableIota => m mro key
        case _ => None
  override def isTruthy: Boolean = true
  override def executable: Boolean = true
  override def execute(vm: CastingVM, world: ServerWorld, continuation: SpellContinuation): CastResult = callMetamethod(using vm.getEnv)(se"deaqq")(vm.getImage, continuation).getOrElse(super.execute(vm, world, continuation))
  override def size = userdata.size + 1
  def callMetamethod(using env: CastingEnvironment)(key: HexPattern)(image: CastingImage, continuation: SpellContinuation): Option[CastResult] =
    for callee <- mro(key) yield
      val result = OpEval.INSTANCE.exec(
        env,
        image,
        continuation,
        TreeList.from((image.getStack.asScala.toSeq :+ userdata).asJava),
        Either.left(callee)
      )
      CastResult(callee, result.getNewContinuation, result.getNewImage, result.getSideEffects, ResolvedPatternType.EVALUATED, result.getSound)
  class MishapBadMetatable(name: String, value: Iota, readonly: Boolean) extends Mishap():
    override def errorMessage(env: CastingEnvironment, ctx: Context): Text = Text.translatable("hexic.bad_metatable", name, value.display())
    override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.GRAY)
    override def execute(env: CastingEnvironment, ctx: Context, stack: TreeList[Iota]): TreeList[Iota] =
      if !readonly then StateStorage.Companion.setProperty(env.getWorld, name, GarbageIota())
      stack.updated(stack.size - 1, GarbageIota())

case class MetatableIotaType private[pool](colorValue: Int) extends IotaType[AbstractMetatableIota]:
  class Instance(userdata: Iota, displayText: Text, metatable: String, readonlyMetatable: Boolean) extends AbstractMetatableIota(MetatableIotaType.this, userdata, displayText, metatable, readonlyMetatable)
  override def color(): Int = colorValue
  override def codec(): MapCodec[AbstractMetatableIota] =
    CompoundTag.CODEC.xmap[AbstractMetatableIota](deserialize(_, null), _.toTag).fieldOf("value")
  override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, AbstractMetatableIota] =
    ByteBufCodecs.fromCodecWithRegistries(codec().codec())
  def deserialize(tag: NbtElement, world: ServerWorld): Instance =
    val c = tag.downcast[NbtCompound]
    Instance(
      userdata = org.eu.net.pool.phlib.deserialize(c.get("userdata").downcast[NbtCompound], world),
      displayText = Codecs.TEXT.parse(NbtOps.INSTANCE, c.get("display")).getOrThrow(),
      metatable = c.get("metatable").downcast[NbtString].asString,
      readonlyMetatable = c.getBoolean("ro"),
    )

object MetatableIotaType:
  val validValues = Seq(0x0, 0x3, 0x6, 0x9, 0xC, 0xF)
  val colors: Map[(Int, Int, Int), MetatableIotaType] = (for r <- validValues; g <- validValues; b <- validValues yield (r, g, b) -> MetatableIotaType((r << 20) | (r << 16) | (g << 12) | (g << 8) | (b << 4) | b)).toMap
  println(s"Metatables: $colors")

class DeltaFrame(val delta: Int) extends ContinuationFrame:
  def breakDownwards(stack: TreeList[Iota]): Pair[java.lang.Boolean, TreeList[Iota]] = Pair(true, stack)
  def getType(): Type[DeltaFrame] = DeltaFrame
  def size = 0
  def evaluate(cont: SpellContinuation, world: ServerWorld, vm: CastingVM): CastResult =
    CastResult(GarbageIota(), cont, DeltaFrame.shift(vm.getImage, delta)(using world), Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.NOTHING.get())
object DeltaFrame extends ContinuationFrame.Type[DeltaFrame]:
  override def codec(): MapCodec[DeltaFrame] = Codec.INT.xmap[DeltaFrame](DeltaFrame(_), _.delta).fieldOf("d")
  override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, DeltaFrame] =
    ByteBufCodecs.fromCodecWithRegistries(codec().codec())
  def shift(image: CastingImage, delta: Int)(using ServerWorld): CastingImage =
    val data = image.getUserData
    val list: NbtList = data.getList("iotaworks:stack", NbtElement.COMPOUND_TYPE) // stack[0] .. stack[n] | list[n] .. list[0]
    // We can always optimize this later. Go with the dumb method for now.
    val newStack = collection.mutable.Buffer.from(image.getStack.asScala)
    if delta > 0 then
      // move from end of stack to end of hold
      delta times list.add(serialize(if newStack.nonEmpty then newStack.remove(newStack.length - 1) else NullIota()))
    else
      // move from end of hold to end of stack
      (-delta) times newStack.append(if list.nonEmpty then org.eu.net.pool.phlib.deserialize(list.remove(list.size - 1).downcast, summon) else NullIota())
    data.put("iotaworks:stack", list)
    CastingImage(
      TreeList.from(newStack.asJava),
      image.getParenCount,
      image.getParenthesized,
      image.getEscapeNext,
      image.getSimulateNext,
      image.getOpsConsumed,
      data
    )

private[iotaworks] object Extern:
  def handleExecute(pattern: PatternIota, vm: CastingVM, world: ServerWorld, continuation: SpellContinuation, original: (CastingVM, ServerWorld, SpellContinuation) => CastResult): CastResult =
    val delta = pattern.getPattern.asInstanceOf[HexPatternAccessor].depth
    if delta != 0 then
      vm.setImage(DeltaFrame.shift(vm.getImage, delta)(using world))
      original(vm, world, continuation.pushFrame(DeltaFrame(-delta)))
    else
      original(vm, world, continuation)

trait HexPatternAccessor:
  var depth: Int

def init() =
  hexXplat.getContinuationTypeRegistry(Identifier.of("iotaworks", "delta")) = DeltaFrame
  for ((_, c), i) <- MetatableIotaType.colors.zipWithIndex do iotaTypeRegistry(s"meta/$i") = c
  Patterns.register("metatable", se"deaqqwqqqeaeqqqeadedaqaaee"):
    Patterns.mkConstAction(4):
      case Seq(userdata, display, isIota[Vec3Iota, 1](color), isIota[PropertyIota, 0](metatable)) =>
        val r = clamp(color.getVec3.x)(0.0, 1.0).*(5).round.toInt
        assume(0 until 6 contains r)
        val g = clamp(color.getVec3.y)(0.0, 1.0).*(5).round.toInt
        assume(0 until 6 contains g)
        val b = clamp(color.getVec3.z)(0.0, 1.0).*(5).round.toInt
        assume(0 until 6 contains b)
        Seq:
          val ty = MetatableIotaType.colors((r * 3, g * 3, b * 3))
          ty.Instance(userdata, display.display(), metatable.getName, metatable.getReadonly)
  if isDev then Patterns.register("metatable_abridged", se"ded"):
    Patterns.mkConstAction(3):
      case Seq(iota, key, value) =>
        val ty = MetatableIotaType.colors(0, 0, 0)
        val pw = ty.Instance(iota, Text.literal("Test"), "iotaworks", false)
        pw.meta = MapIota() + (key -> value)
        Seq(pw)
  Patterns.register("set_subscript", w"eeedewa"):
    Patterns.mkConstAction(2):
      case Seq(isIota[PatternIota, 1](pat), num) =>
        val realPat = pat.getPattern
        val pat2 = HexPattern(realPat.getStartDir, realPat.getAngles)
        pat2.asInstanceOf[HexPatternAccessor].depth = realPat.asInstanceOf[HexPatternAccessor].depth + iotaInt(num, 0)
        Seq(PatternIota(pat2))
  Patterns.register("get_subscript", nw"dwqaqqq"):
    Patterns.mkConstAction(1):
      case Seq(isIota[PatternIota, 0](pat)) =>
        Seq(DoubleIota(pat.getPattern.asInstanceOf[HexPatternAccessor].depth))
  PhEvents.registryLookup.register:
    case (r, i) if r == hexXplat.getIotaTypeRegistry && i.getNamespace == "hexic" && i.getPath.startsWith("meta/") => r(Identifier.of("iotaworks", i.getPath))
  PhEvents.beforePatternExecute.register:
    Function.unlift:
      case (p, vm, given ServerWorld, cont) =>
        given CastingEnvironment = vm.getEnv
        // this is probably cursed
        for
          case rest:+(m: AbstractMetatableIota) <- Option(vm.getStack).map(_.toSeq)
          result <- m.callMetamethod(p.getPattern)(vm.getImage.withStack(_ => rest), cont)
        yield result
