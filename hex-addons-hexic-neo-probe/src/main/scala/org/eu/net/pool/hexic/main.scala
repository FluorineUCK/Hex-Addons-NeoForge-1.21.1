//noinspection NotImplementedCode
package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.addldata.{ADIotaHolder, ADMediaHolder}
import at.petrak.hexcasting.api.casting.{ActionRegistryEntry, OperatorUtils, ParticleSpray, RenderedSpell}
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.engine.InvalidOperatorException
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.arithmetic.predicates.{IotaMultiPredicate, IotaPredicate}
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction, OperationAction, SpecialHandler, SpellAction}
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect.DoMishap
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, ContinuationFrame, FrameEvaluate, FrameFinishEval, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, CastingEnvironmentComponent, MishapEnvironment, OperationResult, ResolvedPattern, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapBadBlock, MishapBadCaster, MishapBadEntity, MishapInternalException, MishapInvalidIota, MishapInvalidOperatorArgs, MishapNeedsParens, MishapNotEnoughArgs, MishapOthersName, MishapStackSize, MishapBadOffhandItem as MishapBadOffpawItem}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.{HexUtils, MediaHelper, TreeList}
import at.petrak.hexcasting.common.lib.{HexAttributes, HexDataComponents, HexItems, HexRegistries, HexSounds}
import at.petrak.hexcasting.common.lib.hex.{HexEvalSounds, HexIotaTypes}
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.google.gson.{JsonElement, JsonObject}
import com.ibm.icu.util.MeasureUnit
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.{ArgumentType, StringArgumentType}
import com.mojang.brigadier.builder.{ArgumentBuilder, LiteralArgumentBuilder, RequiredArgumentBuilder}
import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.{Codec, DynamicOps, JsonOps, Lifecycle, MapCodec}
import com.samsthenerd.inline.api.data.ItemInlineData
import com.sun.nio.file.ExtendedOpenOption
import kotlin.Pair
import kotlin.text.Charsets
import miyucomics.hexcellular.{PropertyIota, StateStorage}
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.{Block, RenderShape, BaseEntityBlock, Blocks, DispenserBlock}
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.shapes.{CollisionContext}
import net.minecraft.commands.arguments.{EntityArgument, NbtTagArgument, UuidArgument}
import net.minecraft.commands.arguments.selector.{EntitySelector}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.{WorldlyContainer}
import net.minecraft.world.entity.{SlotAccess}
import net.minecraft.world.item.{BlockItem, Item, ItemStack, Items}
import net.minecraft.world.item.context.{UseOnContext}
import net.minecraft.nbt.*
import net.minecraft.nbt.StringTagVisitor
import net.minecraft.tags.TagKey
import net.minecraft.core.component.DataComponents
import net.minecraft.core.{WritableRegistry, MappedRegistry}
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.resources.{ResourceKey}
import net.minecraft.server.MinecraftServer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.{ServerPlayer}
import net.minecraft.server.network.{ServerGamePacketListenerImpl}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents
import net.minecraft.network.chat.{HoverEvent, MutableComponent, Style, Component, TextColor, ComponentContents, ComponentUtils}
import net.minecraft.util.ExtraCodecs
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.dispenser.{BlockSource}
import net.minecraft.util.{FastColor, Mth}
import net.minecraft.world.level.{ChunkPos}
import net.minecraft.world.phys.{Vec3}
import net.minecraft.{ChatFormatting, Util}
import net.minecraft.core.{UUIDUtil}
import net.minecraft.resources.{ResourceLocation}
import net.minecraft.world.{InteractionResult, InteractionResultHolder, ItemInteractionResult, InteractionHand as Paw}
import net.minecraft.world.entity.{HumanoidArm}
import net.minecraft.world.inventory.{ClickAction}
import net.minecraft.world.item.{DyeColor, Rarity}
import net.minecraft.world.level.storage.{LevelResource}
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.portal.{DimensionTransition}
import org.eu.net.pool.hexic
import org.objectweb.asm.{ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, InsnList}
import org.slf4j.{Logger, LoggerFactory}
import org.spongepowered.asm.mixin.injection.callback.{CallbackInfo, CallbackInfoReturnable}
import org.eu.net.pool.hexic.hexcompat.StringIota
import org.eu.net.pool.hexic.hexcompat.HexicalCompat
import net.beholderface.oneironaut.casting.iotatypes.DimIota

import java.io.{File, FileNotFoundException, FileOutputStream, IOException, InputStream}
import java.lang.invoke.MethodHandles
import java.lang.reflect.{Constructor, Field, Member, Method}
import java.nio.ByteBuffer
import java.nio.file.{FileSystemException, Files, Path, Paths, StandardOpenOption}
import java.util.{Optional, UUID}
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{elidable, experimental, showAsInfix, static, tailrec, targetName, unused}
import scala.ref.WeakReference
import scala.util.{Failure, NotGiven, Random, Success, Try, TupledFunction, Using, boundary}
import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.language.experimental.{macros, saferExceptions}
import scala.language.{dynamics, existentials, implicitConversions, postfixOps, reflectiveCalls}
import scala.reflect.{ClassTag, classTag}
import at.petrak.hexcasting.api.casting.mishaps.Mishap.Context
import net.minecraft.core.Direction.Axis

import java.util as ju
import scala.math.Ordered.orderingToOrdered
import scala.util.CommandLineParser.FromString
import scala.util.boundary.Label
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type
import at.petrak.hexcasting.api.item.{IotaHolderItem, MediaHolderItem, PigmentItem}
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.common.items.magic.{ItemMediaHolder, ItemPackagedHex}
import at.petrak.hexcasting.common.msgs.{MsgClearSpiralPatternsS2C, MsgNewSpiralPatternsS2C, MsgOpenSpellGuiS2C}
import kotlin.jvm.internal.DefaultConstructorMarker
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.tooltip.{BundleTooltip, TooltipComponent}
import net.minecraft.sounds.{SoundSource, SoundEvents}
import net.minecraft.world.level.block.{SoundType}
import net.minecraft.core.NonNullList
import org.eu.net.pool.hexic.MediaBundle.{DUST_AMOUNT, PERCENTAGE}

import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.function.Predicate
import scala.quoted.Quotes
import java.io.Writer
import java.io.OutputStreamWriter
import net.minecraft.world.entity.animal.Fox
import net.minecraft.stats.Stats
import net.minecraft.world.item.{CreativeModeTab, TooltipFlag}
import net.minecraft.world.item.Item.TooltipContext
import net.minecraft.world.item.component.BundleContents
import org.eu.net.pool.hexic.mixin.ItemStackAccess
import org.eu.net.pool.hexic.hexcompat.{AutoSyncedHexComponent, ComponentKey, HexComponent, *, given}
import org.eu.net.pool.hexic.hexcompat.transfer.*
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.common.casting.actions.spells.{OpBreakBlock, OpErase, OpPotionEffect}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.decoration.ItemFrame
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.{Operation, WrapOperation}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityTicker, BlockEntityType}
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.{VoxelShape, Shapes}
import org.spongepowered.asm.mixin.Mixin

import scala.collection.immutable.BitSet
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType

import scala.util.matching.Regex
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage.ParenthesizedIota
import at.petrak.hexcasting.api.casting.mishaps.circle.MishapNoSpellCircle
import at.petrak.hexcasting.client.RegisterClientStuff
import at.petrak.hexcasting.common.blocks.BlockQuenchedAllay
import at.petrak.hexcasting.interop.inline.InlinePatternData
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior
import net.minecraft.world.level.chunk.{ChunkGenerator, ChunkGenerators}

import java.nio.charset.StandardCharsets
import java.math.BigInteger
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.damagesource.DamageSources
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.entity.RelativeMovement
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.level.chunk.{LevelChunk}
import net.minecraft.world.level.chunk.status.{ChunkStatus}
import org.jetbrains.annotations.Nullable
import org.eu.net.pool.hexic.hexcompat.CuriosCompat
import org.eu.net.pool.hexic.hexcompat.runtimeworld.*

import scala.concurrent.duration.Duration
import phlib.{Events as PhEvents, *, given}

import scala.util.control.TailCalls

class CommandException(message: Component) extends RuntimeException(message.getString)
object CommandException:
  def apply(message: Component): CommandException = new CommandException(message)
  def apply(message: String): CommandException = new CommandException(Component.literal(message))

private[hexic] given Logger = LoggerFactory.getLogger("hexic")
private[hexic] given Conversion[String, ResourceLocation] = ResourceLocation.fromNamespaceAndPath("hexic", _)
private[hexic] var pipelineFrameCodecProbe: () => Either[String, String] =
  () => Left("pipeline frame codecs were not initialized")

extension (i: Iota)
  def asIotaType[T <: Iota: ClassTag](idx: Int, expected: => Component): T = i match
    case i: T => i
    case _ => throw MishapInvalidIota(i, idx, expected)
  def asIotaType[T <: Iota: ClassTag: IotaType](idx: Int): T = i.asIotaType[T](idx, IotaType.brokenIota())
  def asValue[T: FromIota](idx: Int, expected: => Component): T = summon[FromIota[T]].convert(i).getOrElse(throw MishapInvalidIota(i, idx, expected))

extension (c: CompoundTag)
  def iota(using ServerLevel): Iota = org.eu.net.pool.phlib.deserialize(c, summon).asInstanceOf[Iota]

given Conversion[(HexDir, String), HexPattern] = t => HexPattern.fromAngles(t._2, t._1)

case class Box[T](var value: T)

trait Gives[C[_]]:
  type T
  given evidence: C[T]
  def value: T

given [C[_], T_](using C[T_]): Conversion[T_, Gives[C]] with
  override def apply(x: T_) : Gives[C] = new Gives[C]:
    type T = T_
    override given evidence: C[T] = summon[C[T_]]
    def value: T_ = x

trait Outcome[-T]:
  def apply(res: OperationResult, value: T): OperationResult
object Outcome:
  def apply(xs: Gives[Outcome]*): Gives[Outcome] = new Gives[Outcome]:
    type T = Seq[Gives[Outcome]]
    override given evidence: Outcome[T] = (res, values) =>
      values.foldLeft(res): (next, value) =>
        value.evidence(next, value.value)
    override def value: T = xs
  def apply[T: Outcome](xs: T*): OperationResult => OperationResult = res => res.->(xs*)
extension (op: OperationResult)
  def ->[T: Outcome](xs: T*): OperationResult =
    (op /: xs)(summon[Outcome[T]](_, _))

given Outcome[OperationResult => OperationResult] = (res, f) => f(res)
given [T: Outcome]: Outcome[Seq[T]] = (res, value) => res -> Outcome(value*)

private[hexic] object SpellmindCompat:
  private[hexic] val Key = "hexic:spellmind"

  private def userDataWithoutSavedMind(data: CompoundTag): CompoundTag =
    val copy = data.copy()
    copy.remove(Key)
    copy

  private def snapshot(image: CastingImage): CastingImage =
    image(userData = userDataWithoutSavedMind(image.getUserData))

  def save(image: CastingImage): CastingImage =
    val data = image.getUserData.copy()
    data.put(Key, castingImageToNbt(snapshot(image)))
    image(userData = data)

  def restore(image: CastingImage): Option[CastingImage] =
    if !image.getUserData.contains(Key, Tag.TAG_COMPOUND) then
      None
    else
      val savedTag = image.getUserData.getCompound(Key).copy()
      val restored = castingImageFromNbt(savedTag)
      val restoredData = restored.getUserData.copy()
      restoredData.put(Key, savedTag.copy())
      Some(restored(userData = restoredData))

  def hasSavedMind(image: CastingImage): Boolean =
    image.getUserData.contains(Key, Tag.TAG_COMPOUND)

  def noSavedMindMishap: Mishap = new Mishap:
    override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = FrozenPigment.DEFAULT.get()
    override def errorMessage(env: CastingEnvironment, ctx: Context): Component =
      Component.literal("No spellmind has been saved in the current casting image")
    override def execute(env: CastingEnvironment, ctx: Context, stack: TreeList[Iota]): TreeList[Iota] = stack

//given Outcome[Seq[Iota]]:
//  override def ->:(res: OperationResult): OperationResult = ???
//given Outcome[Iota]:
//  override def ->:

class :?[T, G](private val value: T)(using private val proof: G)
object :? :
  given wrap[T, G](using G): Conversion[T, T :? G] = x => :?(x)
  given unwrap[T, G]: Conversion[T :? G, T] = _.value
  given prove[T, G]: Conversion[(G ?=> T) :? G, T] = x => x.value(using x.proof)
  given unneeded[T, G]: Conversion[T, G ?=> T] with
    override def apply(x: T): G ?=> T = _ ?=> x
import :?.given

type :>[K, V] = Map[K, V]

sealed abstract class PropertyAccessIota(val name: String, val direction: "head" | "tail")(using initialWorld: ServerLevel) extends Iota(() => PropertyAccessIota.Type):
  private var boundWorld: ServerLevel | Null = initialWorld
  private[hexic] def bindWorld(world: ServerLevel): this.type =
    boundWorld = world
    this
  private def world: ServerLevel =
    Option(boundWorld).getOrElse(throw MishapInternalException(IllegalStateException("PropertyAccessIota was decoded before a ServerLevel was available")))
  def property: Iota = StateStorage.Companion.getProperty(world, name)
  def property_=(x: Iota): Unit = StateStorage.Companion.setProperty(world, name, x)
  def toStream(reverseIdx: Int): PropertyAccessIota.Stream
  def toWriter(reverseIdx: Int): PropertyAccessIota.Writer
  override def toleratesOther(iota: Iota): Boolean = ==(iota)
  override def display(): Component = PropertyAccessIota.Type.displayIota(this)
  def serializeTag(): CompoundTag =
    val c = CompoundTag()
    c.put("n", name)
    c
object PropertyAccessIota:
  case class Stream(override val name: String, override val direction: "head" | "tail")(using ServerLevel) extends PropertyAccessIota(name, direction) with IterableOnce[Iota]:
    override def iterator: Iterator[Iota] = new Iterator[Iota]:
      override def hasNext: Boolean = isTruthy
      override def next(): Iota = Stream.this.take()
    override def isTruthy: Boolean = property match
      case l: ListIota => l.isTruthy
      case _ => false
    def take(): Iota = property match
      case _: NullIota => NullIota()
      case l: ListIota =>
        val s = l.getList.asScala.toSeq
        if l.getList.isEmpty then
          NullIota()
        else
          direction match
            case "head" =>
              property = ListIota(s.tail)
              s.head
            case "tail" =>
              property = ListIota(s.init)
              s.last
    override def serializeTag(): CompoundTag =
      val c = super.serializeTag()
      c.put("p", direction match
        case "head" => "← "
        case "tail" => " →")
      if isDev then println(s"Stream($direction) = $c")
      c
    override def toStream(reverseIdx: Int): Stream = this
    override def toWriter(reverseIdx: Int): Writer = throw MishapInvalidIota.ofType(this, reverseIdx, "hexic:writer")
  case class Writer(override val name: String, override val direction: "head" | "tail")(using ServerLevel) extends PropertyAccessIota(name, direction):
    override def isTruthy: Boolean = true
    def <<(x: Iota): Unit = property match
      case _: NullIota =>
        property = ListIota(Seq(x))
      case l: ListIota =>
        direction match
          case "head" => property = ListIota(x +: l.getList.toSeq)
          case "tail" => property = ListIota(l.getList.toSeq :+ x)
    override def serializeTag(): CompoundTag =
      val c = super.serializeTag()
      c.put("p", direction match
        case "head" => "→ "
        case "tail" => " ←")
      if isDev then println(s"Writer($direction) = $c")
      c
    override def toStream(reverseIdx: Int): Stream = throw MishapInvalidIota.ofType(this, reverseIdx, "hexic:stream")
    override def toWriter(reverseIdx: Int): Writer = this
  object Type extends IotaType[PropertyAccessIota]:
    type A = (String, "add" | "remove", "head" | "tail")
    def split(tag: Tag): A =
      val c = tag.downcast[CompoundTag]
      val name = c.getString("n")
      c.getString("p") match
        case "→ " => (name, "add", "head")
        case "← " => (name, "remove", "head")
        case " ←" => (name, "add", "tail")
        case " →" => (name, "remove", "tail")
    def deserialize(using nbt: Tag, world: ServerLevel): PropertyAccessIota =
      val a: A = split(nbt)
      deserialize(a, world)
    private def deserialize(a: A, world: ServerLevel | Null): PropertyAccessIota =
      given ServerLevel = world.asInstanceOf[ServerLevel]
      a._2 match
        case "add" => Writer(a._1, a._3)
        case "remove" => Stream(a._1, a._3)
    def display(tag: Tag): Component =
      (split(tag) match
        case (name, "add", "head") => t"→ $name"
        case (name, "add", "tail") => t"$name ←"
        case (name, "remove", "head") => t"← $name"
        case (name, "remove", "tail") => t"$name →"
      ) formatted ChatFormatting.GREEN
    def displayIota(iota: PropertyAccessIota): Component =
      display(iota.serializeTag())
    override def codec(): MapCodec[PropertyAccessIota] =
      CompoundTag.CODEC.xmap[PropertyAccessIota](
        tag => deserialize(split(tag), null),
        _.serializeTag()
      ).fieldOf("value")
    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, PropertyAccessIota] =
      ByteBufCodecs.fromCodecWithRegistries(codec().codec())
    override def validate(iota: PropertyAccessIota, world: ServerLevel): Boolean =
      iota.bindWorld(world)
      true
    override def color: Int = PropertyIota.TYPE.color

private[hexic] object PatternRemapper:
  lazy val remappedPatterns: Map[ClassTag[?], HexPattern] =
    val file = Path.of("config/remapped_patterns.lst")
    if Files.exists(file) then
      Files.readAllLines(file).zipWithIndex.flatMap: t =>
        boundary:
          val (p, i) = t
          if p.isBlank then boundary.break(None)
          val lines = p.split(' ')
          if lines.length != 3 then
            summon[Logger].warn(s"Error on line ${i+1} of pattern remappings: expected 2 fields but got ${lines.length}")
            boundary.break(None)
          val Array(cls, dir, pat) = lines
          val tag = classNamed(cls).getOrElse:
            summon[Logger].warn(s"Error on line ${i+1} of pattern remappings: cannot find action '$cls' (make sure you use the class name, not the identifier!)")
            boundary.break(None)
          val dirValue =
            try
              HexDir.valueOf(dir)
            catch
              case e: IllegalArgumentException =>
                summon[Logger].warn(s"Error on line ${i+1} of pattern remappings: direction '$dir' is not a valid HexDir", e)
                boundary.break(None)
          val pattern =
            try
              HexPattern.fromAngles(pat, dirValue)
            catch
              case e: Exception =>
                summon[Logger].warn(s"Error on line ${i+1} of pattern remappings: anglesig '$pat' cannot be parsed", e)
                boundary.break(None)
          Some:
            (tag, pattern)
      .toMap
    else
      Map.empty

  def remap(pattern: HexPattern, action: Action) =
    remappedPatterns.collectFirst:
      case (k, v) if ClassTag(action.getClass) <:< k => v
    .getOrElse(pattern)

