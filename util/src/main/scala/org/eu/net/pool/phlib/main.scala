package org.eu.net.pool
package phlib

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
import net.minecraft.server.world.ServerWorld
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

private given logger: Logger = LoggerFactory.getLogger("phlib")

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

given Conversion[String, MutableText] = Text.literal

extension (i: CastingImage)
  def withStack(m: Seq[Iota] => Seq[Iota]): CastingImage = i.copy(util.ArrayList(m(i.getStack.toSeq)), i.getParenCount, i.getParenthesized, i.getEscapeNext, i.getOpsConsumed, i.getUserData)
extension (o: OperationResult)
  def withStack(m: Seq[Iota] => Seq[Iota]): OperationResult = o.copy(o.getNewImage.withStack(m), o.getSideEffects, o.getNewContinuation, o.getSound)

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

object Events:
  val beforePatternExecute: Event[PartialFunction[(PatternIota, CastingVM, ServerWorld, SpellContinuation), CastResult]] = EventFactory.createArrayBacked(classOf, PartialFunction.empty, ary => (PartialFunction.empty /: ary) (_ orElse _))