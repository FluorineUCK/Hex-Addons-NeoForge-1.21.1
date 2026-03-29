//noinspection NotImplementedCode
package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.addldata.ADMediaHolder
import at.petrak.hexcasting.api.casting.{ActionRegistryEntry, OperatorUtils, ParticleSpray, RenderedSpell, SpellList}
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction, OperationAction, SpecialHandler, SpellAction}
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect.DoMishap
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, ContinuationFrame, FrameEvaluate, FrameFinishEval, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, CastingEnvironmentComponent, MishapEnvironment, OperationResult, ResolvedPattern, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapBadCaster, MishapInvalidIota, MishapInvalidOperatorArgs, MishapNotEnoughArgs, MishapOthersName, MishapTooManyCloseParens, MishapBadOffhandItem as MishapBadOffpawItem}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.{HexUtils, MediaHelper}
import at.petrak.hexcasting.common.lib.{HexAttributes, HexItems, HexRegistries, HexSounds}
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents
import at.petrak.hexcasting.xplat.IXplatAbstractions
import carpet.patches.EntityPlayerMPFake
import com.chocohead.mm.api.ClassTinkerers
import com.google.gson.{JsonElement, JsonObject}
import com.ibm.icu.util.MeasureUnit
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.{ArgumentType, StringArgumentType}
import com.mojang.brigadier.builder.{ArgumentBuilder, LiteralArgumentBuilder, RequiredArgumentBuilder}
import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.{Codec, DynamicOps, JsonOps, Lifecycle}
import com.samsthenerd.inline.api.data.ItemInlineData
import com.sun.nio.file.ExtendedOpenOption
import dev.kineticcat.hexportation.fabric.api.casting.iota.{ConduitIota, StorageViewIota}
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.entity.{EntityComponentFactoryRegistry, EntityComponentInitializer, RespawnCopyStrategy}
import dev.onyxstudios.cca.api.v3.level.{LevelComponentFactoryRegistry, LevelComponentInitializer}
import kotlin.Pair
import kotlin.text.Charsets
import miyucomics.hexcellular.{PropertyIota, StateStorage}
import miyucomics.hexical.features.dyes.DyeIota
import miyucomics.hexical.features.hopper
import miyucomics.hexical.features.hopper.targets.SidedInventoryEndpoint
import miyucomics.hexical.features.hopper.{HopperDestination, HopperEndpoint, HopperEndpointRegistry, HopperEndpointResolver, HopperSource}
import miyucomics.hexical.features.pigments.{PigmentIota, PigmentIotaKt}
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.transfer.v1.fluid.{FluidConstants, FluidVariant}
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant
import net.fabricmc.fabric.api.transfer.v1.transaction.{Transaction, TransactionContext}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.Bootstrap
import net.minecraft.block.{AbstractBlock, Block, BlockRenderType, BlockState, BlockWithEntity, DispenserBlock, ShapeContext}
import net.minecraft.command.argument.{EntityArgumentType, NbtElementArgumentType, UuidArgumentType}
import net.minecraft.command.{CommandException, EntitySelector}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluid
import net.minecraft.inventory.{SidedInventory, StackReference}
import net.minecraft.item.{BlockItem, Item, ItemStack, ItemUsageContext, Items}
import net.minecraft.nbt.*
import net.minecraft.nbt.visitor.StringNbtWriter
import net.minecraft.registry.tag.TagKey
import net.minecraft.registry.{MutableRegistry, Registries, Registry, RegistryKey, RegistryKeys, SimpleRegistry}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.{ServerPlayNetworkHandler, ServerPlayerEntity}
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.{HoverEvent, LiteralTextContent, MutableText, Style, Text, TextColor, TextContent, Texts}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.{BlockPointer, BlockPos, ChunkPos, Direction, Vec3d}
import net.minecraft.util.{ActionResult, Arm, ClickType, DyeColor, Formatting, Identifier, Rarity, TypedActionResult, Util, Uuids, WorldSavePath, Hand as Paw}
import net.minecraft.world.biome.Biome
import net.minecraft.world.{BlockView, TeleportTarget, World}
import org.eu.net.pool.hexic
import org.eu.net.pool.hexic.ducks.SimpleRegistryDuck
import org.objectweb.asm.{ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, InsnList}
import org.slf4j.{Logger, LoggerFactory}
import org.spongepowered.asm.mixin.injection.callback.{CallbackInfo, CallbackInfoReturnable}
import ram.talia.hexal.api.casting.iota.{GateIota, MoteIota}
import ram.talia.moreiotas.api.casting.iota.{EntityTypeIota, IotaTypeIota, ItemStackIota, ItemTypeIota, MatrixIota, StringIota}

import java.io.{File, FileNotFoundException, FileOutputStream, IOException, InputStream}
import java.lang.invoke.MethodHandles
import java.lang.reflect.{Constructor, Field, Member, Method}
import java.nio.ByteBuffer
import java.nio.file.{FileSystemException, Files, Path, Paths, StandardOpenOption}
import java.util.{Optional, UUID}
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{elidable, experimental, showAsInfix, tailrec, targetName, unused}
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler
import net.fabricmc.fabric.api.networking.v1.{FabricPacket, PacketByteBufs, PacketSender, PacketType, ServerPlayNetworking}
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.Direction.Axis

import java.util as ju
import scala.math.Ordered.orderingToOrdered
import scala.util.CommandLineParser.FromString
import scala.util.boundary.Label
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type
import at.petrak.hexcasting.api.item.{IotaHolderItem, MediaHolderItem}
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.common.items.magic.{ItemMediaHolder, ItemPackagedHex}
import at.petrak.hexcasting.common.msgs.{MsgClearSpiralPatternsS2C, MsgNewSpiralPatternsS2C, MsgOpenSpellGuiS2C}
import at.petrak.hexcasting.fabric.cc.adimpl.CCMediaHolder
import kotlin.jvm.internal.DefaultConstructorMarker
import net.minecraft.client.item.{BundleTooltipData, TooltipContext, TooltipData}
import net.minecraft.entity.{Entity, LivingEntity}
import net.minecraft.screen.slot.Slot
import net.minecraft.sound.{BlockSoundGroup, SoundCategory, SoundEvents}
import net.minecraft.util.collection.DefaultedList
import org.eu.net.pool.hexic.MediaBundle.{DUST_AMOUNT, PERCENTAGE}

import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.function.Predicate
import scala.quoted.Quotes
import java.io.Writer
import java.io.OutputStreamWriter
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.entity.passive.FoxEntity
import net.minecraft.stat.Stats
import org.eu.net.pool.hexic.mixin.{ItemStackAccess, LivingEntityAccess}
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.common.casting.actions.spells.{OpBreakBlock, OpErase, OpPotionEffect}
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.decoration.ItemFrameEntity
import at.petrak.hexcasting.api.casting.castables.SpellAction
import gay.`object`.ioticblocks.api.IoticBlocksAPI
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.{Operation, WrapOperation}
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.entity.BlockEntity
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.shape.{VoxelShape, VoxelShapes}
import org.spongepowered.asm.mixin.Mixin

import scala.collection.immutable.BitSet
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType

import scala.util.matching.Regex
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage.ParenthesizedIota
import at.petrak.hexcasting.interop.inline.InlinePatternData
import dev.emi.trinkets.api
import dev.emi.trinkets.api.{Trinket, TrinketComponent, TrinketEnums, TrinketsApi}
import net.beholderface.oneironaut.casting.iotatypes.DimIota
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.dispenser.ItemDispenserBehavior
import net.minecraft.world.gen.chunk.{ChunkGenerator, ChunkGenerators}
import xyz.nucleoid.fantasy.util.VoidChunkGenerator
import xyz.nucleoid.fantasy.{Fantasy, RuntimeWorldConfig, RuntimeWorldHandle}

import java.nio.charset.StandardCharsets
import java.math.BigInteger
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.damage.DamageSources
import net.minecraft.entity.effect.{StatusEffectInstance, StatusEffects}
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.world.chunk.{ChunkStatus, WorldChunk}

import scala.concurrent.duration.Duration
import phlib.{Events as PhEvents, *, given}

private[hexic] given Logger = LoggerFactory.getLogger("hexic")
private[hexic] given Conversion[String, Identifier] = Identifier.of("hexic", _)

extension (i: Iota)
  def asIotaType[T <: Iota: ClassTag](idx: Int, expected: => Text): T = i match
    case i: T => i
    case _ => throw MishapInvalidIota(i, idx, expected)
  def asIotaType[T <: Iota: ClassTag: IotaType](idx: Int): T = i.asIotaType[T](idx, summon[IotaType[T]].typeName)
  def asValue[T: FromIota](idx: Int, expected: => Text): T = summon[FromIota[T]].convert(i).getOrElse(throw MishapInvalidIota(i, idx, expected))

extension (c: NbtCompound)
  def iota(using ServerWorld): Iota = IotaType.deserialize(c, summon)

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
    (op /: xs)(summon[Outcome[T]](_, _))

given Outcome[OperationResult => OperationResult] = (res, f) => f(res)
given [T: Outcome]: Outcome[Seq[T]] = (res, value) => res -> Outcome(value*)

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

sealed abstract class PropertyAccessIota(name: String, direction: "head" | "tail")(using world: ServerWorld) extends Iota(PropertyAccessIota.Type, ()):
  def property: Iota = StateStorage.Companion.getProperty(world, name)
  def property_=(x: Iota): Unit = StateStorage.Companion.setProperty(world, name, x)
  def toStream(reverseIdx: Int): PropertyAccessIota.Stream
  def toWriter(reverseIdx: Int): PropertyAccessIota.Writer
  override def toleratesOther(iota: Iota): Boolean = ==(iota)
  override def serialize(): NbtCompound =
    val c = NbtCompound()
    c.put("n", name)
    c
