//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.{ActionRegistryEntry, ParticleSpray, RenderedSpell, SpellList}
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.castables.{Action, ConstMediaAction, SpecialHandler}
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.{EvalSound, OperatorSideEffect}
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, ContinuationFrame, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, MishapEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapBadCaster, MishapBadOffhandItem, MishapInvalidIota, MishapNotEnoughArgs, MishapOthersName}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.{HexUtils, MediaHelper}
import at.petrak.hexcasting.common.lib.{HexItems, HexRegistries}
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents
import at.petrak.hexcasting.xplat.IXplatAbstractions
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
import net.minecraft.block.Block
import net.minecraft.command.argument.{EntityArgumentType, NbtElementArgumentType}
import net.minecraft.command.{CommandException, EntitySelector}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluid
import net.minecraft.inventory.{SidedInventory, StackReference}
import net.minecraft.item.{Item, ItemStack}
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
import net.minecraft.util.math.{BlockPos, Vec3d}
import net.minecraft.util.{Arm, ClickType, DyeColor, Formatting, Hand, Identifier, Rarity, Util, Uuids, WorldSavePath}
import net.minecraft.world.World
import org.eu.net.pool.common_curses.client.CommonCursesClientKt
import org.eu.net.pool.common_curses.{CommonCursesKt, SlotAccess, TextManipulator}
import org.eu.net.pool.hexic
import org.eu.net.pool.hexic.ducks.SimpleRegistryDuck
import org.objectweb.asm.{ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, InsnList}
import org.slf4j.{Logger, LoggerFactory}
import org.spongepowered.asm.mixin.injection.callback.{CallbackInfo, CallbackInfoReturnable}
import ram.talia.hexal.api.casting.iota.{GateIota, MoteIota}
import ram.talia.moreiotas.api.casting.iota.{EntityTypeIota, IotaTypeIota, ItemStackIota, ItemTypeIota, MatrixIota, StringIota}
import sun.misc.Unsafe

import java.io.{File, FileNotFoundException, FileOutputStream, IOException, InputStream}
import java.lang.invoke.MethodHandles
import java.lang.reflect.{Constructor, Field, Member, Method}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.{Optional, UUID}
import java.{lang, util}
import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.{experimental, showAsInfix, tailrec, targetName, unused}
import scala.collection.convert.ImplicitConversions.*
import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.language.experimental.{macros, saferExceptions}
import scala.language.{dynamics, existentials, implicitConversions, postfixOps, reflectiveCalls}
import scala.reflect.{ClassTag, classTag}
import scala.util.{NotGiven, Random, TupledFunction, boundary}
import scala.util.chaining.given
import at.petrak.hexcasting.api.casting.mishaps.Mishap.Context
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler
import net.fabricmc.fabric.api.networking.v1.{FabricPacket, PacketSender, PacketType, ServerPlayNetworking}
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.Direction.Axis

import java.util as ju
import scala.math.Ordered.orderingToOrdered
import scala.util.CommandLineParser.FromString
import scala.util.boundary.Label
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type
import at.petrak.hexcasting.api.item.{IotaHolderItem, MediaHolderItem}
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.{ItemMediaHolder, ItemPackagedHex}
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C
import at.petrak.hexcasting.fabric.cc.adimpl.CCMediaHolder
import kotlin.jvm.internal.DefaultConstructorMarker
import net.minecraft.client.item.{BundleTooltipData, TooltipContext, TooltipData}
import net.minecraft.entity.{Entity, LivingEntity}
import net.minecraft.screen.slot.Slot
import net.minecraft.sound.{SoundCategory, SoundEvents}
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
import net.minecraft.block.AbstractBlock
import org.eu.net.pool.hexic.mixin.ItemStackAccess

given Logger = LoggerFactory.getLogger("hexic")

val fabric = FabricLoader.getInstance
val isDev = fabric.isDevelopmentEnvironment

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
    xs.foldLeft(op)(summon[Outcome[T]](_, _))

given Outcome[OperationResult => OperationResult] = (res, f) => f(res)
given [T: Outcome]: Outcome[Seq[T]] = (res, value) => res -> Outcome(value*)

//given Outcome[Seq[Iota]]:
//  override def ->:(res: OperationResult): OperationResult = ???
//given Outcome[Iota]:
//  override def ->:

extension [T] (r: Registry[T])
  def apply(key: Identifier | RegistryKey[?] | Int) =
    key match
      case i: Identifier => r.get(i)
      case i: Int => r.get(i)
      case k: RegistryKey[?] => r.get(k.asInstanceOf[RegistryKey[T]])
  def update(key: Identifier | RegistryKey[?], value: T) =
    key match
      case i: Identifier => Registry.register(r, i, value)
      case k: RegistryKey[?] => Registry.register(r, k.asInstanceOf[RegistryKey[T]], value)