class PlayerInfoComponent(
  val player: Player,
  var leftWeave: ItemStack = ItemStack.EMPTY,
  var rightWeave: ItemStack = ItemStack.EMPTY,
  var foxType: Option[Fox.Type] = None,
) extends AutoSyncedHexComponent:
  override def readFromNbt(c: CompoundTag): Unit =
    if c.contains("shl", Tag.TAG_COMPOUND) then
      leftWeave = itemStackFromNbt(player.registryAccess(), c.getCompound("shl"))
    else
      leftWeave = ItemStack.EMPTY
    if c.contains("shr", Tag.TAG_COMPOUND) then
      rightWeave = itemStackFromNbt(player.registryAccess(), c.getCompound("shr"))
    else
      rightWeave = ItemStack.EMPTY
    if c.contains("fox", Tag.TAG_STRING) then
      foxType = Some(Fox.Type.valueOf(c.getString("fox")))
    else
      foxType = None
  override def writeToNbt(c: CompoundTag): Unit =
    if !leftWeave.isEmpty then c.put("shl", leftWeave.saveOptional(player.registryAccess()))
    if !rightWeave.isEmpty then c.put("shr", rightWeave.saveOptional(player.registryAccess()))
    foxType.fold(c.remove("fox"))(f => c.putString("fox", f.name))
object PlayerInfoComponent:
  given key: ComponentKey[PlayerInfoComponent] = ComponentKey("player_wisp"):
    case p: Player => PlayerInfoComponent(p)
    case owner => throw IllegalArgumentException(s"PlayerInfoComponent requires Player owner, got ${owner.getClass.getName}")
  given Conversion[Player, PlayerInfoComponent] = _.getComponent(key)

class ServerInfoComponent() extends AutoSyncedHexComponent:
  override def readFromNbt(tag: CompoundTag): Unit = ()
  override def writeToNbt(tag: CompoundTag): Unit = ()
object ServerInfoComponent:
  given key: ComponentKey[ServerInfoComponent] = ComponentKey("server_info")(_ => ServerInfoComponent())
  given get: (server: MinecraftServer) => ServerInfoComponent = server.getComponent(key)
  def sync()(using server: MinecraftServer): Unit = server.syncComponent(key)

class ExcursionComponent(var enteredDemiplaneTick: Long = 0, var excursion: Option[(ResourceKey[Level], Vec3)] = None) extends HexComponent:
  override def readFromNbt(tag: CompoundTag): Unit =
    enteredDemiplaneTick = tag.getLong("grace")
    excursion = for
      case c: StringTag <- Option(tag.get("dim"))
      id <- Option(ResourceLocation.tryParse(c.asString))
    yield (ResourceKey.create(Registries.DIMENSION, id), Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")))
  override def writeToNbt(tag: CompoundTag): Unit =
    tag.putLong("grace", enteredDemiplaneTick)
    for world -> pos <- excursion do
      tag.putString("dim", world.getValue.toString)
      tag.putDouble("x", pos.getX)
      tag.putDouble("y", pos.getY)
      tag.putDouble("z", pos.getZ)
object ExcursionComponent:
  given key: ComponentKey[ExcursionComponent] = ComponentKey("excursion", ComponentCopyPolicy.Always)(_ => ExcursionComponent())

extension [S, T <: ArgumentBuilder[S, T]] (builder: T)
  def literal(name: String)(body: LiteralArgumentBuilder[S] => Unit): T = builder.`then`(LiteralArgumentBuilder.literal[S](name).tap(body))
  def argument(name: String, typ: ArgumentType[?])(body: RequiredArgumentBuilder[S, ?] => Unit): T = builder.`then`(RequiredArgumentBuilder.argument(name, typ).tap(body))

extension (i: InputStream)
  def onDisk(prefix: String, suffix: String): Path =
    val tmpFile = Files.createTempFile(prefix, suffix)
    tmpFile.toFile.deleteOnExit()
    i.transferTo(FileOutputStream(tmpFile.toFile))
    tmpFile

lazy val kuboExe: Option[Path] = boundary:
  val os = Util.getPlatform match
    case Util.OS.LINUX => "linux"
    case Util.OS.WINDOWS => "windows"
    case Util.OS.OSX => "darwin"
    case o =>
      summon[Logger].warn(s"Unsupported operating system: $o")
      boundary.break(None)
  val arch = System.getProperty("os.arch").toLowerCase match
    case a if a.contains("amd64") || a.contains("x86_64") => "amd64"
    case a if a.contains("aarch64") || a.contains("arm64") => "arm64"
    case a if a.contains("x86") || a.contains("i386") => "386"
    case a =>
      summon[Logger].warn(s"Unsupported architecture: $a")
      boundary.break(None)
  val tmpFile = Files.createTempFile("ipfs", ".exe")
  tmpFile.toFile.deleteOnExit()
  try
    tmpFile.getClass.getResourceAsStream(s"vendor/hexic/ipfs.$os.$arch.exe").transferTo(FileOutputStream(tmpFile.toFile))
    Some(tmpFile)
  catch
    case e: Exception =>
      summon[Logger].warn("Failed to extract kubo executable", e)
      Files.deleteIfExists(tmpFile)
      boundary.break(None)

lazy val kuboConfigTemplate: Path = ().getClass.getResourceAsStream(s"assets/hexic/kubo-config.json").onDisk("config", ".json")

def startKubo(path: File)(using CanThrow[IOException]): Unit =
  ProcessBuilder()
  `directory` path
  `redirectOutput` ProcessBuilder.Redirect.INHERIT
  `redirectError` ProcessBuilder.Redirect.INHERIT
  `tap` (_.environment.put("IPFS_PATH", path.getAbsolutePath))
  `command` (kuboExe.get.toAbsolutePath.toString, "daemon", "--init=true", s"--init-config=${kuboConfigTemplate.toAbsolutePath}", "--migrate=true", "--enable-gc=true")
  `pipe` (_.start)
  `ensuring` (_.supportsNormalTermination)
  `tap` locally: p =>
    p.onExit.thenAccept: p =>
      if p.exitValue != 0 then sys.exit(p.exitValue)
    sys.runtime.addShutdownHook:
      Thread:
        locally: () =>
          p.destroy()
          p.waitFor()
        : Runnable

given [T <: java.lang.Enum[T]: ClassTag as ct] => FromString[T]:
  override def fromString(s: String): T = Enum.valueOf[T](ct.runtimeClass.asInstanceOf[Class[T]], s)

case class Pen private [hexic] (color: DyeColor) extends Item(Item.Properties().stacksTo(1)) with Registered(BuiltInRegistries.ITEM, s"pen/$color"):
  override def toString = s"$getClass(color=$color)${super[Item].toString}"
  override def use(world: Level, player: Player, paw: Paw): InteractionResultHolder[ItemStack] =
    // if player.getAttributeValue(HexAttributes.FEEBLE_MIND) > 0.0 then
    //   InteractionResultHolder.fail(player.getStackInHand(paw))
    // else
      if !world.isClient && player.isInstanceOf[ServerPlayer] then
        val serverPlayer: ServerPlayer = player.asInstanceOf[ServerPlayer]
        val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, paw)
        val image = vm.getImage
        val patterns = IXplatAbstractions.INSTANCE.getPatternsSavedInUi(serverPlayer)
        IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer, new MsgOpenSpellGuiS2C(paw, patterns, image.getStack, image.ravenmind.orElse(null), image.getParenCount))
      player.incrementStat(Stats.ITEM_USED.get(this))
      InteractionResultHolder.success(player.getStackInHand(paw))
object Pen:
  val instances: DyeColor :> Pen = DyeColor.values.map(c => c -> new Pen(c)).toMap

trait PenAccess:
  def getPen(color: DyeColor): util.List[HexPattern]

case class Mediaweave(color: DyeColor) extends Item(Item.Properties()) with IotaHolderItem:
  def readIotaTag(stack: ItemStack): CompoundTag | Null =
    stack.getNbt match
      case null => null
      case c => c.get("Hex") match
        case c: CompoundTag => c
        case _ => null
  override def readIota(stack: ItemStack): Iota =
    Option(readIotaTag(stack)).flatMap(t => Option(deserializeIota(t))).orNull
  override def writeable(stack: ItemStack): Boolean = true
  override def canWrite(stack: ItemStack, iota: Iota): Boolean =
    iota match
      case null => true
      case l: ListIota => true
      case _ => iota.executable
  override def writeDatum(stack: ItemStack, iota: Iota): Unit =
    iota match
      case null =>
        for nbt <- Option(stack.getNbt) do
          nbt.remove("Hex")
          if nbt.isEmpty then stack.setNbt(null)
      case iota => stack.getOrCreateNbt().put("Hex", serializeIota(iota))
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit =
    IotaHolderItem.appendHoverText(this, stack, tooltip, flag)
  DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior:
    override protected def execute(pointer: BlockSource, stack: ItemStack): ItemStack =
      val facing: Direction = pointer.getBlockState.getValue(DispenserBlock.FACING)
      val pos = pointer.getPos.offset(facing)
      val candidates = pointer.getWorld.getEntitiesByClass(classOf[LivingEntity], net.minecraft.world.phys.AABB(pos), net.minecraft.world.entity.EntitySelector.NO_SPECTATORS)
      if candidates.asScala.exists(CuriosCompat.insertOneIntoFirstEmptySlot(_, stack)) then
        stack
      else
        super.execute(pointer, stack)
    )
  CuriosCompat.registerLockingItem(this)
object Mediaweave:
  val colors: DyeColor :> Mediaweave = DyeColor.values().map(c => c -> Mediaweave(c)).toMap
  val tag: TagKey[Item] = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("hexic", "mediaweaves"))

extension (x: Iterable[Boolean])
  def any: Boolean = x.exists(identity)
  def all: Boolean = x.forall(identity)

object ItemStackAccess:
  def unapply(s: ItemStack): Some[(Item, Int, Option[CompoundTag])] = Some((s.getItem, s.getCount, Option(s.getNbt)))

case class MediaBundle(color: DyeColor, size: Int) extends Item(Item.Properties().stacksTo(1)) with MediaHolderItem:
  extension (stack: ItemStack)
    private def heldItems: Seq[ItemStack] =
      Option(stack.get(DataComponents.BUNDLE_CONTENTS))
        .map(_.itemsCopy().asScala.toSeq)
        .orElse:
          Option(stack.getNbt)
            .map(_.get("Contents"))
            .collect:
              case list: ListTag =>
                list.collect:
                  case c: CompoundTag => itemStackFromNbt(c)
                .filterNot(_.isEmpty)
                .toSeq
        .getOrElse(Seq(ItemStack(Items.AMETHYST_SHARD)))
    private def heldItems_=(x: Seq[ItemStack]): Unit =
      val stored = x.filterNot(_.isEmpty).map(_.copy())
      stack.set(DataComponents.BUNDLE_CONTENTS, BundleContents(stored.asJava))
      Option(stack.getNbt).foreach: root =>
        root.remove("Contents")
        stack.setNbt(root)
    private def isWaxed = Option(stack.getNbt).exists(_.contains("ro"))
    private def isWaxed_=(value: Boolean) =
      if value then
        stack.getOrCreateNbt().put("ro", CompoundTag())
      else
        stack.getOrCreateNbt().remove("ro")
    private def withMediaHolders[T](f: Seq[ADMediaHolder] => T): T =
      if stack.isWaxed then
        f(Seq())
      else
        val heldItems = stack.heldItems
        try
          f(heldItems.flatMap(p => Option(hexXplat.findMediaHolder(p))))
        finally
          stack.heldItems = heldItems
    private def mediaHolders = stack.heldItems.flatMap(p => Option(hexXplat.findMediaHolder(p)))
  override def getMedia(stack: ItemStack): Long = stack.mediaHolders.map(_.getMedia).sum
  override def getMaxMedia(stack: ItemStack): Long = stack.mediaHolders.map(_.getMaxMedia).sum
  override def setMedia(staeck: ItemStack, media: Long): Unit = throw IllegalCallerException()
  override def canProvideMedia(stack: ItemStack): Boolean = stack.mediaHolders.exists(_.canProvide)
  override def canRecharge(stack: ItemStack): Boolean = stack.mediaHolders.exists(_.canRecharge)
  override def insertMedia(stack: ItemStack, amount: Long, simulate: Boolean): Long =
    stack.withMediaHolders: h =>
      var total: Long = 0
      val priorityGroups =
        h.groupBy(_.getConsumptionPriority).toSeq.sortBy(_._1).reverse
      var groupIndex = 0
      while groupIndex < priorityGroups.size && total < amount do
        val holders = priorityGroups(groupIndex)._2
        var rem = holders
        while rem.nonEmpty && total < amount do
          val cur = rem.head
          val ext = cur.insertMedia((amount - total) / rem.size, simulate)
          total += ext
          rem = rem.tail
        groupIndex += 1
      total
  override def withdrawMedia(stack: ItemStack, amount: Long, simulate: Boolean): Long =
    stack.withMediaHolders: h =>
      var total: Long = 0
      val priorityGroups =
        h.groupBy(_.getConsumptionPriority).toSeq.sortBy(_._1).reverse
      var groupIndex = 0
      while groupIndex < priorityGroups.size && total < amount do
        val holders = priorityGroups(groupIndex)._2
        var rem = holders
        while rem.nonEmpty && total < amount do
          val cur = rem.head
          val ext = cur.withdrawMedia((amount - total) / rem.size, simulate)
          total += ext
          rem = rem.tail
        groupIndex += 1
      if total > amount then
        total -= insertMedia(stack, total - amount, simulate)
      total
  override def overrideOtherStackedOnMe(stack: ItemStack, otherStack: ItemStack, slot: Slot, clickType: ClickAction, player: Player, cursorStackReference: SlotAccess): Boolean =
    if clickType == ClickAction.SECONDARY then
      if otherStack.isEmpty then
        val held = stack.heldItems
        held.headOption.foreach: p =>
          cursorStackReference.set(p)
          stack.heldItems = held.tail
          player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if otherStack.isOf(Items.HONEYCOMB) && !stack.isWaxed then
        stack.isWaxed = true
        otherStack.decrement(1)
        player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if otherStack.isOf(Items.WET_SPONGE) && stack.isWaxed then
        stack.isWaxed = false
        player.playSound(SoundEvents.SLIME_BLOCK_PLACE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if hexXplat.findMediaHolder(otherStack) != null then
        val held = stack.heldItems
        if fits(held, otherStack.getItem) then
          stack.heldItems = otherStack.copyAndEmpty() +: held
          player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      true
    else
      false
  private def fits(held: Seq[ItemStack], subj: Item): Boolean =
    val cur = held.map(_.getItem match { case b: MediaBundle => b.size/2; case _ => 1 }).sum
    subj match
      case MediaBundle(_, subjSize) => subjSize < size && cur + subjSize/2 <= size
      case _ => cur < size
  override def overrideStackedOnOther(stack: ItemStack, slot: Slot, clickType: ClickAction, player: Player): Boolean =
    if clickType == ClickAction.SECONDARY then
      if slot.getStack.isEmpty then
        val held = stack.heldItems
        held.headOption.foreach: p =>
          slot.setStack(p)
          stack.heldItems = held.tail
          player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if hexXplat.findMediaHolder(slot.getStack) != null then
        val held = stack.heldItems
        if fits(held, slot.getStack.getItem) then
          stack.heldItems = slot.getStack.copyAndEmpty() +: held
          player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      true
    else
      false
  override def getTooltipImage(stack: ItemStack): Optional[TooltipComponent] =
    Optional.of(BundleTooltip(BundleContents(stack.heldItems.asJava)))
  private type M = (Option[Long], Option[(Long, Long)], Option[(Long, Long)])
  protected def getMediaInfo(stack: ItemStack): M =
    val (recursive, nonrecursive) = stack.heldItems.partitionMap:
      case s@ItemStackAccess(b: MediaBundle, _, _) => Left(b.getMediaInfo(s))
      case s => Right(Option(hexXplat.findMediaHolder(s)))
    val (canProvide, cantProvide) = nonrecursive.flatten.partition(_.canProvide)
    val (canRecharge, consumables) = canProvide.partition(_.canRecharge)
    val mine: M = (Option.when(consumables.nonEmpty)(consumables.map(_.getMedia).sum), Option.when(canRecharge.nonEmpty)((canRecharge.map(_.getMedia).sum, canRecharge.map(_.getMaxMedia).sum)), Option.when(cantProvide.nonEmpty)((cantProvide.map(_.getMedia).sum, cantProvide.map(_.getMaxMedia).sum)))
    (mine /: recursive)((p: M, q: M) => (
      (p._1, q._1) match
        case (None, None) => None
        case (Some(x), None) => Some(x)
        case (None, Some(x)) => Some(x)
        case (Some(x), Some(y)) => Some(x + y),
      (p._2, q._2) match
        case (None, None) => None
        case (Some(x), None) => Some(x)
        case (None, Some(x)) => Some(x)
        case (Some(x), Some(y)) => Some((x._1 + y._1, x._2 + y._2)),
      (p._3, q._3) match
        case (None, None) => None
        case (Some(x), None) => Some(x)
        case (None, Some(x)) => Some(x)
        case (Some(x), Some(y)) => Some((x._1 + y._1, x._2 + y._2)),
    ))
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit =
    tooltip.add(Component.translatable("hexic.media_bundle.items", stack.heldItems.size, size).styled(_.withColor(ChatFormatting.GRAY)))
    val (consumables, batteries, trinkets) = getMediaInfo(stack)
    val isWaxed = stack.isWaxed
    var mentionedSealing = false
    def convertForWaxing(text: MutableComponent) =
      if isWaxed then
        mentionedSealing = true
        Component.empty().append(text.styled(_.withStrikethrough(true))).append(" ").append(Component.translatable("hexcasting.tooltip.spellbook.sealed").formatted(ChatFormatting.GOLD))
      else
        text
    batteries match
      case Some((total, max)) => tooltip.add(showMedia("external", total + consumables.getOrElse(0L), max))
      case None => for value <- consumables do
        tooltip.add(convertForWaxing(showMedia("external", value)))
    for (total, max) <- trinkets do
      tooltip.add(convertForWaxing(showMedia("internal", total, max)))
    if !mentionedSealing then
      tooltip.add(Component.translatable("hexcasting.tooltip.spellbook.sealed").formatted(ChatFormatting.GOLD))
  private def showMedia(tag: String, media: Long) = Component.translatable("hexic.media.infinite", Component.translatable(s"hexic.media.$tag"), Component.translatable("hexcasting.tooltip.media", dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR))))
  private def showMedia(tag: String, media: Long, maxMedia: Long) = Component.translatable("hexic.media.finite", Component.translatable(s"hexic.media.$tag"), dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Component.translatable("hexcasting.tooltip.media", dustAmount(maxMedia)).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Component.literal(PERCENTAGE.format(100.0 * media / maxMedia)+"%").styled(_.withColor(MediaHelper.mediaBarColor(media, maxMedia))))
  private def dustAmount(media: Long) = Component.literal(DUST_AMOUNT.format(media / MediaConstants.DUST_UNIT.toDouble))