object PropertyAccessIota:
  case class Stream(name: String, direction: "head" | "tail")(using ServerWorld) extends PropertyAccessIota(name, direction) with IterableOnce[Iota]:
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
        if l.isEmpty then
          NullIota()
        else
          direction match
            case "head" =>
              property = ListIota(s.tail)
              s.head
            case "tail" =>
              property = ListIota(s.init)
              s.last
    override def serialize(): NbtCompound =
      val c = super.serialize()
      c.put("p", direction match
        case "head" => "← "
        case "tail" => " →")
      if isDev then println(s"Stream($direction) = $c")
      c
    override def toStream(reverseIdx: Int): Stream = this
    override def toWriter(reverseIdx: Int): Writer = throw MishapInvalidIota.ofType(this, reverseIdx, "hexic:writer")
  case class Writer(name: String, direction: "head" | "tail")(using ServerWorld) extends PropertyAccessIota(name, direction):
    override def isTruthy: Boolean = true
    def <<(x: Iota): Unit = property match
      case _: NullIota =>
        property = ListIota(Seq(x))
      case l: ListIota =>
        direction match
          case "head" => property = ListIota(x +: l.getList.toSeq)
          case "tail" => property = ListIota(l.getList.toSeq :+ x)
    override def serialize(): NbtCompound =
      val c = super.serialize()
      c.put("p", direction match
        case "head" => "→ "
        case "tail" => " ←")
      if isDev then println(s"Writer($direction) = $c")
      c
    override def toStream(reverseIdx: Int): Stream = throw MishapInvalidIota.ofType(this, reverseIdx, "hexic:stream")
    override def toWriter(reverseIdx: Int): Writer = this
  object Type extends IotaType[PropertyAccessIota]:
    type A = (String, "add" | "remove", "head" | "tail")
    def split(tag: NbtElement): A =
      val c = tag.downcast[NbtCompound]
      val name = c.getString("n")
      c.getString("p") match
        case "→ " => (name, "add", "head")
        case "← " => (name, "remove", "head")
        case " ←" => (name, "add", "tail")
        case " →" => (name, "remove", "tail")
    override def deserialize(using nbt: NbtElement, world: ServerWorld): PropertyAccessIota =
      val a: A = split(nbt)
      a._2 match
        case "add" => Writer(a._1, a._3)
        case "remove" => Stream(a._1, a._3)
    override def display(tag: NbtElement): Text =
      (split(tag) match
        case (name, "add", "head") => t"→ $name"
        case (name, "add", "tail") => t"$name ←"
        case (name, "remove", "head") => t"← $name"
        case (name, "remove", "tail") => t"$name →"
      ) formatted Formatting.GREEN
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
  val player: PlayerEntity,
  var leftWeave: ItemStack = ItemStack.EMPTY,
  var rightWeave: ItemStack = ItemStack.EMPTY,
  var foxType: Option[FoxEntity.Type] = None,
) extends Component, AutoSyncedComponent:
  override def readFromNbt(c: NbtCompound): Unit =
    if c.contains("shl", NbtElement.COMPOUND_TYPE) then
      leftWeave = ItemStack.fromNbt(c.getCompound("shl"))
    else
      leftWeave = ItemStack.EMPTY
    if c.contains("shr", NbtElement.COMPOUND_TYPE) then
      rightWeave = ItemStack.fromNbt(c.getCompound("shr"))
    else
      rightWeave = ItemStack.EMPTY
    if c.contains("fox", NbtElement.STRING_TYPE) then
      foxType = Some(FoxEntity.Type.valueOf(c.getString("fox")))
    else
      foxType = None
  override def writeToNbt(c: NbtCompound): Unit =
    if !leftWeave.isEmpty then c.put("shl", NbtCompound().tap(leftWeave.writeNbt))
    if !rightWeave.isEmpty then c.put("shr", NbtCompound().tap(rightWeave.writeNbt))
    foxType.fold(c.remove("fox"))(f => c.putString("fox", f.name))
object PlayerInfoComponent:
  given key: ComponentKey[PlayerInfoComponent] = ComponentRegistry.getOrCreate("player_wisp", classOf[PlayerInfoComponent])
  given Conversion[PlayerEntity, PlayerInfoComponent] = _.getComponent(key)
  private[hexic] def register(using fac: EntityComponentFactoryRegistry) =
    fac.registerForPlayers(key, PlayerInfoComponent(_), RespawnCopyStrategy.LOSSLESS_ONLY)

class ServerInfoComponent() extends Component, AutoSyncedComponent:
  override def readFromNbt(tag: NbtCompound): Unit = ()
  override def writeToNbt(tag: NbtCompound): Unit = ()
object ServerInfoComponent:
  given key: ComponentKey[ServerInfoComponent] = ComponentRegistry.getOrCreate("server_info", classOf[ServerInfoComponent])
  given get: (server: MinecraftServer) => ServerInfoComponent = server.getOverworld.getLevelProperties.getComponent(key)
  def sync()(using server: MinecraftServer): Unit = server.getOverworld.getLevelProperties.syncComponent(key)
  private[hexic] def register(using fac: LevelComponentFactoryRegistry) =
    fac.register(key, _ => ServerInfoComponent())

class ExcursionComponent(var enteredDemiplaneTick: Long = 0, var excursion: Option[(RegistryKey[World], Vec3d)] = None) extends Component:
  override def readFromNbt(tag: NbtCompound): Unit =
    enteredDemiplaneTick = tag.getLong("grace")
    excursion = for
      case c: NbtString <- Option(tag.get("dim"))
      id <- Option(Identifier.tryParse(c.asString))
    yield (RegistryKey.of(RegistryKeys.WORLD, id), Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")))
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putLong("grace", enteredDemiplaneTick)
    for world -> pos <- excursion do
      tag.putString("dim", world.getValue.toString)
      tag.putDouble("x", pos.getX)
      tag.putDouble("y", pos.getY)
      tag.putDouble("z", pos.getZ)
object ExcursionComponent:
  given key: ComponentKey[ExcursionComponent] = ComponentRegistry.getOrCreate("excursion", classOf[ExcursionComponent])

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
  val os = Util.getOperatingSystem match
    case Util.OperatingSystem.LINUX => "linux"
    case Util.OperatingSystem.WINDOWS => "windows"
    case Util.OperatingSystem.OSX => "darwin"
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

trait ServerAware[T <: IotaType[?]]:
  type Iota = T match { case IotaType[t] => t }
  def setServer(iota: Iota, server: String): Unit

given [T <: java.lang.Enum[T]: ClassTag as ct] => FromString[T]:
  override def fromString(s: String): T = Enum.valueOf[T](ct.runtimeClass.asInstanceOf[Class[T]], s)

case class Pen private [hexic] (color: DyeColor) extends Item(Item.Settings().maxCount(1)) with Registered(Registries.ITEM, s"pen/$color"):
  override def toString = s"$getClass(color=$color)${super[Item].toString}"
  override def use(world: World, player: PlayerEntity, paw: Paw): TypedActionResult[ItemStack] =
    // if player.getAttributeValue(HexAttributes.FEEBLE_MIND) > 0.0 then
    //   TypedActionResult.fail(player.getStackInHand(paw))
    // else
      if !world.isClient && player.isInstanceOf[ServerPlayerEntity] then
        val serverPlayer: ServerPlayerEntity = player.asInstanceOf[ServerPlayerEntity]
        val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, paw)
        val patterns = IXplatAbstractions.INSTANCE.getPatternsSavedInUi(serverPlayer).asScala
        val descs = vm.generateDescs
        IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer, new MsgOpenSpellGuiS2C(paw, patterns, descs.getFirst, descs.getSecond, 0))
      player.incrementStat(Stats.USED.getOrCreateStat(this))
      TypedActionResult.success(player.getStackInHand(paw))
object Pen:
  val instances: DyeColor :> Pen = DyeColor.values.map(c => c -> new Pen(c)).toMap

trait PenAccess:
  def getPen(color: DyeColor): util.List[HexPattern]

case class Mediaweave(color: DyeColor) extends Item(Item.Settings()) with IotaHolderItem:
  override def readIotaTag(stack: ItemStack): NbtCompound | Null =
    stack.getNbt match
      case null => null
      case c => c.get("Hex") match
        case c: NbtCompound => c
        case _ => null
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
      case iota => stack.getOrCreateNbt.put("Hex", IotaType.serialize(iota))
  override def appendTooltip(stack: ItemStack, world: World, tooltip: util.List[Text], context: TooltipContext): Unit =
    IotaHolderItem.appendHoverText(this, stack, tooltip, context)
  DispenserBlock.registerBehavior(this, new ItemDispenserBehavior:
    override def dispenseSilently(pointer: BlockPointer, stack: ItemStack): ItemStack =
      val pos = pointer.getPos.offset(pointer.getBlockState.get(DispenserBlock.FACING))
      val candidates = pointer.getWorld.getEntitiesByClass(classOf[LivingEntity], net.minecraft.util.math.Box(pos), EntityPredicates.EXCEPT_SPECTATOR)
      for
        candidate <- candidates
        component_? = TrinketsApi.getTrinketComponent(candidate)
        if component_?.isPresent
        component = component_?.get()
        trinkets = component.getInventory
        group <- Option(trinkets.get("chest"))
        inventory <- Option(group.get("hexic_mediaweave"))
        i <- 0 until inventory.size
        if inventory.getStack(i).isEmpty
      do
        inventory.setStack(i, stack.split(1))
        return stack
      super.dispenseSilently(pointer, stack)
    )
  TrinketsApi.registerTrinket(this, Mediaweave.trinket)
object Mediaweave:
  val colors: DyeColor :> Mediaweave = DyeColor.values().map(c => c -> Mediaweave(c)).toMap
  val tag: TagKey[Item] = TagKey.of(Registries.ITEM, "mediaweaves")
  object trinket extends Trinket:
    override def canUnequip(stack: ItemStack, slot: api.SlotReference, entity: LivingEntity): Boolean = Option(stack).flatMap(s => Option(s.getNbt)).forall(_.get("lock") == null)
    override def getDropRule(stack: ItemStack, slot: api.SlotReference, entity: LivingEntity): TrinketEnums.DropRule =
      if Option(stack).flatMap(s => Option(s.getNbt)).exists(_.get("lock") == null) then
        TrinketEnums.DropRule.KEEP
      else
        super.getDropRule(stack, slot, entity)