class :?[T, G](private val value: T)(using private val proof: G)
object :? :
  given wrap[T, G](using G): Conversion[T, T :? G] = x => :?(x)
  given unwrap[T, G]: Conversion[T :? G, T] = _.value
  given prove[T, G]: Conversion[(G ?=> T) :? G, T] = x => x.value(using x.proof)
  given unneeded[T, G]: Conversion[T, G ?=> T] with
    override def apply(x: T): G ?=> T = _ ?=> x
import :?.given

object Patterns:
  def mkAction(body: (CastingEnvironment, ServerWorld) ?=> (CastingImage, SpellContinuation) => (OperationResult | CastResult | (CastingImage, SpellContinuation, EvalSound, Seq[OperatorSideEffect]))): Action =
    (env: CastingEnvironment, image: CastingImage, cont: SpellContinuation) =>
      body(using env, env.getWorld)(image, cont) match
        case res: OperationResult => res
        case res: CastResult => OperationResult(res.getNewData, res.getSideEffects, res.getContinuation, res.getSound)
        case (img, cont, sound, effects) => OperationResult(img, effects, cont, sound)
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
  def register(id: Identifier, pattern: HexPattern)(body: Action): Unit =
    actionRegistry(id) = ActionRegistryEntry(pattern, body)

inline def unsafe(using u: Unsafe) = u
val hexXplat: IXplatAbstractions = IXplatAbstractions.INSTANCE

extension (ctx: StringContext) def ifModLoaded(`then`: => Unit, `else`: => Unit = {}): Unit =
  if isDev || fabric.isModLoaded(ctx.parts(0)) then
    `then`
  else
    `else`

class Pointer[T](val address: Long) extends AnyVal:
  inline def cast[R]: Pointer[R] = Pointer(address)
  inline def alloc(newSize: Long) = Pointer(unsafe.reallocateMemory(address, newSize))
  inline def free(): Unit = unsafe.freeMemory(address)
  inline transparent def value: T =
    import scala.compiletime._
    summonFrom:
      case ev: (Pointer[t] =:= T) => ev(Pointer(unsafe.getAddress(address)))
      case ev: (Int =:= T) => ev(unsafe.getInt(address))
      case ev: (Long =:= T) => ev(unsafe.getLong(address))
      case ev: (Float =:= T) => ev(unsafe.getFloat(address))
      case ev: (Double =:= T) => ev(unsafe.getDouble(address))
      case ev: (Char =:= T) => ev(unsafe.getChar(address))
      case ev: (Short =:= T) => ev(unsafe.getShort(address))
      case ev: (Byte =:= T) => ev(unsafe.getByte(address))
      case _ => error("Cannot use non-primitive types with pointers")
  inline def value_=(value: T): Unit =
    import scala.compiletime._
    summonFrom:
      case ev: (T =:= Pointer[t]) => unsafe.putAddress(address, ev(value).address)
      case ev: (T =:= Int) => unsafe.putInt(address, ev(value))
      case ev: (T =:= Long) => unsafe.putLong(address, ev(value))
      case ev: (T =:= Float) => unsafe.putFloat(address, ev(value))
      case ev: (T =:= Double) => unsafe.putDouble(address, ev(value))
      case ev: (T =:= Char) => unsafe.putChar(address, ev(value))
      case ev: (T =:= Short) => unsafe.putShort(address, ev(value))
      case ev: (T =:= Byte) => unsafe.putByte(address, ev(value))
      case _ => error("Cannot use non-primitive types with pointers")
  inline def +(offset: Long): Pointer[T] = Pointer(address + offset * Pointer.sizeOf[T])
object Pointer:
  inline def alloc[T](count: Long): Pointer[T] = Pointer(unsafe.allocateMemory(size * sizeOf[T]))
  inline def size: Long = unsafe.addressSize
  inline def sizeOf[T]: Long =
    import scala.compiletime._
    inline erasedValue[T] match
      case ev: Pointer[t] => Pointer.size
      case ev: Int => 4
      case ev: Long => 8
      case ev: Float => 4
      case ev: Double => 8
      case ev: Char => 4
      case ev: Short => 2
      case ev: Byte => 1
      case _ => error("Cannot use non-primitive types with pointers")

def bullshit(bytes: Array[Byte]): Unit =
  MethodHandles.lookup.defineHiddenClass(bytes, true)
def bullshit(node: ClassNode): Unit =
  val writer = ClassWriter(0)
  node accept writer
  bullshit(writer toByteArray)

