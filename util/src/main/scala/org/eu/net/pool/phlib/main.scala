package org.eu.net.pool
package phlib


import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage.ParenthesizedIota
import at.petrak.hexcasting.api.casting.mishaps.{MishapInternalException, MishapStackSize}
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import kotlin.jvm.internal.DefaultConstructorMarker
import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction, OperationAction}
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, CastingEnvironmentComponent, OperationResult}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapInvalidIota, MishapNotEnoughArgs}
import at.petrak.hexcasting.api.utils.HexUtils
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents
import com.google.gson.JsonElement
import com.mojang.brigadier.builder.{LiteralArgumentBuilder, RequiredArgumentBuilder}
import com.mojang.serialization.{Codec, DynamicOps, JsonOps}
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.argument.{EntityArgumentType, NbtElementArgumentType, RegistryEntryArgumentType}
import net.minecraft.command.{CommandException, EntitySelector}
import net.minecraft.nbt.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.{Arm, Hand}
import net.minecraft.util.dynamic.Codecs

import scala.annotation.tailrec
import scala.collection.{IterableOnceOps, IterableOps}
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
export scala.collection.convert.ImplicitConversions.*
export scala.util.chaining._
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.fabricmc.fabric.api.event.{Event, EventFactory}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.registry.{Registry, RegistryKey}
import net.minecraft.text.{MutableText, Text}
import net.minecraft.util.Identifier
import org.slf4j.{Logger, LoggerFactory}

import java.util
import scala.util.boundary

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
implicit class IterExt[T](i: IterableOps[T, ?, ?]):
  export i.{exists => ∃, forall => ∀}
  def findFirstOrLast(p: T => Boolean): Option[T] =
    boundary:
      (None /: i):
        case (ctx, x) =>
          if p(x) then
            boundary.break(Some(x))
          else
            Some(x)
implicit class IterOnceExt[T](i: IterableOnceOps[T, ?, ?]):
  export i.{exists => ∃, forall => ∀}
  def findFirstOrLast(p: T => Boolean): Option[T] =
    boundary:
      (None /: i):
        case (ctx, x) =>
          if p(x) then
            boundary.break(Some(x))
          else
            Some(x)

extension (arm: Arm)
  def ^(hand: Hand): Arm = arm ^ (hand == Hand.MAIN_HAND)
  def ^(invert: Boolean): Arm = if invert then arm.getOpposite else arm
  def ^(OtherArm: Arm): Hand = arm match
    case OtherArm => Hand.MAIN_HAND
    case _ => Hand.OFF_HAND
extension (hand: Hand)
  def ^(arm: Arm): Arm = arm ^ hand
  def ^(invert: Boolean): Hand =
    if invert then
      hand match
        case Hand.MAIN_HAND => Hand.OFF_HAND
        case Hand.OFF_HAND => Hand.MAIN_HAND
    else hand

extension (t: Throwable) def toMishap =
  t match
    case m: Mishap => m
    case e: Exception => MishapInternalException(e)
    case _ => MishapInternalException(RuntimeException(t))

extension [T] (x: => T) def trying: Try[T] = Try(x)

given castingImageUpdate: AnyRef with
  extension (img: CastingImage)
    def apply(stack: Seq[Iota] = img.getStack.toSeq,
              parenCount: Int = img.getParenCount,
              parenthesized: Seq[ParenthesizedIota] = img.getParenthesized.toSeq,
              escapeNext: Boolean = img.getEscapeNext,
              opsConsumed: Long = img.getOpsConsumed,
              userData: NbtCompound = img.getUserData) =
      CastingImage(stack = stack,
                   parenCount = parenCount,
                   parenthesized = parenthesized,
                   escapeNext = escapeNext,
                   opsConsumed = opsConsumed,
                   userData = userData,
                   null : DefaultConstructorMarker)
given castResultUpdate: AnyRef with
  extension (r: CastResult)
    def apply(cast: Iota = r.getCast,
              continuation: SpellContinuation = r.getContinuation,
              newData: Option[CastingImage] = Option(r.getNewData),
              sideEffects: Seq[OperatorSideEffect] = r.getSideEffects.toSeq,
              resolutionType: ResolvedPatternType = r.getResolutionType,
              sound: EvalSound = r.getSound) =
      CastResult(cast = cast,
                 continuation = continuation,
                 newData = newData.orNull,
                 sideEffects = sideEffects,
                 resolutionType = resolutionType,
                 sound = sound)