extension (x: Iterable[Boolean])
  def any: Boolean = x.exists(identity)
  def all: Boolean = x.forall(identity)

object ItemStackAccess:
  def unapply(s: ItemStack): Some[(Item, Int, Option[NbtCompound])] = Some((s.getItem, s.getCount, Option(s.getNbt)))

case class MediaBundle(color: DyeColor, size: Int) extends Item(Item.Settings().maxCount(1)) with MediaHolderItem:
  extension (stack: ItemStack)
    private def heldItems: Seq[ItemStack] =
      Option(stack.getNbt)
        .map(_.get("Contents"))
        .collect:
          case list: NbtList => list.collect:
            case c: NbtCompound => ItemStack.fromNbt(c)
        .getOrElse(Seq(ItemStack(Items.AMETHYST_SHARD)))
        .toSeq
    private def heldItems_=(x: Seq[ItemStack]): Unit =
      stack.getOrCreateNbt.put("Contents", NbtList().tap: l =>
        for item <- x if !item.isEmpty do
          val c = NbtCompound()
          item.writeNbt(c)
          l.add(c)
      )
    private def isWaxed = Option(stack.getNbt).exists(_.contains("ro"))
    private def isWaxed_=(value: Boolean) =
      if value then
        stack.getOrCreateNbt.put("ro", NbtCompound())
      else
        stack.getOrCreateNbt.remove("ro")
    private def withMediaHolders[T](f: Seq[CCMediaHolder] => T): T =
      if stack.isWaxed then
        f(Seq())
      else
        val heldItems = stack.heldItems
        try
          f(heldItems.flatMap(p => Option(HexCardinalComponents.MEDIA_HOLDER.getNullable(p))))
        finally
          stack.heldItems = heldItems
    private def mediaHolders = stack.heldItems.flatMap(p => Option(HexCardinalComponents.MEDIA_HOLDER.getNullable(p)))
  override def getMedia(stack: ItemStack): Long = stack.mediaHolders.map(_.getMedia).sum
  override def getMaxMedia(stack: ItemStack): Long = stack.mediaHolders.map(_.getMaxMedia).sum
  override def setMedia(staeck: ItemStack, media: Long): Unit = throw IllegalCallerException()
  override def canProvideMedia(stack: ItemStack): Boolean = stack.mediaHolders.exists(_.canProvide)
  override def canRecharge(stack: ItemStack): Boolean = stack.mediaHolders.exists(_.canRecharge)
  override def insertMedia(stack: ItemStack, amount: Long, simulate: Boolean): Long =
    stack.withMediaHolders: h =>
      var total: Long = 0
      for (_, holders) <- h.groupBy(_.getConsumptionPriority).toSeq.sortBy(_._1).reverse do
        var rem = holders
        while rem.nonEmpty do
          val cur = rem.head
          val ext = cur.insertMedia((amount - total) / rem.size, simulate)
          total += ext
          if total >= amount then return total
          rem = rem.tail
      total
  override def withdrawMedia(stack: ItemStack, amount: Long, simulate: Boolean): Long =
    stack.withMediaHolders: h =>
      var total: Long = 0
      for (_, holders) <- h.groupBy(_.getConsumptionPriority).toSeq.sortBy(_._1).reverse do
        var rem = holders
        while rem.nonEmpty do
          val cur = rem.head
          val ext = cur.withdrawMedia((amount - total) / rem.size, simulate)
          total += ext
          if total >= amount then
            if total > amount then total -= insertMedia(stack, total - amount, simulate)
            return total
          rem = rem.tail
      total
  override def onClicked(stack: ItemStack, otherStack: ItemStack, slot: Slot, clickType: ClickType, player: PlayerEntity, cursorStackReference: StackReference): Boolean =
    if clickType == ClickType.RIGHT then
      if otherStack.isEmpty then
        val held = stack.heldItems
        held.headOption.foreach: p =>
          cursorStackReference.set(p)
          stack.heldItems = held.tail
          player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if otherStack.isOf(Items.HONEYCOMB) && !stack.isWaxed then
        stack.isWaxed = true
        otherStack.decrement(1)
        player.playSound(SoundEvents.ITEM_HONEYCOMB_WAX_ON, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if otherStack.isOf(Items.WET_SPONGE) && stack.isWaxed then
        stack.isWaxed = false
        player.playSound(SoundEvents.BLOCK_SLIME_BLOCK_PLACE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if HexCardinalComponents.MEDIA_HOLDER.getNullable(otherStack) != null then
        val held = stack.heldItems
        if fits(held, otherStack.getItem) then
          stack.heldItems = otherStack.copyAndEmpty() +: held
          player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      true
    else
      false
  private def fits(held: Seq[ItemStack], subj: Item): Boolean =
    val cur = held.map(_.getItem match { case b: MediaBundle => b.size/2; case _ => 1 }).sum
    subj match
      case MediaBundle(_, subjSize) => subjSize < size && cur + subjSize/2 <= size
      case _ => cur < size
  override def onStackClicked(stack: ItemStack, slot: Slot, clickType: ClickType, player: PlayerEntity): Boolean =
    if clickType == ClickType.RIGHT then
      if slot.getStack.isEmpty then
        val held = stack.heldItems
        held.headOption.foreach: p =>
          slot.setStack(p)
          stack.heldItems = held.tail
          player.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      else if HexCardinalComponents.MEDIA_HOLDER.getNullable(slot.getStack) != null then
        val held = stack.heldItems
        if fits(held, slot.getStack.getItem) then
          stack.heldItems = slot.getStack.copyAndEmpty() +: held
          player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      true
    else
      false
  override def getTooltipData(stack: ItemStack): Optional[TooltipData] = Optional.of(BundleTooltipData(DefaultedList.copyOf(ItemStack.EMPTY, stack.heldItems*), stack.heldItems.size))
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
  override def appendTooltip(stack: ItemStack, world: World, tooltip: util.List[Text], context: TooltipContext): Unit =
    tooltip.add(Text.translatable("hexic.media_bundle.items", stack.heldItems.size, size).styled(_.withColor(Formatting.GRAY)))
    val (consumables, batteries, trinkets) = getMediaInfo(stack)
    val isWaxed = stack.isWaxed
    var mentionedSealing = false
    def convertForWaxing(text: MutableText) =
      if isWaxed then
        mentionedSealing = true
        Text.empty().append(text.styled(_.withStrikethrough(true))).append(" ").append(Text.translatable("hexcasting.tooltip.spellbook.sealed").formatted(Formatting.GOLD))
      else
        text
    batteries match
      case Some((total, max)) => tooltip.add(showMedia("external", total + consumables.getOrElse(0L), max))
      case None => for value <- consumables do
        tooltip.add(convertForWaxing(showMedia("external", value)))
    for (total, max) <- trinkets do
      tooltip.add(convertForWaxing(showMedia("internal", total, max)))
    if !mentionedSealing then
      tooltip.add(Text.translatable("hexcasting.tooltip.spellbook.sealed").formatted(Formatting.GOLD))
  private def showMedia(tag: String, media: Long) = Text.translatable("hexic.media.infinite", Text.translatable(s"hexic.media.$tag"), Text.translatable("hexcasting.tooltip.media", dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR))))
  private def showMedia(tag: String, media: Long, maxMedia: Long) = Text.translatable("hexic.media.finite", Text.translatable(s"hexic.media.$tag"), dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Text.translatable("hexcasting.tooltip.media", dustAmount(maxMedia)).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Text.literal(PERCENTAGE.format(100.0 * media / maxMedia)+"%").styled(_.withColor(MediaHelper.mediaBarColor(media, maxMedia))))
  private def dustAmount(media: Long) = Text.literal(DUST_AMOUNT.format(media / MediaConstants.DUST_UNIT.toDouble))

extension [T] (s: => Seq[T]) def *^(n: Int) = Seq.fill(n)(()).flatMap((_) => s)
class Stringworm extends Item(Stringworm.settings)
object Stringworm:
  val settings = Item.Settings().maxCount(16)
  val flavors = Seq("pure", "action", "hex", "media", "thing")
  val biasedFlavors = "pure" +: Seq("action", "hex", "media", "thing") *^ 3
  def randomFlavor(using rng: net.minecraft.util.math.random.Random) = items(biasedFlavors(rng.nextInt(biasedFlavors.size)))
  val items =
    Stringworm.flavors.map(_ -> new Stringworm).toMap
export Stringworm.items as stringworms

object dyedStringworm extends Stringworm:
  override def getName(stack: ItemStack): Text =
    stack.getSubNbt("pigment") match
      case null => super.getName(stack)
      case n => Text.translatable("item.hexic.stringworm." + FrozenPigment.fromNBT(n).item.getTranslationKey)

def toRoman(value: Int): String =
  "M" * (value / 1000) + ("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM").productElement(value % 1000 / 100) + ("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC").productElement(value % 100 / 10) + ("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX").productElement(value % 10)