def runInstrs(instrs: InsnList) =
  val node = ClassNode()
  node.superName = classOf[Runnable].getName
  val method = tree.MethodNode(Member.PUBLIC, "run", "()V", null, Array.empty)
  node.methods.add(method)
  val writer = ClassWriter(0)
  node accept writer
  val id = "_runnable" + UUID.randomUUID().toString.replace("-", "")
  ClassTinkerers.define(id, writer toByteArray)
  classNamed(id).get.runtimeClass.newInstance.asInstanceOf[Runnable].run()

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
  var wispMedia: Option[Long] = None,
  var murmur: Option[String] = None,
  var leftWeave: ItemStack = ItemStack.EMPTY,
  var rightWeave: ItemStack = ItemStack.EMPTY,
  var chatLines: Seq[Text] = Seq(),
) extends Component, AutoSyncedComponent:
  override def readFromNbt(c: NbtCompound): Unit =
    if c.getBoolean("isWisp") then
      wispMedia = Some(c.getLong("media"))
    else
      wispMedia = None
    if c.contains("shl", NbtElement.COMPOUND_TYPE) then
      leftWeave = ItemStack.fromNbt(c.getCompound("shl"))
    else
      leftWeave = ItemStack.EMPTY
    if c.contains("shr", NbtElement.COMPOUND_TYPE) then
      rightWeave = ItemStack.fromNbt(c.getCompound("shr"))
    else
      rightWeave = ItemStack.EMPTY
    chatLines = c.getList("chat", NbtElement.COMPOUND_TYPE).map(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, _)).map(Text.Serializer.fromJson).toSeq

  override def writeToNbt(c: NbtCompound): Unit =
    wispMedia match
      case None =>
        c.putBoolean("isWisp", false)
      case Some(media) =>
        c.putBoolean("isWisp", true)
        c.putLong("media", media)
    if !leftWeave.isEmpty then c.put("shl", NbtCompound().tap(leftWeave.writeNbt))
    if !rightWeave.isEmpty then c.put("shr", NbtCompound().tap(rightWeave.writeNbt))
    c.put("chat", NbtList().tap(_.addAll(chatLines.map(Text.Serializer.toJsonTree).map(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, _)))))
object PlayerInfoComponent:
  val key: ComponentKey[PlayerInfoComponent] = ComponentRegistry.getOrCreate("player_wisp", classOf[PlayerInfoComponent])
  private[hexic] def register(using fac: EntityComponentFactoryRegistry) =
    fac.registerForPlayers(key, PlayerInfoComponent(_), RespawnCopyStrategy.LOSSLESS_ONLY)

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

lazy val iotaTypeRegistry = hexXplat.getIotaTypeRegistry
lazy val actionRegistry = hexXplat.getActionRegistry

trait ServerAware[T <: IotaType[?]]:
  type Iota = T match { case IotaType[t] => t }
  def setServer(iota: Iota, server: String): Unit

given [T <: java.lang.Enum[T]: ClassTag as ct] => FromString[T]:
  override def fromString(s: String): T = Enum.valueOf[T](ct.runtimeClass.asInstanceOf[Class[T]], s)