extension [T] (s: => Seq[T]) def *^(n: Int) = Seq.fill(n)(()).flatMap((_) => s)
class Stringworm extends Item(Stringworm.settings)
object Stringworm:
  val settings = Item.Properties().stacksTo(16)
  val flavors = Seq("pure", "action", "hex", "media", "thing")
  val biasedFlavors = "pure" +: Seq("action", "hex", "media", "thing") *^ 3
  def randomFlavor(using rng: net.minecraft.util.RandomSource) = items(biasedFlavors(rng.nextInt(biasedFlavors.size)))
  val items =
    Stringworm.flavors.map(_ -> new Stringworm).toMap
export Stringworm.items as stringworms

object dyedStringworm extends Stringworm:
  override def getName(stack: ItemStack): Component =
    stack.getSubNbt("pigment") match
      case null => super.getName(stack)
      case n => Component.translatable("item.hexic.stringworm." + frozenPigmentFromNbt(n).item.getDescriptionId)

def toRoman(value: Int): String =
  "M" * (value / 1000) + ("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM").productElement(value % 1000 / 100) + ("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC").productElement(value % 100 / 10) + ("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX").productElement(value % 10)

private [hexic] object Extern:
  private [hexic] val worlds = mutable.Set[WeakReference[Level]]()
  private [hexic] def getWorld(biome: Biome): Level =
    val liveWorlds = worlds.toSeq.flatMap(_.get)
    worlds.filterInPlace(_.get.nonEmpty)
    liveWorlds
      .find(_.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome) != null)
      .orNull
  private [hexic] def getStringworm(idx: Int) = stringworms(Stringworm.flavors(idx))
  private [hexic] def observePropertyHook(args: util.List[? <: Iota], idx: Int, argc: Int)(original: => String)(using cir: CallbackInfoReturnable[util.List[Iota]]) =
    args.lastOption match
      case Some(s: PropertyAccessIota.Stream) =>
        cir.setReturnValue(Seq(s.take()))
        null
      case _ =>
        try
          original
        catch case e: MishapInvalidIota =>
          throw MishapInvalidIota(e.getPerpetrator, e.getReverseIdx, t"${e.getExpected} or stream")
  private [hexic] def writePropertyHook(args: util.List[? <: Iota], idx: Int, argc: Int)(original: => String)(using cir: CallbackInfoReturnable[util.List[Iota]]) =
    args.takeRight(2).toSeq match
      case Seq(s: PropertyAccessIota.Writer, w: Iota) =>
        s << w
        cir.setReturnValue(Seq())
        null
      case _ =>
        try
          original
        catch case e: MishapInvalidIota =>
          throw MishapInvalidIota(e.getPerpetrator, e.getReverseIdx, t"${e.getExpected} or writer")
  private val introPattern = """^q(w*)d\1q$""".r
  private [hexic] def handleParentheses(vm: CastingVM, iota: Iota): Option[(CastingImage, ResolvedPatternType)] = boundary:
    val p = iota match
      case p: PatternIota => p.getPattern
      case _ => boundary.break(None)
    p.anglesSignature match
      case introPattern (measure) =>
        val size = measure.length + 1
        def mishap(m: Mishap) =
          val safeVM = CastingVM(vm.getImage, vm.getEnv)
          OperatorSideEffect.DoMishap(m, Mishap.Context(p, Component.translatable("hexcasting.action.hexic:parenthesize"))).performEffect(safeVM)
          boundary.break(Some(safeVM.getImage, ResolvedPatternType.ERRORED))
        val img = vm.getImage
        val parens = img.getParenCount
        if parens == size then
          img.getStack.toSeq match
            case Seq() => mishap(MishapNotEnoughArgs(1, 0))
            case tail :+ head => Some((
              CastingImage(
                stack = TreeList.from(tail.asJava),
                parenCount = parens,
                parenthesized = TreeList.from((img.getParenthesized.asScala.toSeq :+ ParenthesizedIota(head, false)).asJava),
                escapeNext = false,
                simulateNext = img.getSimulateNext,
                opsConsumed = img.getOpsConsumed,
                userData = img.getUserData
              ),
              ResolvedPatternType.EVALUATED
            ))
        else if parens > size then
          None // leave unescaped, so a nested hex can introject
        else
          mishap(new Mishap:
            override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.ORANGE)
            override def errorMessage(env: CastingEnvironment, ctx: Context): Component =
              Component.literal("Not enough open parentheses to capture the selected iota")
            override def execute(env: CastingEnvironment, ctx: Context, stack: TreeList[Iota]): TreeList[Iota] =
              stack.appended(PatternIota(p))
          )
      case "eadedae" =>
        val img = vm.getImage
        Some(CastingImage(
          TreeList.from((img.getStack.asScala.toSeq :+ ListIota(img.getParenthesized.asScala.map(_.getIota).toSeq) :+ DoubleIota(img.getParenCount)).asJava),
          0,
          TreeList.empty(),
          false,
          false,
          img.getOpsConsumed,
          img.getUserData
        ), ResolvedPatternType.EVALUATED)
      case _ => None
  private [hexic] def getPocketName(pocket: String) = Component.literal("Demiplane " + pocketNames(getPocketID(ResourceLocation.tryParse(pocket)).get))

val _ =
  Interop.playerDeathHook = (p: Player, out: util.List[ItemStack]) =>
    val c = p: PlayerInfoComponent
    if !c.rightWeave.isEmpty then
      out.add(c.rightWeave)
      c.rightWeave = ItemStack.EMPTY
    if !c.leftWeave.isEmpty then
      out.add(c.leftWeave)
      c.leftWeave = ItemStack.EMPTY

given demiplaneExtensions: AnyRef with
  extension (w: ServerLevel)
    def meta: String MutableFunction Option[String] =
      val path = w.getServer.getSavePath(LevelResource.ROOT).resolve(s"dimensions/${w.getRegistryKey.getValue.getNamespace}/${w.getRegistryKey.getValue.getPath}")
      new MutableFunction:
        def apply(name: String) = try Option(Files.getAttribute(path, "user:" + name)).asInstanceOf[Option[Array[Byte]]].map(buf => String(buf, StandardCharsets.UTF_8)) catch case _: FileSystemException => None
        def update(name: String, value: Option[String]): Unit = Files.setAttribute(path, "user:" + name, value.map(StandardCharsets.UTF_8.encode).orNull)
    def parentInfo: Option[(ResourceKey[Level], BlockPos)] = for
      parentStr <- w.meta("parent")
      parentId <- Option(ResourceLocation.tryParse(parentStr))
      parentKey = ResourceKey.create(Registries.DIMENSION, parentId)
      if w.getServer.getWorld(parentKey) ne null
      x <- w.meta("bound_x").flatMap(v => Try(Integer.parseInt(v)).toOption)
      y <- w.meta("bound_y").flatMap(v => Try(Integer.parseInt(v)).toOption)
      z <- w.meta("bound_z").flatMap(v => Try(Integer.parseInt(v)).toOption)
    yield parentKey -> BlockPos(x, y, z)
    def parentInfo_=(parent: Option[(ResourceKey[Level], BlockPos)]) =
      parent.fold {
        w.meta("parent") = None
        w.meta("bound_x") = None
        w.meta("bound_y") = None
        w.meta("bound_z") = None
      } { (world, pos) =>
        w.meta("parent") = Some(world.getValue.toString)
        w.meta("bound_x") = Some(pos.getX.toString)
        w.meta("bound_y") = Some(pos.getY.toString)
        w.meta("bound_z") = Some(pos.getZ.toString)
      }

object JavaPlaneAccess:
  def logExcursion(sp: ServerPlayer) =
    val c = sp.component[ExcursionComponent]
    val curDim = sp.getWorld.getRegistryKey
    c.excursion = Some(curDim, sp.getPos)
    c.enteredDemiplaneTick = sp.getWorld.getTime
    sp.syncComponent(ExcursionComponent.key)
  def getDefaultExcursion(/** must be a demiplane */ world: ServerLevel): (ServerLevel, Vec3) =
    locally:
      // if the plane is bound, where it's bound to is the default excursion
      for
        case (key, pos) <- world.parentInfo
        boundWorld <- Option(world.getServer.getWorld(key))
      yield
        (boundWorld, Vec3.atBottomCenterOf(pos))
    .getOrElse:
      // idfk just go to spawn or something
      // someone would never do something silly like setting the world spawn to y 10000 or something like that riiiight?
      (world.getServer.getOverworld, Vec3.atBottomCenterOf(world.getServer.getOverworld.getSharedSpawnPos))
  def findExcursion(/* must be in a demiplane */ sp: ServerPlayer): (ServerLevel, Vec3) =
    locally:
      // the happy path, assuming we have a player involved
      for
        case (key, pos) <- sp.component[ExcursionComponent].excursion
        world <- Option(sp.getServer.getWorld(key))
      yield
        (world, pos)
    .getOrElse:
      given_Logger.warn(s"$sp claims to have never entered a demiplane")
      getDefaultExcursion(sp.serverLevel())
  def findExcursion(target: Entity)(using env: CastingEnvironment): (ServerLevel, Vec3) =
    env match
      case p: PlayerBasedCastEnv =>
        val (world, pos) = findExcursion(p.getCaster)
        world -> pos.add(target.getPos).subtract(p.getCaster.getPos)
      case _ => target match
        case p: ServerPlayer => findExcursion(p)
        case _ => target.getWorld match
          case sw: ServerLevel => getDefaultExcursion(sw)
          case _ => throw new IllegalStateException("why are you casting Spatial Interchange client-sided")
  def sendDirectlyToHell(sp: ServerPlayer) =
    val c = sp.component[ExcursionComponent]
    if c.enteredDemiplaneTick + cfg[Int]("hexic.demiplaneGracePeriod").getOrElse(5) > sp.getWorld.getTime then
      given_Logger.warn(s"$sp found outside kitchen")
      sp.teleportTo(5.5, 1.0, 5.5)
    else
      given_Logger.warn(s"$sp broke out of cell ${sp.getWorld.getRegistryKey}! mods, please check if the borders are broken")
      val (world, pos) = findExcursion(sp)
      sp.teleportTo(world, pos.getX, pos.getY, pos.getZ, sp.getYaw, sp.getPitch)
      val sp2 = sp
      sp2.addEffect(MobEffectInstance(MobEffects.CONFUSION, 30*20))
      sp2.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 30*20))
      sp2.hurt(sp2.getWorld.damageSources.fellOutOfWorld, sp2.getMaxHealth / 2)

  private[hexic] def shatterDemiplanePlayer(player: Player, outer: ServerLevel, pos: Vec3): Unit =
    player match
      case sp: ServerPlayer if sp.connection != null =>
        sp.teleportTo(outer, pos.getX, pos.getY, pos.getZ, sp.getYaw, sp.getPitch)
        if !sp.isCreative && !sp.isSpectator then sp.kill()
      case sp: ServerPlayer =>
        // Headless probe/fake server-player instances do not have a packet listener.
        // Keep the connected-player path above unchanged, but still make deleteworld
        // cleanup finite and non-crashing for unattached server-side player entities.
        sp.moveTo(pos.getX, pos.getY, pos.getZ, sp.getYaw, sp.getPitch)
        if !sp.isCreative && !sp.isSpectator then sp.discard()
      case p =>
        p.teleportTo(outer, pos.getX, pos.getY, pos.getZ, java.util.Set.of(), p.getYaw, p.getPitch)
        p.kill()

given Codec[Int] = Codec.INT.xmap(p => p, p => p)

type Media = Long
object MediaBundle:
  val items: Seq[MediaBundle] = for i <- Seq(6, 12); c <- DyeColor.values yield new MediaBundle(c, i)
  def apply(c: DyeColor, s: Int) = items.find(b => b.color == c && b.size == s).get
  private val PERCENTAGE = new DecimalFormat("####")
  PERCENTAGE.setRoundingMode(RoundingMode.DOWN)
  private val DUST_AMOUNT = new DecimalFormat("###,###.##")
val wizard = Item(Item.Properties().rarity(Rarity.EPIC).stacksTo(1))

val aLotOfMedia = (200000 /* max phial size */ * 6 /* phials per small pouch */ * 4 /* small pouches per large pouch */ * (36 /* inventory slots */ + 4 /* armor slots */ + 2 /* offpaws */) + 20 /* healthcasting */) * MediaConstants.DUST_UNIT

class Event[T, R](default: T => R) extends (T => R):
  private var current = default
  def apply(x: T): R = current(x)
  def apply(fn: PartialFunction[T, R]): Unit =
    val old = current
    current = fn.applyOrElse(_, old)

val useItemEvent = Event[(Item, UseOnContext, UseOnContext => InteractionResult), InteractionResult](p => p._3(p._2))
private[hexic] var clientPlayerGetter = () => (None: Option[Player])

trait HasCodec:
  def getCodec: Codec[? <: this.type]
given [T <: Mishap] => Conversion[T, HasCodec] = _.asInstanceOf

class DeferMut[T](initial: => T):
  private var value = () => initial
  def apply() = value()
  def update(x: => T): Unit = value = () => x

