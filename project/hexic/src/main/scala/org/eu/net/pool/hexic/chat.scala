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
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import dev.emi.trinkets.api.{TrinketComponent, TrinketsApi}
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentAccess, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.entity.{EntityComponentFactoryRegistry, EntityComponentInitializer, RespawnCopyStrategy}
import net.fabricmc.fabric.api.networking.v1.{PacketByteBufs, ServerPlayNetworking}
import net.minecraft.entity.{Entity, EntityPose, EntityType, LimbAnimator}
import net.minecraft.entity.passive.CatEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NbtCompound, NbtElement, NbtOps}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.{DyeColor, Hand, Identifier, Pair, Util, Uuids}
import org.eu.net.pool.phlib.{Events as PhEvents, *, given}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.moreiotas.api.casting.iota.StringIota

import java.util.UUID
import scala.annotation.{meta, static, targetName}
import scala.collection.immutable.*
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.boundary

private[hexic] case class MurmurCache(var value: Option[String]) extends Component:
  override def readFromNbt(tag: NbtCompound): Unit =
    if tag.getBoolean("active") then
      value = Some(tag.getString("value"))
    else
      value = None
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putBoolean("active", false)
    for value <- value do
      tag.putBoolean("active", true)
      tag.putString("value", value)
private[hexic] object MurmurCache:
  given ComponentKey[MurmurCache] = ComponentRegistry.getOrCreate("murmur", classOf[MurmurCache])

private[hexic] case class RevealComponent(var lines: Seq[Text]) extends AutoSyncedComponent:
  override def readFromNbt(tag: NbtCompound): Unit =
    lines = for n <- 0 until tag.getInt("lineCount") yield Text.Serializer.fromJson(tag(s"line$n")) // nbt is json apparently?
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putInt("lineCount", lines.size)
    for (line, n) <- lines.zipWithIndex do tag(s"line$n") = Text.Serializer.toJsonTree(line)
private[hexic] object RevealComponent:
  given ComponentKey[RevealComponent] = ComponentRegistry.getOrCreate("reveal", classOf[RevealComponent])

def keyOf[C <: Component: ComponentKey as key] = key

extension (c: ComponentAccess)
  def component[C <: Component: ComponentKey as key]: C = c.getComponent(key)
  def syncComponent[C <: Component: ComponentKey as key](): Unit = c.syncComponent(key)

// rephrased from trickster
class CatHolder private[hexic] (p: PlayerEntity) extends Component:
  lazy private val realCat =
    val cat = CatEntity(EntityType.CAT, p.getWorld)
    cat.setAiDisabled(true)
    cat.setInvulnerable(true)
    cat.setTamed(true)
    cat
  def syncCat(collarColor: DyeColor) =
    val cat = this.realCat
    cat.setYaw(p.getYaw)
    cat.prevYaw = p.prevYaw
    cat.setPitch(p.getPitch)
    cat.prevPitch = p.prevPitch
    cat.setPos(p.getX, p.getY, p.getZ)
    cat.prevX = p.prevX
    cat.prevY = p.prevY
    cat.prevZ = p.prevZ
    cat.setBodyYaw(p.bodyYaw)
    cat.prevBodyYaw = p.prevBodyYaw
    cat.setHeadYaw(p.headYaw)
    cat.prevHeadYaw = p.prevHeadYaw
    cat.hurtTime = p.hurtTime
    cat.handSwinging = p.handSwinging
    cat.handSwingTicks = p.handSwingTicks
    cat.handSwingProgress = p.handSwingProgress
    cat.lastHandSwingProgress = p.lastHandSwingProgress
    cat.limbAnimator.prevSpeed = p.limbAnimator.prevSpeed
    cat.limbAnimator.speed = p.limbAnimator.speed
    cat.limbAnimator.pos = p.limbAnimator.pos
    cat.setCollarColor(collarColor)
    if p.getPose == EntityPose.CROUCHING then
      cat.setInSittingPose(true)
      cat.setPose(EntityPose.STANDING)
    else
      cat.setInSittingPose(false)
      cat.setPose(p.getPose)

  def cat = p.catCollarColor.map(_ => realCat)
  def syncAndGetCat() = p.catCollarColor.map { c => syncCat(c); realCat }
  def catOrNull: CatEntity | Null = cat.orNull

  override def readFromNbt(nbtCompound: NbtCompound): Unit = ()
  override def writeToNbt(nbtCompound: NbtCompound): Unit = ()
object CatHolder:
  given key: ComponentKey[CatHolder] = ComponentRegistry.getOrCreate("cat", classOf[CatHolder])
  def apply(p: PlayerEntity) = p.getComponent(key)
  @static def getCat(e: Entity): CatEntity | Null = e match
    case p: PlayerEntity => CatHolder(p).cat.orNull
    case _ => null
  @static def getSyncedCat(e: Entity): CatEntity | Null = e match
    case p: PlayerEntity => CatHolder(p).syncAndGetCat().orNull
    case _ => null

given Conversion[LimbAnimator, hexic.mixin.LimbAnimatorAccess] = _.asInstanceOf
package mixin:
  import org.spongepowered.asm.mixin.Mixin
  import org.spongepowered.asm.mixin.gen.Accessor
  @Mixin(value = Array(classOf[LimbAnimator]))
  private[hexic] trait LimbAnimatorAccess:
    this: LimbAnimator =>
    @targetName("hexic$getPrevSpeed") @Accessor("prevSpeed") private[hexic] def prevSpeed: Float
    @targetName("hexic$getSpeed") @Accessor("speed") private[hexic] def speed: Float
    @targetName("hexic$getPos") @Accessor("pos") private[hexic] def pos: Float
    @targetName("hexic$setPrevSpeed") @Accessor("prevSpeed") private[hexic] def prevSpeed_=(prevSpeed: Float): Unit
    @targetName("hexic$setSpeed") @Accessor("speed") private[hexic] def speed_=(speed: Float): Unit
    @targetName("hexic$setPos") @Accessor("pos") private[hexic] def pos_=(pos: Float): Unit