extension [T] (x: T | Null)
  inline def ?[R](f: T => R): R | Null = x match
    case null => null
    case x: T => f(x)

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
      for
        nbt <- Option(stack.getNbt).toSeq
        case item: NbtCompound <- nbt.getList("Contents", NbtElement.COMPOUND_TYPE)
      yield ItemStack.fromNbt(item)
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
          stack.heldItems = held :+ otherStack.copyAndEmpty()
          player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F, 0.8F + player.getWorld.getRandom.nextFloat * 0.4F)
      true
    else
      false
  private def fits(held: Seq[ItemStack], subj: Item): Boolean =
    val cur = held.map(_.getItem match { case b: MediaBundle => b.size/2; case _ => 1 }).sum
    subj match
      case MediaBundle(_, subjSize) => subjSize <= size && cur + subjSize/2 <= size
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
          stack.heldItems = held :+ slot.getStack.copyAndEmpty()
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
    recursive.foldLeft(mine)((p: M, q: M) => (
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

val stringworms =
  Seq("pure", "action", "hex", "media", "thing").map(_ -> new Stringworm)

object dyedStringworm extends Stringworm:
  override def getName(stack: ItemStack): Text =
    stack.getSubNbt("pigment") match
      case null => super.getName(stack)
      case n => Text.translatable(getTranslationKey, FrozenPigment.fromNBT(n).item.getName.getString.replace("Pigment", "Stringworm"))

object Extern:
  def getStringworm(idx: Int) = stringworms(idx)._2

val _ =
  Interop.playerDeathHook = (p: PlayerEntity, out: util.List[ItemStack]) =>
    val c = p.getComponent(PlayerInfoComponent.key)
    if !c.rightWeave.isEmpty then
      out.add(c.rightWeave)
      c.rightWeave = ItemStack.EMPTY
    if !c.leftWeave.isEmpty then
      out.add(c.leftWeave)
      c.leftWeave = ItemStack.EMPTY

trait Default[T]:
  def default: T
given Default[Int]:
  def default = 0

implicit class EntityExt(e: Entity) extends AnyVal, Dynamic

extension (e: EntityExt)
  def selectDynamic[T: {Codec, Default}](key: String): T = ???
  def updateDynamic[T: Codec](key: String)(value: T): Unit = ???

given Codec[Int] = Codec.INT.xmap(p => p, p => p)

def test =
  val p: ServerPlayerEntity = ???
  val ext: EntityExt = p
  ext.foo = 2
  println(ext.foo: Int)

type Media = Long
object MediaBundle:
  val items: Seq[MediaBundle] = for i <- Seq(6, 12); c <- DyeColor.values yield new MediaBundle(c, i)
  def apply(c: DyeColor, s: Int) = items.find(b => b.color == c && b.size == s).get
  private val PERCENTAGE = new DecimalFormat("####")
  PERCENTAGE.setRoundingMode(RoundingMode.DOWN)
  private val DUST_AMOUNT = new DecimalFormat("###,###.##")
val wizard = Item(Item.Settings().rarity(Rarity.EPIC).maxCount(1))

trait HasCodec:
  def getCodec: Codec[? <: this.type]
given [T <: Mishap] => Conversion[T, HasCodec] = _.asInstanceOf

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
      "see line 631 for more information",
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
  iotaTypeRegistry("stack") = StackIota
  iotaTypeRegistry("map") = MapIota
  iotaTypeRegistry("tripwire") = TripwireIota.getType
  for ((_, c), i) <- MetatableIotaType.colors.zipWithIndex do iotaTypeRegistry(s"meta/$i") = c
  ifModLoaded"infinite-hexxy${
    iotaTypeRegistry("jvm/class") = ClassIota
    iotaTypeRegistry("jvm/pointer") = PointerIota
  }"
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
  Patterns.register("nbt/serialize", nw"edwaq"):
    Patterns.mkConstAction(1):
      case Seq(x: Iota) => Seq(IotaType.serialize(x))
  Patterns.register("tripwire", w"edewqwaqede"):
    Patterns.mkLiteral(TripwireIota)
  Patterns.register("spellmind/save", e"aqqqqqeawqwqwqwqwqweawwqwwqwwqwwqwwqwweawwwqwwwqwwwqwwwqwwwqwww"):
    Patterns.mkAction: (img, cont) =>
      ???
  Patterns.register("spellmind/restore", e"deeeeeqdwewewewewewqdwwewwewwewwewwewwqdwwwewwwewwwewwwewwwewww"):
    Patterns.mkAction: (img, cont) =>
      ???
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
  hexXplat.getArithmeticRegistry("null_abs") = arith("null_abs", Arithmetic.ABS -> ((_: NullIota) => DoubleIota(0)))
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
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("playerwisp").pipe: c =>
      c.argument("target", EntityArgumentType.players()): c =>
        c.literal("make"): c =>
          c.executes: (ctx: CommandContext[ServerCommandSource]) =>
            val player = ctx.getSource.getPlayer
            player.getComponent(PlayerInfoComponent.key).wispMedia = Some(-1)
            PlayerInfoComponent.key.sync(player)
            1
        c.literal("unmake"): c =>
          c.executes: (ctx: CommandContext[ServerCommandSource]) =>
            val player = ctx.getSource.getPlayer
            player.getComponent(PlayerInfoComponent.key).wispMedia = None
            PlayerInfoComponent.key.sync(player)
            1
        c.literal("media"): c =>
          c.literal("add"): c =>
            c.executes: (ctx: CommandContext[ServerCommandSource]) =>
              ???
          c.literal("set"): c =>
            c.executes: (ctx: CommandContext[ServerCommandSource]) =>
              ???
      // only rasonable to query one player
      c.argument("target", EntityArgumentType.players()): c =>
        c.literal("media"): c =>
          c.literal("query"): c =>
            c.executes: (ctx: CommandContext[ServerCommandSource]) =>
              ???
      c.build()
    )
    d.getRoot.addChild(LiteralArgumentBuilder.literal[ServerCommandSource]("property").pipe: c =>
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
  ifModLoaded"infinite-hexxy${
    extension (ctx: StringContext) def jvm() = e"aqqqqqdeeweweweweeaaedeqedee${ctx.s()}"
    Patterns.register("jvm/class_of_iota", jvm"aeeee"):
      Patterns.mkConstAction(1):
        case Seq(x: Iota) => Seq(ClassIota()(using ClassTag(x.getClass)))
    Patterns.register("jvm/class_of_payload", jvm"dqqqq"):
      Patterns.mkConstAction(1):
        case Seq(x: Iota) =>
          val f = classOf[Iota].getDeclaredField("payload")
          f.setAccessible(true)
          val payload = f.get(x)
          given ClassTag[payload.type] = ClassTag(payload.getClass)
          Seq(ClassIota[payload.type]())
    Patterns.register("jvm/newinstance_unboxed", jvm"aeeeedw"):
      Patterns.mkConstAction(1):
        case Seq(x@ClassIota()) =>
          Seq(uninitialized[x.T](using x.tag).asInstanceOf[Iota])
    Patterns.register("jvm/newinstance_boxed", jvm"dqqqqaw"):
      Patterns.mkConstAction(1):
        case Seq(x@ClassIota()) =>
          Seq(ObjectIota(uninitialized[x.T](using x.tag).asInstanceOf[AnyRef]))
    Patterns.register("malloc", jvm"wwaa"):
      Patterns.mkConstAction(1):
        case Seq(x: DoubleIota) =>
          Seq(PointerIota(Pointer.alloc[Byte](x.getDouble.round)))
    Patterns.register("free", jvm"wwdd"):
      Patterns.mkConstAction(1):
        case Seq(PointerIota(p)) =>
          p.free()
          Seq()
  }"
  Patterns.register("staffcast_factory", ne"wwwwwaqqqqqeaqeaeaeaeaeq"):
    Patterns.mkAction: (img, cont) =>
      summon[CastingEnvironment].getCastingEntity match
        case caster: ServerPlayerEntity =>
          val staffcast = HexCardinalComponents.STAFFCAST_IMAGE.get(caster)
          val oldImage = staffcast.getVM(Hand.MAIN_HAND).getImage
          staffcast.setImage(img)
          val vm = staffcast.getVM(summon[CastingEnvironment].getCastingHand)
          try
            vm.queueExecuteAndWrapIota(PatternIota((HexDir.SOUTH_EAST, "deaqq")), summon)
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
            vm.queueExecuteAndWrapIota(PatternIota((HexDir.SOUTH_EAST, "deaqq")), summon)
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
            HexCardinalComponents.STAFFCAST_IMAGE.sync(caster);
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
          ty.Instance(userdata, display.display, metatable.getName)
  Patterns.register("murmur", e"wwaqwa"):
    Patterns.mkLiteral:
      locally(summon[CastingEnvironment]).getCastingEntity match
        case null => throw MishapBadCaster()
        case p: ServerPlayerEntity => p.getComponent(PlayerInfoComponent.key).murmur.fold(NullIota())(StringIota.make)
        case _ => throw MishapBadCaster()
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
  SlotAccess.playerInventory.register: (player, slot, stack) =>
    player.getComponent(PlayerInfoComponent.key).wispMedia match
      case Some(_) => SlotAccess.LOCK_AND_DROP
      case None => SlotAccess.ALLOW
  ServerPlayNetworking.registerGlobalReceiver("murmur", (_, player, _, buf, _) =>
    val in = Option.when(buf.readBoolean())(buf.readString())
    if isDev then println(s"${player.getName.getString} murmurs: $in")
    player.getComponent(PlayerInfoComponent.key).murmur = in)
  ServerPlayNetworking.registerGlobalReceiver("sync_mediaweave", (_, player, _, buf, _) =>
    val flags = buf.readByte()
    if (flags & 1) != 0 && (flags & 8) == 0 then player.playerScreenHandler.setCursorStack(buf.readItemStack())
    if (flags & 2) != 0 then
      val c = player.getComponent(PlayerInfoComponent.key)
      if (flags & 4) != 0 then
        c.leftWeave = buf.readItemStack()
      else
        c.rightWeave = buf.readItemStack()
      PlayerInfoComponent.key.sync(player)
    if (flags & 8) != 0 then
      val text = buf.readString()
      val c = player.getComponent(PlayerInfoComponent.key)
      val stack = if (flags & 4) != 0 then c.leftWeave else c.rightWeave
      stack.getItem match
        case m@Mediaweave(color) => m.readIotaTag(stack) match
          case t: NbtCompound => IotaType.deserialize(t, player.getServerWorld) match
            case s: ListIota =>
              val env = new PlayerBasedCastEnv(player,
                if player.getMainArm match
                  case Arm.LEFT => (flags & 4) != 0
                  case Arm.RIGHT => (flags & 4) == 0
                then Hand.MAIN_HAND else Hand.OFF_HAND
              ):
                override def extractMediaEnvironment(cost: Long, simulate: Boolean): Long =
                  if player.isCreative then 0L else extractMediaFromInventory(cost, canOvercast, simulate)
                override def getCastingHand: Hand = castingHand
                override def getPigment = FrozenPigment(ItemStack(HexItems.DYE_PIGMENTS.get(color)), Util.NIL_UUID)
              val image = CastingImage(Seq(StringIota.make(text)).asJava, 0, Seq().asJava, false, 0, NbtCompound(), null)
              val instrs = s.getList.asScala.toSeq
              val vm = CastingVM(image, env)
              val view = vm.queueExecuteAndWrapIotas(instrs.asJava, player.getServerWorld)
              println(view)
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

def assume(cond: Boolean, msg: => String = "assumption failed"): Unit = if !cond then panic(msg)

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

private[hexic] class ComponentInit extends EntityComponentInitializer:
  override def registerEntityComponentFactories(using EntityComponentFactoryRegistry): Unit =
    PlayerInfoComponent.register

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

given Conversion[Double, DoubleIota] = DoubleIota(_)
given Conversion[Int, DoubleIota] = DoubleIota(_)
given Conversion[DoubleIota, Double] = _.getDouble
extension (d: DoubleIota) def asIntOrThrow(idx: Int): Int =
  val v = d.getDouble
  if (v.round - v).abs > DoubleIota.TOLERANCE then
    throw MishapInvalidIota.of(d, idx, "int")
  v.round.intValue

extension (i: CastingImage)
  def withStack(m: Seq[Iota] => Seq[Iota]): CastingImage = i.copy(util.ArrayList(m(i.getStack.asScala.toSeq).asJavaCollection), i.getParenCount, i.getParenthesized, i.getEscapeNext, i.getOpsConsumed, i.getUserData)
extension (o: OperationResult)
  def withStack(m: Seq[Iota] => Seq[Iota]): OperationResult = o.copy(o.getNewImage.withStack(m), o.getSideEffects, o.getNewContinuation, o.getSound)

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
      case _ => panic("Iota changed types or became null during serialization")

inline given DynamicOps[JsonElement] = JsonOps.COMPRESSED
extension [T: DynamicOps as t] (x: T) def convertDynamic[R: DynamicOps as r]: R = t.convertTo(r, x)

given (vm: CastingVM) => CastingEnvironment = vm.getEnv
given envGetWorld: (env: CastingEnvironment) => ServerWorld = env.getWorld

abstract case class AbstractMetatableIota(iotaType: MetatableIotaType & Singleton, userdata: Iota, override val display: Text, metatable: String) extends Iota(iotaType, (userdata, display, metatable)):
  override def subIotas(): lang.Iterable[Iota] = util.List.of(userdata)
  override def toleratesOther(that: Iota): Boolean = that match
    case AbstractMetatableIota(_, u, _, m) => metatable == m && Iota.tolerates(userdata, u)
    case _ => Iota.tolerates(userdata, that)
  override def serialize(): NbtElement = NbtCompound().tap: c =>
    c.put("userdata", IotaType.serialize(userdata))
    c.put("display",  Text.Serializer.toJsonTree(display).convertDynamic)
    c.put("metatable", metatable)
  def meta(using world: ServerWorld): MapIota =
    StateStorage.Companion.getProperty(world, metatable) match
      case m: MapIota => m
      case i => throw MishapBadMetatable(metatable, i)
  def meta_=(using world: ServerWorld)(x: MapIota): Unit =
    StateStorage.Companion.setProperty(world, metatable, x)
  override def isTruthy: Boolean = panic("isTruthy")
  override def executable: Boolean = true
  def callMetamethod(using env: CastingEnvironment)(key: HexPattern)(image: CastingImage, continuation: SpellContinuation): CastResult =
    PatternIota(se"deaqq").execute(CastingVM(image.withStack(_ :+ meta.get(PatternIota(key)).getOrElse(PatternIota(key))), summon), summon, continuation)
  override def execute(using vm: CastingVM, world: ServerWorld, continuation: SpellContinuation): CastResult = callMetamethod(se"deaqq")(vm.getImage, continuation)
  class MishapBadMetatable(name: String, value: Iota) extends Mishap():
    override def errorMessage(env: CastingEnvironment, ctx: Context): Text = Text.translatable("hexic.bad_metatable", name, value.display)
    override def accentColor(env: CastingEnvironment, ctx: Context): FrozenPigment = dyeColor(DyeColor.GRAY)
    override def execute(env: CastingEnvironment, ctx: Context, stack: ju.List[Iota]): Unit =
      stack(stack.length - 1) = GarbageIota()
      StateStorage.Companion.setProperty(env.getWorld, name, GarbageIota())

private[hexic] object metatableHook:
  extension (p: PatternIota) def executeHook(using Label[CastResult], ServerWorld)(vm: CastingVM, continuation: SpellContinuation): Unit =
    vm.getImage.getStack.lastOption match
      case Some(m: AbstractMetatableIota) =>
        for x <- m.meta.get(p) do
          // this is probably cursed
          vm.setImage(vm.getImage.withStack(_.init :+ m.userdata :+ x))
          boundary.break(PatternIota(se"deaqq").execute(vm, summon, continuation))
      case _ =>

case class MetatableIotaType private[hexic](override val color: Int) extends IotaType[AbstractMetatableIota]:
  class Instance(userdata: Iota, display: Text, metatable: String) extends AbstractMetatableIota(MetatableIotaType.this, userdata, display, metatable)
  override def deserialize(tag: NbtElement, world: ServerWorld): Instance =
    val c = tag.downcast[NbtCompound]
    Instance(
      userdata = IotaType.deserialize(c.get("userdata").downcast[NbtCompound], world),
      display = Text.Serializer.fromJson(c.get("display").convertDynamic: JsonElement),
      metatable = c.get("metatable").downcast[NbtString].asString,
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

case class StackIota[T](stack: VariantIota[T], count: BigInt) extends Iota(StackIota, stack):
  export stack.given ClassTag[T]
  override def isTruthy: Boolean = ???
  override def toleratesOther(that: Iota): Boolean = that match
    case s: StackIota[?] => stack == s.stack && count == s.count
    case _ => false
  override def serialize: NbtElement = stack.serialize tap (_.asInstanceOf[NbtCompound].putByteArray("n", count.toByteArray))
object StackIota extends IotaType[StackIota[?]]:
  def color: Int = 0xa34646
  def deserialize(using NbtElement, ServerWorld): StackIota[?] =
    val c: NbtCompound = HexUtils.downcast(summon, NbtCompound.TYPE)
    StackIota(VariantIota.deserialize, c.getByteArray("n") pipe (BigInt(_)))
  def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    VariantIota.parseVariant(c).fold(NullIota.DISPLAY)(t => BigInt(c.getByteArray("n")) pipe t._1.display)
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

def x10[T: Numeric](power: T) = "x10" + (power.toString: String).map((c: Char) => "⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '0'))

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

def uninitialized[T: ClassTag](using u: Unsafe) = u.allocateInstance(summon[ClassTag[T]].runtimeClass).asInstanceOf[T]

private def normalize(obj: Any)(using u: Unsafe): Long =
  if u.arrayIndexScale(classOf[Array[Object]]) == 4 then
    u.getInt(obj, 4L).toLong & 0xFFFFFFFFL
  else
    u.getLong(obj, 8L)

def expNotation[T](n: T)(using num: Integral[T]) =
  toExp(n)() match
    case (v, Some(d)) => s"${v}${x10(d)}"
    case (v, None) => v.toString

given Codec[Text] = Codecs.TEXT
given DynamicOps[NbtElement] = NbtOps.INSTANCE

given IotaType[DoubleIota] = DoubleIota.TYPE
given IotaType[StringIota] = StringIota.TYPE
given IotaType[LocationIota] = LocationIota
given IotaType[NbtIota] = NbtIota

case class ClassIota[_T: ClassTag]() extends Iota(ClassIota, summon[ClassTag[_T]]):
  type T = _T
  def tag: ClassTag[T] = summon[ClassTag[T]]
  def runtimeClass: Class[T] = tag.runtimeClass.asInstanceOf
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtString.of(runtimeClass.getName)
object ClassIota extends IotaType[ClassIota[?]]:
  override def color: Int = Formatting.GOLD.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): ClassIota[?] | Null = HexUtils.downcast(element, NbtString.TYPE).asString match
    case "void" => ClassIota()(using ClassTag(java.lang.Void.TYPE))
    case "byte" => ClassIota()(using ClassTag(java.lang.Byte.TYPE))
    case "short" => ClassIota()(using ClassTag(java.lang.Short.TYPE))
    case "int" => ClassIota()(using ClassTag(java.lang.Integer.TYPE))
    case "long" => ClassIota()(using ClassTag(java.lang.Long.TYPE))
    case "boolean" => ClassIota()(using ClassTag(java.lang.Boolean.TYPE))
    case "float" => ClassIota()(using ClassTag(java.lang.Float.TYPE))
    case "double" => ClassIota()(using ClassTag(java.lang.Double.TYPE))
    case "char" => ClassIota()(using ClassTag(java.lang.Character.TYPE))
    case s => classNamed(s).fold(null)(ClassIota()(using _))
  override def display(element: NbtElement): Text =
    val klass = deserialize(element, null).runtimeClass
    (klass.getSimpleName: MutableText).styled(_.withColor(if klass.isPrimitive then Formatting.RED else Formatting.GOLD))

given Conversion[String, NbtString] = NbtString.of
given Conversion[NbtString, String] = _.asString

case class FieldIota(field: Field | Method | Constructor[?]) extends Iota(FieldIota, field):
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtCompound().tap: c =>
    c("c") = field.getDeclaringClass.getName
    field match
      case f: Field =>
        c("f") = NbtByte.of(0: Byte)
        c("n") = field.getName
      case m: Method =>
        c("f") = NbtByte.of(1: Byte)
        c("n") = field.getName
      case k: Constructor[?] =>
        c("f") = NbtByte.of(2: Byte)
        c("k") = NbtList().tap: p =>
          for t <- k.getParameterTypes do
            p.add(t.getName)
object FieldIota extends IotaType[FieldIota]:
  override def color: Int = Formatting.YELLOW.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): FieldIota | Null =
    boundary:
      val c = HexUtils.downcast(element, NbtCompound.TYPE)
      val klass = classNamed(c("c").downcast[NbtString]).getOrElse(boundary.break(null)).runtimeClass
      lazy val name = c("n").downcast[NbtString]
      try
        FieldIota:
          c("f").downcast[NbtByte].byteValue() match
            case 0 => klass.getDeclaredField(name)
            case 1 => klass.getDeclaredMethod(name)
            case 2 => klass.getDeclaredConstructor(c("k").downcast[NbtList].map(m => classNamed(m.downcast[NbtString]).getOrElse(boundary.break(null)).runtimeClass).toSeq*)
            case _ => boundary.break(null)
      catch
        case _: (NoSuchFieldException | NoSuchMethodException) => null
  override def display(element: NbtElement): Text = t"Pointer: 0x${f"${HexUtils.downcast(element, NbtLong.TYPE).longValue}%x"}".styled(_.withColor(color))

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

case class ObjectIota[T](obj: AnyRef) extends Iota(ObjectIota, obj):
  override def isTruthy: Boolean = !obj.isInstanceOf[Unit]
  override def toleratesOther(iota: Iota): Boolean = false
  override def serialize(): NbtElement = NbtCompound()
  override def display(): Text = obj.toString
object ObjectIota extends IotaType[ObjectIota[?]]:
  override def color: Int = Formatting.RED.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): ObjectIota[?] = null
  override def display(element: NbtElement): Text = t"Object"