object ItemGroups:
  val utils = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
    .icon(() => new ItemStack(stringworms("media")))
    .title(Component.translatable("itemGroup.hexic.sub.utils.tab"))
    .displayItems: (ctx, entries) =>
      entries.accept(CastingEngine.item)
      for c <- DyeColor.values do entries.accept(Mediaweave.colors(c))
      for c <- DyeColor.values do entries.accept(MediaBundle(c, 6))
      for c <- DyeColor.values do entries.accept(MediaBundle(c, 12))
    .build()
  val root = utils
  val cosmetic = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
    .icon(() => new ItemStack(stringworms("hex")))
    .title(Component.translatable("itemGroup.hexic.sub.cosmetic.tab"))
    .displayItems: (ctx, entries) =>
      for c <- DyeColor.values do entries.accept(Mediaweave.colors(c))
      for f <- Stringworm.flavors do entries.accept(stringworms(f))
      for player <- clientPlayerGetter(); uuid = player.getUUID; case p: PigmentItem <- BuiltInRegistries.ITEM do
        val stack = ItemStack(dyedStringworm)
        val pigment = FrozenPigment(ItemStack(p), uuid)
        stack.getOrCreateNbt().put("pigment", frozenPigmentToNbt(pigment))
        entries.accept(stack)
    .build()
  val wip = Option.when(isDev):
    CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
      .icon(() => new ItemStack(Pen.instances(DyeColor.MAGENTA)))
      .title(Component.translatable("itemGroup.hexic.sub.wip.tab"))
      .displayItems: (ctx, entries) =>
        entries.accept(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hexic", "chisel")))
        entries.accept(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hexic", "chisel_table")))
        for c <- DyeColor.values do entries.accept(Pen.instances(c))
      .build()

val good_modulo = ne"daawdda"

private def positiveModulo(x: Double, y: Double): Double =
  (x % y + y) % y

object GoodModuloArithmetic extends Arithmetic:
  private val numOrVec = IotaPredicate.any(
    IotaPredicate.ofType(DoubleIota.TYPE),
    IotaPredicate.ofType(Vec3Iota.TYPE)
  )
  private val operator = new Operator(2, IotaMultiPredicate.all(numOrVec)):
    override def operate(env: CastingEnvironment, img: CastingImage, cont: SpellContinuation): OperationResult =
      val stack = img.getStack.asScala.toSeq
      val args = stack.takeRight(2)
      if args.size < 2 then throw MishapNotEnoughArgs(2, args.size)
      val left = args.head
      val right = args(1)
      val result = (left, right) match
        case (x: DoubleIota, y: DoubleIota) =>
          DoubleIota(positiveModulo(x.getDouble, y.getDouble))
        case _ =>
          val l = asVector(left, 1)
          val r = asVector(right, 0)
          Vec3Iota(Vec3(
            positiveModulo(l.x, r.x),
            positiveModulo(l.y, r.y),
            positiveModulo(l.z, r.z)
          ))
      OperationResult(
        img.withStack(s => s.dropRight(2) ++ Seq(result)),
        util.ArrayList[OperatorSideEffect](),
        cont,
        HexEvalSounds.NORMAL_EXECUTE.get()
      )

  private def asVector(iota: Iota, reverseIdx: Int): Vec3 =
    iota match
      case v: Vec3Iota => v.getVec3
      case d: DoubleIota => Vec3(d.getDouble, d.getDouble, d.getDouble)
      case other => throw MishapInvalidIota.ofType(other, reverseIdx, "num_or_vec")

  override def arithName: String = "good_modulo"
  override def opTypes: lang.Iterable[HexPattern] = java.util.List.of(good_modulo)
  override def getOperator(pattern: HexPattern): Operator =
    if pattern == good_modulo then operator
    else throw InvalidOperatorException(s"$pattern is not supported by $arithName")

def getEntity(iota: Iota)(using world: ServerLevel): Option[Entity] =
  iota match
    case e: EntityIota => Option(e.getEntity(world))
    case _ => None

extension (iota: Iota)
  def executeInPlace(cont: SpellContinuation, cause: Iota = iota)(using vm: CastingVM): CastResult =
    iota match
      case li: ListIota =>
        val l = li.getList
        if !l.isEmpty then
          l.head.execute(vm, vm.getEnv.getWorld, cont.pushFrame(FrameFinishEval.INSTANCE).pushFrame(FrameEvaluate(l.tail, true)))
        else
          CastResult(cause, cont, null, Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE.get())
      case i => i.execute(vm, vm.getEnv.getWorld, cont)

def memo[T, R](f: T => R, limit: Option[Int] = Some(128)): T => R =
  val cache = limit.fold(ju.HashMap()): cap =>
    new ju.LinkedHashMap[T, R](cap + 1, 1, true):
      override def removeEldestEntry(eldest: ju.Map.Entry[T, R]): Boolean = size > cap
  x =>
    cache.synchronized:
      cache.computeIfAbsent(x, f(_))

def iotaInt(iota: Iota, er: => Nothing): Int =
  iota match
    case d: DoubleIota =>
      val n = d.getDouble
      val i = n.toInt
      if (i - n).abs > DoubleIota.TOLERANCE then
        er
      else
        i
    case _ => er
@targetName("iotaInt max")
def iotaInt(iota: Iota, max: Int, er: => Nothing): Int =
  val x = iotaInt(iota, er)
  if x > max then
    er
  else
    x
@targetName("iotaInt under")
def iotaInt(iota: Iota, under: Int, er: => Nothing): Int =
  val x = iotaInt(iota, er)
  if x >= under then
    er
  else
    x

trait MutableFunction[T, R] extends (T => R):
  def update(key: T, value: R): Unit

object ChiselCutItem extends Item(Item.Properties().stacksTo(16)) with MediaHolderItem:
  override def getMedia(stack: ItemStack): Long = stack.getNbt.getLong("c")
  override def getMaxMedia(stack: ItemStack): Long = stack.getNbt.getLong("c")
  override def setMedia(stack: ItemStack, l: Media): Unit = ()
  override def canProvideMedia(stack: ItemStack): Boolean = true
  override def canRecharge(stack: ItemStack): Boolean = true
  override def getConsumptionPriority(stack: ItemStack): Int = 1100

object ChiselItem extends Item(Item.Properties().stacksTo(1))

object ChiselTable extends BaseEntityBlock(BlockBehaviour.Properties.of().noOcclusion()):
  override protected def codec(): MapCodec[? <: BaseEntityBlock] = BlockBehaviour.simpleCodec(_ => ChiselTable)
  private[hexic] lazy val entityType: BlockEntityType[BlockEntity] =
    IXplatAbstractions.INSTANCE.createBlockEntityType(
      new java.util.function.BiFunction[BlockPos, BlockState, BlockEntity]:
        override def apply(pos: BlockPos, state: BlockState): BlockEntity = newBlockEntity(pos, state)
      ,
      ChiselTable
    )
  override protected def getRenderShape(state: BlockState) = RenderShape.MODEL
  sealed trait entity extends BlockEntity:
    def markDirty(): Unit = setChanged()
    def createNbt(): CompoundTag = saveCustomOnly(getLevel.registryAccess())
    var bits: BitSet = BitSet()
    object bit:
      def apply(x: Int, y: Int): Boolean = bits(x * 16 + y)
      def update(x: Int, y: Int, value: Boolean): Unit =
        if value then
          bits += x * 16 + y
        else
          bits -= x * 16 + y
        markDirty()
  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new BlockEntity(entityType, pos, state) with entity:
      override protected def loadAdditional(nbt: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider): Unit =
        super.loadAdditional(nbt, provider)
        bits = BitSet.fromBitMask(nbt.getLongArray("b"))
      override protected def saveAdditional(nbt: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider): Unit =
        super.saveAdditional(nbt, provider)
        nbt.putLongArray("b", bits.toBitMask)
      override def getUpdatePacket: Packet[ClientGamePacketListener] = ClientboundBlockEntityDataPacket.create(this)
      override def getUpdateTag(provider: net.minecraft.core.HolderLookup.Provider): CompoundTag = createNbt()

  def findEntity(world: BlockGetter, pos: BlockPos, logMissing: Boolean = true): Option[entity] =
    world.getBlockEntity(pos) match
      case p: entity => Some(p)
      case null =>
        if logMissing then
          given_Logger.error(s"Missing block entity at $pos for chisel table.")
        None
      case p =>
        given_Logger.error(s"Unexpected block entity at $pos for chisel table, got $p (${summon[ClassTag[p.type]]}).")
        None

  val emptyShape = Shapes.or(
    Shapes.box(0.00, 0.00, 0.00, 0.25, 0.50, 0.25),
    Shapes.box(0.75, 0.00, 0.75, 1.00, 0.50, 1.00),
    Shapes.box(0.00, 0.50, 0.00, 1.00, 0.75, 1.00),
    Shapes.box(0.00, 0.75, 0.00, 0.0625, 0.8125, 1.00),
    Shapes.box(0.00, 0.75, 0.00, 1.00, 0.8125, 0.0625),
    Shapes.box(0.00, 0.75, 1.00, 0.9375, 0.8125, 1.00),
    Shapes.box(1.00, 0.75, 0.00, 1.00, 0.8125, 0.9375),
  )
  val chunks = memo: (i: Int) =>
    val x = i / 16
    val z = i % 16
    assume(x < 14 && z < 14)
    val dx = (x + 1) / 16.0
    val dz = (z + 1) / 16.0
    Shapes.box(dx, 0.75, dz, dx + 0.0625, 0.8125, dz + 0.0625)
  val shapes = memo { (bits: BitSet) => Shapes.or(emptyShape, bits.toSeq.map(chunks)*) }

  override protected def getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    val entity = findEntity(world, pos, logMissing = false)
    shapes(entity.fold(BitSet.empty)(_.bits))
  override protected def getCollisionShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    getShape(state, world, pos, context)

  override def useItemOn(stackInHand: ItemStack, state: BlockState, world: Level, pos: BlockPos, player: Player, paw: Paw, hit: BlockHitResult): ItemInteractionResult = boundary:
    lazy val entity = findEntity(world, pos).getOrElse(boundary.break(ItemInteractionResult.FAIL))
    player.getStackInHand(paw) match
      case stack@ItemStackAccess(item, _, _) if item == HexItems.CHARGED_AMETHYST.get() && !(for i <- 0 until 14; j <- 0 until 14 yield entity.bit(i, j)).all =>
        for i <- 0 until 14; j <- 0 until 14 do
          entity.bit(i, j) = true
        stack.decrement(1)
        ItemInteractionResult.SUCCESS
      case stack@ItemStackAccess(_, c, _) if c == 0 && entity.bits.nonEmpty && player.isShiftKeyDown =>
        stack.setItem(ChiselCutItem)
        stack.setCount(1)
        stack.getOrCreateNbt().putLongArray("b", entity.bits.toBitMask)
        entity.bits = BitSet.empty
        entity.markDirty()
        ItemInteractionResult.SUCCESS
      case stack@ItemStackAccess(item, _, _) if item eq ChiselItem =>
        val side = hit.getDirection
        val pos = hit.getLocation.add(side.getStepX * -1/32, side.getStepY * -1/32, side.getStepZ * -1/32)
        val x = ((pos.x * 16 % 16 + 16) % 16 - 1).toInt
        val y = ((pos.z * 16 % 16 + 16) % 16 - 1).toInt
        if x >= 0 && y >= 0 && x < 14 && y < 14 then
          if entity.bit(x, y) then
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND)
            entity.bit(x, y) = false
            ItemInteractionResult.SUCCESS
          else
            ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        else
          ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
      case _ => ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

def init(): Unit =
  given_Logger.info:
    val possible = Seq(
      "Performing unnecessary JVM war crimes...",
      "ough or something idk",
      "i'm sorry",
      "don't look at the networking too hard",
      "and the ASM stared back.",
      "'put everything in one file', they said",
      "hey did I tell you about the two secret slots in the player preview?",
      "see line 844 for more information",
      "no, you cannot flay sheep.",
      "filled with undocumented features! no do not open the bug tracker that's supposed to do that",
      "i bet your game is about to crash",
      "a" + "wa".repeat(Random.nextInt(20) + 10),
    )
    possible(Random.nextInt(possible.size))
  Interop.thoughtWorld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("hexic", "thought"))
  iotaTypeRegistry("access") = PropertyAccessIota.Type
  iotaTypeRegistry("string") = StringIota.TYPE
  for color -> item <- Mediaweave.colors do BuiltInRegistries.ITEM(s"${color.getName}_mediaweave") = item
  for item <- MediaBundle.items do
    BuiltInRegistries.ITEM(item.size match
      case 6 => s"small_${item.color.getName}_bundle"
      case 12 => s"large_${item.color.getName}_bundle") = item
  for (flavor, item) <- stringworms do
    BuiltInRegistries.ITEM(s"stringworm_$flavor") = item
  Pen.instances
  BuiltInRegistries.ITEM("stringworm_pigmented") = dyedStringworm
  BuiltInRegistries.BLOCK_ENTITY_TYPE("engine") = CastingEngine.entityType
  BuiltInRegistries.ITEM("engine") = CastingEngine.item
  BuiltInRegistries.ITEM("engine/gear_delegate") = CastingEngine.delegate
  BuiltInRegistries.ITEM("wizard") = wizard
  BuiltInRegistries.ITEM("cut") = ChiselCutItem
  BuiltInRegistries.BLOCK_ENTITY_TYPE("chisel_table") = ChiselTable.entityType
  BuiltInRegistries.ITEM("chisel_table") = BlockItem(ChiselTable, Item.Properties())
  BuiltInRegistries.ITEM("chisel") = ChiselItem
  BuiltInRegistries.CREATIVE_MODE_TAB("group") = ItemGroups.root
  BuiltInRegistries.CREATIVE_MODE_TAB("cosmetic") = ItemGroups.cosmetic
  for tab <- ItemGroups.wip do BuiltInRegistries.CREATIVE_MODE_TAB("wip") = tab
  //BuiltInRegistries.ITEM("echo") = EchoItem
  initChat()
  initMacros()
  initViews()
  if net.neoforged.fml.ModList.get().isLoaded("hexical") then
    HexicalCompat.registerDyeOffpaw()
    HexicalCompat.registerHopperEndpoints()
  Patterns.register("prop_fi", sw"aawqe"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) if !x.getReadonly => Seq(PropertyAccessIota.Writer(x.getName, "head"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "writeable_prop")
  Patterns.register("prop_fo", sw"aawqd"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) if !x.getReadonly => Seq(PropertyAccessIota.Stream(x.getName, "head"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "writeable_prop")
  Patterns.register("prop_li", sw"aawdwq"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) if !x.getReadonly => Seq(PropertyAccessIota.Writer(x.getName, "tail"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "writeable_prop")
  Patterns.register("prop_lo", sw"aawdwa"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) if !x.getReadonly => Seq(PropertyAccessIota.Stream(x.getName, "tail"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "writeable_prop")
  Patterns.register("where", nw"qaeaqwdd"):
    Patterns.mkConstAction(1): i =>
      val Seq(x) = i
      def mishap = throw MishapInvalidIota.ofType(x, 0, "list_int_or_bool")
      x match
        case x: ListIota =>
          Seq(ListIota(
            x.getList.zipWithIndex.toSeq.flatMap:
              case (x: BooleanIota, i) =>
                if x.getBool then
                  Seq(DoubleIota(i))
                else
                  Seq()
              case (x: DoubleIota, i) =>
                Iterator continually DoubleIota(i) take iotaInt(x, mishap)
              case _ => mishap
          ))
        case _ => mishap
  Patterns.arithmetic("modulo", good_modulo)
  hexXplat.getArithmeticRegistry("good_modulo": ResourceLocation) = GoodModuloArithmetic
  Patterns.register("spellmind/save", e"aqqqqqeawqwqwqwqwqweawwqwwqwwqwwqwwqwweawwwqwwwqwwwqwwwqwwwqwww"):
    Patterns.mkAction: (img, cont) =>
      OperationResult(SpellmindCompat.save(img), Seq(), cont, HexEvalSounds.THOTH.get())
  Patterns.register("spellmind/restore", e"deeeeeqdwewewewewewqdwwewwewwewwewwewwqdwwwewwwewwwewwwewwwewww"):
    Patterns.mkAction: (img, cont) =>
      SpellmindCompat.restore(img) match
        case Some(restored) => OperationResult(restored, Seq(), cont, HexEvalSounds.THOTH.get())
        case None => throw SpellmindCompat.noSavedMindMishap
  def fox(tr: Player ?=> PartialFunction[Option[Fox.Type], Option[Fox.Type]]): Action =
    Patterns.mkAction: (img, cont) =>
      img.getStack.lastOption match
        case None => throw MishapNotEnoughArgs(1, 0)
        case Some(ei: EntityIota) =>
          val player = Option(ei.getEntity(summon[CastingEnvironment].getWorld)) match
            case Some(sp: ServerPlayer) => sp
            case _ => throw MishapInvalidIota(ei, 0, "player")
          given Player = player
          val c: PlayerInfoComponent = player
          c.foxType match
            case tr(newFoxType) =>
              OperationResult(img.withStack(_.init), Seq(
                OperatorSideEffect.ConsumeMedia(MediaConstants.SHARD_UNIT + MediaConstants.DUST_UNIT),
                OperatorSideEffect.AttemptSpell(
                  new RenderedSpell:
                    override def cast(env: CastingEnvironment): Unit =
                      c.foxType = newFoxType
                      summon[Player].syncComponent(PlayerInfoComponent.key)
                    override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
                  , true, true)
              ), cont, HexEvalSounds.SPELL.get())
            case _ =>
              OperationResult(img.withStack(_.init), Seq(), cont, HexEvalSounds.SPELL.get())
        case Some(x) => throw MishapInvalidIota(x, 0, "player")
  Patterns.register("fox", se"wqwqeeeweedqqeqwaeeaw"):
    fox:
      case None => Some:
        val p = summon[Player]
        Fox.Type.byBiome(p.level().getBiome(p.blockPosition()))
  Patterns.register("unfox", se"wqwqwqwaeeaw"):
    fox { case Some(_) => None }
  // /data get entity @s cardinal_components."trinkets:trinkets".chest.hexic_mediaweave.Items[0].tag.lock
  Patterns.register("collar", sw"aqeqqqwqqqqqaqwqa"):
    Patterns.mkAction: (img, cont) =>
      // (→) patterns do not need to modify the image
      summon[CastingEnvironment] match
        case env: PlayerBasedCastEnv =>
          val consume = OperatorSideEffect.ConsumeMedia(15000)
          if isDev then println(s"starting with ${env.getCaster.equippedMediaweave}")
          (
            img, cont,
            HexEvalSounds.SPELL.get(),
            for
              (_, stack) <- env.getCaster.equippedMediaweave
              if !isDev || { println(s"ok ${stack} how ya ${stack.getNbt} okie ${Option(stack.getNbt).forall(_.get("lock") == null)}"); true }
              if Option(stack.getNbt).forall(_.get("lock") == null)
              e <- Seq(
                consume,
                OperatorSideEffect.AttemptSpell(
                  new RenderedSpell:
                    override def cast(env: CastingEnvironment): Unit =
                      stack.getOrCreateNbt().put("lock", CompoundTag())
                    override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
                  , true, true
                )
              )
            yield e
          )
        case _ => throw MishapBadCaster()
  Patterns.register("decollar", ne"wwqaqqqqqwqqqew"):
    Patterns.mkAction: (img, cont) =>
      // (→) patterns do not need to modify the image
      summon[CastingEnvironment] match
        case env: PlayerBasedCastEnv =>
          val consume = OperatorSideEffect.ConsumeMedia(15000)
          (
            img, cont,
            HexEvalSounds.SPELL.get(),
            for
              (_, stack) <- env.getCaster.equippedMediaweave
              if Option(stack.getNbt).exists(_.get("lock") != null)
              e <- Seq(
                consume,
                OperatorSideEffect.AttemptSpell(
                  new RenderedSpell:
                    override def cast(env: CastingEnvironment): Unit =
                      Option(stack.getNbt).foreach(_.remove("lock"))
                    override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
                  , true, true
                )
              )
            yield e
          )
        case _ => throw MishapBadCaster()
  hexXplat.getArithmeticRegistry("null_abs") = arith("null_abs", Arithmetic.ABS -> ((_: NullIota) => DoubleIota(0)))
  val planeCaches = ju.WeakHashMap[MinecraftServer, mutable.Map[UUID, RuntimeWorldHandle]]()
  def planeCache(using server: MinecraftServer): mutable.Map[UUID, RuntimeWorldHandle] = planeCaches.computeIfAbsent(server, _ => mutable.Map())
  def planes(uuid: UUID)(using server: MinecraftServer) =
    planeCache.computeIfAbsent(uuid, _ =>
      val dimID: ResourceLocation = s"fresh-${uuid.toString.replace("-", "")}"
      val handle = Fantasy get server getOrOpenPersistentWorld(dimID, new RuntimeWorldConfig setDimensionType ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath("hexic", "cell")) setGenerator new VoidChunkGenerator(server.registryAccess().registryOrThrow(Registries.BIOME)))
      handle
    )
  extension (server: MinecraftServer)
    def savedPlanes =
      val file = server.getSavePath(LevelResource.ROOT).resolve("fresh")
      if Files.exists(file) then
        Files.readAllLines(file, StandardCharsets.UTF_8).toSet.filter(!_.isBlank).map(UUID.fromString)
      else
        Set.empty
    def savedPlanes_=(planes: Set[UUID]) =
      val file = server.getSavePath(LevelResource.ROOT).resolve("fresh")
      Files.write(file, planes.map(_.toString))
  hexXplat.getArithmeticRegistry("list_math") = arith("list_math",
    Arithmetic.MUL -> ((a: ListIota, b: ListIota) => ListIota(for x <- a.getList.toSeq; y <- b.getList yield ListIota(Seq(x, y)))),
    Arithmetic.DIV -> ((a: ListIota, b: ListIota) => ListIota(for (x, y) <- a.getList.toSeq zip b.getList yield ListIota(Seq(x, y)))),
  )
  hexXplat.getSpecialHandlerRegistry("tuple") = ((pattern, env) =>
    val regex = "qq(w*)qq".r
    pattern.anglesSignature match
      case regex(middle) =>
        val size = middle.length + 1
        new SpecialHandler:
          override val act: Action = Patterns.mkConstAction(size)(ListIota(_).pipe(Seq(_)))
          override val getName: Component = Component.translatable("hexcasting.special.hexic:tuple.n", toRoman(size))
      case _ => null
  ): SpecialHandler.Factory[? <: SpecialHandler]
  def planeAction(name: String)(body: MinecraftServer ?=> UUID => Int): Unit =
    Commands.server.literal(name): c =>
      c.requires(_.hasPermissionLevel(2))
      c.argument("id", UuidArgument.uuid()): (uuid, c) =>
        c.executes: src =>
          given MinecraftServer = src.getServer
          body(uuid)
  planeAction("pin_plane"): id =>
    summon[MinecraftServer].savedPlanes += id
    1
  planeAction("un_plane"): id =>
    summon[MinecraftServer].savedPlanes -= id
    1
  planeAction("touch_plane"): id =>
    planes(id)
    1
  planeAction("unmap_plane"): id =>
    planeCache.remove(id) match
      case Some(h) =>
        h.unload()
        1
      case None => throw CommandException(Component.literal("Plane not mapped"))
  planeAction("delete_plane"): id =>
    planeCache.remove(id) match
      case Some(h) =>
        h.delete()
        1
      case None => throw CommandException(Component.literal("Plane not mapped"))
  Commands.server.literal("property"): c =>
    c.requires(_.hasPermissionLevel(2))
    c.literal("get"): c =>
      c.argument("property", StringArgumentType.string()): (prop, c) =>
        c.executes: src =>
          System.getProperty(prop) match
            case null => throw CommandException(t"Property $prop is not set")
            case s =>
              src.sendFeedback(() => t"Property $prop is set to $s", false)
              1
    c.literal("set"): c =>
      c.argument("property", StringArgumentType.string()): (prop, c) =>
        c.argument("value", StringArgumentType.string()): (value, c) =>
          c.executes: src =>
            System.setProperty(prop, value)
            src.sendFeedback(() => t"Changed the value of property $prop", true)
            1
    c.literal("remove"): c =>
      c.argument("property", StringArgumentType.string()): (property, c) =>
        // nothing?
        ()
    c.literal("reload"): c =>
      c.executes: src =>
        val out = Files.newBufferedReader(Path.of("config/jvm.properties"), Charsets.UTF_8)
        try
          System.getProperties.load(out)
        catch
          case _: FileNotFoundException => throw CommandException("Properties file does not exist")
        finally
          out.close()
        src.sendFeedback(() => "Reloaded properties from file", true)
        1
    c.literal("flush"): c =>
      c.executes: src =>
        val out = Files.newBufferedWriter(Path.of("config/jvm.properties"), Charsets.UTF_8)
        try
          System.getProperties.store(out, null)
        finally
          out.close()
        src.sendFeedback(() => "Saved properties to file", true)
        1
  given (env: CastingEnvironment) => MinecraftServer = env.getWorld.getServer
  def warmSavedPlanes(using server: MinecraftServer): Int =
    val saved = server.savedPlanes
    saved.foreach(id => planes(id))
    saved.size

  def probeWarmSavedPlanes(server: MinecraftServer): Unit =
    given MinecraftServer = server
    val before = server.savedPlanes
    val probeId = Iterator.continually(UUID.randomUUID()).find(id => !before.contains(id)).get
    try
      server.savedPlanes = before + probeId
      val warmedCount = warmSavedPlanes
      val cachedProbe = planeCache.contains(probeId)
      if warmedCount >= 1 && cachedProbe then
        summon[Logger].info("[HEXIC-PROBE] saved_plane_warmup=PASS count={} probe_cached=true", warmedCount)
      else
        summon[Logger].error("[HEXIC-PROBE] saved_plane_warmup=FAIL count={} probe_cached={}", warmedCount, cachedProbe)
    finally
      planeCache.remove(probeId).foreach(_.unload())
      Fantasy.get(server).drainPendingForProbe()
      server.savedPlanes = before

  NeoForge.EVENT_BUS.addListener((event: ServerStartedEvent) =>
    val server = event.getServer
    try
      given MinecraftServer = server
      if java.lang.Boolean.getBoolean("hexic.probe.validateRegistries") then
        probeWarmSavedPlanes(server)
      else
        val warmed = warmSavedPlanes
        if warmed > 0 then
          summon[Logger].info("Warmed {} saved Hexic demiplane(s)", warmed)
    catch
      case t: Throwable =>
        if java.lang.Boolean.getBoolean("hexic.probe.validateRegistries") then
          summon[Logger].error("[HEXIC-PROBE] saved_plane_warmup=FAIL exception", t)
        else
          summon[Logger].error("Failed to warm saved Hexic demiplanes", t)
  )
  Patterns.register("makeworld", e"qaaqqwaeddeawqqaaqqwwwaeddeewdqaaqdweeddeawwwqqaaqqwaeddeawqqaaqawwwwwwwawwwwwww"):
    Patterns.mkConstAction(argc = 0, mediaCost = MediaConstants.QUENCHED_BLOCK_UNIT * 6): _ =>
      val uuid = UUID.randomUUID()
      val handle = planes(uuid)
      val world = handle.asWorld
      // TODO: world config
      val state = border.getDefaultState
      val bp = BlockPos.MutableBlockPos()
      for i <- 0 to 10; j <- 0 to 10; k <- Seq(0, 10) do
        bp.set(i, j, k)
        world.setBlockState(handle.planePos(bp), state, 0)
        bp.set(i, k, j)
        world.setBlockState(handle.planePos(bp), state, 0)
        bp.set(k, i, j)
        world.setBlockState(handle.planePos(bp), state, 0)
      world.getServer.savedPlanes += uuid
      Seq(DimIota(handle.asKey))
  Patterns.register("attachworld", e"qaaqqwaeddeawqqaaqawwwawwwwwwwqwwwawwwqwwwwwwwawwwaqaaqqwaeddeawqqaaq"):
    new SpellAction:
      override def getArgc: Int = 2
      override def awardsCastingStat(env: CastingEnvironment): Boolean = true
      override def execute(stack: util.List[? <: Iota], env: CastingEnvironment): SpellAction.Result =
        stack.toSeq match
          case Seq(plane: DimIota, dest: Vec3Iota) if plane.getDimString.startsWith("hexic:fresh-") =>
            val pos = BlockPos.containing(dest.getVec3)
            env.assertPosInRangeForEditing(pos)
            given server: MinecraftServer = env.getWorld.getServer
            val id = getPocketID(plane.getWorldKey.getValue).get
            val handle = planes(id)
            if env.getWorld.getRegistryKey == handle.asKey then
              throw new Mishap:
                override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.PINK)
                override def errorMessage(env: CastingEnvironment, ctx: Context): Component =
                  Component.translatable("hexcasting.mishap.hexic:demiplane_self_bind")
                override def execute(env: CastingEnvironment, ctx: Context, stack: TreeList[Iota]): TreeList[Iota] =

                  if env.getWorld.getBlockState(pos).isAir then
                    env.getWorld.setBlockState(pos, Interop.VOID_AIR.getDefaultState, 3)
                  stack
            SpellAction.Result(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  handle.parentInfo = Some(env.getWorld.getRegistryKey, pos)
                override def cast(env: CastingEnvironment, image: CastingImage): CastingImage = { cast(env); image },
                MediaConstants.SHARD_UNIT,
                Seq(),
                1
            )
      override def executeWithUserdata(list: util.List[? <: Iota], env: CastingEnvironment, data: CompoundTag): SpellAction.Result = SpellAction.DefaultImpls.executeWithUserdata(this, list, env, data)
      override def hasCastingSound(env: CastingEnvironment): Boolean = true
      override def operate(env: CastingEnvironment, castingImage: CastingImage, cont: SpellContinuation): OperationResult = SpellAction.DefaultImpls.operate(this, env, castingImage, cont)
  Patterns.register("deleteworld", e"qaaqqwaeddeawqqaaqawwwawwwwwwwaqaaqqwaeddeawqqaaqawwwwwwwawwwaqaaqqwaeddeawqqaaq"):
    new SpellAction:
      override def getArgc: Int = 1
      override def awardsCastingStat(env: CastingEnvironment): Boolean = true
      override def execute(stack: util.List[? <: Iota], env: CastingEnvironment): SpellAction.Result =
        stack.toSeq match
          case Seq(plane: DimIota) if plane.getDimString.startsWith("hexic:fresh-") =>
            given server: MinecraftServer = env.getWorld.getServer
            val id = getPocketID(plane.getWorldKey.getValue).get
            val handle = planes(id)
            val planeWorld = handle.asWorld
            val (outer, pos) = (for
              (key, pos) <- handle.parentInfo
              world = server.getWorld(key)
              if world != null
            yield (world, Vec3.atCenterOf(pos))).getOrElse:
              throw new Mishap:
                override def accentColor(env: CastingEnvironment, context: Context): FrozenPigment = dyeColor(DyeColor.PINK)
                override def errorMessage(env: CastingEnvironment, context: Context): Component =
                  Component.translatable("hexcasting.mishap.hexic:demiplane_untethered")
                override def execute(env: CastingEnvironment, context: Context, list: TreeList[Iota]): TreeList[Iota] =

                  val r = planeWorld.random
                  val pos = BlockPos.MutableBlockPos(r.nextIntBetweenInclusive(1, 9), r.nextIntBetweenInclusive(1, 9), r.nextIntBetweenInclusive(1, 9))
                  Seq(pos.setX, pos.setY, pos.setZ)(r.nextInt(2))(if r.nextBoolean() then 10 else 0)
                  planeWorld.destroyBlock(handle.planePos(pos), true, null, 512)
                  list
            if isDev then println(s"Destroying pocket $id $planeWorld into $outer@$pos")
            SpellAction.Result(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  val loc = BlockPos.MutableBlockPos()
                  for x <- 1 to 9; y <- 1 to 9; z <- 1 to 9 do
                    loc.set(x, y, z)
                    planeWorld.destroyBlock(handle.planePos(loc), true, null, 512)
                  val itemsToSpawn = mutable.Map[ItemVariant, Long]().withDefaultValue(0)
                  var xpToSpawn: Long = 0
                  val chunk = planeWorld.getChunkSource.getChunk(handle.origin.getX >> 4, handle.origin.getZ >> 4, ChunkStatus.FULL, false)
                  if isDev then println(s"Scanning runtime demiplane ${handle.asKey.location()} ${handle.bounds} for entity dump")
                  boundary:
                    if isDev then println("Beginning lurker cleanup")
                    try
                      var pass = 0
                      val processed = mutable.Set[UUID]()
                      // test dim: hexic:fresh-9116c992558d4aca854d75270e100b84, uuid 9116c992-558d-4aca-854d-75270e100b84
                      iterated(handle.entities): (entities, recurse) =>
                        val entitySeq = entities.toSeq.filter(entity => processed.add(entity.getUUID))
                        if isDev then println(s"Performing pass $pass over ${entitySeq.size} entities")
                        if entitySeq.nonEmpty then
                          for entity <- entitySeq do
                            entity match
                              case e: ItemEntity =>
                                val stack = e.getStack
                                itemsToSpawn(ItemVariant.of(stack)) += stack.getCount
                                if isDev then println(s"Collecting item entity $stack, need to spawn: $itemsToSpawn")
                                e.discard()
                              case e: ExperienceOrb =>
                                xpToSpawn += e.getValue
                                if isDev then println(s"Collecting XP orb, need to spawn: $xpToSpawn")
                                e.discard()
                              case p: Player =>
                                // thanks but please stop eating my ears
                                if isDev then println(s"Teleporting player $p")
                                JavaPlaneAccess.shatterDemiplanePlayer(p, outer, pos)
                              case e: LivingEntity =>
                                e.kill()
                                if isDev then println(s"Killing living entity $e")
                                var n = 0
                                while !e.isRemoved && n < 1 do
                                  n += 1
                                  if isDev then println(s"Discarding lingering living entity after kill")
                                  e.discard()
                              case e =>
                                if isDev then println(s"Killing nonliving entity $e")
                                e.teleportTo(outer, pos.getX, pos.getY, pos.getZ, java.util.Set.of(), e.getYaw, e.getPitch)
                                e.kill()
                          pass += 1
                          if isDev then println(s"Proceeding to pass ${pass}")
                          recurse
                      if isDev then println(s"Ledger: $itemsToSpawn, $xpToSpawn XP")
                      for (item, count) <- itemsToSpawn do
                        if isDev then println(s"Spawning $item ($count)")
                        spawnManyItems(pos, item, count)(using outer)
                      while xpToSpawn > 2477 do
                        if isDev then println(s"Spawning max XP orb, $xpToSpawn left")
                        ExperienceOrb.award(outer, pos, 2477)
                        xpToSpawn -= 2477
                      if xpToSpawn > 0 then
                        if isDev then println(s"Spawning final XP orb, $xpToSpawn")
                        ExperienceOrb.award(outer, pos, xpToSpawn.toInt)
                    catch case e: Throwable =>
                      summon[Logger].error(
                        s"Failed to remove Hexic demiplane $id; keeping it loaded",
                        e
                      )
                      boundary.break()
                      throw e
                    // FIN.
                    server.savedPlanes -= id
                    planeCache.remove(id).fold(handle)(identity).delete()
                override def cast(env: CastingEnvironment, image: CastingImage): CastingImage = { cast(env); image },
              MediaConstants.SHARD_UNIT * 25,
              Seq(),
              1
            )
          case Seq(x) =>
            throw MishapInvalidIota(x, 0, "hexic:world")
      override def executeWithUserdata(list: util.List[? <: Iota], env: CastingEnvironment, data: CompoundTag): SpellAction.Result = SpellAction.DefaultImpls.executeWithUserdata(this, list, env, data)
      override def hasCastingSound(env: CastingEnvironment): Boolean = true
      override def operate(env: CastingEnvironment, castingImage: CastingImage, cont: SpellContinuation): OperationResult = SpellAction.DefaultImpls.operate(this, env, castingImage, cont)
  Patterns.register("omni_open", w"qdaqadq"):
    Patterns.mkAction: (img, cont) =>
      (img.getStack.toSeq: Seq[Iota]) match
        case stack:+allegedCount =>
          val count = OperatorUtils.getPositiveInt(Seq(allegedCount), 0, 1)
          (new CastingImage(TreeList.from(stack.asJava), count, TreeList.empty(), false, false, img.getOpsConsumed, img.getUserData), cont, HexEvalSounds.NORMAL_EXECUTE.get(), Seq())
  Patterns.register("omni_close", e"eadedae"):
    Patterns.mkConstAction(0):
      case Seq() => Seq(ListIota(Seq()), DoubleIota(0))
  Patterns.register("staffcast_factory", ne"wwwwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayer =>
          val oldImage = IXplatAbstractions.INSTANCE.getStaffcastVM(caster, Paw.MAIN_HAND).getImage
          IXplatAbstractions.INSTANCE.setStaffcastImage(caster, img)
          val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(caster, summon[CastingEnvironment].getCastingHand)
          try
            if cfg("hexic.compat.laniSwallowsMishaps").getOrElse(false) then
              vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
            else
              propagateMishaps(vm.getEnv):
                vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
          finally
            IXplatAbstractions.INSTANCE.setStaffcastImage(caster, oldImage)
          (vm.getImage, cont, HexEvalSounds.HERMES.get(), Seq())
        case _ => throw MishapBadCaster()
  Patterns.register("staffcast_factory/lazy", ne"wwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayer =>
          val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(caster, summon[CastingEnvironment].getCastingHand)
          val oldImage = vm.getImage
          val injected = img.getStack.lastOption.getOrElse:
            throw MishapNotEnoughArgs(1, 0)
          vm.setImage(new CastingImage(
            stack = oldImage.getStack.appended(injected),
            parenCount = 0,
            parenthesized = TreeList.empty(),
            escapeNext = false,
            simulateNext = false,
            opsConsumed = img.getOpsConsumed,
            userData = img.getUserData
          ))
          try
            if cfg("hexic.compat.laniSwallowsMishaps").getOrElse(false) then
              vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
            else
              propagateMishaps(vm.getEnv):
                vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
          finally
            IXplatAbstractions.INSTANCE.setStaffcastImage(caster, new CastingImage(
              stack = vm.getImage.getStack,
              parenCount = oldImage.getParenCount,
              parenthesized = oldImage.getParenthesized,
              escapeNext = oldImage.getEscapeNext,
              simulateNext = oldImage.getSimulateNext,
              opsConsumed = oldImage.getOpsConsumed,
              userData = oldImage.getUserData
            ))
          // do not remove this comment
          (new CastingImage(
            stack = img.getStack.init(),
            parenCount = img.getParenCount,
            parenthesized = img.getParenthesized,
            escapeNext = img.getEscapeNext,
            simulateNext = img.getSimulateNext,
            opsConsumed = vm.getImage.getOpsConsumed,
            userData = vm.getImage.getUserData
          ), cont, HexEvalSounds.HERMES.get(), Seq())
        case _ => throw MishapBadCaster()
  Patterns.register("get_other_caster", nw"ede"):
    Patterns.mkLiteral:
      val players: Set[LivingEntity] = summon[CastingEnvironment].getWorld.getPlayers(_ => true).asScala.toSet
      var others = players - summon[CastingEnvironment].getCastingEntity
      for fakeClass <- classNamed("carpet.patches.EntityPlayerMPFake") do
        others = others.filterNot(entity => fakeClass.runtimeClass.isInstance(entity))
      val sorted = others.toSeq.sortBy(_.getPos.distanceToSqr(summon[CastingEnvironment].mishapSprayPos)).filter(summon[CastingEnvironment].isEntityInRange(_, true))
      sorted.headOption.fold(NullIota())(EntityIota(_))
  Patterns.register("blind", se"qqqqqadwawawd")(OpPotionEffect(MobEffects.BLINDNESS, 1000, false, false))
  Patterns.register("erase", e"wqwdwqwawwwwwawwwww"):
    Patterns.mkAction: (img, cont) =>
      def mkResult(scale: Int, pos: => Vec3, spell: => Unit) =
        OperationResult(
          img.withStack(_.init),
          Seq(
            OperatorSideEffect.ConsumeMedia(MediaConstants.DUST_UNIT * scale),
            OperatorSideEffect.AttemptSpell(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  spell(using env)
                override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
              , true, true
            ),
            OperatorSideEffect.Particles(
              ParticleSpray(pos, Vec3(1, 0, 0), 0.25, 3.14, 40)
            ),
          ),
          cont, HexEvalSounds.SPELL.get()
        )
      img.getStack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0)) match
        case s: EntityIota =>
          val target = s.getEntity(summon[CastingEnvironment].getWorld)
          summon[CastingEnvironment].assertEntityInRange(target)
          def result(scale: Int, spell: CastingEnvironment ?=> Unit) = mkResult(scale, target match { case e: ItemEntity => e.getPos.add(0, .375, .0); case e => e.getPos }, spell)
          boundary: outer ?=>
            val maybeItem = target match
              case i: ItemEntity => Some(i.getStack)
              case f: ItemFrame => Some(f.getItem)
              case _ => None
            boundary:
              val item = maybeItem.getOrElse(boundary.break())
              val holder = hexXplat.findHexHolder(item)
              if holder == null || !holder.hasHex then boundary.break()
              boundary.break(result(item.getCount, holder.clearHex()))(using outer)
            val holder = hexXplat.findDataHolder(target)
            if holder == null || !holder.writeIota(null, true) then throw MishapBadEntity.of(target, "hexic:erase")
            result(maybeItem.fold(1)(_.getCount), holder.writeIota(null, false))
        case i => throw MishapInvalidIota.ofType(i, 0, "hexic:erase")
  Patterns.register("rotate", nw"qaeaqweeee"):
    Patterns.mkConstAction(2):
      case Seq(ary: ListIota, nr: DoubleIota) =>
        val list = ary.getList.asScala
        val d = nr.getDouble
        var n = d.toInt
        if (d - n).abs > DoubleIota.TOLERANCE then
          throw MishapInvalidIota.ofType(nr, 0, "hexic:int_or_list")
        val size = list.size
        if size == 0 then
          Seq(ListIota(Seq()))
        else
          val delta = (n % size + size) % size
          Seq(ListIota((list.drop(delta) ++ list.take(delta)).toSeq.asJava))
      case Seq(ary: ListIota, nr) => throw MishapInvalidIota.ofType(nr, 0, "hexic:int_or_list")
      case Seq(ary, _) => throw MishapInvalidIota.ofType(ary, 1, "list")
  Patterns.register("take", nw"qaeaqwd"):
    Patterns.mkConstAction(2):
      case Seq(ary: ListIota, nr: DoubleIota) =>
        val list = ary.getList.asScala
        val d = nr.getDouble
        var n = d.toInt
        if (d - n).abs > DoubleIota.TOLERANCE then
          throw MishapInvalidIota.ofType(nr, 0, "hexic:int_or_list")
        Seq(ListIota((if n < 0 then list.takeRight(-n) else list.take(n)).toSeq.asJava))
      case Seq(ary: ListIota, nrs: ListIota) =>
        val list = ary.getList.asScala.toIndexedSeq
        val incl = nrs.getList.asScala.map(iotaInt(_, throw MishapInvalidIota.ofType(nrs, 0, "hexic:int_or_list")))
        Seq(ListIota(list.indices.filter(incl.contains(_)).map(list(_)).toSeq.asJava))
      case Seq(ary: ListIota, nr) => throw MishapInvalidIota.ofType(nr, 0, "hexic:int_or_list")
      case Seq(ary, _) => throw MishapInvalidIota.ofType(ary, 1, "list")
  Patterns.register("drop", nw"qaeaqda"):
    Patterns.mkConstAction(2):
      case Seq(ary: ListIota, nr: DoubleIota) =>
        val list = ary.getList.asScala
        val d = nr.getDouble
        var n = d.toInt
        if (d - n).abs > DoubleIota.TOLERANCE then
          throw MishapInvalidIota.ofType(nr, 0, "int")
        Seq(ListIota((if n < 0 then list.dropRight(-n) else list.drop(n)).toSeq.asJava))
      case Seq(ary: ListIota, nrs: ListIota) =>
        val list = ary.getList.asScala.toIndexedSeq
        val excl = nrs.getList.asScala.map(iotaInt(_, throw MishapInvalidIota.ofType(nrs, 0, "int_list")))
        Seq(ListIota(list.indices.filter(!excl.contains(_)).map(list(_)).toSeq.asJava))
      case Seq(ary: ListIota, nr) => throw MishapInvalidIota.ofType(nr, 0, "int")
      case Seq(ary, _) => throw MishapInvalidIota.ofType(ary, 1, "list")
  def frameIotasFromList(list: ListTag): Seq[Iota] =
    list.asScala.toSeq.map(tag => Option(deserializeIota(tag)).getOrElse(NullIota()))
  def frameIotasFromNbt(tag: CompoundTag, key: String): Seq[Iota] =
    frameIotasFromList(tag.getList(key, Tag.TAG_COMPOUND))
  def frameIotaFromNbt(tag: CompoundTag, key: String): Iota =
    Option(tag.get(key)).flatMap(t => Option(deserializeIota(t))).getOrElse(NullIota())
  def frameIotaGroupsFromNbt(tag: CompoundTag, key: String): Seq[Seq[Iota]] =
    tag.getList(key, Tag.TAG_LIST).asScala.toSeq.map:
      case list: ListTag => frameIotasFromList(list)
      case _ => Seq.empty

  class FilterFrame(stack: Seq[Iota], filter: Seq[Iota], focus: Iota, received: Seq[Iota], remaining: Seq[Iota]) extends ContinuationFrame:
    override def getType: ContinuationFrame.Type[FilterFrame] = FilterFrame
    override def evaluate(cont: SpellContinuation, world: ServerLevel, vm: CastingVM): CastResult =
      def toMishap(mishap: Mishap) = CastResult(PatternIota(nw"qaeaqea"), cont, vm.getImage, Seq(OperatorSideEffect.DoMishap(mishap, Mishap.Context(nw"qaeaqea", null))), ResolvedPatternType.ERRORED, HexEvalSounds.MISHAP.get())
      vm.getImage.getStack.toSeq match
        case Seq() => toMishap(MishapNotEnoughArgs(1, 0))
        case _ :+ (x: BooleanIota) =>
          val newReceived = if x.getBool then received :+ focus else received
          val (newStack, newCont) = remaining match
            case next +: rest => (stack :+ next, cont.pushFrame(new FilterFrame(stack, filter, next, newReceived, rest)).pushFrame(FrameEvaluate(TreeList.from(filter.asJava), true)))
            case Seq() => (stack :+ ListIota(newReceived), cont)
          CastResult(PatternIota(nw"qaeaqea"), newCont, vm.getImage.withStack(_ => newStack), Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.THOTH.get())
        case _ :+ i => toMishap(MishapInvalidIota(i, 0, "boolean"))
    def serializeToNBT(): CompoundTag = CompoundTag()
      .tap(_.put("p", seqToNBT(stack.map(serializeIota))))
      .tap(_.put("k", seqToNBT(received.map(serializeIota))))
      .tap(_.put("r", seqToNBT(remaining.map(serializeIota))))
      .tap(_.put("f", serializeIota(focus)))
      .tap(_.put("d", seqToNBT(filter.map(serializeIota))))
    override def breakDownwards(stack: TreeList[Iota]): Pair[java.lang.Boolean, TreeList[Iota]] =
      Pair(true, TreeList.from((this.stack :+ ListIota(received)).asJava))
    override def size = (Iterable(focus) ++ stack ++ filter ++ received ++ remaining).map(_.size).sum
  object FilterFrame extends ContinuationFrame.Type[FilterFrame]:
    def apply(stack: Seq[Iota], filter: Seq[Iota], focus: Iota, received: Seq[Iota], remaining: Seq[Iota]): FilterFrame =
      new FilterFrame(stack, filter, focus, received, remaining)
    private def fromTag(tag: CompoundTag): FilterFrame =
      FilterFrame(
        frameIotasFromNbt(tag, "p"),
        frameIotasFromNbt(tag, "d"),
        frameIotaFromNbt(tag, "f"),
        frameIotasFromNbt(tag, "k"),
        frameIotasFromNbt(tag, "r")
      )
    override def codec(): MapCodec[FilterFrame] =
      CompoundTag.CODEC.xmap[FilterFrame](fromTag, _.serializeToNBT()).fieldOf("value")
    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, FilterFrame] =
      ByteBufCodecs.fromCodecWithRegistries(codec().codec())
  Patterns.register("grep", nw"qaeaqea"):
    Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq match
        case Seq() => throw MishapNotEnoughArgs(2, 0)
        case Seq(_) => throw MishapNotEnoughArgs(2, 1)
        case saved:+(target: ListIota):+(filter: ListIota) =>
          if filter.getList.isEmpty then
            // TreeList.filter in Hex Casting pre-39 drops a retained prefix when
            // the first rejected element is later in the tree. Iterate through
            // the public list view so grep keeps every truthy input in order.
            val truthy = target.getList.iterator.asScala.filter(_.isTruthy).toList.asJava
            (img.withStack(_.dropRight(2) :+ ListIota(truthy)), cont, HexEvalSounds.THOTH.get(), Seq()) // short-circuit on empty filter
          else target.getList.toSeq match
            case first +: rest =>
              // set up filter, ideally FilterFrame would do this
              (img.withStack(_.dropRight(2) :+ first), cont.pushFrame(FilterFrame(saved, filter.getList.toSeq, first, Seq(), rest)).pushFrame(FrameEvaluate(filter.getList, true)), HexEvalSounds.THOTH.get(), Seq())
            case _ =>
              // we can't start a filter with no iota, but it'd always be empty anyway
              (img.withStack(_.dropRight(2) :+ ListIota(Seq())), cont, HexEvalSounds.THOTH.get(), Seq())
        case saved:+(_: ListIota):+filter => throw MishapInvalidIota(filter, 1, "list")
        case saved:+target:+_ => throw MishapInvalidIota(target, 0, "list")
  case class ConnectFrame(stack: Seq[Iota], filter: Seq[Iota], done: Seq[Seq[Iota]], wip: Seq[Iota], focus: Iota, remaining: Seq[Iota]) extends ContinuationFrame:
    override def getType: ContinuationFrame.Type[ConnectFrame] = ConnectFrame
    override def breakDownwards(list: TreeList[Iota]): kotlin.Pair[lang.Boolean, TreeList[Iota]] =
      Pair(true, TreeList.from((stack :+ ListIota((done :+ wip).map(ListIota(_)))).asJava))
    override def evaluate(next: SpellContinuation, world: ServerLevel, vm: CastingVM): CastResult =
      def toMishap(mishap: Mishap) = CastResult(PatternIota(nw"qaeaqeeaqwaqa"), next, vm.getImage, Seq(OperatorSideEffect.DoMishap(mishap, Mishap.Context(nw"qaeaqeeaqwaqa", null))), ResolvedPatternType.ERRORED, HexEvalSounds.MISHAP.get())
      vm.getImage.getStack.toSeq match
        case Seq() => toMishap(MishapNotEnoughArgs(expected = 1, got = 0))
        case _ :+ (x: BooleanIota) =>
          val (newDone, newWip) =
            if x.getBool then
              // no slice here
              (done, wip :+ focus)
            else
              // slice and start a new wip
              (done :+ wip, Seq(focus))
          remaining match
            case Seq() =>
              // we're free, restore saved stack and push results
              val fin = newDone :+ newWip
              val finIota = ListIota(fin.map(ListIota(_)))
              CastResult(PatternIota(nw"qaeaqeeaqwaqa"), next, vm.getImage() (stack = stack :+ finIota), Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.THOTH.get())
            case newFocus +: newRemaining =>
              // prepare for next iteration
              val nextWithEval = next.pushFrame(copy(done = newDone, wip = newWip, focus = newFocus, remaining = newRemaining)).pushFrame(FrameEvaluate(TreeList.from(filter.asJava), true))
              CastResult(PatternIota(nw"qaeaqeeaqwaqa"), nextWithEval, vm.getImage() (stack = stack :+ focus :+ newFocus), Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.THOTH.get())
        case _ :+ i => toMishap(MishapInvalidIota(i, 0, "boolean"))
    def serializeToNBT: CompoundTag = CompoundTag()
      .tap(_.put("p", seqToNBT(stack.map(serializeIota))))
      .tap(_.put("k", seqToNBT(done.map(k => seqToNBT(k.map(serializeIota))))))
      .tap(_.put("c", seqToNBT(wip.map(serializeIota))))
      .tap(_.put("r", seqToNBT(remaining.map(serializeIota))))
      .tap(_.put("f", serializeIota(focus)))
      .tap(_.put("d", seqToNBT(filter.map(serializeIota))))
    override def size: Int = (Iterable(focus) ++ filter ++ done.iterator.flatten ++ wip ++ remaining).map(_.size).sum
  object ConnectFrame extends ContinuationFrame.Type[ConnectFrame]:
    private def fromTag(tag: CompoundTag): ConnectFrame =
      ConnectFrame(
        frameIotasFromNbt(tag, "p"),
        frameIotasFromNbt(tag, "d"),
        frameIotaGroupsFromNbt(tag, "k"),
        frameIotasFromNbt(tag, "c"),
        frameIotaFromNbt(tag, "f"),
        frameIotasFromNbt(tag, "r")
      )
    override def codec(): MapCodec[ConnectFrame] =
      CompoundTag.CODEC.xmap[ConnectFrame](fromTag, _.serializeToNBT).fieldOf("value")
    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, ConnectFrame] =
      ByteBufCodecs.fromCodecWithRegistries(codec().codec())

  hexXplat.getContinuationTypeRegistry("filter") = FilterFrame
  hexXplat.getContinuationTypeRegistry("connect") = ConnectFrame
  pipelineFrameCodecProbe = () =>
    Try:
      val filter = FilterFrame(Seq(DoubleIota(1)), Seq(NullIota()), DoubleIota(2), Seq(NullIota()), Seq(DoubleIota(3)))
      val connect = ConnectFrame(Seq(DoubleIota(1)), Seq(NullIota()), Seq(Seq(DoubleIota(2))), Seq(DoubleIota(3)), DoubleIota(4), Seq(DoubleIota(5)))
      val encodedFilter = ContinuationFrame.Type.getTYPED_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, filter).getOrThrow
      val encodedConnect = ContinuationFrame.Type.getTYPED_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, connect).getOrThrow
      val decodedFilter = ContinuationFrame.Type.getTYPED_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encodedFilter).getOrThrow
      val decodedConnect = ContinuationFrame.Type.getTYPED_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encodedConnect).getOrThrow
      if decodedFilter.getType == FilterFrame && decodedConnect.getType == ConnectFrame then
        val filterRoundTrip = decodedFilter.asInstanceOf[FilterFrame]
        val connectRoundTrip = decodedConnect.asInstanceOf[ConnectFrame]
        if filterRoundTrip.serializeToNBT().toString == filter.serializeToNBT().toString &&
            connectRoundTrip.serializeToNBT.toString == connect.serializeToNBT.toString then
          Right(s"filter=${encodedFilter.getClass.getName} connect=${encodedConnect.getClass.getName}")
        else
          Left("decoded payload mismatch")
      else
        Left(s"decoded=${decodedFilter.getType},${decodedConnect.getType}")
    .fold(t => Left(s"${t.getClass.getName}: ${t.getMessage}"), identity)
  // edge-detection pattern, casts $filter with each n=2 window from $target then splits between iotas where it returns false
  // ^ [a], (^ a, a → bool) → ^ [[a]]
  Patterns.register("connect", nw"qaeaqeeaqwaqa"):
    Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq match
        case Seq() => throw MishapNotEnoughArgs(2, 0)
        case Seq(_) => throw MishapNotEnoughArgs(2, 1)
        case saved:+(target: ListIota):+(filter: ListIota) =>
          target.getList.toSeq match
            case prev +: focus +: remaining =>
              // put the first two iotas on the stack and arrange for the rest of the procedure
              (img(stack = saved :+ prev :+ focus), cont.pushFrame(ConnectFrame(stack = saved, filter = filter.getList.toSeq, done = Seq(), wip = Seq(prev), focus = focus, remaining = remaining)).pushFrame(FrameEvaluate(filter.getList, true)), HexEvalSounds.THOTH.get(), Seq())
            case l =>
              // these seqs are too short to meaningfully slice
              (img.withStack(_.dropRight(2) :+ ListIota(l)), cont, HexEvalSounds.THOTH.get(), Seq())
        case saved:+(_: ListIota):+filter => throw MishapInvalidIota(filter, 1, "list")
        case saved:+target:+_ => throw MishapInvalidIota(target, 0, "list")
  Patterns.register("extract", nw"dewaqawed"):
    Patterns.mkConstAction(2):
      case Seq(ary: ListIota, nr: DoubleIota) =>
        val ls = ary.getList.toSeq
        val n = iotaInt(nr, under = ls.size, throw MishapInvalidIota.of(nr, 0, "int.positive.less", ls.size))
        Seq(ListIota(ls take n), ListIota(ls drop (n+1)), ls(n))
      case Seq(ary: ListIota, nr) => throw MishapInvalidIota.ofType(nr, 0, "int")
      case Seq(ary, _) => throw MishapInvalidIota.ofType(ary, 1, "list")
  Patterns.register("make_cme", ne"dqqd"):
    Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq.reverse match
        case Seq(args: ListIota, fn: ListIota, stack*) =>
          given ExecutionContext = ExecutionContext.global
          val p = Promise[Seq[CastingImage]]()
          if isDev then println(s"parathoth args=$args fn=${fn.getList} stack=$stack")
          val imgs = args.getList.map: x =>
            Future:
              try
                val subImg = new CastingImage(
                  stack = TreeList.from((stack :+ x).asJava),
                  parenCount = 0,
                  parenthesized = TreeList.empty(),
                  escapeNext = false,
                  simulateNext = false,
                  opsConsumed = img.getOpsConsumed,
                  userData = img.getUserData
                )
                if isDev then println(s"- thread $x started")
                val env = summon[CastingEnvironment]
                val vm = CastingVM(subImg, new CastingEnvironment(env.getWorld):
                  export env.{getUsableStacks => _, getPrimaryStacks => _, *}
                  override def hasEditPermissionsAtEnvironment(pos: BlockPos): Boolean = false
                  override def extractMediaEnvironment(cost: Media, simulate: Boolean): Media = 0
                  override def isVecInRangeEnvironment(vec: Vec3): Boolean = false
                  override def getUsableStacks(mode: CastingEnvironment.StackDiscoveryMode): util.List[ItemStack] = Seq()
                  override def getPrimaryStacks: util.List[CastingEnvironment.HeldItemInfo] = Seq()
                )
                vm.queueExecuteAndWrapIotas(fn.getList.toSeq, env.getWorld)
                if isDev then println(s"- thread $x ended")
                vm.getImage
              catch case e =>
                p.tryFailure(e)
                throw e
          Future.sequence(imgs).onComplete(p tryComplete _.map(_.toSeq))
          val results = Await.result(p.future, Duration.Inf)
          OperationResult(
            newImage = CastingImage(
              stack = TreeList.from((stack :+ ListIota(results.flatMap(_.getStack))).asJava),
              parenCount = img.getParenCount,
              parenthesized = img.getParenthesized,
              escapeNext = img.getEscapeNext,
              simulateNext = img.getSimulateNext,
              opsConsumed = results.map(_.getOpsConsumed).maxOption.getOrElse(img.getOpsConsumed),

              userData = img.getUserData
            ),
            sideEffects = Seq(),
            newContinuation = cont,
            sound = HexEvalSounds.THOTH.get(),
          )
        case Seq(i, _: ListIota, _*) => throw MishapInvalidIota.ofType(i, 0, "list")
        case Seq(_, i, _*) => throw MishapInvalidIota.ofType(i, 1, "list")
        case s => throw MishapNotEnoughArgs(2, s.size)
  CastingEnvironment.addCreateEventListener: (env: CastingEnvironment, data: CompoundTag) =>
    val id = env.getWorld.getRegistryKey.getValue
    if isDev then println(s"Environment created in $id")
    for pocketID <- getPocketID(id) do
      if isDev then println(s"Preparing pocket $pocketID for environment $env")
      env.addExtension:
        new CastingEnvironmentComponent with CastingEnvironmentComponent.IsVecInRange with CastingEnvironmentComponent.HasEditPermissionsAt:
          object getKey extends CastingEnvironmentComponent.Key[this.type]
          override def onIsVecInRange(vec: Vec3, current: Boolean): Boolean = boundary:
            for axis <- Direction.Axis.values do
              val x = vec.get(axis)
              if x < 0 || x >= 11 then boundary.break(false)
            true
          override def onHasEditPermissionsAt(pos: BlockPos, current: Boolean): Boolean = boundary:
            for axis <- Direction.Axis.values do
              val x = pos.get(axis)
              if x < 0 || x >= 11 then boundary.break(false)
            current
  // Developer-only pattern dump. Production instances must not write files
  // into the game directory merely because Hexic was initialized.
  if isDev then
    val out = Files.newOutputStream(Path.of("patterns.csv"))
    try
      val o = OutputStreamWriter(out)
      for ent <- hexXplat.getActionRegistry.entrySet.asScala.toSeq.sortBy(_.getKey.location.toString) do
        o.write(s"${ent.getKey.location},${ent.getValue.prototype.getStartDir},${ent.getValue.prototype.anglesSignature}\n")
      o.flush()
    finally
      out.close()