private [hexic] object Extern:
  private [hexic] val worlds = mutable.Set[WeakReference[World]]()
  private [hexic] def getWorld(biome: Biome): World =
    worlds.collect:
      case WeakReference(world) if world.getRegistryManager.get(RegistryKeys.BIOME).getId(biome) != null => return world
      case dropped => worlds -= dropped
    null
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
          OperatorSideEffect.DoMishap(m, Mishap.Context(p, Text.translatable("hexcasting.action.hexic:parenthesize"))).performEffect(safeVM)
          boundary.break(Some(safeVM.getImage, ResolvedPatternType.ERRORED))
        val img = vm.getImage
        val parens = img.getParenCount
        if parens == size then
          img.getStack.toSeq match
            case Seq() => mishap(MishapNotEnoughArgs(1, 0))
            case tail :+ head => Some((
              CastingImage(
                stack = tail,
                parenCount = parens,
                parenthesized = img.getParenthesized :+ ParenthesizedIota(head, false),
                escapeNext = false,
                opsConsumed = img.getOpsConsumed,
                userData = img.getUserData,
                null
              ),
              ResolvedPatternType.EVALUATED
            ))
        else if parens > size then
          None // leave unescaped, so a nested hex can introject
        else
          mishap(new Mishap:
            override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.ORANGE)
            override def errorMessage(env: CastingEnvironment, ctx: Context): Text = ???
            override def execute(env: CastingEnvironment, ctx: Context, stack: util.List[Iota]): Unit =
              stack.add(PatternIota(p))
          )
      case "eadedae" =>
        val img = vm.getImage
        Some(CastingImage(img.getStack:+ListIota(img.getParenthesized.map(_.getIota)):+DoubleIota(img.getParenCount), 0, Seq(), false, img.getOpsConsumed, img.getUserData, null), ResolvedPatternType.EVALUATED)
      case _ => None
  private [hexic] def getPocketName(pocket: String) = Text.of("Demiplane " + pocketNames(getPocketID(Identifier.tryParse(pocket)).get))

val _ =
  Interop.playerDeathHook = (p: PlayerEntity, out: util.List[ItemStack]) =>
    val c = p: PlayerInfoComponent
    if !c.rightWeave.isEmpty then
      out.add(c.rightWeave)
      c.rightWeave = ItemStack.EMPTY
    if !c.leftWeave.isEmpty then
      out.add(c.leftWeave)
      c.leftWeave = ItemStack.EMPTY

given demiplaneExtensions: AnyRef with
  extension (w: ServerWorld)
    def meta: String MutableFunction Option[String] =
      val path = w.getServer.getSavePath(WorldSavePath.ROOT).resolve(s"dimensions/${w.getRegistryKey.getValue.getNamespace}/${w.getRegistryKey.getValue.getPath}")
      new MutableFunction:
        def apply(name: String) = try Option(Files.getAttribute(path, "user:" + name)).asInstanceOf[Option[Array[Byte]]].map(buf => String(buf, StandardCharsets.UTF_8)) catch case _: FileSystemException => None
        def update(name: String, value: Option[String]): Unit = Files.setAttribute(path, "user:" + name, value.map(StandardCharsets.UTF_8.encode).orNull)
    def parentInfo: Option[(RegistryKey[World], BlockPos)] = for
      parentStr <- w.meta("parent")
      parentId <- Option(Identifier.tryParse(parentStr))
      parentKey = RegistryKey.of(RegistryKeys.WORLD, parentId)
      if w.getServer.getWorld(parentKey) ne null
      x <- w.meta("bound_x").flatMap(v => Try(Integer.parseInt(v)).toOption)
      y <- w.meta("bound_y").flatMap(v => Try(Integer.parseInt(v)).toOption)
      z <- w.meta("bound_z").flatMap(v => Try(Integer.parseInt(v)).toOption)
    yield parentKey -> BlockPos(x, y, z)
    def parentInfo_=(parent: Option[(RegistryKey[World], BlockPos)]) =
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
  def logExcursion(sp: ServerPlayerEntity) =
    val c = sp.component[ExcursionComponent]
    val curDim = sp.getWorld.getRegistryKey
    c.excursion = Some(curDim, sp.getPos)
    c.enteredDemiplaneTick = sp.getWorld.getTime
    sp.syncComponent(ExcursionComponent.key)
  def getDefaultExcursion(/** must be a demiplane */ world: ServerWorld): (ServerWorld, Vec3d) =
    locally:
      // if the plane is bound, where it's bound to is the default excursion
      for
        case (key, pos) <- world.parentInfo
        boundWorld <- Option(world.getServer.getWorld(key))
      yield
        (boundWorld, Vec3d.ofBottomCenter(pos))
    .getOrElse:
      // idfk just go to spawn or something
      // someone would never do something silly like setting the world spawn to y 10000 or something like that riiiight?
      (world.getServer.getOverworld, Vec3d.ofBottomCenter(world.getServer.getOverworld.getSpawnPos))
  def findExcursion(/* must be in a demiplane */ sp: ServerPlayerEntity): (ServerWorld, Vec3d) =
    locally:
      // the happy path, assuming we have a player involved
      for
        case (key, pos) <- sp.component[ExcursionComponent].excursion
        world <- Option(sp.getServer.getWorld(key))
      yield
        (world, pos)
    .getOrElse:
      given_Logger.warn(s"$sp claims to have never entered a demiplane")
      getDefaultExcursion(sp.getServerWorld)
  def findExcursion(target: Entity)(using env: CastingEnvironment): (ServerWorld, Vec3d) =
    env match
      case p: PlayerBasedCastEnv =>
        val (world, pos) = findExcursion(p.getCaster)
        world -> pos.add(target.getPos).subtract(p.getCaster.getPos)
      case _ => target match
        case p: ServerPlayerEntity => findExcursion(p)
        case _ => target.getWorld match
          case sw: ServerWorld => getDefaultExcursion(sw)
          case _ => throw new IllegalStateException("why are you casting Spatial Interchange client-sided")
  def sendDirectlyToHell(sp: ServerPlayerEntity) =
    val c = sp.component[ExcursionComponent]
    if c.enteredDemiplaneTick + cfg[Int]("hexic.demiplaneGracePeriod").getOrElse(5) > sp.getWorld.getTime then
      given_Logger.warn(s"$sp found outside kitchen")
      sp.requestTeleport(5.5, 1.0, 5.5)
    else
      given_Logger.warn(s"$sp broke out of cell ${sp.getWorld.getRegistryKey}! mods, please check if the borders are broken")
      val (world, pos) = findExcursion(sp)
      val sp2 = FabricDimensions.teleport(sp, world, TeleportTarget(pos, Vec3d.ZERO, sp.getYaw, sp.getPitch))
      sp2.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 30*20))
      sp2.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 30*20))
      sp2.damage(sp2.getWorld.getDamageSources.outOfWorld, sp2.getMaxHealth / 2)

given Codec[Int] = Codec.INT.xmap(p => p, p => p)

type Media = Long
object MediaBundle:
  val items: Seq[MediaBundle] = for i <- Seq(6, 12); c <- DyeColor.values yield new MediaBundle(c, i)
  def apply(c: DyeColor, s: Int) = items.find(b => b.color == c && b.size == s).get
  private val PERCENTAGE = new DecimalFormat("####")
  PERCENTAGE.setRoundingMode(RoundingMode.DOWN)
  private val DUST_AMOUNT = new DecimalFormat("###,###.##")
val wizard = Item(Item.Settings().rarity(Rarity.EPIC).maxCount(1))

val aLotOfMedia = (200000 /* max phial size */ * 6 /* phials per small pouch */ * 4 /* small pouches per large pouch */ * (36 /* inventory slots */ + 4 /* armor slots */ + 2 /* offpaws */) + 20 /* healthcasting */) * MediaConstants.DUST_UNIT

class Event[T, R](default: T => R) extends (T => R):
  private var current = default
  def apply(x: T): R = current(x)
  def apply(fn: PartialFunction[T, R]): Unit =
    val old = current
    current = fn.applyOrElse(_, old)

val useItemEvent = Event[(Item, ItemUsageContext, ItemUsageContext => ActionResult), ActionResult](p => p._3(p._2))

trait HasCodec:
  def getCodec: Codec[? <: this.type]
given [T <: Mishap] => Conversion[T, HasCodec] = _.asInstanceOf

class DeferMut[T](initial: => T):
  private var value = () => initial
  def apply() = value()
  def update(x: => T): Unit = value = () => x

lazy val itemGroup = FabricItemGroup.builder()
  .icon(() => new ItemStack(stringworms("media")))
  .displayName(Text.translatable("itemGroup.hexic.group"))
  .entries: (ctx, entries) =>
    for c <- DyeColor.values do
      entries.add(Mediaweave.colors(c))
      entries.add(MediaBundle(c, 6))
      // entries.add(MediaBundle(c, 12))
      entries.add(Pen.instances(c))
    for f <- Stringworm.flavors do
      entries.add(stringworms(f))
  .build()

val goodModulo = ne"daawdda"
val getEntity: PartialFunction[Iota, Entity] = { case e: EntityIota => e.getEntity }