case class PointerIota[T](pointer: Pointer[T]) extends Iota(PointerIota, pointer):
  override def isTruthy: Boolean = true
  override def toleratesOther(iota: Iota): Boolean = ???
  override def serialize(): NbtElement = NbtLong.of(pointer.address)
object PointerIota extends IotaType[PointerIota[?]]:
  override def color: Int = Formatting.RED.getColorValue
  override def deserialize(element: NbtElement, world: ServerWorld): PointerIota[?] = PointerIota(Pointer(HexUtils.downcast(element, NbtLong.TYPE).longValue))
  override def display(element: NbtElement): Text = t"Pointer: 0x${f"${HexUtils.downcast(element, NbtLong.TYPE).longValue}%x"}".styled(_.withColor(color))

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
    def display(count: BigInt): Text =
      t"${display}x${expNotation(count)}"
  def color: Int = 0x720a0a
  private[hexic] def parseVariant(c: NbtCompound): Option[(TaggedVariant, RegistryKey[Reader])] =
    Identifier.tryParse(c.getString("type")) match
      case null => None
      case i => Option.fromNullable(registry.get(i)).flatMap(_(c)).map((_, RegistryKey.of(VariantIota, i)))
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
  override def display(e: NbtElement): Text =
    val c = HexUtils.downcast(e, NbtCompound.TYPE)
    parseVariant(c).fold(NullIota.DISPLAY)(_._1.display)
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
        override def display(count: BigInt): Text =
          val buckets = count / FluidConstants.BUCKET
          // small quantities of liquid value using millibuckets
          if buckets < 100 then
            display.append("x").append(f"${count.toDouble / FluidConstants.BUCKET.toDouble}%.3f")
          else
            // normal exp-notation bucket count
            display.append("x").append(expNotation(buckets))
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
case class MapIota(map: Map[NbtCompound, NbtCompound] = Map())(using val world: ServerWorld) extends Iota(MapIota, map):
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
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  override def serialize(): NbtElement = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) tap l.add)
object MapIota extends IotaType[MapIota]:
  def color: Int = 0xb0641c
  def deserialize(using data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    def o = HexUtils.downcast(_, NbtCompound.TYPE)
    l.map(o)
      .map(c => ((c("k") pipe o) -> (c("v") pipe o)))
      .toMap[NbtCompound, NbtCompound]
      .pipe(new MapIota(_))
  def display(data: NbtElement): Text =
    val items = HexUtils.downcast(data, NbtList.TYPE)
    val output: MutableText = "["
    output.styled(_.withColor(color))
    if items.nonEmpty then
      def castToCompound = HexUtils.downcast(_, NbtCompound.TYPE)
      val itemPair = items.map(castToCompound).iterator
      def writePair(pair: NbtCompound) =
        output.append(IotaType.getDisplay(pair("k") pipe castToCompound))
        output.append(" → ")
        output.append(IotaType.getDisplay(pair("v") pipe castToCompound))
      writePair(itemPair.next())
      while itemPair.hasNext do
        output.append(", ")
        writePair(itemPair.next())
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
given Conversion[String, MutableText] = Text.literal
object NbtIota extends IotaType[NbtIota]:
  def name: Text = ("NBT": MutableText).styled(_.withColor(color))
  def color: Int = Formatting.DARK_AQUA.getColorValue
  def deserialize(using NbtElement, ServerWorld): NbtIota = NbtIota(summon)
  def display(e: NbtElement): Text = Text.literal(StringNbtWriter()(e)).styled(_.withColor(color))