given IotaType[PropertyIota] = PropertyIota.TYPE
given IotaType[Vec3Iota] = Vec3Iota.TYPE

case class Const[T](value: T)
inline given [T <: Singleton] => Const[T] = Const[T](compiletime.constValue[T])
given [T] => Conversion[Const[T], T] = _.value

private[hexic] class ComponentInit

opaque type Attrition = Unit
object Attrition extends Registrar[Attrition]("attrition")

type subtypes[T, R <: T] = T
//case class StaffcastFrame(owner: ServerPlayer, oldImage: CastingImage) extends ContinuationFrame:
//  override def getType: ContinuationFrame.Type[StaffcastFrame] = StaffcastFrame
//  override def breakDownwards(list: util.List[? <: Iota]): Pair[lang.Boolean, util.List[Iota]] = ???
//  override def evaluate(rest: SpellContinuation, world: ServerLevel, vm: CastingVM): CastResult =
//    HexCardinalComponents.STAFFCAST_IMAGE.get(owner).setImage(oldImage)
//    HexCardinalComponents.STAFFCAST_IMAGE.sync(owner)
//    CastResult(NullIota(), rest)
//  override def serializeToNBT: CompoundTag = ???
//  override def size: Int = 1
//object StaffcastFrame extends ContinuationFrame.Type[StaffcastFrame]:
//  def deserializeFromNBT(data: CompoundTag, world: ServerLevel): StaffcastFrame = ???

