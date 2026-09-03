package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, FrameEvaluate, FrameFinishEval}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, CastingEnvironmentComponent, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.iota.{EntityIota, Iota, IotaType, ListIota, NullIota, PatternIota, Vec3Iota}
import at.petrak.hexcasting.api.casting.math.{HexAngle, HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapInvalidIota, MishapNotEnoughArgs}
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.TreeList
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import at.petrak.hexcasting.interop.inline.InlinePatternData
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.{CompoundTag, StringTag}
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.{Style, Component}
import net.minecraft.core.BlockPos
import org.eu.net.pool.hexic.hexcompat.*
import org.eu.net.pool.phlib.{*, given}

import java.util
import java.util.UUID
import java.util.function.UnaryOperator
import scala.collection.convert.ImplicitConversions.given
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class Defer[T](value: => T):
  lazy val now = value
object Defer:
  given [T] => Conversion[Defer[T], T] = _.now
extension [T] (x: => T) def later = Defer(x)

case class MacroDefinition(pattern: HexPattern, name: Option[String], iotaTag: CompoundTag):
  def iota(using world: ServerLevel): Iota =
    org.eu.net.pool.phlib.deserialize(iotaTag, world).asInstanceOf[Iota]
  def asNBT =
    val c = CompoundTag()
    c.put("value", iotaTag)
    c.put("angle", pattern.getStartDir.name)
    name.foreach(c.putString("name", _))
    c
object MacroDefinition:
  def apply(data: CompoundTag, anglesig: String): Option[MacroDefinition] =
    data.get("value") match
      case null => None
      case iotaTag: CompoundTag =>
        val pattern = HexPattern.fromAnglesUnchecked(anglesig, HexDir.fromString(data.getString("angle")))
        val name = data.get("name") match
          case s: StringTag => Some(s.asString)
          case _ => None
        Some(new MacroDefinition(pattern, name, iotaTag))
      case _ => None
  def findMacros(stack: ItemStack) = stack.getMacros
extension (p: Player)
  /** returns items held in paws, worn armor, and equipped trinkets */
  def relevantEquipment: Set[ItemStack] =
    p.getHandSlots.asScala.toSet ++ p.getArmorSlots.asScala ++ CuriosCompat.equippedStacks(p)
  def getMacros: Set[(ItemStack, MacroDefinition)] =
    for
      stack <- relevantEquipment
      data <- stack.getMacros
    yield stack -> data
  def findMacro(pattern: HexPattern): Set[(ItemStack, MacroDefinition)] = getMacros.filter(_._2.pattern.anglesSignature == pattern.anglesSignature)
extension (s: ItemStack)
  def getMacros: Set[MacroDefinition] =
    val nbt = s.getNbt
    if nbt == null then
      Set.empty
    else (for
      case key@macroPattern(anglesig) <- nbt.getAllKeys.iterator
      case macroTag: CompoundTag <- Option(nbt.get(key))
      data <- MacroDefinition(macroTag, anglesig)
    yield data).toSet
  def getMacro(pattern: HexPattern) = Option(s.getNbt).flatMap(nbt => Option(nbt.getCompound(s"hexic:macro/${pattern.anglesSignature}"))).flatMap(MacroDefinition(_, pattern.anglesSignature))
  def putMacro(m: MacroDefinition) =
    s.getOrCreateNbt().put(s"hexic:macro/${m.pattern.anglesSignature}", m.asNBT)
