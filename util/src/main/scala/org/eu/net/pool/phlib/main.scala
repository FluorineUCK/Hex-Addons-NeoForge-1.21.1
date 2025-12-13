package org.eu.net.pool
package phlib

import at.petrak.hexcasting.api.utils.HexUtils
import com.google.gson.JsonElement
import com.mojang.serialization.{Codec, DynamicOps, JsonOps}
import net.minecraft.nbt.{NbtByte, NbtByteArray, NbtDouble, NbtEnd, NbtFloat, NbtInt, NbtIntArray, NbtList, NbtLong, NbtLongArray, NbtOps, NbtShort, NbtString, NbtType}
import net.minecraft.util.dynamic.Codecs
import at.petrak.hexcasting.api.addldata.ADMediaHolder
import at.petrak.hexcasting.api.casting.{ActionRegistryEntry, ParticleSpray, RenderedSpell, SpellList}
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction, OperationAction, SpecialHandler, SpellAction}
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect.DoMishap
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, ContinuationFrame, FrameEvaluate, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, CastingEnvironmentComponent, MishapEnvironment, OperationResult, ResolvedPattern, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapBadCaster, MishapBadOffhandItem, MishapInvalidIota, MishapInvalidOperatorArgs, MishapNotEnoughArgs, MishapOthersName, MishapTooManyCloseParens}
import net.minecraft.nbt.{NbtCompound, NbtElement}
import net.minecraft.server.world.ServerWorld

import scala.annotation.tailrec
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
export scala.collection.convert.ImplicitConversions.*
export scala.util.chaining._
import java.util
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier
import scala.util.boundary
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

val fabric = FabricLoader.getInstance
val isDev = fabric.isDevelopmentEnvironment
lazy val iotaTypeRegistry = hexXplat.getIotaTypeRegistry
lazy val actionRegistry = hexXplat.getActionRegistry

private[phlib] given logger: Logger = LoggerFactory.getLogger("phlib")
private[phlib] given Conversion[String, Identifier] = Identifier.of("phlib", _)

implicit class RegistryOps[T](r: Registry[T]) extends AnyRef:
  def apply(key: Identifier | RegistryKey[?] | Int) =
    key match
      case i: Identifier => r.get(i)
      case i: Int => r.get(i)
      case k: RegistryKey[?] => r.get(k.asInstanceOf[RegistryKey[T]])
  def update(key: Identifier | RegistryKey[?], value: T) =
    key match
      case i: Identifier => Registry.register(r, i, value)
      case k: RegistryKey[?] => Registry.register(r, k.asInstanceOf[RegistryKey[T]], value)

extension [T](l: util.AbstractList[T])
  def apply(n: Int): T = l.get(n)
  def update(n: Int, x: T): Unit = l.set(n, x)
extension (c: NbtCompound)
  def apply(k: String): NbtElement | Null = c.get(k)
  def update(k: String, v: NbtElement | Null): Unit = c.put(k, v)

inline given DynamicOps[JsonElement] = JsonOps.COMPRESSED
extension [T: DynamicOps as t] (x: T) def convertDynamic[R: DynamicOps as r]: R = t.convertTo(r, x)

given (vm: CastingVM) => CastingEnvironment = vm.getEnv
given envGetWorld: (env: CastingEnvironment) => ServerWorld = env.getWorld

given Conversion[String, MutableText] = Text.literal

given Codec[Text] = Codecs.TEXT
given DynamicOps[NbtElement] = NbtOps.INSTANCE

given IotaType[DoubleIota] = DoubleIota.TYPE

given (vm: CastingVM) => CastingImage = vm.getImage
given Conversion[CastingVM, CastingImage] = _.getImage
given Conversion[CastingVM, CastingEnvironment] = _.getEnv
given Conversion[String, NbtString] = NbtString.of
given Conversion[NbtString, String] = _.asString

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

@tailrec
def finishOperation(p: OperationResult)(using env: CastingEnvironment): OperationResult =
  p.getNewContinuation match
    case c: SpellContinuation.Done => p
    case c: SpellContinuation.NotDone =>
      finishCast(c.getFrame.evaluate(c.getNext, env.getWorld, CastingVM(p.getNewImage, env)), p.getNewImage)

inline def finishCast(p: CastResult, oldImage: CastingImage)(using env: CastingEnvironment): OperationResult =
  finishOperation(OperationResult(p.getNewData??oldImage, p.getSideEffects, p.getContinuation, p.getSound))

extension [T] (x: T | Null)
  inline def ?[R](f: T => R): R | Null = x match
    case null => null
    case x: T => f(x)
  inline def ??(y: T): T = x match
    case null => y
    case x: T => x

extension (i: CastingImage)
  def withStack(m: Seq[Iota] => Seq[Iota]): CastingImage = i.copy(util.ArrayList(m(i.getStack.toSeq)), i.getParenCount, i.getParenthesized, i.getEscapeNext, i.getOpsConsumed, i.getUserData)
extension (o: OperationResult)
  def withStack(m: Seq[Iota] => Seq[Iota]): OperationResult = o.copy(o.getNewImage.withStack(m), o.getSideEffects, o.getNewContinuation, o.getSound)

extension (ctx: StringContext)
  def ne(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_EAST)
  def e(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.EAST)
  def se(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_EAST)
  def nw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_WEST)
  def w(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.WEST)
  def sw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_WEST)