val fadedScrolls: TagKey[ActionRegistryEntry] = TagKey.create(HexRegistries.ACTION, ResourceLocation.fromNamespaceAndPath("hexic", "faded_scrolls"))

extension (text: Component)
  def +(other: Component): MutableComponent = Component.literal("").append(text).append(other)
  def uncons: Option[(Component, Component)] =
    boundary:
      if !text.getContents.empty then
        val contentText = text.copy
        val siblingsText = Component.literal("")
        siblingsText.setStyle(text.getStyle)
        siblingsText.getSiblings ++= contentText.getSiblings
        contentText.getSiblings.clear()
        boundary.break(Some((contentText, siblingsText)))
      for sibling <- text.getSiblings do
        for p <- sibling.uncons do
          boundary.break(Some(p both(_.copy.styled(_.applyTo(text.getStyle)))))
      None
  def unsnoc: Option[(Component, Component)] =
    boundary:
      for sibling <- text.getSiblings do
        for p <- sibling.unsnoc do
          boundary.break(Some(p both(_.copy.styled(_.applyTo(text.getStyle)))))
      if !text.getContents.empty then
        boundary.break(Some((text.plainCopy, Component.literal("")) both(_.setStyle(text.getStyle))))
      None
extension (content: ComponentContents)
  def empty = content match
    case l: LiteralContents => l.text == ""
    case _ => false

