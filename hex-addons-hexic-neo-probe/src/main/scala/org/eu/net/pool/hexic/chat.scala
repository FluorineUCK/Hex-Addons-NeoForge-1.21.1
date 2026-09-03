package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect.DoMishap
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, ContinuationFrame, SpellContinuation}
import at.petrak.hexcasting.api.casting.iota.{ContinuationIota, Iota, IotaType, ListIota, NullIota}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapBadCaster, MishapInvalidIota, MishapNotEnoughArgs}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.TreeList
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.google.gson.JsonElement
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.JsonOps
import net.minecraft.world.entity.{Entity, Pose, EntityType, WalkAnimationState}
import net.minecraft.world.entity.animal.Cat
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.{NbtOps}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.{Component as ChatComponent, ComponentSerialization}
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.{Util}
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.{ResourceLocation}
import net.minecraft.world.{InteractionHand}
import net.minecraft.world.item.{DyeColor}
import kotlin.Pair
import org.eu.net.pool.hexic.hexcompat.*
import org.eu.net.pool.phlib.{Events as PhEvents, *, given}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.annotation.{meta, static, targetName}
import scala.collection.immutable.*
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary

private[hexic] case class MurmurCache(var value: Option[String]) extends HexComponent:
  override def readFromNbt(tag: CompoundTag): Unit =
    if tag.getBoolean("active") then
      value = Some(tag.getString("value"))
    else
      value = None
  override def writeToNbt(tag: CompoundTag): Unit =
    tag.putBoolean("active", false)
    for value <- value do
      tag.putBoolean("active", true)
      tag.putString("value", value)
private[hexic] object MurmurCache:
  given ComponentKey[MurmurCache] = ComponentKey("murmur", ComponentCopyPolicy.Always)(_ => MurmurCache(None))

private[hexic] case class RevealComponent(var lines: Seq[ChatComponent]) extends AutoSyncedHexComponent:
  override def readFromNbt(tag: CompoundTag): Unit =
    lines = for n <- 0 until tag.getInt("lineCount") yield ComponentTagCompat.decode(tag.get(s"line$n"))
  override def writeToNbt(tag: CompoundTag): Unit =
    tag.putInt("lineCount", lines.size)
    for (line, n) <- lines.zipWithIndex do tag.put(s"line$n", ComponentTagCompat.encode(line))
private[hexic] object RevealComponent:
  given ComponentKey[RevealComponent] = ComponentKey("reveal")(_ => RevealComponent(Seq.empty))

// rephrased from trickster
class CatHolder private[hexic] (p: Player) extends HexComponent:
  lazy private val realCat =
    val cat = Cat(EntityType.CAT, p.level())
    cat.setNoAi(true)
    cat.setInvulnerable(true)
    cat.setTame(true, true)
    cat
  def syncCat(collarColor: DyeColor) =
    val cat = this.realCat
    cat.setYRot(p.getYRot)
    cat.yRotO = p.yRotO
    cat.setXRot(p.getXRot)
    cat.xRotO = p.xRotO
    cat.setPos(p.getX, p.getY, p.getZ)
    cat.xOld = p.xOld
    cat.yOld = p.yOld
    cat.zOld = p.zOld
    cat.setYBodyRot(p.yBodyRot)
    cat.yBodyRotO = p.yBodyRotO
    cat.setYHeadRot(p.getYHeadRot)
    cat.yHeadRotO = p.yHeadRotO
    cat.hurtTime = p.hurtTime
    cat.swinging = p.swinging
    cat.swingingArm = p.swingingArm
    cat.swingTime = p.swingTime
    cat.oAttackAnim = p.oAttackAnim
    cat.attackAnim = p.attackAnim
    val catWalk = cat.walkAnimation.asInstanceOf[hexic.mixin.LimbAnimatorAccess]
    val playerWalk = p.walkAnimation.asInstanceOf[hexic.mixin.LimbAnimatorAccess]
    catWalk.prevSpeed = playerWalk.prevSpeed
    catWalk.speed = playerWalk.speed
    catWalk.pos = playerWalk.pos
    cat.asInstanceOf[hexic.mixin.CatAccess].hexic$setCollarColor(collarColor)
    if p.getPose == Pose.CROUCHING then
      cat.setInSittingPose(true)
      cat.setPose(Pose.STANDING)
    else
      cat.setInSittingPose(false)
      cat.setPose(p.getPose)

  def cat = p.catCollarColor.map(_ => realCat)
  def syncAndGetCat() = p.catCollarColor.map { c => syncCat(c); realCat }
  def catOrNull: Cat | Null = cat.orNull

  override def readFromNbt(nbtCompound: CompoundTag): Unit = ()
  override def writeToNbt(nbtCompound: CompoundTag): Unit = ()