extension (iota: Iota)
  def executeInPlace(cont: SpellContinuation, cause: Iota = iota)(using vm: CastingVM): CastResult =
    iota match
      case li: ListIota =>
        val l = li.getList
        if l.getNonEmpty then
          l.getCar.execute(vm, vm.getEnv.getWorld, cont.pushFrame(FrameFinishEval.INSTANCE).pushFrame(FrameEvaluate(l.getCdr, true)))
        else
          CastResult(cause, cont, null, Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.NORMAL_EXECUTE)
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
  Interop.thoughtWorld = RegistryKey.of(RegistryKeys.WORLD, "thought")
  iotaTypeRegistry("access") = PropertyAccessIota.Type
  for color -> item <- Mediaweave.colors do Registries.ITEM(s"${color.asString}_mediaweave") = item
  for item <- MediaBundle.items do
    Registries.ITEM(item.size match
      case 6 => s"small_${item.color.asString}_bundle"
      case 12 => s"large_${item.color.asString}_bundle") = item
  for (flavor, item) <- stringworms do
    Registries.ITEM(s"stringworm_$flavor") = item
  Pen.instances
  Registries.ITEM("stringworm_pigmented") = dyedStringworm
  Registries.ITEM("wizard") = wizard
  val cutItem = new Item(Item.Settings().maxCount(16)) with MediaHolderItem:
    Registries.ITEM("cut") = this
    override def getMedia(stack: ItemStack): Long = stack.getNbt.getLong("c")
    override def getMaxMedia(stack: ItemStack): Long = stack.getNbt.getLong("c")
    override def setMedia(stack: ItemStack, l: Media): Unit = ()
    override def canProvideMedia(stack: ItemStack): Boolean = true
    override def canRecharge(stack: ItemStack): Boolean = true
    override def getConsumptionPriority(stack: ItemStack): Int = 1100

  Registries.ITEM("chisel") = new Item(Item.Settings().maxCount(1)):
    val Chisel = this
    object table extends BlockWithEntity(AbstractBlock.Settings.create().nonOpaque()):
      private val entityType = FabricBlockEntityTypeBuilder.create(createBlockEntity, table).build()
      Registries.BLOCK_ENTITY_TYPE("chisel_table") = entityType
      override def getRenderType(state: BlockState) = BlockRenderType.MODEL
      sealed trait entity extends BlockEntity:
        var bits: BitSet = BitSet()
        private[table] object bit:
          def apply(x: Int, y: Int): Boolean = bits(x * 16 + y)
          def update(x: Int, y: Int, value: Boolean): Unit =
            if value then
              bits += x * 16 + y
            else
              bits -= x * 16 + y
            markDirty()
      override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        new BlockEntity(entityType, pos, state) with entity:
          override def readNbt(nbt: NbtCompound): Unit =
            bits = BitSet.fromBitMask(nbt.getLongArray("b"))
          override def writeNbt(nbt: NbtCompound): Unit =
            nbt.putLongArray("b", bits.toBitMask)
          override def toUpdatePacket: Packet[ClientPlayPacketListener] = BlockEntityUpdateS2CPacket.create(this)
          override def toInitialChunkDataNbt: NbtCompound = createNbt()

      def findEntity(world: BlockView, pos: BlockPos): Option[entity] =
        world.getBlockEntity(pos) match
          case p: entity => Some(p)
          case p =>
            given_Logger.error(s"Unexpected block entity at $pos for chisel table, got $p (${summon[ClassTag[p.type]]}).")
            None

      val emptyShape = VoxelShapes.union(
        VoxelShapes.cuboid(0.00, 0.00, 0.00, 0.25, 0.50, 0.25),
        VoxelShapes.cuboid(0.75, 0.00, 0.75, 1.00, 0.50, 1.00),
        VoxelShapes.cuboid(0.00, 0.50, 0.00, 1.00, 0.75, 1.00),
        VoxelShapes.cuboid(0.00, 0.75, 0.00, 0.0625, 0.8125, 1.00),
        VoxelShapes.cuboid(0.00, 0.75, 0.00, 1.00, 0.8125, 0.0625),
        VoxelShapes.cuboid(0.00, 0.75, 1.00, 0.9375, 0.8125, 1.00),
        VoxelShapes.cuboid(1.00, 0.75, 0.00, 1.00, 0.8125, 0.9375),
      )
      val chunks = memo: (i: Int) =>
        val x = i / 16
        val z = i % 16
        assume(x < 14 && z < 14)
        val dx = (x + 1) / 16.0
        val dz = (z + 1) / 16.0
        VoxelShapes.cuboid(dx, 0.75, dz, dx + 0.0625, 0.8125, dz + 0.0625)
      val shapes = memo { (bits: BitSet) => VoxelShapes.union(emptyShape, bits.toSeq.map(chunks)*) }

      override def getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        val entity = findEntity(world, pos)
        shapes(entity.fold(BitSet.empty)(_.bits))
      override def getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        getOutlineShape(state, world, pos, context)

      override def onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, paw: Paw, hit: BlockHitResult): ActionResult = boundary:
        lazy val entity = findEntity(world, pos).getOrElse(boundary.break(ActionResult.FAIL))
        player.getStackInHand(paw) match
          case stack@ItemStackAccess(HexItems.CHARGED_AMETHYST, _, _) if !(for i <- 0 until 14; j <- 0 until 14 yield entity.bit(i, j)).all =>
            for i <- 0 until 14; j <- 0 until 14 do
              entity.bit(i, j) = true
            stack.decrement(1)
            ActionResult.SUCCESS
          case stack@ItemStackAccess(_, c, _) if c == 0 && entity.bits.nonEmpty && player.isSneaking =>
            stack.setItem(cutItem)
            stack.setCount(1)
            stack.getOrCreateNbt().putLongArray("b", entity.bits.toBitMask)
            entity.bits = BitSet.empty
            entity.markDirty()
            ActionResult.SUCCESS
          case stack@ItemStackAccess(_: Chisel.type, _, _) =>
            val pos = hit.getPos.add(hit.getSide.getOffsetX * -1/32, hit.getSide.getOffsetY * -1/32, hit.getSide.getOffsetZ * -1/32)
            val x = ((pos.x * 16 % 16 + 16) % 16 - 1).toInt
            val y = ((pos.z * 16 % 16 + 16) % 16 - 1).toInt
            if x >= 0 && y >= 0 && x < 14 && y < 14 then
              if entity.bit(x, y) then
                stack.damage(1, player, { _ => })
                entity.bit(x, y) = false
                ActionResult.SUCCESS
              else
                ActionResult.PASS
            else
              ActionResult.PASS
          case _ => ActionResult.PASS
    Registries.BLOCK("chisel_table") = table
    Registries.ITEM("chisel_table") = BlockItem(table, Item.Settings())
  Registries.ITEM_GROUP("group") = itemGroup
  //Registries.ITEM("echo") = EchoItem
  initChat()
  initMacros()
  initViews()
  if fabric.isModLoaded("hexical") then
    for
      HopperEndpointRegistry <- classNamed("miyucomics.hexical.features.hopper.HopperEndpointRegistry")
      ConduitIota <- classNamed("dev.kineticcat.hexportation.fabric.api.casting.iota.ConduitIota")
      registerHopperEndpoint <- classNamed("org.eu.net.pool.registerHopperEndpoint")
    do
      registerHopperEndpoint.runtimeClass.newInstance().asInstanceOf[() => Unit]()
    Patterns.register("dye_offpaw", w"eqdeeqdweeqddqdwwdew"):
      Patterns.mkAction: (img, cont) =>
        val stack = img.getStack.asScala
        stack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0)) match
          case p: PigmentIota =>
            val info = summon[CastingEnvironment].getHeldItemToOperateOn(!_.isEmpty).pipe(Option(_)).getOrElse(throw MishapBadOffpawItem(null, Text.translatable("text.hexic.pigment_holder_item")))
            (img.withStack(_.init), cont, HexEvalSounds.SPELL, Seq:
              OperatorSideEffect.AttemptSpell(
                new RenderedSpell:
                  override def cast(env: CastingEnvironment): Unit =
                    info.stack.getItem match
                      case h: PigmentHolderItem => h.setPigment(info.stack)(p.getPigment)
                      case _: Stringworm =>
                        info.stack.setItem(dyedStringworm)
                        info.stack.getOrCreateNbt().put("pigment", p.getPigment.serializeToNBT)
                  override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
                , true, true
              ))
          case i => throw MishapInvalidIota.ofType(i, 0, "pigment")
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
  Patterns.arithmetic("modulo", goodModulo)
  Registry.register(hexXplat.getArithmeticRegistry, "goodModulo": Identifier, arith("goodModulo",
    goodModulo -> ((x: DoubleIota, y: DoubleIota) => Seq(DoubleIota((x.getDouble % y.getDouble + y.getDouble) % y.getDouble))),
    // TODO: vectors
  ))
  Patterns.register("spellmind/save", e"aqqqqqeawqwqwqwqwqweawwqwwqwwqwwqwwqwweawwwqwwwqwwwqwwwqwwwqwww"):
    Patterns.mkAction: (img, cont) =>
      ???
  Patterns.register("spellmind/restore", e"deeeeeqdwewewewewewqdwwewwewwewwewwewwqdwwwewwwewwwewwwewwwewww"):
    Patterns.mkAction: (img, cont) =>
      ???
  def fox(tr: PlayerEntity ?=> PartialFunction[Option[FoxEntity.Type], Option[FoxEntity.Type]]): Action =
    Patterns.mkAction: (img, cont) =>
      img.getStack.lastOption match
        case None => throw MishapNotEnoughArgs(1, 0)
        case Some(getEntity(given ServerPlayerEntity)) =>
          val c: PlayerInfoComponent = summon[PlayerEntity]
          c.foxType match
            case tr(newFoxType) =>
              OperationResult(img.withStack(_.init), Seq(
                OperatorSideEffect.ConsumeMedia(MediaConstants.SHARD_UNIT + MediaConstants.DUST_UNIT),
                OperatorSideEffect.AttemptSpell(
                  new RenderedSpell:
                    override def cast(env: CastingEnvironment): Unit =
                      c.foxType = newFoxType
                      summon[PlayerEntity].syncComponent(PlayerInfoComponent.key)
                    override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
                  , true, true)
              ), cont, HexEvalSounds.SPELL)
            case _ =>
              OperationResult(img.withStack(_.init), Seq(), cont, HexEvalSounds.SPELL)
        case Some(x) => throw MishapInvalidIota(x, 0, "player")
  Patterns.register("fox", se"wqwqeeeweedqqeqwaeeaw"):
    fox:
      case None => Some:
        val p = summon[PlayerEntity]
        FoxEntity.Type.fromBiome(p.getWorld.getBiome(p.getBlockPos))
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
            HexEvalSounds.SPELL,
            for
              (_, stack) <- env.getCaster.equippedMediaweave
              if isDev && { println(s"ok ${stack} how ya ${stack.getNbt} okie ${Option(stack.getNbt).forall(_.get("lock") == null)}"); true }
              if Option(stack.getNbt).forall(_.get("lock") == null)
              e <- Seq(
                consume,
                OperatorSideEffect.AttemptSpell(
                  new RenderedSpell:
                    override def cast(env: CastingEnvironment): Unit =
                      stack.getOrCreateNbt().put("lock", NbtCompound())
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
            HexEvalSounds.SPELL,
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
      val dimID: Identifier = s"fresh-${uuid.toString.replace("-", "")}"
      val handle = Fantasy get server getOrOpenPersistentWorld(dimID, new RuntimeWorldConfig setDimensionType RegistryKey.of(RegistryKeys.DIMENSION_TYPE, "cell") setGenerator new VoidChunkGenerator(server.getOverworld.getRegistryManager get RegistryKeys.BIOME))
      server.submit((() => handle.asWorld.getChunkManager.setChunkForced(ChunkPos.ORIGIN, true)): Runnable)
      handle
    )
  extension (server: MinecraftServer)
    def savedPlanes =
      val file = server.getSavePath(WorldSavePath("fresh"))
      if Files.exists(file) then
        Files.readAllLines(file, StandardCharsets.UTF_8).toSet.filter(!_.isBlank).map(UUID.fromString)
      else
        Set.empty
    def savedPlanes_=(planes: Set[UUID]) =
      val file = server.getSavePath(WorldSavePath("fresh"))
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
          override val getName: Text = Text.translatable("hexcasting.special.hexic:tuple.n", toRoman(size))
      case _ => null
  ): SpecialHandler.Factory[? <: SpecialHandler]
  CommandRegistrationCallback.EVENT.register: (d, r, e) =>
    def planeAction(name: String)(body: MinecraftServer ?=> UUID => Int): Unit =
      d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource](name).pipe: c =>
        c.requires(_.hasPermissionLevel(2))
        c.argument("id", UuidArgumentType.uuid()): c =>
          c.executes: (ctx: CommandContext[ServerCommandSource]) =>
            given MinecraftServer = ctx.getSource.getServer
            body(UuidArgumentType.getUuid(ctx, "id"))
        c.build()
      )
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
        case None => throw CommandException(Text.literal("Plane not mapped"))
    planeAction("delete_plane"): id =>
      planeCache.remove(id) match
        case Some(h) =>
          h.delete()
          1
        case None => throw CommandException(Text.literal("Plane not mapped"))
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("property").pipe: c =>
      c.requires(_.hasPermissionLevel(2))
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
  Registries.BLOCK("void_air") = Interop.VOID_AIR
  given (env: CastingEnvironment) => MinecraftServer = env.getWorld.getServer
  ServerLifecycleEvents.SERVER_STARTED.register: server =>
    given MinecraftServer = server
    for id <- server.savedPlanes do
      planes(id)
  Registries.BLOCK("border") = border
  Patterns.register("makeworld", e"qaaqqwaeddeawqqaaqqwwwaeddeewdqaaqdweeddeawwwqqaaqqwaeddeawqqaaqawwwwwwwawwwwwww"):
    Patterns.mkConstAction(argc = 0, mediaCost = MediaConstants.QUENCHED_BLOCK_UNIT * 6): _ =>
      val uuid = UUID.randomUUID()
      val world = planes(uuid).asWorld
      // TODO: world config
      val state = border.getDefaultState
      val bp = BlockPos.Mutable()
      for i <- 0 to 10; j <- 0 to 10; k <- Seq(0, 10) do
        bp.set(i, j, k)
        world.setBlockState(bp, state, 0)
        bp.set(i, k, j)
        world.setBlockState(bp, state, 0)
        bp.set(k, i, j)
        world.setBlockState(bp, state, 0)
      world.getServer.savedPlanes += uuid
      Seq(DimIota(world))
  Patterns.register("attachworld", e"qaaqqwaeddeawqqaaqawwwawwwwwwwqwwwawwwqwwwwwwwawwwaqaaqqwaeddeawqqaaq"):
    new SpellAction:
      override def getArgc: Int = 2
      override def awardsCastingStat(env: CastingEnvironment): Boolean = true
      override def execute(stack: util.List[? <: Iota], env: CastingEnvironment): SpellAction.Result =
        stack.toSeq match
          case Seq(plane: DimIota, dest: Vec3Iota) if plane.getDimString.startsWith("hexic:fresh-") =>
            val pos = BlockPos.ofFloored(dest.getVec3)
            env.assertPosInRangeForEditing(pos)
            val world = env.getWorld.getServer.getWorld(plane.getWorldKey)
            given server: MinecraftServer = world.getServer
            if world == env.getWorld then
              throw new Mishap:
                override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.PINK)
                override def errorMessage(env: CastingEnvironment, ctx: Context): Text = Text.literal("Cannot bind a demiplane to itself") // TODO: make translatable
                override def execute(env: CastingEnvironment, ctx: Context, stack: util.List[Iota]): Unit =
                  if env.getWorld.getBlockState(pos).isReplaceable then
                    env.getWorld.setBlockState(pos, Interop.VOID_AIR.getDefaultState)
            SpellAction.Result(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  world.parentInfo = Some(env.getWorld.getRegistryKey, pos)
                override def cast(env: CastingEnvironment, image: CastingImage): CastingImage = { cast(env); image },
                MediaConstants.SHARD_UNIT,
                Seq(),
                1
            )
      override def executeWithUserdata(list: util.List[? <: Iota], env: CastingEnvironment, data: NbtCompound): SpellAction.Result = SpellAction.DefaultImpls.executeWithUserdata(this, list, env, data)
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
            val planeWorld = server.getWorld(plane.getWorldKey)
            val (outer, pos) = (for
              (key, pos) <- planeWorld.parentInfo
              world = server.getWorld(key)
              if world != null
            yield (world, pos.toCenterPos)).getOrElse:
              throw new Mishap:
                override def accentColor(env: CastingEnvironment, context: Context): FrozenPigment = dyeColor(DyeColor.PINK)
                override def errorMessage(env: CastingEnvironment, context: Context): Text = "Cannot shatter a Demiplane that is not tethered" // TODO: translate
                override def execute(env: CastingEnvironment, context: Context, list: util.List[Iota]): Unit =
                  val r = planeWorld.random
                  val pos = BlockPos.Mutable(r.nextBetween(1, 9), r.nextBetween(1, 9), r.nextBetween(1, 9))
                  Seq(pos.setX, pos.setY, pos.setZ)(r.nextInt(2))(if r.nextBoolean() then 10 else 0)
                  planeWorld.breakBlock(pos, true)
            val id = getPocketID(plane.getWorldKey.getValue).get
            if isDev then println(s"Destroying pocket $id $planeWorld into $outer@$pos")
            SpellAction.Result(
              new RenderedSpell:
                override def cast(env: CastingEnvironment): Unit =
                  val loc = BlockPos.Mutable()
                  for x <- 1 to 9; y <- 1 to 9; z <- 1 to 9 do
                    loc.set(x, y, z)
                    planeWorld.breakBlock(loc, true)
                  val itemsToSpawn = mutable.Map[ItemVariant, Long]().withDefaultValue(0)
                  var xpToSpawn: Long = 0
                  val chunk = planeWorld.getChunkManager.getChunk(0, 0, ChunkStatus.FULL, false)
                  chunk.asInstanceOf[WorldChunk].loadEntities()
                  if isDev then planeWorld.dump(Files.createDirectories(Paths.get("deleted_world")))
                  boundary:
                    if isDev then println("Beginning lurker cleanup")
                    try
                      var pass = 0
                      // test dim: hexic:fresh-9116c992558d4aca854d75270e100b84, uuid 9116c992-558d-4aca-854d-75270e100b84
                      iterated(planeWorld.iterateEntities): (entities, recurse) =>
                        val entitySeq = entities.toSeq
                        if isDev then println(s"Performing pass $pass over ${entitySeq.size} entities")
                        if entitySeq.nonEmpty then
                          for entity <- entitySeq do
                            entity match
                              case e: ItemEntity =>
                                val stack = e.getStack
                                itemsToSpawn(ItemVariant.of(stack)) += stack.getCount
                                if isDev then println(s"Collecting item entity $stack, need to spawn: $itemsToSpawn")
                                e.discard()
                              case e: ExperienceOrbEntity =>
                                xpToSpawn += e.getExperienceAmount
                                if isDev then println(s"Collecting XP orb, need to spawn: $xpToSpawn")
                                e.discard()
                              case p: PlayerEntity =>
                                // thanks but please stop eating my ears
                                if isDev then println(s"Teleporting player $p")
                                val p2 = FabricDimensions.teleport(p, outer, TeleportTarget(pos, Vec3d.ZERO, p.getYaw, p.getPitch))
                                if !p2.isCreative && !p2.isSpectator then p2.kill()
                              case e: LivingEntity =>
                                e.kill()
                                if isDev then println(s"Killing living entity $e")
                                var n = 0
                                while !e.isRemoved do
                                  n += 1
                                  if isDev then println(s"Waiting $n ticks to die")
                                  (e: LivingEntityAccess).callUpdatePostDeath()
                              case e =>
                                if isDev then println(s"Killing nonliving entity $e")
                                val e2 = FabricDimensions.teleport(e, outer, TeleportTarget(pos, Vec3d.ZERO, e.getYaw, e.getPitch))
                                e2.kill()
                          pass += 1
                          if isDev then println(s"Proceeding to pass ${pass}")
                          recurse
                      if isDev then println(s"Ledger: $itemsToSpawn, $xpToSpawn XP")
                      for (item, count) <- itemsToSpawn do
                        if isDev then println(s"Spawning $item ($count)")
                        spawnManyItems(pos, item, count)(using outer)
                      while xpToSpawn > 2477 do
                        if isDev then println(s"Spawning max XP orb, $xpToSpawn left")
                        ExperienceOrbEntity.spawn(outer, pos, 2477)
                        xpToSpawn -= 2477
                      if xpToSpawn > 0 then
                        if isDev then println(s"Spawning final XP orb, $xpToSpawn")
                        ExperienceOrbEntity.spawn(outer, pos, xpToSpawn.toInt)
                    catch case e: Throwable =>
                      println(s"\u0007FAILED TO REMOVE DIMENSION!! $id. Not unloading.")
                      e.printStackTrace()
                      boundary.break()
                      throw e
                    // FIN.
                    server.savedPlanes -= id
                    planes(id).unload()
                override def cast(env: CastingEnvironment, image: CastingImage): CastingImage = { cast(env); image },
              MediaConstants.SHARD_UNIT * 25,
              Seq(),
              1
            )
          case Seq(x) =>
            throw MishapInvalidIota(x, 0, "hexic:world")
      override def executeWithUserdata(list: util.List[? <: Iota], env: CastingEnvironment, data: NbtCompound): SpellAction.Result = SpellAction.DefaultImpls.executeWithUserdata(this, list, env, data)
      override def hasCastingSound(env: CastingEnvironment): Boolean = true
      override def operate(env: CastingEnvironment, castingImage: CastingImage, cont: SpellContinuation): OperationResult = SpellAction.DefaultImpls.operate(this, env, castingImage, cont)
  Patterns.register("omni_open", w"qdaqadq"):
    Patterns.mkAction: (img, cont) =>
      (img.getStack.toSeq: Seq[Iota]) match
        case stack:+allegedCount =>
          val count = OperatorUtils.getPositiveInt(Seq(allegedCount), 0, 1)
          (new CastingImage(stack, count, Seq(), false, img.getOpsConsumed, img.getUserData, null), cont, HexEvalSounds.NORMAL_EXECUTE, Seq())
  Patterns.register("omni_close", e"eadedae"):
    Patterns.mkConstAction(0):
      case Seq() => Seq(ListIota(Seq()), DoubleIota(0))
  Patterns.register("staffcast_factory", ne"wwwwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayerEntity =>
          val staffcast = HexCardinalComponents.STAFFCAST_IMAGE.get(caster)
          val oldImage = staffcast.getVM(Paw.MAIN_HAND).getImage
          staffcast.setImage(img)
          val vm = staffcast.getVM(summon[CastingEnvironment].getCastingHand)
          try
            if cfg("hexic.compat.laniSwallowsMishaps").getOrElse(false) then
              vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
            else
              propagateMishaps(vm.getEnv):
                vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
          finally
            staffcast.setImage(oldImage)
            HexCardinalComponents.STAFFCAST_IMAGE.sync(caster)
          (vm.getImage, cont, HexEvalSounds.HERMES, Seq())
        case _ => throw MishapBadCaster()
  Patterns.register("staffcast_factory/lazy", ne"wwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayerEntity =>
          val staffcast = HexCardinalComponents.STAFFCAST_IMAGE.get(caster)
          val vm = staffcast.getVM(summon[CastingEnvironment].getCastingHand)
          val oldImage = vm.getImage
          vm.setImage(new CastingImage(
            stack = oldImage.getStack :+ img.getStack.lastOption.getOrElse:
              throw MishapNotEnoughArgs(1, 0),
            parenCount = 0,
            parenthesized = util.List.of(),
            escapeNext = false,
            opsConsumed = img.getOpsConsumed,
            userData = img.getUserData,
            null // kotlin bullshit
          ))
          try
            if cfg("hexic.compat.laniSwallowsMishaps").getOrElse(false) then
              vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
            else
              propagateMishaps(vm.getEnv):
                vm.queueExecuteAndWrapIota(PatternIota(se"deaqq"), summon)
          finally
            staffcast.setImage(new CastingImage(
              stack = vm.getImage.getStack,
              parenCount = oldImage.getParenCount,
              parenthesized = oldImage.getParenthesized,
              escapeNext = oldImage.getEscapeNext,
              opsConsumed = oldImage.getOpsConsumed,
              userData = oldImage.getUserData,
              null // kotlin bullshit
            ))
            HexCardinalComponents.STAFFCAST_IMAGE.sync(caster)
          // do not remove this comment
          (new CastingImage(
            stack = img.getStack.asScala.init.asJava,
            parenCount = img.getParenCount,
            parenthesized = img.getParenthesized,
            escapeNext = img.getEscapeNext,
            opsConsumed = vm.getImage.getOpsConsumed,
            userData = vm.getImage.getUserData,
            null // kotlin bullshit
          ), cont, HexEvalSounds.HERMES, Seq())
        case _ => throw MishapBadCaster()
  Patterns.register("get_other_caster", nw"ede"):
    Patterns.mkLiteral:
      val players: Set[LivingEntity] = summon[CastingEnvironment].getWorld.getPlayers.toSet
      var others = players - summon[CastingEnvironment].getCastingEntity
      for case given ClassTag[EntityPlayerMPFake] <- classNamed("carpet.patches.EntityPlayerMPFake") do
        others = others.filter:
          case _: EntityPlayerMPFake => false
          case _ => true
      val sorted = others.toSeq.sortBy(_.getPos.squaredDistanceTo(summon[CastingEnvironment].mishapSprayPos)).filter(summon[CastingEnvironment].isEntityInRange(_, true))
      sorted.headOption.fold(NullIota())(EntityIota(_))
  Patterns.register("blind", se"qqqqqadwawawd")(OpPotionEffect(StatusEffects.BLINDNESS, 1000, false, false))
  Patterns.register("erase", e"wqwdwqwawwwwwawwwww"):
    Patterns.mkAction: (img, cont) =>
      def mkResult(scale: Int, pos: => Vec3d, spell: => Unit) =
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
              ParticleSpray(pos, Vec3d(1, 0, 0), 0.25, 3.14, 40)
            ),
          ),
          cont, HexEvalSounds.SPELL
        )
      img.getStack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0)) match
        case s: Vec3Iota if fabric.isModLoaded("ioticblocks") =>
          val pos = BlockPos.ofFloored(s.getVec3)
          summon[CastingEnvironment].assertPosInRangeForEditing(pos)
          val holder = IoticBlocksAPI.INSTANCE.findIotaHolder(summon[CastingEnvironment].getWorld, pos)
          if holder == null || !holder.writeIota(null, true) then throw MishapBadBlock.of(pos, "hexic:erase")
          mkResult(1, pos.toCenterPos, holder.writeIota(null, false))
        case s: EntityIota =>
          summon[CastingEnvironment].assertEntityInRange(s.getEntity)
          def result(scale: Int, spell: CastingEnvironment ?=> Unit) = mkResult(scale, s.getEntity match { case e: ItemEntity => e.getPos.add(0, .375, .0); case e => e.getPos }, spell)
          boundary: outer ?=>
            val maybeItem = s.getEntity match
              case i: ItemEntity => Some(i.getStack)
              case f: ItemFrameEntity => Some(f.getHeldItemStack)
              case _ => None
            boundary:
              val item = maybeItem.getOrElse(boundary.break())
              val holder = hexXplat.findHexHolder(item)
              if holder == null || !holder.hasHex then boundary.break()
              boundary.break(result(item.getCount, holder.clearHex()))(using outer)
            val holder = hexXplat.findDataHolder(s.getEntity)
            if holder == null || !holder.writeIota(null, true) then throw MishapBadEntity.of(s.getEntity, "hexic:erase")
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
  lazy val filterFrameType: ContinuationFrame.Type[FilterFrame] = (c: NbtCompound, world: ServerWorld) =>
    FilterFrame(
      stack = c.getList("p", NbtElement.COMPOUND_TYPE).asScala.collect { case c: NbtCompound => IotaType.deserialize(c, world) }.toSeq,
      filter = c.getList("d", NbtElement.COMPOUND_TYPE).asScala.collect { case c: NbtCompound => IotaType.deserialize(c, world) }.toSeq,
      focus = IotaType.deserialize(c.getCompound("f"), world),
      received = c.getList("k", NbtElement.COMPOUND_TYPE).asScala.collect { case c: NbtCompound => IotaType.deserialize(c, world) }.toSeq,
      remaining = c.getList("r", NbtElement.COMPOUND_TYPE).asScala.collect { case c: NbtCompound => IotaType.deserialize(c, world) }.toSeq,
    )
  class FilterFrame(stack: Seq[Iota], filter: Seq[Iota], focus: Iota, received: Seq[Iota], remaining: Seq[Iota]) extends ContinuationFrame:
    override def getType: ContinuationFrame.Type[FilterFrame] = filterFrameType
    override def evaluate(cont: SpellContinuation, world: ServerWorld, vm: CastingVM): CastResult =
      val newReceived = vm.getImage.getStack.toSeq match
        case Seq() => throw MishapNotEnoughArgs(1, 0)
        case _ :+ x => if x.isTruthy then received :+ focus else received
      val (newStack, newCont) = remaining match
        case next +: rest => (stack :+ next, cont.pushFrame(FilterFrame(stack, filter, next, newReceived, rest)).pushFrame(FrameEvaluate(SpellList.LList(0, filter), true)))
        case Seq() => (stack :+ ListIota(newReceived), cont)
      CastResult(ListIota(filter), newCont, vm.getImage.withStack(_ => newStack), Seq(), ResolvedPatternType.EVALUATED, HexEvalSounds.THOTH)
    override def serializeToNBT(): NbtCompound = NbtCompound()
      .tap(_.put("p", seqToNBT(stack.map(IotaType.serialize))))
      .tap(_.put("k", seqToNBT(received.map(IotaType.serialize))))
      .tap(_.put("r", seqToNBT(remaining.map(IotaType.serialize))))
      .tap(_.put("f", IotaType.serialize(focus)))
      .tap(_.put("d", seqToNBT(filter.map(IotaType.serialize))))
    override def breakDownwards(stack: ju.List[? <: Iota]): Pair[java.lang.Boolean, ju.List[Iota]] = Pair(true, this.stack :+ ListIota(received))
    override def size = 0
  Patterns.register("grep", nw"qaeaqea"):
    Patterns.mkAction: (img, cont) =>
      img.getStack.toSeq match
        case Seq() => throw MishapNotEnoughArgs(2, 0)
        case Seq(_) => throw MishapNotEnoughArgs(2, 1)
        case saved:+(target: ListIota):+(filter: ListIota) =>
          if filter.isEmpty then (img.withStack(_.dropRight(2) :+ ListIota(target.getList.filter(_.isTruthy).toSeq)), cont, HexEvalSounds.THOTH, Seq()) // short-circuit on empty filter
          else target.getList.toSeq match
            case first +: rest =>
              // set up filter, ideally FilterFrame would do this
              (img.withStack(_.dropRight(2) :+ first), cont.pushFrame(FilterFrame(saved, filter.getList.toSeq, first, Seq(), rest)).pushFrame(FrameEvaluate(filter.getList, true)), HexEvalSounds.THOTH, Seq())
            case _ =>
              // we can't start a filter with no iota, but it'd always be empty anyway
              (img.withStack(_.dropRight(2) :+ ListIota(Seq())), cont, HexEvalSounds.THOTH, Seq())
        case saved:+(_: ListIota):+filter => throw MishapInvalidIota.ofType(filter, 1, "list")
        case saved:+target:+_ => throw MishapInvalidIota.ofType(target, 1, "list")
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
                  stack = stack :+ x,
                  parenCount = 0,
                  parenthesized = Seq.empty,
                  escapeNext = false,
                  opsConsumed = img.getOpsConsumed,
                  userData = img.getUserData,
                  null // kotlin bullshit
                )
                println(s"- thread $x started")
                val env = summon[CastingEnvironment]
                val vm = CastingVM(subImg, new CastingEnvironment(env.getWorld):
                  export env._
                  override def hasEditPermissionsAtEnvironment(pos: BlockPos): Boolean = false
                  override def extractMediaEnvironment(cost: Media, simulate: Boolean): Media = 0
                  override def isVecInRangeEnvironment(vec: Vec3d): Boolean = false
                  override def getUsableStacks(mode: CastingEnvironment.StackDiscoveryMode): util.List[ItemStack] = Seq()
                  override def getPrimaryStacks: util.List[CastingEnvironment.HeldItemInfo] = Seq()
                )
                vm.queueExecuteAndWrapIotas(fn.getList.toSeq, env.getWorld)
                println(s"- thread $x ended")
                vm.getImage
              catch case e =>
                p.tryFailure(e)
                throw e
          Future.sequence(imgs).onComplete(p tryComplete _.map(_.toSeq))
          val results = Await.result(p.future, Duration.Inf)
          OperationResult(
            newImage = CastingImage(
              stack = stack :+ ListIota(results.flatMap(_.getStack)),
              parenCount = img.getParenCount,
              parenthesized = img.getParenthesized,
              escapeNext = img.getEscapeNext,
              opsConsumed = results.map(_.getOpsConsumed).max,
              userData = img.getUserData,
              null // kotlin bullshit
            ),
            sideEffects = Seq(),
            newContinuation = cont,
            sound = HexEvalSounds.THOTH,
          )
        case Seq(i, _: ListIota, _*) => throw MishapInvalidIota.ofType(i, 0, "list")
        case Seq(_, i, _*) => throw MishapInvalidIota.ofType(i, 1, "list")
        case s => throw MishapNotEnoughArgs(2, s.size)
  CastingEnvironment.addCreateEventListener: (env: CastingEnvironment, data: NbtCompound) =>
    val id = env.getWorld.getRegistryKey.getValue
    if isDev then println(s"Environment created in $id")
    for pocketID <- getPocketID(id) do
      if isDev then println(s"Preparing pocket $pocketID for environment $env")
      env.addExtension:
        new CastingEnvironmentComponent with CastingEnvironmentComponent.IsVecInRange with CastingEnvironmentComponent.HasEditPermissionsAt:
          object getKey extends CastingEnvironmentComponent.Key[this.type]
          override def onIsVecInRange(vec: Vec3d, current: Boolean): Boolean = boundary:
            for axis <- Direction.Axis.values do
              val x = vec.getComponentAlongAxis(axis)
              if x < 0 || x >= 11 then boundary.break(false)
            true
          override def onHasEditPermissionsAt(pos: BlockPos, current: Boolean): Boolean = boundary:
            for axis <- Direction.Axis.values do
              val x = pos.getComponentAlongAxis(axis)
              if x < 0 || x >= 11 then boundary.break(false)
            current
  // dump patterns
  val out = Files.newOutputStream(Path.of("patterns.csv"))
  try
    val o = OutputStreamWriter(out)
    for ent <- hexXplat.getActionRegistry.getEntrySet.asScala.toSeq.sortBy(_.getKey.getValue.toString) do
      o.write(s"${ent.getKey.getValue},${ent.getValue.prototype.getStartDir},${ent.getValue.prototype.anglesSignature}\n")
    o.flush()
  finally
    out.close()