object EchoItem extends Item(Item.Properties().rarity(Rarity.RARE))

case class Nonce(id: UUID):
  def this() = this(UUID.randomUUID())
object Nonce:
  given Codec[Nonce] = UUIDUtil.CODEC.xmap(Nonce(_), _.id)
  given Conversion[Nonce, Component] = _.id.toString.takeRight(6).pipe(Component.literal).styled(_.withFont(ResourceLocation.fromNamespaceAndPath("minecraft", "illageralt")))

object elementTag:
  def apply[T <: Tag](l: CollectionTag[T]): ClassTag[T] =
    l match
      case _: ByteArrayTag => summon[ClassTag[ByteTag]]
      case _: IntArrayTag => summon[ClassTag[IntTag]]
      case _: LongArrayTag => summon[ClassTag[LongTag]]
      case _: ListTag => summon[ClassTag[Tag]]
  def unapply[T <: Tag](l: CollectionTag[T]): Some[ClassTag[T]] = Some(elementTag(l))

private[hexic] object cfg:
  locally:
    Using.resource(Files.list(Path.of("config/"))): dir =>
      dir.forEach: file =>
        if file.toString.endsWith(".properties") then
          try
            System.getProperties.load(Files.newBufferedReader(file, Charsets.UTF_8))
          catch
            case i: IOException => summon[Logger].warn(s"Failed to read properties from $file", i)
  def apply[T: FromString as t](key: String): Option[T] =
    sys.props.get(key).map(t.fromString)
  def flag(key: String): Boolean = cfg[Boolean](s"hexic.$key").contains(true)
  def update[T](key: String, value: T): Unit =
    sys.props(key) = value.toString

def eq[T: ClassTag, U: ClassTag] = summon[ClassTag[T]] == summon[ClassTag[U]]

object Droplet extends Item(Item.Properties()):
  def apply(fluid: Fluid, nbt: Option[CompoundTag] = None): ItemVariant =
    ItemVariant.of(Droplet, CompoundTag().tap: c =>
      c.putString("id", BuiltInRegistries.FLUID.getId(fluid).toString)
      nbt.foreach(c.put("nbt", _))
    )

case class TransactionalValue[@specialized T](private var state: T) extends SnapshotParticipant[T]:
  override def createSnapshot(): T = state
  override def readSnapshot(snapshot: T): Unit = state = snapshot
  def value: T = state
  def value_=(v: T)(using tx: TransactionContext): Unit =
    updateSnapshots(tx)
    state = v

object iotaLike:
  def unapply[T: FromIota](iota: Iota): Option[T] = summon[FromIota[T]].convert(iota)

object itsGiving:
  inline transparent def unapply[T](x: Any): Option[(x.type, T)] =
    summonFrom:
      case y: T => Some((x, y))
      case _ => None

trait FromIota[T]:
  def convert(iota: Iota): Option[T]
object FromIota:
  def lift[T](f: PartialFunction[Iota, T]): FromIota[T] = (iota: Iota) => f.lift(iota)
  def liftFlat[T](f: PartialFunction[Iota, Option[T]]): FromIota[T] = (iota: Iota) => f.lift(iota).flatten
given FromIota[Iota] = Some(_)
given FromIota[String] = FromIota.lift:
  case s: StringIota => s.getString
given FromIota[Boolean] = FromIota.lift:
  case b: BooleanIota => b.getBool
given [T: ClassTag](using elems: FromIota[T]): FromIota[Seq[T]] = FromIota.liftFlat:
  case l: ListIota =>
    boundary:
      val b = mutable.Seq.empty[T]
      l.getList.map(elems.convert).collect:
        case Some(p) => b.add(p)
        case None => boundary.break(None)
      Some(b.toSeq)
given FromIota[Double] = FromIota.lift:
  case d: DoubleIota => d.getDouble