private val macroPattern = "hexic:macro/([qweasd]+)".r
def initMacros() =
  phlib.Events.beforePatternExecute.register:
    Function.unlift: (iota, vm, world, cont) =>
      given CastingVM = vm
      given ServerLevel = world
      val pattern = iota.getPattern
      vm.getEnv match
        case env: PlayerBasedCastEnv =>
          val p = env.getCaster
          p.findMacro(pattern).toSeq match
            case Seq() => None
            case Seq(_ -> i) => Some(i.iota.executeInPlace(cont, cause=iota))
            case matches =>
              Some(new CastResult(iota, cont, null, Seq(
                OperatorSideEffect.DoMishap(
                  new Mishap:
                    override def accentColor(env: CastingEnvironment, ctx: Mishap.Context): FrozenPigment =
                      FrozenPigment(ItemStack(HexItems.UUID_PIGMENT.get()), UUID(-1195142673136205144L, -6449161185315608748L))
                    override def errorMessage(env: CastingEnvironment, ctx: Mishap.Context): Component =
                      Component.translatable("hexic.mishap.pattern_conflict", InlinePatternData(pattern).asText(true))
                    override def execute(env: CastingEnvironment, ctx: Mishap.Context, stack: TreeList[Iota]): TreeList[Iota] =
                      for m ->_<- matches do p.dropItem(m.copyAndEmpty(), true, true)
                      stack
                  , Mishap.Context(pattern, null)
                )
              ), ResolvedPatternType.ERRORED, HexEvalSounds.NORMAL_EXECUTE.get()))
        case _ => None
  Patterns.register("mkmacro", e"deeeeewwdedqqqdedwawwdeeeee"):
    Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq match
        case origStack@(stack :+ nameOrPatternIota :+ maybeEntityIota :+ hexOrNullIota) =>
          val (stack1, pattern, maybeName) =
            nameOrPatternIota match
              case nameIota: StringIota =>
                // we still want a pattern iota
                stack match
                  case stack1 :+ (patternIota: PatternIota) =>
                    (stack1, patternIota.getPattern, Some(nameIota.getString))
                  case _ :+ iota =>
                    throw MishapInvalidIota.ofType(iota, 3, "pattern")
                  case _ =>
                    throw MishapNotEnoughArgs(expected = 4, got = origStack.length)
              case patternIota: PatternIota =>
                // we're fine with just this
                (stack, patternIota.getPattern, None)
              case iota =>
                    throw MishapInvalidIota(iota, 2, Component.translatable("hexcasting.mishap.invalid_value.pattern_or_string"))
          end val
          val itemRef =
            scala.util.boundary:
              def throwMishap() = throw MishapInvalidIota.ofType(maybeEntityIota, 1, "entity")
              maybeEntityIota match
                case e: EntityIota =>
                  val entity = e.getEntity(summon[CastingEnvironment].getWorld)
                  summon[CastingEnvironment].assertEntityInRange(entity) // FIXME: this doesn't consider claims
                  entity match
                    case i: ItemEntity => (stack = i.getItem, markDirty = () => ())
                    case f: ItemFrame => (stack = f.getItem, markDirty = () => f.setItem(f.getItem, false))
                    case _ => throwMishap()
                case v: Vec3Iota =>
                  val pos = BlockPos.containing(v.getVec3)
                  summon[CastingEnvironment].assertPosInRangeForEditing(pos)
                  summon[CastingEnvironment].getWorld.getBlockEntity(pos) match
                    case pedestal: net.minecraft.world.Container
                        if pedestal.getClass.getName == "miyucomics.hexical.features.pedestal.PedestalBlockEntity" =>
                      (
                        stack = pedestal.getItem(0),
                        markDirty = () => pedestal.setChanged()
                      )
                    case _ => throwMishap()
                case _ => throwMishap()
          end itemRef
          val hex: Option[Iota] =
            hexOrNullIota match
              case l: ListIota => Some(l)
              case _: NullIota => None
              case i if i.executable => Some(i)
              case i => throw MishapInvalidIota(i, 0, Component.translatable("hexcasting.mishap.invalid_value.executable_list_or_null"))
          // it's time to begin, isn't it
          val spell =
            OperatorSideEffect.AttemptSpell(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  hex match
                    case Some(hex) =>
                      itemRef.stack.putMacro(new MacroDefinition(pattern, maybeName, hex))
                      itemRef.markDirty()
                    case None =>
                      for nbt <- Option(itemRef.stack.getNbt) do
                        nbt.remove(s"hexic:macro/${pattern.anglesSignature}")
                        if nbt.isEmpty then
                          itemRef.stack.setNbt(null)
                        itemRef.markDirty()
                override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
              , true, true
            )
          (
            CastingImage(TreeList.from(stack1.asJava), img.getParenCount, img.getParenthesized, img.getEscapeNext, img.getSimulateNext, img.getOpsConsumed, img.getUserData),
            cont,
            HexEvalSounds.SPELL.get(),
            if hex.isDefined && itemRef.stack.getMacro(pattern).isEmpty then
              Seq(OperatorSideEffect.ConsumeMedia(MediaConstants.SHARD_UNIT), spell)
            else
              Seq(spell)
          )
        case stack =>
          // we don't even know how many iotas we need; assume 3
          throw MishapNotEnoughArgs(expected = 3, got = stack.length)