object CatHolder:
  given key: ComponentKey[CatHolder] = ComponentKey("cat", ComponentCopyPolicy.Never):
    case p: Player => new CatHolder(p)
    case owner => throw IllegalArgumentException(s"CatHolder requires Player owner, got ${owner.getClass.getName}")
  def apply(p: Player) = p.getComponent(key)
  @static def getCat(e: Entity): Cat | Null = e match
    case p: Player => CatHolder(p).cat.orNull
    case _ => null
  @static def getSyncedCat(e: Entity): Cat | Null = e match
    case p: Player => CatHolder(p).syncAndGetCat().orNull
    case _ => null

given Conversion[WalkAnimationState, hexic.mixin.LimbAnimatorAccess] = _.asInstanceOf
package mixin:
  import org.spongepowered.asm.mixin.Mixin
  import org.spongepowered.asm.mixin.gen.Accessor
  @Mixin(value = Array(classOf[WalkAnimationState]))
  private[hexic] trait LimbAnimatorAccess:
    this: WalkAnimationState =>
    @targetName("hexic$getPrevSpeed") @Accessor("speedOld") private[hexic] def prevSpeed: Float
    @targetName("hexic$getSpeed") @Accessor("speed") private[hexic] def speed: Float
    @targetName("hexic$getPos") @Accessor("position") private[hexic] def pos: Float
    @targetName("hexic$setPrevSpeed") @Accessor("speedOld") private[hexic] def prevSpeed_=(prevSpeed: Float): Unit
    @targetName("hexic$setSpeed") @Accessor("speed") private[hexic] def speed_=(speed: Float): Unit
    @targetName("hexic$setPos") @Accessor("position") private[hexic] def pos_=(pos: Float): Unit

extension (p: Player)
  def equippedMediaweave: Seq[(Mediaweave, ItemStack)] =
    CuriosCompat.equippedStacks(p).collect:
      case stack if stack.getItem.isInstanceOf[Mediaweave] =>
        stack.getItem.asInstanceOf[Mediaweave] -> stack
  def validMediaweave: Option[(Mediaweave, ItemStack, ServerLevel ?=> Iota)] =
    p.equippedMediaweave.collectFirst:
      Function.unlift:
        case (item, stack) =>
          Option(item.readIotaTag(stack)).map: tag =>
            val loader: ServerLevel ?=> Iota =
              org.eu.net.pool.phlib.deserialize(tag, summon[ServerLevel]).asInstanceOf[Iota]
            (item, stack, loader)
  def catCollarColor: Option[DyeColor] =
    p.equippedMediaweave.collectFirst:
      case (item, stack) if stack.has(DataComponents.CUSTOM_NAME) && stack.getHoverName.getString.equalsIgnoreCase("instant cat") => item.color
extension (p: ServerPlayer)
  def executeMediaweave(text: String, ctx: Seq[Iota]): Boolean =
    p.validMediaweave match
      case Some(item, stack, hex) =>
        given world: ServerLevel = p.serverLevel()
        lazy val env = new PlayerBasedCastEnv(p, InteractionHand.OFF_HAND):
          override def extractMediaEnvironment(cost: Long, simulate: Boolean): Long =
            if p.isCreative then 0L else extractMediaFromInventory(cost, canOvercast, simulate)
          override def getCastingHand: InteractionHand = castingHand
          override def getPigment: FrozenPigment = hexXplat.getPigment(p)
        val image = CastingImage(TreeList.from((ctx :+ StringIota.make(text)).asJava), 0, TreeList.empty(), false, false, 0, CompoundTag())
        val instrs = hex match
          case list: ListIota => list.getList.asScala.toSeq
          case iota => Seq(iota)
        val vm = CastingVM(image, env)
        val view = vm.queueExecuteAndWrapIotas((instrs :+ ContinuationIota(SpellContinuation.NotDone(MessageFrame(p.getUUID, stack.getHoverName, p), SpellContinuation.Done.INSTANCE))).asJava, world)
        true
      case _ => false