given IotaType[PropertyIota] = PropertyIota.TYPE
given IotaType[Vec3Iota] = Vec3Iota.TYPE

case class Const[T](value: T)
inline given [T <: Singleton] => Const[T] = Const[T](compiletime.constValue[T])
given [T] => Conversion[Const[T], T] = _.value

private[hexic] class ComponentInit extends EntityComponentInitializer, LevelComponentInitializer:
  override def registerEntityComponentFactories(using r: EntityComponentFactoryRegistry): Unit =
    PlayerInfoComponent.register
    r.registerForPlayers(summon[ComponentKey[MurmurCache]], _ => MurmurCache(None), RespawnCopyStrategy.ALWAYS_COPY)
    r.registerForPlayers(summon[ComponentKey[ExcursionComponent]], _ => ExcursionComponent(), RespawnCopyStrategy.ALWAYS_COPY)
    r.registerForPlayers(summon[ComponentKey[RevealComponent]], _ => RevealComponent(Seq.empty), RespawnCopyStrategy.LOSSLESS_ONLY)
    r.registerForPlayers(CatHolder.key, new CatHolder(_), RespawnCopyStrategy.NEVER_COPY)
  override def registerLevelComponentFactories(using LevelComponentFactoryRegistry): Unit =
    ServerInfoComponent.register

