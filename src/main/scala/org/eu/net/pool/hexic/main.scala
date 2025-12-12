//noinspection NotImplementedCode
package org.eu.net.pool
package hexic

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
import net.minecraft.block.{AbstractBlock, Block, BlockRenderType, BlockState, BlockWithEntity, ShapeContext}
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
import net.minecraft.util.math.{BlockPos, ChunkPos, Direction, Vec3d}
import net.minecraft.util.{ActionResult, Arm, ClickType, DyeColor, Formatting, Hand, Identifier, Rarity, TypedActionResult, Util, Uuids, WorldSavePath}
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
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util.{Optional, UUID}
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{elidable, experimental, showAsInfix, tailrec, targetName, unused}
import scala.ref.WeakReference
import scala.util.{Failure, Success, Try}
import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.language.experimental.{macros, saferExceptions}
import scala.language.{dynamics, existentials, implicitConversions, postfixOps, reflectiveCalls}
import scala.reflect.{ClassTag, classTag}
import scala.util.{NotGiven, Random, TupledFunction, boundary}
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
import net.minecraft.block.AbstractBlock
import net.minecraft.entity.passive.FoxEntity
import net.minecraft.stat.Stats
import org.eu.net.pool.hexic.mixin.{ItemStackAccess, LivingEntityAccess}
import at.petrak.hexcasting.common.casting.actions.eval.OpEval
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.common.casting.actions.spells.OpBreakBlock
import at.petrak.hexcasting.common.casting.actions.spells.OpErase
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
import net.beholderface.oneironaut.casting.iotatypes.DimIota
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.world.gen.chunk.{ChunkGenerator, ChunkGenerators}
import xyz.nucleoid.fantasy.util.VoidChunkGenerator
import xyz.nucleoid.fantasy.{Fantasy, RuntimeWorldConfig, RuntimeWorldHandle}

import java.nio.charset.StandardCharsets
import java.math.BigInteger
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.world.chunk.{ChunkStatus, WorldChunk}

import scala.concurrent.duration.Duration

import phlib.{Events => PhEvents, _, given}

given Logger = LoggerFactory.getLogger("hexic")

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

trait SlotReference:
  def item(using CastingEnvironment): Item
  def nbt(using CastingEnvironment): Option[NbtCompound]
  def count(using CastingEnvironment): Long
  @throws[Mishap]
  def nbt_=(using Transaction, CastingEnvironment)(nbt: Option[NbtCompound]): Unit
  @throws[Mishap]
  def count_=(using Transaction, CastingEnvironment)(count: Long): Unit

class PlayerInfoComponent(
  val player: PlayerEntity,
  var murmur: Option[String] = None,
  var leftWeave: ItemStack = ItemStack.EMPTY,
  var rightWeave: ItemStack = ItemStack.EMPTY,
  var chatLines: Seq[Text] = Seq(),
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
    chatLines = c.getList("chat", NbtElement.COMPOUND_TYPE).map(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, _)).map(Text.Serializer.fromJson).toSeq
    if c.contains("fox", NbtElement.STRING_TYPE) then
      foxType = Some(FoxEntity.Type.valueOf(c.getString("fox")))
    else
      foxType = None
  override def writeToNbt(c: NbtCompound): Unit =
    if !leftWeave.isEmpty then c.put("shl", NbtCompound().tap(leftWeave.writeNbt))
    if !rightWeave.isEmpty then c.put("shr", NbtCompound().tap(rightWeave.writeNbt))
    c.put("chat", NbtList().tap(_.addAll(chatLines.map(Text.Serializer.toJsonTree).map(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, _)))))
    foxType.fold(c.remove("fox"))(f => c.putString("fox", f.name))
object PlayerInfoComponent:
  given key: ComponentKey[PlayerInfoComponent] = ComponentRegistry.getOrCreate("player_wisp", classOf[PlayerInfoComponent])
  given Conversion[PlayerEntity, PlayerInfoComponent] = _.getComponent(key)
  private[hexic] def register(using fac: EntityComponentFactoryRegistry) =
    fac.registerForPlayers(key, PlayerInfoComponent(_), RespawnCopyStrategy.LOSSLESS_ONLY)

class ServerInfoComponent(
  var endSnowTick: Long = 0
) extends Component, AutoSyncedComponent:
  override def readFromNbt(tag: NbtCompound): Unit =
    endSnowTick = tag.getLong("snow")
  override def writeToNbt(tag: NbtCompound): Unit =
    tag.putLong("snow", endSnowTick)
object ServerInfoComponent:
  given key: ComponentKey[ServerInfoComponent] = ComponentRegistry.getOrCreate("server_info", classOf[ServerInfoComponent])
  given get: (server: MinecraftServer) => ServerInfoComponent = server.getOverworld.getLevelProperties.getComponent(key)
  def sync()(using server: MinecraftServer): Unit = server.getOverworld.getLevelProperties.syncComponent(key)
  private[hexic] def register(using fac: LevelComponentFactoryRegistry) =
    fac.register(key, _ => ServerInfoComponent())

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

extension [T] (x: T | Null)
  inline def ?[R](f: T => R): R | Null = x match
    case null => null
    case x: T => f(x)
  inline def ??(y: T): T = x match
    case null => y
    case x: T => x

case class Pen private [hexic] (color: DyeColor) extends Item(Item.Settings().maxCount(1)):
  override def use(world: World, player: PlayerEntity, hand: Hand): TypedActionResult[ItemStack] =
    // if player.getAttributeValue(HexAttributes.FEEBLE_MIND) > 0.0 then
    //   TypedActionResult.fail(player.getStackInHand(hand))
    // else
      if !world.isClient && player.isInstanceOf[ServerPlayerEntity] then
        val serverPlayer: ServerPlayerEntity = player.asInstanceOf[ServerPlayerEntity]
        val vm = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, hand)
        val patterns = IXplatAbstractions.INSTANCE.getPatternsSavedInUi(serverPlayer).asScala
        val descs = vm.generateDescs
        IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer, new MsgOpenSpellGuiS2C(hand, patterns, descs.getFirst, descs.getSecond, 0))
      player.incrementStat(Stats.USED.getOrCreateStat(this))
      TypedActionResult.success(player.getStackInHand(hand))
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
  override def writeable(stack: ItemStack): Boolean = readIotaTag(stack) == null
  override def canWrite(stack: ItemStack, iota: Iota): Boolean = writeable(stack) && (iota match
    case l: ListIota =>
      val i = l.getList.asScala
      i.isEmpty || i.head.executable && i.last.executable
    case _ => false)
  override def writeDatum(stack: ItemStack, iota: Iota): Unit =
    assume(canWrite(stack, iota))
    stack.getOrCreateNbt.put("Hex", IotaType.serialize(iota))
  override def appendTooltip(stack: ItemStack, world: World, tooltip: util.List[Text], context: TooltipContext): Unit =
    IotaHolderItem.appendHoverText(this, stack, tooltip, context)