object MessageFrame extends ContinuationFrame.Type[MessageFrame]:
  private val messageFrameCodec: MapCodec[MessageFrame] =
    RecordCodecBuilder.mapCodec[MessageFrame]: instance =>
      instance.group(
        UUIDUtil.CODEC.fieldOf("player").forGetter((frame: MessageFrame) => frame.id),
        ComponentSerialization.CODEC.fieldOf("text").forGetter((frame: MessageFrame) => frame.text)
      ).apply(instance, (id: UUID, text: ChatComponent) => MessageFrame(id, text, null))
  override def codec(): MapCodec[MessageFrame] = messageFrameCodec
  override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, MessageFrame] =
    ByteBufCodecs.fromCodecWithRegistries(messageFrameCodec.codec())
class MessageFrame(val id: UUID, val text: ChatComponent, player0: => ServerPlayer) extends ContinuationFrame:
  private def player(world: ServerLevel): ServerPlayer =
    Option(player0).getOrElse(world.getServer.getPlayerList.getPlayer(id))
  override def getType: ContinuationFrame.Type[MessageFrame] = MessageFrame
  override def evaluate(rest: SpellContinuation, world: ServerLevel, vm: CastingVM): CastResult =
    boundary:
      def mishap(m: Mishap) = boundary.break(CastResult(NullIota(), rest, vm.getImage, Seq(DoMishap(m, Mishap.Context(null, text))), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE.get()))
      vm.getImage.getStack.toSeq.reverse match
        case Seq() =>
          mishap(MishapNotEnoughArgs(1, 0))
        case Seq(s: StringIota, stack*) =>
          CastResult(NullIota(), rest, vm.getImage.withStack(_ => stack), Seq(
            OperatorSideEffect.AttemptSpell(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  NetworkCompat.sendString(player(world), "msg", s.getString)
                override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
              , false, false
            )
          ), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE.get())
        case Seq(i, _*) =>
          mishap(MishapInvalidIota.ofType(i, 0, "string"))
  override def breakDownwards(stack: TreeList[Iota]): Pair[java.lang.Boolean, TreeList[Iota]] =
    Pair(java.lang.Boolean.FALSE, stack)
  override def size = 0

object hasComponent:
  def unapply[C <: HexComponent: ComponentKey](ctx: AnyRef): Option[C] =
    ComponentStore.maybe(ctx, summon[ComponentKey[C]])

def initChat() =
  Patterns.register("reveal", ne"deqed" ):
    Patterns.mkConstAction(1, 0):
      case Seq(iota: Iota) =>
        locally(summon[CastingEnvironment]).getCastingEntity match
          case null => throw MishapBadCaster()
          case p: ServerPlayer =>
            p.component[RevealComponent].lines = iota match
              case s: ListIota => s.getList.map(_.display).toSeq
              //case m: MapIota => m.map.toSeq.map(p => IotaType.getDisplay(p._1) -> IotaType.getDisplay(p._2)).sortBy(_._1.getString)
              case _: NullIota => Seq()
              case _ => Seq(iota.display)
            p.syncComponent[RevealComponent]()
            Seq()
          case _ => throw MishapBadCaster()
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral: (env, _) ?=>
      hasComponent.unapply[MurmurCache](env.getCastingEntity).fold(throw MishapBadCaster())(_.value.fold(NullIota())(StringIota.make))
  hexXplat.getContinuationTypeRegistry("send_message") = MessageFrame
  NetworkCompat.registerServerReceiver("murmur"): (player, buf) =>
    player.component[MurmurCache].value = Option.when(buf.readBoolean())(buf.readUtf())
  NetworkCompat.registerServerReceiver("message"): (player, buf) =>
    val context = buf.readByte()
    if context != 0 then
      throw IllegalArgumentException("Nonzero context is reserved for future use")
    val text = buf.readUtf()
    player.executeMediaweave(text, Seq())