given FromIota[Float] = FromIota.lift:
  case d: DoubleIota if (d.getDouble.round.toFloat - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toFloat
given FromIota[Byte] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Byte.MaxValue && d.getDouble > Byte.MinValue && (d.getDouble.round.toByte - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toByte
given FromIota[Short] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Short.MaxValue && d.getDouble > Short.MinValue && (d.getDouble.round.toShort - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toShort
given FromIota[Int] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Int.MaxValue && d.getDouble > Int.MinValue && (d.getDouble.round.toInt - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round.toInt
given FromIota[Long] = FromIota.lift:
  case d: DoubleIota if d.getDouble < Long.MaxValue && d.getDouble > Long.MinValue && (d.getDouble.round - d.getDouble) < DoubleIota.TOLERANCE => d.getDouble.round

object nbtList:
  def unapply(l: Tag): Option[Tagged[CollectionTag, Tag]] =
    l match
      case c: ListTag => Some(Tagged(c))
      case c: IntArrayTag => Some(Tagged(c))
      case c: ByteArrayTag => Some(Tagged(c))
      case c: LongArrayTag => Some(Tagged(c))
      case _ => None

given Conversion[Array[Byte], ByteArrayTag] = ByteArrayTag(_)
given Conversion[Array[Int], IntArrayTag] = IntArrayTag(_)
given Conversion[Array[Long], LongArrayTag] = LongArrayTag(_)
given Conversion[ByteArrayTag, Array[Byte]] = _.getAsByteArray
given Conversion[IntArrayTag, Array[Int]] = _.getAsIntArray
given Conversion[LongArrayTag, Array[Long]] = _.getAsLongArray

trait Tagged[+F[_ <: U @uncheckedVariance], +U]:
  type T <: U: ClassTag
  val value: F[T]
object Tagged:
  def apply[F[_ <: R], R: ClassTag](v: F[R]): Tagged[F, R] =
    new Tagged:
      type T = R
      val value: F[R] = v
  def unapply[F[_ <: R], R](v: Tagged[F, R]): (F[v.T], ClassTag[v.T]) = (v.value, summon)

def seqToNBT(data: Seq[Tag]) =
  val l = ListTag()
  data.forEach(l.add(_))
  l

trait PigmentHolderItem:
  def getPigment(stack: ItemStack): FrozenPigment
  def setPigment(stack: ItemStack)(pigment: FrozenPigment): Unit
object PigmentHolderItem:
  def packagedHex(item: ItemPackagedHex): PigmentHolderItem =
    new PigmentHolderItem:
      override def getPigment(stack: ItemStack): FrozenPigment =
        item.getPigment(stack)
      override def setPigment(stack: ItemStack)(pigment: FrozenPigment): Unit =
        stack.set(HexDataComponents.PIGMENT.get(), pigment)
given Conversion[ItemPackagedHex, PigmentHolderItem] = PigmentHolderItem.packagedHex
given Conversion[ItemStack, ItemStackAccess] = _.asInstanceOf // by mixin

given Conversion[Double, DoubleIota] = DoubleIota(_)
given Conversion[Int, DoubleIota] = DoubleIota(_)
given Conversion[DoubleIota, Double] = _.getDouble
extension (d: DoubleIota) def asIntOrThrow(idx: Int): Int =
  val v = d.getDouble
  if (v.round - v).abs > DoubleIota.TOLERANCE then
    throw MishapInvalidIota.of(d, idx, "int")
  v.round.intValue

trait Selector[-T, R]:
  def apply(target: T): R
  def update(target: T, value: R): Unit

//extension [T, R] (target: T)
//  def apply(selector: Selector[T, R]): R = selector(target)
//  def update(selector: Selector[T, R], value: R): Unit = selector(target) = value

def copy[T <: Iota](iota: T)(using ServerLevel): T | Null = deserializeIota(serializeIota(iota)).asInstanceOf[T | Null]

def spawnItem(pos: Vec3, stack: ItemStack)(using world: ServerLevel): ItemEntity =
  ItemEntity(world, pos.getX, pos.getY, pos.getZ, stack).tap(world.addFreshEntity(_))
def spawnManyItems(pos: Vec3, variant: ItemVariant, amount: Long)(using ServerLevel): Seq[ItemEntity] =
  assume(amount >= 0)
  if amount > Int.MaxValue then
    spawnItem(pos, variant.toStack(Int.MaxValue)) +: spawnManyItems(pos, variant, amount - Int.MaxValue)
  else if amount > 0 then
    List(spawnItem(pos, variant.toStack(amount.toInt)))
  else
    Nil

object border extends Block(BlockBehaviour.Properties.of().noLootTable().isValidSpawn((_, _, _, _) => false).sound(SoundType.STONE).requiresCorrectToolForDrops().strength(100.0F, 1200.0F).lightLevel(_ => 14))
def getPocketID(key: ResourceLocation): Option[UUID] =
  if key.getNamespace == "hexic" && key.getPath.startsWith("fresh-") then
    val hash = key.getPath.replace("fresh-", "")
    val bi1 = BigInteger(hash.substring(0, 16), 16)
    val bi2 = BigInteger(hash.substring(16, 32), 16)
    Some(UUID(bi1.longValue, bi2.longValue))
  else
    None
def pocketName(using rand: Random) =
  def b = "zxcvbnm".charAt(rand.nextInt(7)).toString
  def v = "aeiouaeiouaeiouy".charAt(rand.nextInt(16)).toString
  def piece = s"${b.toUpperCase}$v$b" + Iterator.continually(v + b).takeWhile(_ => rand.nextInt(3) != 0).mkString("")
  (piece +: Iterator.continually(piece).takeWhile(_ => rand.nextInt(5) == 0).toSeq).mkString("-")
val pocketNames = memo((id: UUID) => pocketName(using Random(id.getLeastSignificantBits)))

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


object CastingEngine extends BaseEntityBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.DEEPSLATE_TILES).strength(4f, 4f).noOcclusion()):
  override protected def codec(): MapCodec[? <: BaseEntityBlock] = BlockBehaviour.simpleCodec(_ => CastingEngine)
  final val π: 3.1415927f = compiletime.constValue
  final val τ: 6.2831855f = compiletime.constValue
  override protected def getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
  trait Access:
    def pos: BlockPos
    def world: Level
    def terminate(): Nothing
    def suspend(img: CastingImage, cont: SpellContinuation): Nothing
    def sleep(img: CastingImage, cont: SpellContinuation, ticks: Int): Nothing
  object Access:
    val scope = Scope:
      new Access:
        private def mishap: Nothing =
          throw new Mishap:
            val surrogate = MishapNoSpellCircle()
            export surrogate.{execute, accentColor}
            override def errorMessage(castingEnvironment: CastingEnvironment, context: Context): Component =
              Component.translatable("hexic.mishap.notengine")
        override def pos: BlockPos = mishap
        override def world: Level = mishap
        override def terminate(): Nothing = mishap
        override def suspend(img: CastingImage, cont: SpellContinuation): Nothing = mishap
        override def sleep(img: CastingImage, cont: SpellContinuation, ticks: Int): Nothing = mishap
  private[hexic] sealed trait Entity extends BlockEntity with ADIotaHolder:
    def getWorld: Level = getLevel
    def getPos: BlockPos = getBlockPos
    def markDirty(): Unit = setChanged()
    def createNbt: CompoundTag = saveCustomOnly(getLevel.registryAccess())
    //#region state stuff
    var hexTag: Option[CompoundTag] = None
    // TODO: playerless casting
    var state: Option[(Suspension, Option[(UUID, HumanoidArm)])] = None
    var θ = 0f
    var ω = 0f
    case class Suspension(imageData: CompoundTag, frameData: Seq[CompoundTag]):
      require(frameData.nonEmpty)
      def image(using ServerLevel): CastingImage = castingImageFromNbt(imageData)
      def frames(using ServerLevel): Seq[ContinuationFrame] = frameData.map(continuationFrameFromNbt)
      def continuation(using ServerLevel) : SpellContinuation.NotDone =
        ((SpellContinuation.Done.INSTANCE : SpellContinuation) /: frames) (_ pushFrame _) // this will expect the topmost frame to be last in the list
          .asInstanceOf[SpellContinuation.NotDone] // SAFETY:
                                                   // • `frameData.nonEmpty` implies that frameDataSeq will not be empty
                                                   // • `frameData` is an immutable collection, so its length cannot change
                                                   // • `Seq[T]#map` returns a new seq with the same length as the original seq, therefore `frames` will not be empty
                                                   // • `frames` is an immutable collection, so its length cannot change
                                                   // • if `frames` is non-empty, the body of `/:` is guaranteed to run at least once
                                                   // • `pushFrame` is assumed to return an instance of `SpellContinuation.NotDone`
    object Suspension:
      def apply(image : CastingImage, frames : Seq[ContinuationFrame]) : Suspension = apply(castingImageToNbt(image).asInstanceOf[CompoundTag], frames.map(continuationFrameToNbt(_).asInstanceOf[CompoundTag]))
      def apply(image : CastingImage, continuation : SpellContinuation.NotDone) : Suspension =
        /**
         * recursively converts a SpellContinuation into a list of its component frames
         * @param accumulator a list of frames ‘above’ the current continuation, where the first element is the frame to be executed just-before `currentContinuation`
         * @return the list of component frames, where the frame to be executed last is the first element
         */
        @tailrec
        def getFrames(currentContinuation : SpellContinuation, accumulator: List[ContinuationFrame] = Nil) : List[ContinuationFrame] =
          currentContinuation match
            case _: SpellContinuation.Done => accumulator
            case step: SpellContinuation.NotDone => getFrames(step.getNext, step.getFrame :: accumulator)
        apply(image, getFrames(continuation))
    var pigment: Option[FrozenPigment] = None
    //#endregion
    //region iota storage
    def readIotaTag: CompoundTag = hexTag.orNull
    override def readIota(): Iota =
      hexTag.flatMap(t => Option(deserializeIota(t))).orNull
    override def writeIota(iota: Iota, simulate: Boolean): Boolean =
      (iota.isInstanceOf[ListIota] || iota.executable).`&&`:
        if !simulate then
          hexTag = Option(iota).map(serializeIota(_).asInstanceOf[CompoundTag])
          state = None
        true
    override def writeable: Boolean = state.isEmpty
    //endregion
    //region persistence
    override protected def loadAdditional(nbt: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider): Unit =
      super.loadAdditional(nbt, provider)
      if isDev then println(s"[$getPos] ${if getWorld == null then "------" else if getWorld.isClient then "CLIENT" else "SERVER"} gets nbt: ${nbt.copy().tap { t => t.remove("hex") }}")
      hexTag = for case data: CompoundTag <- Option(nbt.get("hex")) yield data
      state =
        for
          case imgData: CompoundTag <- Option(nbt.get("image"))
          case frameData: ListTag <- Option(nbt.get("frames"))
          frameDataSeq = frameData.collect { case c: CompoundTag => c }.toSeq
          if frameDataSeq.nonEmpty
        yield (
          Suspension(imgData, frameDataSeq),
          if nbt.hasUUID("caster") then
            Some(nbt.getUUID("caster"), HumanoidArm.RIGHT ^ (nbt.get("left_paw") != null))
          else
            None
        )
      pigment = for case data: CompoundTag <- Option(nbt.get("pigment")) yield frozenPigmentFromNbt(data)
      θ = nbt.getFloat("θ")
      ω = nbt.getFloat("ω")
    override protected def saveAdditional(nbt: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider): Unit =
      super.saveAdditional(nbt, provider)
      for data <- hexTag do nbt.put("hex", data)
      for (suspension, activation) <- state do
        nbt.put("image", suspension.imageData)
        nbt.put("frames", seqToNBT(suspension.frameData))
        for (caster, arm) <- activation do
          nbt.putUUID("caster", caster)
          if arm == HumanoidArm.LEFT then
            nbt.put("left_paw", CompoundTag())
      nbt.putFloat("θ", θ)
      nbt.putFloat("ω", ω)
    override def getUpdatePacket: Packet[ClientGamePacketListener] = ClientboundBlockEntityDataPacket.create(this)
    override def getUpdateTag(provider: net.minecraft.core.HolderLookup.Provider): CompoundTag =
      createNbt.tap: nbt =>
        if nbt.contains("image") then nbt.put("image", castingImageToNbt(CastingImage()))
        if nbt.contains("frames") then nbt.put("frames", seqToNBT(Seq(continuationFrameToNbt(FrameFinishEval.INSTANCE))))
        nbt.remove("left_paw")
    def setStackNbt(stack: ItemStack): Unit =
      val nbt = this.createNbt
      // strip actively-casting params
      nbt.remove("caster")
      nbt.remove("left_paw")
      BlockItem.setBlockEntityData(stack, this.getType, nbt)
    //endregion
    //region casting
    //region environments
    sealed trait BaseEngineEnv extends CastingEnvironment:
      override def getCastingEntity: ServerPlayer
      override def getPigment =
        pigment orElse Option(getCastingEntity).map(hexXplat.getPigment) getOrElse FrozenPigment.DEFAULT.get()
      override def setPigment(pigment: FrozenPigment) =
        Entity.this.pigment = Option(pigment)
        markDirty()
        pigment
      override def mishapSprayPos: Vec3 = Vec3.atCenterOf(getPos)
      override def isVecInRangeEnvironment(vec: Vec3): Boolean =
        vec.distanceToSqr(Vec3.atCenterOf(getPos)) <= 147.0156 // 12.125²
      override def hasEditPermissionsAtEnvironment(pos: BlockPos): Boolean = true
      override def produceParticles(particles: ParticleSpray, colorizer: FrozenPigment): Unit = particles.sprayParticles(Entity.this.getWorld.asInstanceOf[ServerLevel], colorizer)
      override def getCastingHand: Paw
      class DummyMishapEnv extends MishapEnvironment(Entity.this.getWorld.asInstanceOf[ServerLevel], getCastingEntity):
        override def yeetHeldItemsTowards(targetPos: Vec3): Unit = ()
        override def dropHeldItems(): Unit = ()
        override def drown(): Unit = ()
        override def damage(healthProportion: Float): Unit = ()
        override def removeXp(amount: Int): Unit = ()
        override def blind(ticks: Int): Unit = ()
        override def nauseate(ticks: Int): Unit = ()
      override def getMishapEnvironment = DummyMishapEnv()
      override def getUsableStacks(mode: CastingEnvironment.StackDiscoveryMode): util.List[ItemStack] = Seq.empty
      override def getPrimaryStacks: util.List[CastingEnvironment.HeldItemInfo] = Seq.empty
      override def replaceItem(stackOk: Predicate[ItemStack], replaceWith: ItemStack, paw: Paw): Boolean = false
      override def isEnlightened: Boolean = true
    class PlayerEngineEnv(player: ServerPlayer, paw: Paw) extends PlayerBasedCastEnv(player, paw) with BaseEngineEnv:
      override def getCastingHand: Paw = paw
      override def getCastingEntity: ServerPlayer = player
      override def extractMediaEnvironment(cost: Media, simulate: Boolean): Media = if player.isCreative then 0 else extractMediaFromInventory(cost, canOvercast, simulate)
      override def printMessage(message: Component): Unit = super[PlayerBasedCastEnv].printMessage(t"${t"[${ItemInlineData(ItemStack(CastingEngine)).asText(false)} ${getPos.getX} ${getPos.getY} ${getPos.getZ}]".styled(_.withColor(0xe0aa2b))} $message")
      override def getMishapEnvironment = DummyMishapEnv()
      override def isVecInRangeEnvironment(vec: Vec3): Boolean =
        super.isVecInRangeEnvironment(vec) || Option(HexAPI.instance.getSentinel(player)).exists(s => s.extendsRange && s.dimension == caster.getWorld.getRegistryKey)
    //endregion
    final def tick() =
      getWorld match
        case sw@given ServerLevel =>
          for (_ඞ, caster) <- state; (uuid, arm) <- caster do
            sw.getServer.getPlayerList.getPlayer(uuid) match
              case sp: ServerPlayer if sp.getPos.distanceToSqr(Vec3.atCenterOf(getPos)) < 64 =>
                val vm : CastingVM = CastingVM(_ඞ.image, PlayerEngineEnv(sp, sp.getMainArm ^ arm))
                state = wrapReturn: setState =>
                  Access.scope.enter(
                    new Access:
                      override def pos: BlockPos = getPos
                      override def world: Level = getWorld
                      override def terminate(): Nothing = setState(None)
                      override def suspend(img: CastingImage, cont: SpellContinuation): Nothing =
                        cont match
                          case _: SpellContinuation.Done => setState(None)
                          case notDone: SpellContinuation.NotDone => setState(Some(Suspension(img(opsConsumed = 0), notDone), None))
                      override def sleep(img: CastingImage, cont: SpellContinuation, ticks: Int): Nothing =
                        if ticks <= 0 then
                          cont match
                            case _: SpellContinuation.Done => setState(None)
                            case notDone: SpellContinuation.NotDone => setState(Some(Suspension(img(opsConsumed = 0), notDone), caster))
                        else
                          // this is incredibly hacky
                          setState(Some(Suspension(img(opsConsumed = 0), SpellContinuation.NotDone(FrameEvaluate(TreeList.from(Seq(PatternIota(w"qqqaw"), DoubleIota(ticks-1), PatternIota(hexXplat.getActionRegistry.get("engine/sleep").prototype)).asJava), false), cont)), caster))
                  ) {
                    _ඞ.continuation.executePreemptive(vm, 100).map(c => (Suspension(vm.getImage()(opsConsumed = 0), c), caster))
                  }
                markDirty()
                if state.forall(_._2.isEmpty) then sw.sendBlockUpdated(getPos, getBlockState, getBlockState, 3)
              case _ =>
                state = Some(_ඞ, None)
                markDirty()
                sw.sendBlockUpdated(getPos, getBlockState, getBlockState, 3)
        case _ =>
      val (θ, ω) = simulatePhysics(1)
      this.θ = θ%τ; this.ω = ω
    def simulatePhysics(Δt: Float): (θ: Float, ω: Float) =
      val ωMax = cfg[Float]("hexic.engine.omegaMax").getOrElse(0.33f)
      val α = if state.exists(_._2.isDefined) then cfg[Float]("hexic.engine.alphaActive").getOrElse(0.07f) else -cfg[Float]("hexic.engine.alphaInactive").getOrElse(0.02f)
      val ωEnd = ω+α*Δt
      if ωEnd < 0 then
        // ω+αΔt=0
        // -αΔt=ω
        // Δt=-ω/α
        val Δt1 = -ω/α;
        (θ+ω*Δt1+α*Δt1*Δt1/2,0)
      else if ωEnd > ωMax then
        // ω+αΔt=M
        // M-αΔt=ω
        // -αΔt=ω-M
        // αΔt=M-ω
        // Δt=(M-ω)/α
        val Δt1 = (ωMax-ω)/α
        val Δt2 = Δt - Δt1;
        (θ+ω*Δt1+α*Δt1*Δt1/2+ωMax*Δt2,ωMax)
      else
        (θ+ω*Δt+α*Δt*Δt/2,ω+α*Δt)
    //endregion
  private[hexic] lazy val entityType: BlockEntityType[? <: BlockEntity with Entity] =
    IXplatAbstractions.INSTANCE.createBlockEntityType(
      new java.util.function.BiFunction[BlockPos, BlockState, BlockEntity]:
        override def apply(pos: BlockPos, state: BlockState): BlockEntity =
          new BlockEntity(entityType.asInstanceOf[BlockEntityType[BlockEntity]], pos, state) with Entity
      ,
      CastingEngine
    ).asInstanceOf[BlockEntityType[? <: BlockEntity with Entity]]
  private[hexic] object item extends BlockItem(this, Item.Properties().stacksTo(1)) with IotaHolderItem:
    private def legacyBlockEntityData(stack: ItemStack): CompoundTag | Null =
      Option(stack.getNbt)
        .filter(_.contains("BlockEntityTag", Tag.TAG_COMPOUND))
        .map(_.getCompound("BlockEntityTag"))
        .orNull

    private[hexic] def blockEntityData(stack: ItemStack): CompoundTag | Null =
      Option(stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA))
        .map(_.copyTag())
        .orElse(Option(legacyBlockEntityData(stack)).map(_.copy()))
        .orNull

    private def clearLegacyBlockEntityData(stack: ItemStack): Unit =
      Option(stack.getNbt).foreach: existing =>
        val updated = existing.copy()
        updated.remove("BlockEntityTag")
        stack.setNbt(updated)

    /**
     * Old Hexic loot tables stored block-entity state under the pre-1.20.5
     * {@code BlockEntityTag} item-NBT key. 1.21 uses the
     * {@code minecraft:block_entity_data} component instead. Keep old worlds
     * readable, then normalize before placement so BlockItem applies the data
     * to the newly created engine.
     */
    private[hexic] def normalizeBlockEntityData(stack: ItemStack): CompoundTag | Null =
      val modern = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
      if modern != null then modern.copyTag()
      else
        val legacy = legacyBlockEntityData(stack)
        if legacy == null || legacy.isEmpty then null
        else
          val migrated = legacy.copy()
          BlockItem.setBlockEntityData(stack, entityType, migrated)
          clearLegacyBlockEntityData(stack)
          migrated

    def readIotaTag(stack: ItemStack): CompoundTag =
      Option(blockEntityData(stack)).map(_.getCompound("hex")).filter(!_.isEmpty).orNull
    override def readIota(stack: ItemStack): Iota =
      Option(readIotaTag(stack)).flatMap(t => Option(deserializeIota(t))).orNull
    override def writeable(stack: ItemStack): Boolean = true
    override def canWrite(stack: ItemStack, iota: Iota): Boolean = iota.isInstanceOf[ListIota] || iota.executable
    override def writeDatum(stack: ItemStack, iota: Iota): Unit =
      val data = Option(blockEntityData(stack)).map(_.copy()).getOrElse(CompoundTag())
      data.put("hex", serializeIota(iota))
      BlockItem.setBlockEntityData(stack, entityType, data)
      clearLegacyBlockEntityData(stack)
    override protected def updateCustomBlockEntityTag(
        pos: BlockPos,
        level: Level,
        player: Player,
        stack: ItemStack,
        state: BlockState
    ): Boolean =
      normalizeBlockEntityData(stack)
      super.updateCustomBlockEntityTag(pos, level, player, stack, state)
    override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit =
      for bet <- Option(blockEntityData(stack)) do
        for case hex: CompoundTag <- Option(bet.get("hex")) do
          tooltip.add(t"Hex: ${displayIotaTag(hex)}")
          for
            case imgData: CompoundTag <- Option(bet.get("image"))
            case frameData: ListTag <- Option(bet.get("frames"))
            frameDataSeq = frameData.collect { case c: CompoundTag => c }.toSeq
            if frameDataSeq.nonEmpty
          do
            tooltip.add(t"Active".styled(_.withColor(0x00d996)))
            def mapStyle(text: Component)(f: Style => Style): TailCalls.TailRec[Component] =
              val text2 = text.plainCopy()
              text2.setStyle(f(text.getStyle))
              (text.getSiblings :\ TailCalls.done(text2)): (curr, next) =>
                mapStyle(curr)(f).flatMap: newCurr =>
                  text2.append(newCurr)
                  next
            tooltip.addAll(imgData.getList("stack", Tag.TAG_COMPOUND).toVector.reverseIterator.collect { case c: CompoundTag => c }.take(7).map(displayIotaTag).zipWithIndex.map { case (text, n) =>
              val progress = Mth.lerp(n/10f, 0xFF, 0x00).toInt
              val aprogress = Mth.lerp(n/7f, 0xFF, 0x00).toInt
              val scalar = (aprogress << 24) | (progress << 16) | (progress << 8) | progress
              mapStyle(text)(s => if s.getColor != null then
                s.withColor(FastColor.ARGB32.multiply(s.getColor.getValue, scalar))
              else s).result })
  val delegate = Item(Item.Properties())
  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = entityType.create(pos, state)
  override def getTicker[T <: BlockEntity](world: Level, state: BlockState, `type`: BlockEntityType[T]): BlockEntityTicker[T] =
    case (_, _, _, e: Entity) => e.tick()
    case a => summon[Logger].warn(s"Mismatch in CastingEngine ticker ${`type`} $a")
  override def useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult =
    val paw = Paw.MAIN_HAND
    if hit.getDirection == Direction.UP || (hit.getLocation.getY - Vec3.atBottomCenterOf(pos).getY) >= 0.62 then
      world.getBlockEntity(pos) match
        case e: Entity =>
          if player.isShiftKeyDown then
            world.playSound(player, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1, 2)
            if e.state.isDefined then
              world.playSound(player, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1, 2)
              if !world.isClient then
                e.state = None
                e.markDirty()
            if !world.isClient then
              player.displayClientMessage(Component.literal("Engine cleared").styled(_.withColor(ChatFormatting.GRAY)), true)
              world.asInstanceOf[ServerLevel].sendBlockUpdated(pos, state, state, 3)
            InteractionResult.SUCCESS
          else
            e.state match
              case Some(susp, Some(cast)) =>
                world.playSound(player, pos, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 1, 2)
                if !world.isClient then
                  e.state = Some(susp, None)
                  e.markDirty()
                  player.displayClientMessage(Component.literal("Engine stopped").styled(_.withColor(ChatFormatting.GRAY)), true)
                  world.asInstanceOf[ServerLevel].sendBlockUpdated(pos, state, state, 3)
                InteractionResult.SUCCESS
              case Some(susp, None) =>
                world.playSound(player, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1, 2)
                if !world.isClient then
                  e.state = Some(susp, Some(player.getUUID, player.getMainArm ^ paw))
                  e.markDirty()
                  player.displayClientMessage(Component.literal("Engine resumed").styled(_.withColor(ChatFormatting.GRAY)), true)
                  world.asInstanceOf[ServerLevel].sendBlockUpdated(pos, state, state, 3)
                InteractionResult.SUCCESS
              case None => e.hexTag match
                case Some(hexTag) =>
                  world.playSound(player, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1, 2)
                  world.playSound(player, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1)
                  if !world.isClient then
                    given ServerLevel = world.asInstanceOf[ServerLevel]
                    val iota = org.eu.net.pool.phlib.deserialize(hexTag, summon).asInstanceOf[Iota]
                    e.state = Some(e.Suspension(CastingImage(), Seq(FrameEvaluate(iota match { case l: ListIota => l.getList; case i => TreeList.from(Seq(i).asJava) }, false))), Some(player.getUUID, player.getMainArm ^ paw))
                    e.markDirty()
                    player.displayClientMessage(Component.literal("Engine started").styled(_.withColor(ChatFormatting.GRAY)), true)
                    world.asInstanceOf[ServerLevel].sendBlockUpdated(pos, state, state, 3)
                  InteractionResult.SUCCESS
                case None =>
                  world.playSound(player, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1, 2)
                  world.playSound(player, pos, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1, 2)
                  if !world.isClient then
                    player.displayClientMessage(Component.literal("Missing iota").styled(_.withColor(ChatFormatting.RED)), true)
                  InteractionResult.FAIL
    else
      InteractionResult.PASS
  def pat(suffix: String) = se"eedadda$suffix"
  phlib.Patterns.register("engine/pos", pat("wdd")):
    phlib.Patterns.mkLiteral(if Access.scope.world == summon[CastingEnvironment].getWorld then Vec3Iota(Vec3.atCenterOf(Access.scope.pos)) else NullIota())
  phlib.Patterns.register("engine/terminate", pat("edaadee")):
    phlib.Patterns.mkAction: (img, cont) =>
      Access.scope.terminate()
  phlib.Patterns.register("engine/suspend", pat("edaqdee")):
    phlib.Patterns.mkAction: (img, cont) =>
      Access.scope.suspend(img, cont)
  phlib.Patterns.register("engine/sleep", pat("wdwaaw")):
    phlib.Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq match
        case Seq() => throw MishapNotEnoughArgs(expected = 1, got = 0)
        case newStack :+ tickIota =>
          val ticks = OperatorUtils.getPositiveInt(Seq(tickIota), 0, 1)
          Access.scope.sleep(img(stack = newStack), cont, ticks)
object registerHopperEndpoint extends (() => Unit):
  def apply(): Unit = HexicalCompat.registerHopperEndpoints()

extension [A, B] (p: (A, B))
  infix def both[R, S](f: (A => R) & (B => S)): (R, S) = (f(p._1), f(p._2))
trait IotaCoercion[T]:
  typ: IotaType[I] =>
  // need _root_ path, since `typ` could theoretically have these as members
  type I <: _root_.at.petrak.hexcasting.api.casting.iota.Iota
  def foo: Unit = ()
def downcast[R: ClassTag](t: Any): Option[R] = t match
  case r: R => Some(r)
  case _ => None