object Mediaweave:
  val colors: DyeColor :> Mediaweave = DyeColor.values().map(c => c -> Mediaweave(c)).toMap
  val tag: TagKey[Item] = TagKey.of(Registries.ITEM, "mediaweaves")

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
        for item <- x do
          val c = NbtCompound()
          item.writeNbt(c)
          l.add(c)
      )
    private def withMediaHolders[T](f: Seq[CCMediaHolder] => T): T =
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
    batteries match
      case Some((total, max)) => tooltip.add(showMedia("external", total + consumables.getOrElse(0L), max))
      case None => for value <- consumables do
        tooltip.add(showMedia("external", value))
    for (total, max) <- trinkets do
      tooltip.add(showMedia("internal", total, max))
  private def showMedia(tag: String, media: Long) = Text.translatable("hexic.media.infinite", Text.translatable(s"hexic.media.$tag"), Text.translatable("hexcasting.tooltip.media", dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR))))
  private def showMedia(tag: String, media: Long, maxMedia: Long) = Text.translatable("hexic.media.finite", Text.translatable(s"hexic.media.$tag"), dustAmount(media).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Text.translatable("hexcasting.tooltip.media", dustAmount(maxMedia)).styled(_.withColor(ItemMediaHolder.HEX_COLOR)), Text.literal(PERCENTAGE.format(100.0 * media / maxMedia)+"%").styled(_.withColor(MediaHelper.mediaBarColor(media, maxMedia))))
  private def dustAmount(media: Long) = Text.literal(DUST_AMOUNT.format(media / MediaConstants.DUST_UNIT.toDouble))

class Stringworm extends Item(Stringworm.settings)
object Stringworm:
  val settings = Item.Settings().maxCount(16)
  val flavors = Seq("pure", "action", "hex", "media", "thing")

val stringworms =
  Stringworm.flavors.map(_ -> new Stringworm).toMap

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
    val measure = p.anglesSignature match
      case introPattern (measure) => measure
      case _ => boundary.break(None)
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
  def splat(original: (args: util.List[Iota], env: CastingEnvironment) => util.List[Iota])(args: util.List[Iota], env: CastingEnvironment): util.List[Iota] =
    try
      original(args, env)
    catch case e: MishapInvalidIota =>
      args.last match
        case m: MapIota => m.toList
        case _ => throw MishapInvalidIota(e.getPerpetrator, e.getReverseIdx, Text.translatable("text.hexic.or_map", e.getExpected)).initCause(e);
  private [hexic] def getPocketName(pocket: String) = Text.of(pocketNames(getPocketID(Identifier.tryParse(pocket)).get))

val _ =
  Interop.playerDeathHook = (p: PlayerEntity, out: util.List[ItemStack]) =>
    val c = p: PlayerInfoComponent
    if !c.rightWeave.isEmpty then
      out.add(c.rightWeave)
      c.rightWeave = ItemStack.EMPTY
    if !c.leftWeave.isEmpty then
      out.add(c.leftWeave)
      c.leftWeave = ItemStack.EMPTY

given Codec[Int] = Codec.INT.xmap(p => p, p => p)

type Media = Long
object MediaBundle:
  val items: Seq[MediaBundle] = for i <- Seq(6, 12); c <- DyeColor.values yield new MediaBundle(c, i)
  def apply(c: DyeColor, s: Int) = items.find(b => b.color == c && b.size == s).get
  private val PERCENTAGE = new DecimalFormat("####")
  PERCENTAGE.setRoundingMode(RoundingMode.DOWN)
  private val DUST_AMOUNT = new DecimalFormat("###,###.##")
val wizard = Item(Item.Settings().rarity(Rarity.EPIC).maxCount(1))

val aLotOfMedia = (200000 /* max phial size */ * 6 /* phials per small pouch */ * 4 /* small pouches per large pouch */ * (36 /* inventory slots */ + 4 /* armor slots */ + 2 /* offhands */) + 20 /* healthcasting */) * MediaConstants.DUST_UNIT

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

def memo[T, R](f: T => R, limit: Option[Int] = Some(128)): T => R =
  val cache = limit.fold(ju.HashMap()): cap =>
    new ju.LinkedHashMap[T, R](cap + 1, 1, true):
      override def removeEldestEntry(eldest: ju.Map.Entry[T, R]): Boolean = size > cap
  x =>
    cache.synchronized:
      cache.computeIfAbsent(x, f(_))