extension [T, R] (f: T => R) def ∘ [U](g: U => T) = (x: U) => f(g(x))
def wrapReturn[T](body: (T => Nothing) => T): T = body(return _)
def wrapThrow[T, E <: Throwable](body: (E => Nothing) => T): T = wrapReturn[Try[T]](r => Success(body(r∘Failure))).get

def propagateMishaps[T](env: CastingEnvironment)(body: => T): T =
  wrapThrow[T, Mishap]: doThrow =>
    object key extends CastingEnvironmentComponent.Key[?]
    env.addExtension:
      new CastingEnvironmentComponent with CastingEnvironmentComponent.PostExecution:
        override def getKey: CastingEnvironmentComponent.Key[?] = key

        override def onPostExecution(result: CastResult): Unit =
          result.getSideEffects.collectFirst:
            case m: OperatorSideEffect.DoMishap =>
              if isDev then println(s"Propagating mishap: $m")
              doThrow(m.getMishap)
    try body finally env.removeExtension(key)

def clamp[@specialized T: Ordering](x: T)(min: T, max: T): T =
  assume(max > min)
  if x < min then min
  else if x > max then max
  else x

object isIota:
  inline def unapply[T <: Iota : IotaType as ty : ClassTag, I <: Int & Singleton](iota: Iota): Some[T] =
    iota match
      case iota: T => Some(iota)
      case _ => throw MishapInvalidIota(iota, compiletime.constValue[I], ty.typeName)

given IotaType[Vec3Iota] = Vec3Iota.TYPE

object Patterns:
  def mkAction(body: (CastingEnvironment, ServerWorld) ?=> (CastingImage, SpellContinuation) => (OperationResult | CastResult | (CastingImage, SpellContinuation, EvalSound, Seq[OperatorSideEffect]))): Action =
    (env: CastingEnvironment, image: CastingImage, cont: SpellContinuation) =>
      try
        body(using env, env.getWorld)(image, cont) match
          case res: OperationResult => res
          case res: CastResult => OperationResult(res.getNewData, res.getSideEffects, res.getContinuation, res.getSound)
          case (img, cont, sound, effects) => OperationResult(img, effects, cont, sound)
      catch
        case _: NotImplementedError => throw MishapTodo()
        case e: MatchError =>
          e.printStackTrace()
          throw MishapInvalidIota(image.getStack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0).tap(_.initCause(e))), 0, "unknown").tap(_.initCause(e))
  def mkConstAction(argc: Int, mediaCost: Long = 0)(body: (CastingEnvironment, ServerWorld) ?=> Seq[Iota] => Seq[Iota]): Action =
    new ConstMediaAction:
      import ConstMediaAction.DefaultImpls as d
      override def getArgc: Int = argc
      override def getMediaCost: Long = mediaCost
      override def execute(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): util.List[Iota] =
        body(using castingEnvironment, castingEnvironment.getWorld)(list.toSeq)
      override def executeWithOpCount(list: util.List[? <: Iota], castingEnvironment: CastingEnvironment): ConstMediaAction.CostMediaActionResult = d.executeWithOpCount(this, list, castingEnvironment)
      override def operate(castingEnvironment: CastingEnvironment, castingImage: CastingImage, spellContinuation: SpellContinuation): OperationResult = d.operate(this, castingEnvironment, castingImage, spellContinuation)
  def mkLiteral(value: (CastingEnvironment, ServerWorld) ?=> Iota): Action =
    mkConstAction(0): (args: Seq[Iota]) =>
      args :+ value
  def register(id: Identifier, pattern: => HexPattern)(body: => Action): Unit =
    boundary:
      val p = try pattern catch case _: NotImplementedError =>
        logger.warn(s"No pattern for action $id")
        boundary.break()
      lazy val b = try body catch case _: NotImplementedError => throw MishapTodo()
      actionRegistry(id) = ActionRegistryEntry(p, new Action { export b._ })
  def arithmetic(id: Identifier, pattern: HexPattern): Unit =
    Patterns.register(id, pattern):
      OperationAction(pattern)

val hexXplat: IXplatAbstractions = IXplatAbstractions.INSTANCE

extension (ctx: StringContext) def ifModLoaded(`then`: => Unit, `else`: => Unit = {}): Unit =
  if isDev || fabric.isModLoaded(ctx.parts(0)) then
    `then`
  else
    `else`

// this is out-of-scope for phlib but I have no idea where else to put it
def init() =
  hexXplat.getIotaTypeRegistry("map") = MapIota
  hexXplat.getArithmeticRegistry("maps") = mapArithmetic
  Events.registryLookup.register:
    case (r, i) if r == hexXplat.getIotaTypeRegistry && i == Identifier.of("hexic", "map") => MapIota
  Patterns.register("empty_map", e"dqdwdqd"):
    Patterns.mkLiteral(MapIota())

object Events:
  def partialEvent[T, R]: Event[PartialFunction[T, R]] = EventFactory.createArrayBacked(classOf, PartialFunction.empty, ary => (PartialFunction.empty /: ary) (_ orElse _))
  val beforePatternExecute: Event[PartialFunction[(PatternIota, CastingVM, ServerWorld, SpellContinuation), CastResult]] = partialEvent
  val registryLookup: Event[PartialFunction[(Registry[?], Identifier), ?]] = partialEvent