opaque type Attrition = Unit
object Attrition extends Registrar[Attrition]("attrition")

type subtypes[T, R <: T] = T
//case class StaffcastFrame(owner: ServerPlayerEntity, oldImage: CastingImage) extends ContinuationFrame:
//  override def getType: ContinuationFrame.Type[StaffcastFrame] = StaffcastFrame
//  override def breakDownwards(list: util.List[? <: Iota]): Pair[lang.Boolean, util.List[Iota]] = ???
//  override def evaluate(rest: SpellContinuation, world: ServerWorld, vm: CastingVM): CastResult =
//    HexCardinalComponents.STAFFCAST_IMAGE.get(owner).setImage(oldImage)
//    HexCardinalComponents.STAFFCAST_IMAGE.sync(owner)
//    CastResult(NullIota(), rest)
//  override def serializeToNBT: NbtCompound = ???
//  override def size: Int = 1
//object StaffcastFrame extends ContinuationFrame.Type[StaffcastFrame]:
//  def deserializeFromNBT(data: NbtCompound, world: ServerWorld): StaffcastFrame = ???

val fadedScrolls: TagKey[ActionRegistryEntry] = TagKey.of(HexRegistries.ACTION, "faded_scrolls")

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

object elementTag:
  def apply[T <: NbtElement](l: AbstractNbtList[T]): ClassTag[T] =
    l match
      case _: NbtByteArray => summon[ClassTag[NbtByte]]
      case _: NbtIntArray => summon[ClassTag[NbtInt]]
      case _: NbtLongArray => summon[ClassTag[NbtLong]]
      case _: NbtList => summon[ClassTag[NbtElement]]
  def unapply[T <: NbtElement](l: AbstractNbtList[T]): Some[ClassTag[T]] = Some(elementTag(l))

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