extension (continuation: SpellContinuation.NotDone)
  @tailrec
  def executePreemptive(vm: CastingVM /* do not do the bad thing */ , ttl: Int): Option[SpellContinuation.NotDone] =
    // spin the vm around a bit
    val result =
      val maybeResult = continuation.getFrame.evaluate(continuation.getNext, vm.getEnv.getWorld, vm)
      // check for stack-overflow
      if maybeResult.getNewData != null && IotaType.isTooLargeToSerialize(maybeResult.getNewData.getStack) then
        // blindly doing what hexmod does
        maybeResult(newData = None,
                    sideEffects = Seq(OperatorSideEffect.DoMishap(MishapStackSize(), Mishap.Context(null, null))),
                    resolutionType = ResolvedPatternType.ERRORED,
                    sound = HexEvalSounds.MISHAP)
      else
        maybeResult
    // update the vm with the new image immediately (if we have one)
    if result.getNewData != null then vm.setImage(result.getNewData)
    // notify anyone interested
    vm.getEnv.postExecution(result)
    // execute each side effect (ignoring errors)
    result.getSideEffects.foreach(_.performEffect(vm).trying)
    // finally, decide our fate
    result.getContinuation match
      // we're only interested in continuing execution if we have more to execute and nothing bad happened
      case newContinuation: SpellContinuation.NotDone if result.getResolutionType.getSuccess =>
        if vm.getImage.getOpsConsumed < ttl then
          // we still have time left, go for another round
          newContinuation.executePreemptive(vm, ttl)
        else
          // stick the hex in the pear wiggler, we'll return next tick
          Some(newContinuation)
      case _ => None
extension (continuation: SpellContinuation)
  def executePreemptive(vm: CastingVM /* do not do the bad thing */ , ttl: Int): Option[SpellContinuation.NotDone] =
    continuation match
      case done: SpellContinuation.Done => None
      case notDone: SpellContinuation.NotDone => notDone.executePreemptive(vm, ttl)

extension (ctx: StringContext) def ifModLoaded(`then`: => Unit, `else`: => Unit = {}): Unit =
  if isDev || fabric.isModLoaded(ctx.parts(0)) then
    `then`
  else
    `else`

trait Registered[T](registry: Registry[T], id: Identifier):
  this: T =>
  registry(id) = this

extension (p: ServerPlayerEntity) def gimmeIota(iota: Iota): Unit =
  val m = p.getComponent(HexCardinalComponents.STAFFCAST_IMAGE)
  m.setImage(m.getVM(Hand.MAIN_HAND).getImage.withStack(_ ++ Vector(iota)))

private[phlib] trait AllocationTracked:
  private[phlib] val phlib$createdAt: Exception

package mixin:
  import net.minecraft.block.Block
  import net.minecraft.item.Item
  import org.spongepowered.asm.mixin.Mixin
  @Mixin(value = Array(classOf[Item], classOf[Block]))
  private[phlib] class AllocationTrackerMixin() extends AnyRef with AllocationTracked:
    private[phlib] val phlib$createdAt: Exception = new RuntimeException(s"unregistered $this ($getClass) created")

// this is out-of-scope for phlib but I have no idea where else to put it
def init() =
  hexXplat.getIotaTypeRegistry("map") = MapIota
  hexXplat.getArithmeticRegistry("maps") = mapArithmetic
  Events.registryLookup.register:
    case (r, i) if r == hexXplat.getIotaTypeRegistry && i == Identifier.of("hexic", "map") => MapIota
  Patterns.register("empty_map", e"dqdwdqd"):
    Patterns.mkLiteral(MapIota())
  CommandRegistrationCallback.EVENT.register: (d, r, e) =>
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("gimmeiota")
      .requires(c => c.hasPermissionLevel(2) || (c.getPlayer != null && c.getPlayer.isCreative))
      .`then`(RequiredArgumentBuilder.argument("type", RegistryEntryArgumentType.registryEntry(r, HexRegistries.IOTA_TYPE))
        .`then`(RequiredArgumentBuilder.argument[ServerCommandSource, NbtElement]("data", NbtElementArgumentType.nbtElement())
          .executes(c =>
            val t = RegistryEntryArgumentType.getRegistryEntry(c, "type", HexRegistries.IOTA_TYPE)
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
            val r = EntityIota(EntityArgumentType getEntity(c, "entity")) tap p.gimmeIota
            c.getSource.sendFeedback(() => Text.translatable("Pushed %s to stack", r.display), true)
            1
          ).build()
      ).build()
    )

object Events:
  def partialEvent[T, R]: Event[PartialFunction[T, R]] = EventFactory.createArrayBacked(classOf, PartialFunction.empty, ary => (PartialFunction.empty /: ary) (_ orElse _))
  val beforePatternExecute: Event[PartialFunction[(PatternIota, CastingVM, ServerWorld, SpellContinuation), CastResult]] = partialEvent
  val registryLookup: Event[PartialFunction[(Registry[?], Identifier), ?]] = partialEvent