extension (p: PlayerEntity)
  def equippedMediaweave: Seq[(Mediaweave, ItemStack)] =
    TrinketsApi.getTrinketComponent(p)
      .pipe(o => Option.when[TrinketComponent](o.isPresent)(o.get()).toSeq)
      .flatMap: (c: TrinketComponent) =>
        c.getEquipped(_.getItem.isInstanceOf[Mediaweave]).asScala.collect:
          Function.unlift: e =>
            e.getRight.getItem match
              case m: Mediaweave => Some(m, e.getRight)
              case _ => None
  def validMediaweave: Option[(Mediaweave, ItemStack, ServerWorld ?=> Iota)] =
    p.equippedMediaweave.collectFirst:
      Function.unlift:
        case (item, stack) => Option(item.readIotaTag(stack)).map(tag => (item, stack, IotaType.deserialize(tag, summon[ServerWorld])))
  def catCollarColor: Option[DyeColor] =
    p.equippedMediaweave.collectFirst:
      case (item, stack) if stack.hasCustomName && stack.getName.getString.toLowerCase == "instant cat" => item.color
extension (p: ServerPlayerEntity)
  def executeMediaweave(text: String, ctx: Seq[Iota]): Boolean =
    p.validMediaweave match
      case Some(item, stack, hex) =>
        given world: ServerWorld = p.getWorld.asInstanceOf[ServerWorld]
        lazy val env = new PlayerBasedCastEnv(p, Hand.OFF_HAND):
          override def extractMediaEnvironment(cost: Long, simulate: Boolean): Long =
            if p.isCreative then 0L else extractMediaFromInventory(cost, canOvercast, simulate)
          override def getCastingHand: Hand = castingHand
          override def getPigment = FrozenPigment(ItemStack(HexItems.DYE_PIGMENTS.get(item.color)), Util.NIL_UUID)
        val image = CastingImage(ctx :+ StringIota.make(text), 0, Seq(), false, 0, NbtCompound(), null)
        val instrs = hex match
          case list: ListIota => list.getList.asScala.toSeq
          case iota => Seq(iota)
        val vm = CastingVM(image, env)
        val view = vm.queueExecuteAndWrapIotas(instrs :+ ContinuationIota(SpellContinuation.NotDone(MessageFrame(p.getUuid, stack.getName, p), SpellContinuation.Done.INSTANCE)), world)
        true
      case _ => false
object MessageFrame extends ContinuationFrame.Type[MessageFrame]:
  override def deserializeFromNBT(c: NbtCompound, world: ServerWorld): MessageFrame =
    val id = Uuids.toUuid(c.getIntArray("id"))
    MessageFrame(id, Text.Serializer.fromJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, c.getCompound("t"))), world.getServer.getPlayerManager.getPlayer(id))
class MessageFrame(id: UUID, text: Text, player: => ServerPlayerEntity) extends ContinuationFrame:
  override def getType: ContinuationFrame.Type[MessageFrame] = MessageFrame
  override def evaluate(rest: SpellContinuation, world: ServerWorld, vm: CastingVM): CastResult =
    boundary:
      def mishap(m: Mishap) = boundary.break(CastResult(NullIota(), rest, vm.getImage, Seq(DoMishap(m, Mishap.Context(null, text))), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE))
      vm.getImage.getStack.toSeq.reverse match
        case Seq() =>
          mishap(MishapNotEnoughArgs(1, 0))
        case Seq(s: StringIota, stack*) =>
          CastResult(NullIota(), rest, vm.getImage.withStack(_ => stack), Seq(
            OperatorSideEffect.AttemptSpell(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  ServerPlayNetworking.send(player, "msg", PacketByteBufs.create.tap(_.writeString(s.getString)))
                override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
              , false, false
            )
          ), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE)
        case Seq(i, _*) =>
          mishap(MishapInvalidIota.ofType(i, 0, "string"))
  override def serializeToNBT(): NbtCompound = NbtCompound()
    .tap(_.putIntArray("id", Uuids.toIntArray(id)))
    .tap(_.put("t", JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, Text.Serializer.toJsonTree(text))))
  override def breakDownwards(stack: java.util.List[? <: Iota]): kotlin.Pair[java.lang.Boolean, java.util.List[Iota]] = kotlin.Pair(false, stack.toSeq)
  override def size = 0

object hasComponent:
  def unapply[C <: Component: ComponentKey](ctx: ComponentAccess): Option[C] =
    try
      Some(ctx.component[C])
    catch case _: NoSuchElementException =>
      None

def initChat() =
  Patterns.register("reveal", ne"deqed" ):
    Patterns.mkConstAction(1, 0):
      case Seq(iota: Iota) =>
        locally(summon[CastingEnvironment]).getCastingEntity match
          case null => throw MishapBadCaster()
          case p: ServerPlayerEntity =>
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
  ServerPlayNetworking.registerGlobalReceiver("murmur", { case (_, hasComponent[MurmurCache](c), _, buf, _) => c.value = Option.when(buf.readBoolean())(buf.readString()) }: ServerPlayNetworking.PlayChannelHandler)
  ServerPlayNetworking.registerGlobalReceiver("message", (_, player, _, buf, _) =>
    val context = buf.readByte()
    if context != 0 then
      throw IllegalArgumentException("Nonzero context is reserved for future use")
    val text = buf.readString()
    player.executeMediaweave(text, Seq())
  )