object Droplet extends Item(Item.Settings()):
  def apply(fluid: Fluid, nbt: Option[NbtCompound] = None): ItemVariant =
    ItemVariant.of(Droplet, NbtCompound().tap: c =>
      c.putString("id", Registries.FLUID.getId(fluid).toString)
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

def seqToNBT(data: Seq[NbtElement]) =
  val l = NbtList()
  data.forEach(l.add(_))
  l

trait PigmentHolderItem:
  this: Item =>
  def getPigment(stack: ItemStack): FrozenPigment
  def setPigment(stack: ItemStack)(pigment: FrozenPigment): Unit
given Conversion[ItemPackagedHex, PigmentHolderItem] = _.asInstanceOf // by mixin
given Conversion[ItemStack, ItemStackAccess] = _.asInstanceOf // by mixin
given Conversion[LivingEntity, LivingEntityAccess] = _.asInstanceOf // by mixin

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

given Conversion[Iota, IotaDuck] = _.asInstanceOf
given Conversion[IotaDuck, Iota] = _.asInstanceOf

def copy[T <: Iota](iota: T)(using ServerWorld): T | Null = iota.getType.deserialize(iota, summon[ServerWorld]).asInstanceOf[T | Null]

def spawnItem(pos: Vec3d, stack: ItemStack)(using world: ServerWorld): ItemEntity =
  ItemEntity(world, pos.getX, pos.getY, pos.getZ, stack).tap(world.spawnEntity(_))
def spawnManyItems(pos: Vec3d, variant: ItemVariant, amount: Long)(using ServerWorld): Seq[ItemEntity] =
  assume(amount >= 0)
  if amount > Int.MaxValue then
    spawnItem(pos, variant.toStack(Int.MaxValue)) +: spawnManyItems(pos, variant, amount - Int.MaxValue)
  else if amount > 0 then
    List(spawnItem(pos, variant.toStack(amount.toInt)))
  else
    Nil

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
    (data: Iota | Null) match
      case iota: target.type => iota
      case _ => throw IllegalStateException("Iota changed types or became null during serialization")

object border extends Block(AbstractBlock.Settings.create().dropsNothing().allowsSpawning((_, _, _, _) => false).sounds(BlockSoundGroup.STONE).requiresTool().strength(100.0F, 1200.0F).luminance(_ => 14))
def getPocketID(key: Identifier): Option[UUID] =
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
              export s._
            case (None, Some(d)) => new HopperDestination:
              export d._
            case (Some(s), Some(d)) => new HopperSource with HopperDestination:
              export s._
              export d._
        case _ => null

extension [A, B] (p: (A, B))
  infix def both[R, S](f: (A => R) & (B => S)): (R, S) = (f(p._1), f(p._2))
trait IotaCoercion[T]:
  typ: IotaType[I] =>
  // need _root_ path, since `typ` could theoretically have these as members
  type I <: _root_.at.petrak.hexcasting.api.casting.iota.Iota
  def foo = ???
def downcast[R: ClassTag](t: Any): Option[R] = t match
  case r: R => Some(r)
  case _ => None