trait MutableFunction[T, R] extends (T => R):
  def update(key: T, value: R): Unit

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
  try System.getProperties.load(Files.newBufferedReader(Path.of("config/jvm.properties"), Charsets.UTF_8))
  catch
    case _: FileNotFoundException =>
    case i: IOException => summon[Logger].warn("Failed to read properties", i)
  iotaTypeRegistry("location") = LocationIota
  iotaTypeRegistry("nbt") = NbtIota
  iotaTypeRegistry("variant") = VariantIota
  iotaTypeRegistry("map") = MapIota
  iotaTypeRegistry("tripwire") = TripwireIota.getType
  iotaTypeRegistry("access") = PropertyAccessIota.Type
  for ((_, c), i) <- MetatableIotaType.colors.zipWithIndex do iotaTypeRegistry(s"meta/$i") = c
  hexXplat.getContinuationTypeRegistry("tripwire") = TripwireIota.Frame
  for (color, item) <- Mediaweave.colors do
    Registries.ITEM(s"${color.asString}_mediaweave") = item
  for item <- MediaBundle.items do
    Registries.ITEM(item.size match
      case 6 => s"small_${item.color.asString}_bundle"
      case 12 => s"large_${item.color.asString}_bundle") = item
  for (flavor, item) <- stringworms do
    Registries.ITEM(s"stringworm_$flavor") = item
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

      override def onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult = boundary:
        lazy val entity = findEntity(world, pos).getOrElse(boundary.break(ActionResult.FAIL))
        player.getStackInHand(hand) match
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
  for (color, item) <- Pen.instances do Registries.ITEM(s"pen/${color.asString}") = item
  Registries.ITEM_GROUP("group") = itemGroup
  //Registries.ITEM("echo") = EchoItem
  if fabric.isModLoaded("hexical") then
    for
      HopperEndpointRegistry <- classNamed("miyucomics.hexical.features.hopper.HopperEndpointRegistry")
      ConduitIota <- classNamed("dev.kineticcat.hexportation.fabric.api.casting.iota.ConduitIota")
      registerHopperEndpoint <- classNamed("org.eu.net.pool.registerHopperEndpoint")
    do
      registerHopperEndpoint.runtimeClass.newInstance().asInstanceOf[() => Unit]()
    Patterns.register("dye_offhand", w"eqdeeqdweeqddqdwwdew"):
      Patterns.mkAction: (img, cont) =>
        val stack = img.getStack.asScala
        stack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0)) match
          case p: PigmentIota =>
            val info = summon[CastingEnvironment].getHeldItemToOperateOn(!_.isEmpty).pipe(Option(_)).getOrElse(throw MishapBadOffhandItem(null, Text.translatable("text.hexic.pigment_holder_item")))
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
  def mkNbtLiftAction[T: FromIota](lift: T => NbtElement, expected: Identifier) =
    Patterns.mkConstAction(1):
      case Seq(iotaLike[T](x)) => Seq(NbtIota(lift(x)))
      case Seq(x) => throw MishapInvalidIota.of(x, 0, expected.toString)
  def mkNbtLiftArrayAction[T: FromIota](lift: T => NbtElement, liftArray: Array[T] => NbtElement, expected: Identifier)(using FromIota[Array[T]]) =
    Patterns.mkConstAction(1):
      case Seq(iotaLike[T](x)) => Seq(NbtIota(lift(x)))
      case Seq(iotaLike[Array[T]](x)) => Seq(NbtIota(liftArray(x)))
      case Seq(x) => throw MishapInvalidIota.of(x, 0, expected.toString)
  Patterns.register("nbt/lift1", (HexDir.NORTH_WEST, "edwaqw")):
    mkNbtLiftArrayAction[Byte](NbtByte.of, (b => NbtByteArray(b)): Array[Byte] => NbtElement, "byte")
  Patterns.register("nbt/lift2", (HexDir.NORTH_WEST, "edwaqww")):
    mkNbtLiftAction[Short](NbtShort.of, "short")
  Patterns.register("nbt/lift4", (HexDir.NORTH_WEST, "edwaqwww")):
    mkNbtLiftArrayAction[Int](NbtInt.of, (b: Array[Int]) => NbtIntArray(b), "int")
  Patterns.register("nbt/lift8", (HexDir.NORTH_WEST, "edwaqwwww")):
    mkNbtLiftArrayAction[Long](NbtLong.of, (b: Array[Long]) => NbtLongArray(b), "long")
  Patterns.register("nbt/liftf", (HexDir.NORTH_WEST, "edwaqwaa")):
    mkNbtLiftAction[Float](NbtFloat.of, "float")
  Patterns.register("nbt/liftd", (HexDir.NORTH_WEST, "edwaqwaawaa")):
    mkNbtLiftAction[Double](NbtDouble.of, "double")
  Patterns.register("nbt/literal/collection", (HexDir.EAST, "qqddqdewqaeaaee")):
    Patterns.mkLiteral(NbtIota(NbtCompound()))
  Patterns.register("nbt/literal/list", (HexDir.EAST, "eedwaqq")):
    Patterns.mkLiteral(NbtIota(NbtList()))
  Patterns.register("nbt/literal/array1", (HexDir.EAST, "eedwaqqe")):
    Patterns.mkLiteral(NbtIota(NbtByteArray(Array[Byte]())))
  Patterns.register("nbt/literal/array2", (HexDir.EAST, "eedwaqqew")):
    Patterns.mkLiteral(NbtIota(NbtIntArray(Array[Int]())))
  Patterns.register("nbt/literal/array4", (HexDir.EAST, "eedwaqqewww")):
    Patterns.mkLiteral(NbtIota(NbtLongArray(Array[Long]())))
  Patterns.register("empty_map", (HexDir.EAST, "dqdwdqd")):
    Patterns.mkLiteral(MapIota())
  Patterns.register("prop_fi", sw"aawqe"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) => Seq(PropertyAccessIota.Writer(x.getName, "head"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "property")
  Patterns.register("prop_fo", sw"aawqd"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) => Seq(PropertyAccessIota.Stream(x.getName, "head"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "property")
  Patterns.register("prop_li", sw"aawdwq"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) => Seq(PropertyAccessIota.Writer(x.getName, "tail"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "property")
  Patterns.register("prop_lo", sw"aawdwa"):
    Patterns.mkConstAction(1):
      case Seq(x: PropertyIota) => Seq(PropertyAccessIota.Stream(x.getName, "tail"))
      case Seq(x) => throw MishapInvalidIota(x, 0, "property")
  Patterns.register("nbt/serialize", nw"edwaq"):
    Patterns.mkConstAction(1):
      case Seq(x: Iota) => Seq(IotaType.serialize(x))
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
  Patterns.register("tripwire", w"edewqwaqede"):
    Patterns.mkLiteral(TripwireIota)
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
  Patterns.register("whatthefuck", ne"daadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaadaadaddaddaadaddaadaadaddaddaadaddaddaadaddaadaadadda"):
    Patterns.mkLiteral(PatternIota(e"wedqawqeewdeaqeewdeaqqedqawqqedqawqeedqawqqewdeaqeedqawqeewdeaqqewdeaqeewdeaqeedqawqqedqawqqewdeaqeedqawqeewdeaqqewdeaqeewdeaqeedqawqqedqawqqewdeaqqedqawqeewdeaqeewdeaqqedqawqqedqawqeedqawqqewdeaqqedqawqeewdeaqeewdeaqqedqawqqedqawqeedqawqqewdeaqeedqawqeewdeaqeewdeaqqedqawqqedqawqeedqawqqewdeaqqedqawqeewdeaqqewdeaqeewdeaqeedqawqqedqawqqewdeaqe"))
  Patterns.register("nbt/deserialize", (HexDir.NORTH_WEST, "edwaqa")):
    Patterns.mkConstAction(1):
      case Seq(data: NbtIota) =>
        val env = summon[CastingEnvironment]
        given ServerWorld = env.getWorld
        val iota = data.data.asInstanceOf[NbtCompound].iota
        iota match
          case p: EntityIota => p.getEntity match
            case t: PlayerEntity if t != env.getCastingEntity =>
              throw MishapOthersName(t)
            case _ =>
          case _ =>
        Seq(iota)
      case Seq(x) =>
        throw MishapInvalidIota(x, 0, Text.literal("an ").append(Text.literal("NBT compound").styled(_.withColor(NbtIota.color))))
  Registry.register(hexXplat.getArithmeticRegistry, "nbt": Identifier, {
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
  hexXplat.getArithmeticRegistry("maps") =
    import Arithmetic.*
    arith("map",
      ADD -> ((a: MapIota, b: MapIota) => a ++ b),
      SUB -> ((a: MapIota, b: MapIota) => a -- b),
      ABS -> ((a: MapIota) => DoubleIota(a.map.size)),
      INDEX -> ((a: MapIota, k: Iota) => a(k)),
      UNAPPEND -> ((a: MapIota) => a.lastOption.map(p => Seq(p._1, p._2)).getOrElse(Seq(NullIota(), NullIota())) prepended a.init),
      INDEX_OF -> ((a: MapIota, v: Iota) =>
        val c = IotaType.serialize(v)
        a.update(_.filter(_._2 == c))),
      REMOVE -> ((a: MapIota, k: Iota) => a - k),
      REPLACE -> ((a: MapIota, k: Iota, v: Iota) => a + (k -> v)),
      UNCONS -> ((a: MapIota) => a.headOption.map(p => Seq(p._1, p._2)).getOrElse(Seq(NullIota(), NullIota())) prepended a.tail),
      AND -> ((a: MapIota, b: MapIota) => a & b),
      OR -> ((a: MapIota, b: MapIota) => b ++ a),
      XOR -> ((a: MapIota, b: MapIota) => a ^ b),
      GREATER -> ((a: MapIota, b: MapIota) => a.map.containsAll(b.map) && a.map != b.map),
      LESS -> ((a: MapIota, b: MapIota) => b.map.containsAll(a.map) && a.map != b.map),
      GREATER_EQ -> ((a: MapIota, b: MapIota) => a.map containsAll b.map),
      LESS_EQ -> ((a: MapIota, b: MapIota) => b.map containsAll a.map),
    )
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
  extension (w: ServerWorld)
    def meta: String MutableFunction Option[String] =
      val path = w.getServer.getSavePath(WorldSavePath.ROOT).resolve(s"dimensions/${w.getRegistryKey.getValue.getNamespace}/${w.getRegistryKey.getValue.getPath}")
      new MutableFunction:
        def apply(name: String) = Option(Files.getAttribute(path, "user:" + name)).asInstanceOf[Option[Array[Byte]]].map(buf => String(buf, StandardCharsets.UTF_8))
        def update(name: String, value: Option[String]): Unit = Files.setAttribute(path, "user:" + name, value.map(StandardCharsets.UTF_8.encode).orNull)
    def parentInfo: Option[(RegistryKey[World], BlockPos)] = w.meta("parent").flatMap(value => Option(Identifier.tryParse(value))).map(RegistryKey.of(RegistryKeys.WORLD, _)).filter(w.getServer.getWorld(_) ne null).map((_, BlockPos(Integer.parseInt(w.meta("bound_x").get), Integer.parseInt(w.meta("bound_y").get), Integer.parseInt(w.meta("bound_z").get))))
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
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("gimmeiota")
      .requires(c => c.hasPermissionLevel(2) || (c.getPlayer != null && c.getPlayer.isCreative))
      .`then`(RequiredArgumentBuilder.argument("type", Interop.reat(r, HexRegistries.IOTA_TYPE))
        .`then`(RequiredArgumentBuilder.argument[ServerCommandSource, NbtElement]("data", NbtElementArgumentType.nbtElement())
          .executes(c =>
            val t = Interop.gre[IotaType[?]](c, "type", HexRegistries.IOTA_TYPE)
            val d = NbtElementArgumentType getNbtElement(c, "data")
            val p = c.getSource.getPlayer
            if p == null then
              throw CommandException("Command must be run by a player")
            try
              t.value deserialize(d, c.getSource.getWorld) match
                case null => throw CommandException("Iota did not accept the given data")
                case r: Iota =>
                  p gimmeIota r
                  c.getSource sendFeedback(() => Text.translatable("Pushed %s to stack", try r.display catch case x: (Exception | Error) => x.getMessage), true)
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
      ).build())
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
  Patterns.register("makeworld", ne"qaaqqwaeddeawqqaaqqwwwaeddeewdqaaqdweeddeawwwqqaaqqwaeddeawqqaaqawwwwwwwawwwwwww"):
    Patterns.mkConstAction(argc = 0, mediaCost = MediaConstants.SHARD_UNIT * 3645): _ =>
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
                MediaConstants.SHARD_UNIT * 3,
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
                                FabricDimensions.teleport(p, outer, TeleportTarget(pos, Vec3d.ZERO, p.getYaw, p.getPitch))
                                if !p.isCreative && !p.isSpectator then p.kill()
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
                                FabricDimensions.teleport(e, outer, TeleportTarget(pos, Vec3d.ZERO, e.getYaw, e.getPitch))
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
                        ExperienceOrbEntity.spawn(outer, pos, 2477)
                        xpToSpawn -= 2477
                      if xpToSpawn > 0 then
                        if isDev then println(s"Spawning final XP orb, $xpToSpawn")
                        ExperienceOrbEntity.spawn(outer, pos, xpToSpawn.toInt)
                    catch case e: Throwable =>
                      println(s"FAILED TO REMOVE DIMENSION!! $id. Not unloading.")
                      e.printStackTrace()
                      boundary.break()
                      throw e
                    // FIN.
                    server.savedPlanes -= id
                    planes(id).unload()
                override def cast(env: CastingEnvironment, image: CastingImage): CastingImage = { cast(env); image },
              MediaConstants.SHARD_UNIT * 750,
              Seq(),
              1
            )
          case Seq(x) =>
            throw MishapInvalidIota(x, 0, "hexic:world")
      override def executeWithUserdata(list: util.List[? <: Iota], env: CastingEnvironment, data: NbtCompound): SpellAction.Result = SpellAction.DefaultImpls.executeWithUserdata(this, list, env, data)
      override def hasCastingSound(env: CastingEnvironment): Boolean = true
      override def operate(env: CastingEnvironment, castingImage: CastingImage, cont: SpellContinuation): OperationResult = SpellAction.DefaultImpls.operate(this, env, castingImage, cont)
  Patterns.register("snow", nw"eewwweewdadadaddwwwaqwwq"):
    Patterns.mkAction: (img, cont) =>
      (img, cont, HexEvalSounds.SPELL, Seq(
        OperatorSideEffect.ConsumeMedia(12 * MediaConstants.DUST_UNIT),
        OperatorSideEffect.AttemptSpell(
          new RenderedSpell:
            override def cast(env: CastingEnvironment): Unit =
              given MinecraftServer = env.getWorld.getServer
              val duration = env.getWorld.random.nextBetween(15, 30) * 20 * 60
              env.getWorld.setWeather(0, duration, true, false)
              ServerInfoComponent.get.endSnowTick = env.getWorld.getTime + duration
              ServerInfoComponent.sync()
            override def cast(env: CastingEnvironment, img: CastingImage): CastingImage = { cast(env); img }
        , true, true)
      ))
  Patterns.register("staffcast_factory", ne"wwwwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayerEntity =>
          val staffcast = HexCardinalComponents.STAFFCAST_IMAGE.get(caster)
          val oldImage = staffcast.getVM(Hand.MAIN_HAND).getImage
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
          ty.Instance(userdata, display.display, metatable.getName, metatable.getReadonly)
  PhEvents.beforePatternExecute.register:
    Function.unlift:
      case (p, vm, given ServerWorld, cont) =>
        given CastingEnvironment = vm.getEnv
        // this is probably cursed
        for case m: AbstractMetatableIota <- vm.getStack.lastOption; x <- m mro p.getPattern yield
          m.callMetamethod(p.getPattern)(vm.getImage.withStack(_.init), cont)
  Patterns.register("get_other_caster", nw"ede"):
    Patterns.mkLiteral:
      val players: Set[LivingEntity] = summon[CastingEnvironment].getWorld.getPlayers.toSet
      var others = players - summon[CastingEnvironment].getCastingEntity
      for case given ClassTag[EntityPlayerMPFake] <- classNamed("carpet.patches.EntityPlayerMPFake") do
        others = others.filter:
          case _: EntityPlayerMPFake => false
          case _ => true
      val sorted = others.toSeq.sortBy(_.getPos.squaredDistanceTo(summon[CastingEnvironment].mishapSprayPos))
      sorted.headOption.fold(NullIota())(EntityIota(_))
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
          if filter.isEmpty then (img.withStack(_ :+ ListIota(target.getList.filter(_.isTruthy).toSeq)), cont, HexEvalSounds.THOTH, Seq()) // short-circuit on empty filter
          else target.getList.toSeq match
            case first +: rest =>
              // set up filter, ideally FilterFrame would do this
              (img.withStack(_ :+ first), cont.pushFrame(FilterFrame(saved, filter.getList.toSeq, first, Seq(), rest)).pushFrame(FrameEvaluate(filter.getList, true)), HexEvalSounds.THOTH, Seq())
            case _ =>
              // we can't start a filter with no iota, but it'd always be empty anyway
              (img.withStack(_ :+ ListIota(Seq())), cont, HexEvalSounds.THOTH, Seq())
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
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral:
      locally(summon[CastingEnvironment]).getCastingEntity match
        case null => throw MishapBadCaster()
        case p: ServerPlayerEntity => p.getComponent(PlayerInfoComponent.key).murmur.fold(NullIota())(StringIota.make)
        case _ => throw MishapBadCaster()
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
  Patterns.register("reveal", ne"deqed"):
    Patterns.mkConstAction(1, 0):
      case Seq(iota: Iota) =>
        locally(summon[CastingEnvironment]).getCastingEntity match
          case null => throw MishapBadCaster()
          case p: ServerPlayerEntity =>
            p.getComponent(PlayerInfoComponent.key).chatLines = iota match
              case s: ListIota => s.getList.map(_.display).toSeq
              case _ => Seq(iota.display)
            p.syncComponent(PlayerInfoComponent.key)
            Seq()
          case _ => throw MishapBadCaster()
  ServerPlayNetworking.registerGlobalReceiver("murmur", (_, player, _, buf, _) =>
    val in = Option.when(buf.readBoolean())(buf.readString())
    if isDev then println(s"${player.getName.getString} murmurs: $in")
    player.getComponent(PlayerInfoComponent.key).murmur = in)
  lazy val messageFrameType: ContinuationFrame.Type[MessageFrame] = (c: NbtCompound, world: ServerWorld) =>
    val id = Uuids.toUuid(c.getIntArray("id"))
    MessageFrame(id, Text.Serializer.fromJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, c.getCompound("t"))), world.getServer.getPlayerManager.getPlayer(id))
  class MessageFrame(id: UUID, text: Text, player: => ServerPlayerEntity) extends ContinuationFrame:
    override def getType: ContinuationFrame.Type[MessageFrame] = messageFrameType
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
    override def breakDownwards(stack: ju.List[? <: Iota]): Pair[java.lang.Boolean, ju.List[Iota]] = Pair(false, stack.toSeq)
    override def size = 0
  hexXplat.getContinuationTypeRegistry("send_message") = messageFrameType
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
            true
  ServerPlayNetworking.registerGlobalReceiver("sync_mediaweave", (_, player, _, buf, _) =>
    val flags = buf.readByte()
    val c = player.getComponent(PlayerInfoComponent.key)
    val cursorStack = player.playerScreenHandler.getCursorStack()
    def currentWeave =
      if (flags & 4) != 0 then
        c.leftWeave
      else
        c.rightWeave
    lazy val lastWeave = currentWeave
    if (flags & 1) != 0 && (flags & 8) == 0 then
      assume(cursorStack.isEmpty || (flags & 2) != 0, "if this isn't empty, the player's held item gets voided")
      buf.readItemStack()
      player.playerScreenHandler.setCursorStack(lastWeave.copyAndEmpty())
      PlayerInfoComponent.key.sync(player)
    if (flags & 2) != 0 then
      buf.readItemStack()
      assume(currentWeave.isEmpty, "if this isn't empty, an equipped mediaweave gets voided")
      if (flags & 4) != 0 then
        c.leftWeave = cursorStack
      else
        c.rightWeave = cursorStack
      PlayerInfoComponent.key.sync(player)
    if (flags & 8) != 0 then
      val text = buf.readString()
      val c = player.getComponent(PlayerInfoComponent.key)
      val stack = if (flags & 4) != 0 then c.leftWeave else c.rightWeave
      stack.getItem match
        case m@Mediaweave(color) => m.readIotaTag(stack) match
          case t: NbtCompound => IotaType.deserialize(t, player.getServerWorld) match
            case s: ListIota =>
              given ServerWorld = env.getWorld
              lazy val env = new PlayerBasedCastEnv(player,
                if player.getMainArm match
                  case Arm.LEFT => (flags & 4) != 0
                  case Arm.RIGHT => (flags & 4) == 0
                then Hand.MAIN_HAND else Hand.OFF_HAND
              ):
                override def extractMediaEnvironment(cost: Long, simulate: Boolean): Long =
                  if player.isCreative then 0L else extractMediaFromInventory(cost, canOvercast, simulate)
                override def getCastingHand: Hand = castingHand
                override def getPigment = FrozenPigment(ItemStack(HexItems.DYE_PIGMENTS.get(color)), Util.NIL_UUID)
              val context =
                if (flags & 1) != 0 then
                  Seq.fill(buf.readInt)(buf.readUnlimitedNbt: Iota)
                else
                  Seq()
              val image = CastingImage(context :+ StringIota.make(text), 0, Seq(), false, 0, NbtCompound(), null)
              val instrs = s.getList.asScala.toSeq
              val vm = CastingVM(image, env)
              val view = vm.queueExecuteAndWrapIotas(instrs :+ ContinuationIota(SpellContinuation.NotDone(MessageFrame(player.getUuid, stack.getName, player), SpellContinuation.Done.INSTANCE)), player.getServerWorld)
              val packet = MsgNewSpiralPatternsS2C(player.getUuid, instrs.collect { case p: PatternIota => p.getPattern }.asJava, 140)
              hexXplat.sendPacketToPlayer(player, packet)
              hexXplat.sendPacketTracking(player, packet)
            case _ =>
          case null =>
        case _ =>)
  // dump patterns
  val out = Files.newOutputStream(Path.of("patterns.csv"))
  try
    val o = OutputStreamWriter(out)
    for ent <- hexXplat.getActionRegistry.getEntrySet.asScala.toSeq.sortBy(_.getKey.getValue.toString) do
      o.write(s"${ent.getKey.getValue},${ent.getValue.prototype.getStartDir},${ent.getValue.prototype.anglesSignature}\n")
    o.flush()
  finally
    out.close()

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
  def unapply[T <: Iota: IotaType as ty: ClassTag, I <: Int: Const as i](iota: Iota): Some[T] =
    iota match
      case iota: T => Some(iota)
      case _ => throw MishapInvalidIota(iota, i, ty.typeName)

given IotaType[PropertyIota] = PropertyIota.TYPE
given IotaType[Vec3Iota] = Vec3Iota.TYPE

case class Const[T](value: T)
inline given [T <: Singleton] => Const[T] = Const[T](compiletime.constValue[T])
given [T] => Conversion[Const[T], T] = _.value

private[hexic] class ComponentInit extends EntityComponentInitializer, LevelComponentInitializer:
  override def registerEntityComponentFactories(using EntityComponentFactoryRegistry): Unit =
    PlayerInfoComponent.register
  override def registerLevelComponentFactories(using LevelComponentFactoryRegistry): Unit =
    ServerInfoComponent.register

opaque type Attrition = Unit
object Attrition extends Registrar[Attrition]("attrition")

@tailrec
def finishOperation(p: OperationResult)(using env: CastingEnvironment): OperationResult =
  p.getNewContinuation match
    case c: SpellContinuation.Done => p
    case c: SpellContinuation.NotDone =>
      finishCast(c.getFrame.evaluate(c.getNext, env.getWorld, CastingVM(p.getNewImage, env)), p.getNewImage)

inline def finishCast(p: CastResult, oldImage: CastingImage)(using env: CastingEnvironment): OperationResult =
  finishOperation(OperationResult(p.getNewData??oldImage, p.getSideEffects, p.getContinuation, p.getSound))

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

extension (ctx: StringContext)
  def ne(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_EAST)
  def e(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.EAST)
  def se(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_EAST)
  def nw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.NORTH_WEST)
  def w(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.WEST)
  def sw(args: String*): HexPattern = HexPattern.fromAngles(ctx.s(args*), HexDir.SOUTH_WEST)

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

private[hexic] object cfg:
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

//noinspection UnstableApiUsage
object MediaVariant extends TransferVariant[MediaVariant.type]:
  def getNbt = NbtCompound()
  def getObject: MediaVariant.type = MediaVariant
  def isBlank = false
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

case class MishapWrongDimensionKiddo(val dim1: RegistryKey[World], val dim2: RegistryKey[World]) extends Mishap:
  override def accentColor(using CastingEnvironment, Mishap.Context): FrozenPigment = dyeColor(DyeColor.MAGENTA)
  override def errorMessage(using CastingEnvironment, Mishap.Context): Text =
    val env = summon[CastingEnvironment]
    val color = env.getPigment.getColorProvider.getColor(0, Vec3d.ZERO)
    val mind = t"Mind".styled(_ `withColor` color)
    t"My ${mind} cannot think in ${dim1.getValue `toTranslationKey` "dimension"} and ${dim2.getValue `toTranslationKey` "dimension"} at once"
  override def execute(using CastingEnvironment, Mishap.Context, util.List[Iota]): Unit = ???

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

extension [T](l: util.AbstractList[T])
  def apply(n: Int): T = l.get(n)
  def update(n: Int, x: T): Unit = l.set(n, x)
extension (c: NbtCompound)
  def apply(k: String): NbtElement | Null = c.get(k)
  def update(k: String, v: NbtElement | Null): Unit = c.put(k, v)

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

inline given DynamicOps[JsonElement] = JsonOps.COMPRESSED
extension [T: DynamicOps as t] (x: T) def convertDynamic[R: DynamicOps as r]: R = t.convertTo(r, x)

given (vm: CastingVM) => CastingEnvironment = vm.getEnv
given envGetWorld: (env: CastingEnvironment) => ServerWorld = env.getWorld

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

abstract case class AbstractMetatableIota(iotaType: MetatableIotaType & Singleton, userdata: Iota, override val display: Text, metatable: String, readonlyMetatable: Boolean) extends Iota(iotaType, (userdata, display, metatable, readonlyMetatable)):
  override def subIotas(): lang.Iterable[Iota] = util.List.of(userdata)
  override def toleratesOther(that: Iota): Boolean = that match
    case AbstractMetatableIota(_, u, _, m, _) => metatable == m && Iota.tolerates(userdata, u)
    case _ => Iota.tolerates(userdata, that)
  override def serialize(): NbtElement = NbtCompound().tap: c =>
    c.put("userdata", IotaType.serialize(userdata))
    c.put("display",  Text.Serializer.toJsonTree(display).convertDynamic)
    c.put("metatable", metatable)
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
  override def execute(using vm: CastingVM, world: ServerWorld, continuation: SpellContinuation): CastResult = callMetamethod(se"deaqq")(vm.getImage, continuation)
  override def size = userdata.size + 1
  def callMetamethod(using env: CastingEnvironment)(key: HexPattern)(image: CastingImage, continuation: SpellContinuation): CastResult =
    val callee = mro(key).getOrElse(PatternIota(key))
    val result = OpEval.INSTANCE.exec(env, image, continuation, image.getStack.init :+ userdata, callee)
    CastResult(callee, result.getNewContinuation, result.getNewImage, result.getSideEffects, ResolvedPatternType.EVALUATED, result.getSound)
  class MishapBadMetatable(name: String, value: Iota, readonly: Boolean) extends Mishap():
    override def errorMessage(env: CastingEnvironment, ctx: Context): Text = Text.translatable("hexic.bad_metatable", name, value.display)
    override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.GRAY)
    override def execute(env: CastingEnvironment, ctx: Context, stack: ju.List[Iota]): Unit =
      stack(stack.length - 1) = GarbageIota()
      if !readonly then StateStorage.Companion.setProperty(env.getWorld, name, GarbageIota())

case class MetatableIotaType private[hexic](override val color: Int) extends IotaType[AbstractMetatableIota]:
  class Instance(userdata: Iota, display: Text, metatable: String, readonlyMetatable: Boolean) extends AbstractMetatableIota(MetatableIotaType.this, userdata, display, metatable, readonlyMetatable)
  override def deserialize(tag: NbtElement, world: ServerWorld): Instance =
    val c = tag.downcast[NbtCompound]
    Instance(
      userdata = IotaType.deserialize(c.get("userdata").downcast[NbtCompound], world),
      display = Text.Serializer.fromJson(c.get("display").convertDynamic: JsonElement),
      metatable = c.get("metatable").downcast[NbtString].asString,
      readonlyMetatable = c.getBoolean("ro"),
    )
  override def display(tag: NbtElement): Text = Text.Serializer.fromJson(tag.downcast[NbtCompound].get("display").convertDynamic: JsonElement)

object MetatableIotaType:
  val validValues = Seq(0x0, 0x3, 0x6, 0x9, 0xC, 0xF)
  val colors: (Int, Int, Int) :> MetatableIotaType = (for r <- validValues; g <- validValues; b <- validValues yield (r, g, b) -> MetatableIotaType((r << 20) | (r << 16) | (g << 12) | (g << 8) | (b << 4) | b)).toMap
  println(s"Metatables: $colors")

object TripwireIota extends Iota(new IotaType[TripwireIota.type]:
  override def deserialize(tag: NbtElement, world: ServerWorld): TripwireIota.type = TripwireIota
  override def color: Int = 0xba4216
  override def display(tag: NbtElement): Text = typeName
, Object()):
  override def isTruthy: Boolean = true
  override def toleratesOther(that: Iota): Boolean = eq(that)
  override def serialize(): NbtElement = NbtCompound()
  class Frame(mishap: Mishap) extends ContinuationFrame:
    override def breakDownwards(x: ju.List[? <: Iota]): Pair[java.lang.Boolean, ju.List[Iota]] = ???
    override def evaluate(cont: SpellContinuation, world: ServerWorld, vm: CastingVM): CastResult = throw mishap
    override def getType: Type[?] = Frame
    override def serializeToNBT(): NbtCompound =
      val c = NbtCompound()
      val codec = mishap.getCodec
      c.putString("Class", mishap.getClass.getName)
      c.put("Data", codec.encodeStart(NbtOps.INSTANCE, mishap.asInstanceOf).getOrThrow(false, _ => {}))
      c
    override def size: Int = 1
  object Frame extends ContinuationFrame.Type[Frame]:
    override def deserializeFromNBT(c: NbtCompound, world: ServerWorld): Frame =
      val klass = classNamed(c.getString("Class"))
      val codec = klass.get.runtimeClass.getMethod("getCodec").invoke(null).asInstanceOf[Codec[? <: Mishap]]
      Frame(codec.decode(NbtOps.INSTANCE, c.get("Data")).getOrThrow(false, _ => {}).getFirst)

case class LocationIota(vec: Vec3d, dim: Option[RegistryKey[World]]) extends Vec3Iota(vec), IotaTypeHint:
  override def serialize: NbtElement = NbtCompound().tap(_.put("vec", super.serialize())).tap(n => dim.map(v => n.putString("dim", v.getValue.toString)))
  override def hexic$iotaType(): IotaType[?] = LocationIota

object LocationIota extends IotaType[LocationIota]:
  override def color: Int = Vec3Iota.TYPE.color()
  override def deserialize(using NbtElement, ServerWorld): LocationIota = ???
  override def display(d: NbtElement): Text = d match
    case d: NbtCompound => Vec3Iota.TYPE.display(d.get("vec"))
    case _ => null

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

given Codec[Text] = Codecs.TEXT
given DynamicOps[NbtElement] = NbtOps.INSTANCE

given IotaType[DoubleIota] = DoubleIota.TYPE
given IotaType[StringIota] = StringIota.TYPE
given IotaType[LocationIota] = LocationIota
given IotaType[NbtIota] = NbtIota

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
  override def isTruthy: Boolean = true
  override def toleratesOther(that: Iota): Boolean =
    that match
      case v: VariantIota[T] => key == v.key && data == v.data
      case _ => false
  override def serialize: NbtElement =
    data.toNbt tap(_.putString("type", key.getValue.toString))
//noinspection UnstableApiUsage
object VariantIota extends IotaType[VariantIota[?]], Registrar[VariantIota.Reader]("transfer_variants"):
  type Reader = NbtCompound => Option[VariantIota.TaggedVariant]
  trait TaggedVariant:
    type T: ClassTag
    def variant: TransferVariant[T]
    def display: Text
  def color: Int = 0x720a0a
  private[hexic] def parseVariant(c: NbtCompound): Option[(TaggedVariant, RegistryKey[Reader])] =
    for
      i <- Option(Identifier.tryParse(c.getString("type")))
      entry <- Option.fromNullable(registry.get(i))
      parsed <- entry(c)
    yield (parsed, RegistryKey.of(VariantIota, i))
  end parseVariant
  def deserialize(using NbtElement, ServerWorld): VariantIota[?] | Null =
    summon[NbtElement] match
      case c: NbtCompound =>
        parseVariant(c) match
          case Some((t, k)) =>
            import t.given
            VariantIota(t.variant, k)
          case None => null
      case _ => null
  end deserialize
  override def display(e: NbtElement): Text = parseVariant(e.downcast).fold(NullIota.DISPLAY)(_._1.display)
  end display
  registry(Identifier("item")) = c =>
    val s = ItemVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Item
        def variant: TransferVariant[Item] = s
        def display: Text = t"${s.getItem.getName(s.toStack)}: ${ItemInlineData(s.toStack).asText(true)}"
          .styled(_.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(s.toStack))))
  registry(Identifier("fluid")) = c =>
    val s = FluidVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Fluid
        def variant: TransferVariant[Fluid] = s
        def display: MutableText = t"${s.getFluid.getDefaultState.getBlockState.getBlock.getName}: ${ItemInlineData.make(s.getFluid.getBucketItem.getDefaultStack)}"
  registry("media") = c =>
    Some(new TaggedVariant:
      type T = MediaVariant.type
      def variant: MediaVariant.type = MediaVariant
      def display: Text = Text.literal("Media").styled(_.withColor(0x74b3f2)))

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
case class MapIota(map: Map[NbtCompound, NbtCompound] = Map(), trusted: Boolean = false)(using val world: ServerWorld) extends Iota(MapIota, map):
  def get(key: Iota): Option[Iota] = map.get(IotaType.serialize(key)).map(IotaType.deserialize(_, summon))
  def apply(key: Iota): Iota = get(key) getOrElse NullIota()
  def -(keys: Iota*): MapIota = MapIota(map -- (keys map IotaType.serialize))
  def --(other: MapIota): MapIota = MapIota(map -- other.map.keys)
  def +(pairs: (Iota, Iota)*): MapIota = MapIota(map ++ pairs.map(_ both IotaType.serialize))
  def ++(other: MapIota): MapIota = MapIota(map ++ other.map)
  def update(f: map.type => Map[NbtCompound, NbtCompound]): MapIota = MapIota(map pipe f)
  def head: (Iota, Iota) = map.head both(IotaType.deserialize(_, summon))
  def tail: MapIota = MapIota(map.tail)
  def init: MapIota = MapIota(map.init)
  def last: (Iota, Iota) = map.last both(IotaType.deserialize(_, summon))
  def headOption: Option[(Iota, Iota)] = map.headOption map(_.both(IotaType.deserialize(_, summon)))
  def lastOption: Option[(Iota, Iota)] = map.lastOption map(_.both(IotaType.deserialize(_, summon)))
  def &(other: MapIota): MapIota = MapIota(map.filter(_._1 pipe other.map.contains))
  def ^(other: MapIota): MapIota = mutable.Map[NbtCompound, NbtCompound]().tap(m =>
    m.addAll(map)
    for ((k, v) <- other.map) do
      if m contains k then
        m -= k
      else
        m += (k -> v)
  ) pipe (_.toMap) pipe (new MapIota(_))
  def toList: util.List[Iota] = map.flatMap((k, v) => Seq(k: Iota, v: Iota)).toSeq
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  override def serialize(): NbtElement = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) tap l.add)
  override def size = map.toSeq.map(_.size + _.size - 1).sum + 1
  override def subIotas(): lang.Iterable[Iota] =
    if trusted then
      // skip deserialization for performance
      // this is safe because `trusted` can only be true if truenames have
      // already been checked, and we override `size`
      Seq()
    else
      toList
object MapIota extends IotaType[MapIota]:
  def color: Int = 0xb0641c
  def deserialize(using data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    def o = HexUtils.downcast(_, NbtCompound.TYPE)
    l.map(o)
      .map(c => ((c("k") pipe o) -> (c("v") pipe o)))
      .toMap[NbtCompound, NbtCompound]
      .pipe(new MapIota(_, trusted = true))
  def display(data: NbtElement): Text =
    val items = HexUtils.downcast(data, NbtList.TYPE)
    val output: MutableText = "["
    output.styled(_.withColor(color))
    if items.nonEmpty then
      def castToCompound = HexUtils.downcast(_, NbtCompound.TYPE)
      val itemPair = items.map(castToCompound).iterator
      def writePair(pair: NbtCompound) =
        output.append(try IotaType.getDisplay(pair("k") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
        output.append(" → ")
        output.append(try IotaType.getDisplay(pair("v") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
      writePair(itemPair.next())
      while itemPair.hasNext do
        output.append(", ")
        writePair(itemPair.next())
    else
      output.append("→")
    output.append("]")
    output
trait IotaCoercion[T]:
  typ: IotaType[I] =>
  // need _root_ path, since `typ` could theoretically have these as members
  type I <: _root_.at.petrak.hexcasting.api.casting.iota.Iota
  def foo = ???
def downcast[R: ClassTag](t: Any): Option[R] = t match
  case r: R => Some(r)
  case _ => None
object NbtIota extends IotaType[NbtIota]:
  def name: Text = ("NBT": MutableText).styled(_.withColor(color))
  def color: Int = Formatting.DARK_AQUA.getColorValue
  def deserialize(using NbtElement, ServerWorld): NbtIota = NbtIota(summon)
  def display(e: NbtElement): Text = Text.literal(StringNbtWriter()(e)).styled(_.withColor(color))
