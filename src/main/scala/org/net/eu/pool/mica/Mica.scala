package org.net.eu.pool.mica
import com.mojang.authlib.properties.PropertyMap
import com.mojang.datafixers.util.Pair
import com.mojang.logging.LogUtils
import com.mojang.serialization
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.{Codec, DataResult, Decoder, DynamicOps, Encoder, Lifecycle, MapCodec}
import it.unimi.dsi.fastutil.longs.{Long2FloatMap, Long2IntMap, Long2IntMaps, Long2IntOpenHashMap, Long2LongMap}
import net.minecraft.component.`type`.ProfileComponent
import net.minecraft.component.{ComponentChanges, ComponentType, DataComponentTypes}
import net.minecraft.entity.{Entity, ItemEntity, LivingEntity}
import net.minecraft.entity.attribute.{EntityAttribute, EntityAttributeInstance}
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
import net.minecraft.entity.effect.{StatusEffectInstance, StatusEffects}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.loot.entry.ItemEntry
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvent, SoundEvents}
import net.minecraft.state.property.Property
import net.minecraft.text.{TextCodecs, TextColor, Texts}
import net.minecraft.util.{Hand, Rarity, Uuids}
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World.ExplosionSourceType
import org.net.eu.pool.mica
import org.net.eu.pool.mica.VoidRune.Frame
import org.slf4j.{Logger, LoggerFactory}
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

import java.nio.file.{Files, Path}
import java.util.{Optional, UUID}
import java.util.stream.LongStream
import scala.::
import scala.NamedTuple.{AnyNamedTuple, NamedTuple}
import scala.Tuple.:*
import scala.annotation.targetName
import scala.collection.immutable.{AbstractSet, SortedSet}
import scala.compiletime.constValue
import scala.runtime.ObjectRef
import scala.util.boundary
// ifversion(>=2100, <[[
import net.minecraft.storage.{ReadView, WriteView}
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent
import org.ladysnake.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import org.ladysnake.cca.api.v3.world.{WorldComponentFactoryRegistry, WorldComponentInitializer}
// ]]>, <[[
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.world.{WorldComponentFactoryRegistry, WorldComponentInitializer}
// ]]>)
import it.unimi.dsi.fastutil.longs.{Long2ObjectMap, Long2ObjectOpenHashMap}
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.{FabricBlockEntityType, FabricBlockEntityTypeBuilder}
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback
import net.minecraft.block.{AbstractBlock, Block, BlockState, BlockWithEntity}
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.item.{Item, ItemUsageContext}
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.{MutableRegistry, Registries, Registry, RegistryKey, RegistryKeys, SimpleDefaultedRegistry, SimpleRegistry}
import net.minecraft.resource.metadata.BlockEntry
import net.minecraft.text.Text
import net.minecraft.util.{ActionResult, Identifier}
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.{BlockPos, Direction, Vec3d, Vec3i}
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.World
import net.minecraft.world.chunk.PalettedContainer

import java.io.{DataInput, DataOutput, InputStream, OutputStream}
import scala.annotation.{Annotation, MacroAnnotation, compileTimeOnly, tailrec}
import scala.collection.convert.ImplicitConversions.given
import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.compiletime.{deferred, erasedValue, summonInline}
import scala.deriving.Mirror
import scala.language.experimental.erasedDefinitions
import scala.quoted.{Expr, Quotes}
import scala.reflect.ClassTag
import scala.util.chaining.scalaUtilChainingOps

private given ModID = ModID("mica")

/**
 * Current serial Minecraft version. This is solely provided for IDE completion; it is expanded before compile-time by m4.
 */
private[mica] val /* <[[ */ minecraft_version: /* ]]> */ Int = minecraft_version

/**
 * Represents a value such that the only thing known about it is that it is a member of a typeclass [[C]]. While this
 * doesn't seem useful on the surface, it can be used to represent things similar to Java interfaces - the type
 * `Abstract[ValueType]` could represent any value for which a [[ValueType]] exists, but without requiring the class
 * itself to add to its `implements` clause.
 * @tparam C Typeclass providing the sole known information about the type.
 */
trait Abstract[C[_]]:
  /**
   * Real type of [[value]]. Usually, this is used as a path-dependent type - the actual type is rarely known.
   */
  type T: C
  /**
   * Backing value of the abstract.
   */
  val value: T

/**
 * Helpers for creating and un-creating [[Abstract]] instances.
 */
object Abstract:
  /**
   * Construct an [[Abstract]] from a value and the proof of [[C]] in scope.
   * @param x Backing value placed in [[Abstract.value]].
   * @tparam C Typeclass for this [[Abstract]] instance.
   * @tparam T Actual type within the [[Abstract]]. The return type is '''not''' narrowed to this type!
   * @return Abstract instance of [[x]] plus the `C[x.type]` proof in scope.
   */
  def apply[C[_], T: C](x: T): Abstract[C] =
    type _T = T
    new Abstract[C]:
      type T = _T
      val value: _T = x

  /**
   * Splits an [[Abstract]] into its [[Abstract.value]] and proof. This is intended to be used with `given`-patterns:
   * {{{
   * val data: Abstract[C] = ???
   * data match
   *   case Abstract(value, given _) =>
   *     // brings C[value.type] into scope
   *     summon[C[value.type]]
   * }}}
   * @param x An [[Abstract]] value to dissect.
   * @tparam C Typeclass to extract from the [[Abstract]].
   * @return Tuple of the [[Abstract]]'s [[Abstract.value]] and associated proof.
   */
  def unapply[C[_]](x: Abstract[C]): Some[(x.T, C[x.T])] =
    import x.given
    Some((x.value, /* WITH THIS TREASURE I */ summon))

// divert(-1)
class BitReader(val base: InputStream):
  private var bits = 0
  private var current = 0
  private def advance(): Unit =
    current = base.read()
    bits = 8
  def align(): Unit =
    bits = 0
  def read(): Boolean =
    if (bits == 0) advance()
    bits -= 1
    /*dnl*/(current & (1 << bits)) != 0
  def get[T: BitCodec as c] = c.read(this)

class BitWriter(val base: OutputStream):
  private var bits = 0
  private var current = 0
  private def flush(): Unit =
    if bits != 0 then
      base.write(current)
      bits = 0
  def write(b: Boolean): Unit =
    /*dnl*/current = current | (if b then 1 << bits else 0)
    bits += 1
    if (bits >= 8) flush()
  def put[T: BitCodec as c](x: T): Unit = c.write(this, x)

extension (r: InputStream)
  def get[T: ByteCodec as c](): T = c.read(r)
extension (w: OutputStream)
  def put[T: ByteCodec as c](x: T): Unit = c.write(w, x)

private[mica] trait BitCodec[@specialized T]:
  def read(r: BitReader): T
  def write(w: BitWriter, x: T): Unit
private[mica] object BitCodec:
  /**
   * The [[Unit]] type is serialized as an empty sequence of bits.
   */
  given BitCodec[Unit]:
    override def read(r: BitReader): Unit = ()
    override def write(w: BitWriter, x: Unit): Unit = ()
  end given

  /**
   * The [[Boolean]] type is serialized as a single bit.
   */
  given BitCodec[Boolean]:
    override def read(r: BitReader): Boolean = r.read()
    override def write(w: BitWriter, x: Boolean): Unit = w.write(x)
  end given

  given [T: BitCodec as c] => BitCodec[Option[T]]:
    override def read(r: BitReader): Option[T] =
      if r.read() then
        Some(c.read(r))
      else
        None

    override def write(w: BitWriter, x: Option[T]): Unit =
      x.fold(w.write(false)):
        w.write(true)
        c.write(w, _)

trait Multipart:
  type Data: BitCodec

  /**
   * Returns the block position of this multipart based on its world position.
   * @param data The data associated with this multipart.
   * @return The position of this multipart in the layer - two values from 0 to 15.
   */
  def position(data: Data): (Byte, Byte)

  /**
   * Returns this part's selection box (same rules as [[net.minecraft.block.Block]]
   */
  def bounds(data: Data): VoxelShape

class MultipartLayer:
  def isEmpty: Boolean

class MultipartSection:
  val layers: Array[Option[MultipartLayer]] = Array.fill(16)(None)

// divert
class immutable extends Annotation

/**
 * A '''side effect''' allows a [[Rune]] to have a greater effect on the world than mere arithmetic. Whereas runes
 * don't have access to a [[ServerWorld]], side effects ''do'', letting them read from and even write to the world.
 * However, they are less flexible than normal values - an execution ''must'' return a side effect to have it executed.
 */
trait SideEffect derives HasRegistry:
  /**
   * Arbitrary data for your side effect to use. For example, an effect that replaces a block would store the target
   * [[BlockPos]], as well as the new [[Block]].
   */
  type Data
  given dataCodec: Codec[Data] = deferred

  /**
   * Return value exposed to Mica code. This could be information such as a position (for a read-only effect), or
   * information about changes made (for example, the old [[Block]]). In the case you have ''nothing'' valuable to
   * return, use [[Unit]].
   */
  type Return: {ValueType, ClassTag}
  given returnType: ValueType[Return] = deferred

  /**
   * Arbitrary data your side effect might need in [[unexecute]], in addition to your return value. This data is never
   * written to disk, so can be anything. For example, the aforemented replace-block effect could store the associated
   * [[BlockEntity]] of the block.
   */
  type RevertData

  /**
   * Check whether your side effect applies to the world ''before'' any changes. '''This method must not mutate `world`!'''
   * @param data Arbitrary [[Data]] to use (such as a target position, etc.) in your cast.
   * @param world Reference world for checking whether effects apply. '''Do not mutate this world!'''
   */
  @immutable def check(data: Data)(using @immutable world: World @immutable): Unit
  @immutable def check(data: Data, player: PlayerEntity)(using @immutable world: World @immutable): Unit = check(data)

  /**
   * Apply your side effect to the world. This method should be reversible by [[unexecute]], in case a later effect's [[execute]] throws. However, avoid throwing in this method when possible - validate your effect in [[check]] instead.
   * @param data Arbitrary [[Data]] to use (such as a target position, etc.) in your cast.
   * @param world World to apply effects to; the same world as passed to [[check]].
   * @return Information for reversing the effect. Spells should provide reversion data if possible.
   */
  def execute(data: Data)(using world: World): (Return, RevertData)

  /**
   * Apply a side effect that requires a player. This method is otherwise identical to [[execute(data:SideEffect)* the playerless version]] - keep its contract in mind.
   * @param player Player initiating the cast.
   */
  def execute(data: Data, player: PlayerEntity)(using World): (Return, RevertData) = execute(data)

  /**
   * Un-apply your side effect, if possible. This method is rarely called, only in the case [[execute]] breaks its contract. However, it's safest to implement it if you can.
   * @param data The same [[Data]] that [[execute]] was called with.
   * @param return The value returned by [[execute]].
   * @param revert The revert data returned by [[execute]].
   * @param world World to reverse this effect in; the same world as [[execute]].
   */
  def unexecute(data: Data, `return`: Return, revert: RevertData)(using world: World): Unit

  def translationFields(data: Data): Seq[AnyRef] = Seq()

@register("noop")
object NoopEffect extends SideEffect:
  override type Data = Unit
  override type Return = Unit
  override type RevertData = Unit

  override def check(data: Unit)(using world: World): Unit = ()
  override def execute(data: Unit)(using world: World): (Unit, Unit) = ((), ())
  override def unexecute(data: Unit, `return`: Unit, revert: Unit)(using world: World): Unit = ()

given unitValueType: ValueType[Unit]:
  override def eq[U: ValueType as v](x: Unit, y: U): Boolean = v eq unitValueType
  override def show(x: Unit): Text = Text.literal("Unit").styled(_.withColor(0x7a7a7a))

// divert(-1)
trait ByteCodec[T]:
  def read(r: DataInput): T
  def write(w: DataOutput, x: T): Unit

given ByteCodec[MultipartSection]:
  override def read(r: InputStream): MultipartSection =
    val bottomMask = r.read()
    val topMask = r.read()
    val section = MultipartSection()
    for n <- 0 to 7 do
      /*dnl*/if bottomMask & (1 << n) != 0 then
        section.layers(n) = r.get()
      /*dnl*/if topMask & (1 << n) != 0 then
        section.layers(n+8) = r.get()
    section
  override def write(w: OutputStream, x: MultipartSection): Unit =
    var bottomMask = 0
    var topMask = 0
    for n <- 0 to 7 do
      /*dnl*/for l <- x.layers(n) if !l.isEmpty do bottomMask |= 1 << n
      /*dnl*/for l <- x.layers(n+8) if !l.isEmpty do topMask |= 1 << n
    w.write(bottomMask)
    w.write(topMask)
    for n <- 0 to 7 do
      for l <- x.layers(n) if !l.isEmpty do w.put(l)
      for l <- x.layers(n+8) if !l.isEmpty do w.put(l)

extension [T] (c: BitCodec[T])
  def aligned: ByteCodec[T] =
    new ByteCodec[T]:
      override def read(r: InputStream): T = c.read(BitReader(r))
      override def write(w: OutputStream, x: T): Unit = c.write(BitWriter(w), x)

class sparse extends Annotation

// divert

def assumed[T, U]: T =:= U = summon[Int =:= Int].asInstanceOf[T =:= U]

inline given tupleCodec: [T <: Tuple] => Codec[T] = compiletime.summonFrom:
  case ev: (T =:= EmptyTuple) => ev.substituteContra(Codec.unit(EmptyTuple))
  case ev: (T =:= (h *: t)) => ev.substituteContra(Codec.pair(summonInline[Codec[h]], tupleCodec[t]).xmap(p => p.getFirst *: p.getSecond, { case h *: t => Pair(h, t) }))

// divert(-1)

extension [T] (i: Iterator[T]) def dropAfter(cond: T => Boolean): Iterator[T] =
  val split = i.span(!cond(_))
  (split._1 ++ split._2.take(1))

opaque type VarInt = Int
object VarInt:
  def apply(n: Int): VarInt = n
  def unapply(n: VarInt): Some[Int] = Some(n)
  given ByteCodec[VarInt]:
    private val STOP_BIT = 0x80
    private val DATA_BIT = 0x7f
    override def read(r: DataInput): VarInt =
      Iterator.continually(r.readUnsignedByte())
        .dropAfter(x => (x & STOP_BIT) != 0)
        .map(_ & DATA_BIT)
        .foldLeft(0)(_ << 7 | _)
    override def write(w: DataOutput, x: VarInt): Unit =
      assume(x >= 0)
      if x == 0 then
        w.write(STOP_BIT)
      else
        Iterator.unfold[Int, Int](x): x =>
          Option.unless(x == 0):
            val x1 = x >>> 7
            (x1, x & DATA_BIT)
        .toSeq.reverse match
          case h :+ t =>
            h.foreach(w.write)
            w.write(t | STOP_BIT)
          case _ => w.write(STOP_BIT)
// divert

@register("need_side_effect")
object NeedSideEffectFrame extends ThunkFrame:
  override type Data = Unit
  override def accept[T: ValueType as ty](data: Unit, value: T): BoxedThunk =
    ty.cast[BoxedSideEffect](value) match
      case Some(e) => BoxedThunk(GotSideEffectFrame, e)

@register("got_side_effect")
object GotSideEffectFrame extends ThunkFrame:
  override type Data = BoxedSideEffect
  override def accept[T: ValueType](data: BoxedSideEffect, value: T): BoxedThunk =
    //throw RuneError(Text.literal("Too many side effects in one cast"))
    ???
def findRunes(start: RuneRef, prev: Option[RuneRef])(using World): Option[List[(Rune, RuneRef)]] =
  logger.debug(s"findRunes ${start} ${prev}")
  if start.isEmpty then
    None
  else
    val neigh = (start.rune.computeNeighbors(using start) -- prev).flatMap(findRunes(_, Some(start))).toSeq
    neigh match
      case Seq() =>
        logger.debug(s"\tfindRunes ${start} ${prev}, neigh = ${neigh} | no more neighbors; stop here.")
        Some(List((start.rune, start)))
      case Seq(x) =>
        val l = (start.rune, start)::x
        logger.debug(s"\tfindRunes ${start} ${prev}, neigh = ${neigh} | ${l}")
        Some(l)
      case _ =>
        logger.debug(s"\tfindRunes ${start} ${prev}, neigh = ${neigh} | too many neighbors!")
        None

given logger: Logger = LoggerFactory.getLogger(given_ModID.name)

def parseRunes(runes: List[(Rune, RuneRef)])(using World): List[BoxedRune] =
  runes match
    case (head, given RuneRef)::next =>
      val (data, rest) = head.read(next)
      BoxedRune(head, data)::parseRunes(rest)
    case Nil => Nil

@tailrec
def interpretRunes(runes: List[BoxedRune], stack: BoxedThunk): BoxedThunk =
  println(s"Interpreter: runes = $runes, stack = $stack")
  runes match
    case head::next =>
      val newStack = head.rune.execute(head.data, stack)
      println(s"-> $head, stack = $newStack, $next")
      interpretRunes(next, newStack)
    case Nil =>
      println("interpreter finished.")
      stack

@throws[RuneError]
def interpretForSideEffect(runes: List[BoxedRune]): Option[BoxedSideEffect] =
  val ret = interpretRunes(runes, BoxedThunk(NeedSideEffectFrame, ()))
  ret.narrow(GotSideEffectFrame) match
    case Some(value) => Some(value.data)
    case None =>
      println(s"Nothing seems to happen (top of stack is ${ret})")
      None

//given emptyTupleCodec: MapCodec[EmptyTuple] = MapCodec.unit(EmptyTuple)
//inline given namedTupleCodec: [K <: String & Singleton, V: Codec as codec, R <: Tuple: MapCodec as tail] => MapCodec[(K, V) *: R] =
//  RecordCodecBuilder.mapCodec[(K, V) *: R]: i =>
//    i.group[V, R](
//      codec.fieldOf(constValue[K]).forGetter { case h *: _ => h._2 },
//      tail.forGetter { case _ *: t => t },
//    ).apply(i, (h, t) => (constValue[K], h) *: t)

extension [T1, T2, R1, R2] (ev$1: T1 =:= R1) def zip(ev$2: T2 =:= R2): (T1, T2) =:= (R1, R2) = assumed
extension [T1, T2, R1, R2] (ev: (T1, T2) =:= (R1, R2)) def unzip: (T1 =:= R1, T2 =:= R2) = (assumed, assumed)

//inline given namedTupleCodec: [N <: Tuple, T <: Tuple] => MapCodec[NamedTuple[N, T]] =
//  compiletime.summonFrom:
//    case ev: ((N, T) =:= (EmptyTuple, EmptyTuple)) => MapCodec.unit(NamedTuple.Empty.asInstanceOf)
//    case ev: ((N, T) =:= (k *: ks, v *: vs)) =>
//      erased val ev2 = summonInline[k <:< (String & Singleton & k)]
//      val name = constValue[k].asInstanceOf[String]
//      RecordCodecBuilder.mapCodec[NamedTuple[k *: ks, v *: vs]]: i =>
//        i.group[v, vs](
//          summonInline[Codec[v]].fieldOf(name).forGetter(???),
//          namedTupleCodec[ks, vs].forGetter(???)
//        ).apply(i, (h, t) => (name, h) *: t)

//erased def namedTupleCodecTest: MapCodec[((a: Int, b: String))] = summon

/**
 * Exception related to [[Rune]] evaluation, including the position.
 *
 * @param message Cause of the error, displayed to the caster.
 * @param ref Location of the rune that threw the error.
 */
case class RuneError(message: Text)(using val ref: RuneRef) extends Exception

/**
 * Stores a [[Rune]] together with its [[Rune.Data associated data]]. This allows easily passing around heterogeneous parsed rune values.
 * @param rune The rune stored in this pair (responsible for determining [[data]]'s type).
 * @param data The extra data associated with [[Rune]].
 */
case class BoxedRune(rune: Rune, data: rune.Data)
inline given `dispatch codec for boxed runes`: Codec[BoxedRune] =
  summon[Codec[Rune]]
    .dispatch[BoxedRune](_.rune, (r: Rune) =>
      import r.given
      summon[Codec[r.Data]].xmap(BoxedRune(r, _),
        // FIXME: Rewrite [dispatch] to avoid this unsafe cast.
        _.data.asInstanceOf[r.Data])
      // ifversion(>= 2100,<[[
        .fieldOf("value")
      // ]]>,)
    )
inline given `dispatch codec for boxed values`: Codec[BoxedValue] =
  summon[Codec[ValueType[?]]]
    .dispatch[BoxedValue](_.given_ValueType_T, {
      case r: ValueType[t] =>
        given ValueType[t] = r
        r.codec.xmap(BoxedValue(_)(using r), _.value.asInstanceOf[t])
        // ifversion(>= 2100,<[[
        .fieldOf("value")
    // ]]>,)
    })
given `dispatch codec for boxed thunk frames`: Codec[BoxedThunk] =
  summon[Codec[ThunkFrame]]
    .dispatch[BoxedThunk](_.tag, (r: ThunkFrame) =>
      import r.given
      summon[Codec[r.Data]].xmap(BoxedThunk(r, _),
          // FIXME: Rewrite [dispatch] to avoid this unsafe cast.
          _.data.asInstanceOf[r.Data])
        // ifversion(>= 2100,<[[
        .fieldOf("value")
      // ]]>,)
    )
given `dispatch codec for boxed side-effects`: Codec[BoxedSideEffect] =
  summon[Codec[SideEffect]]
    .dispatch[BoxedSideEffect](_.effect, (r: SideEffect) =>
      import r.given
      summon[Codec[r.Data]].xmap(BoxedSideEffect(r, _),
          // FIXME: Rewrite [dispatch] to avoid this unsafe cast.
          _.data.asInstanceOf[r.Data])
        // ifversion(>= 2100,<[[
        .fieldOf("value")
      // ]]>,)
    )

/**
 * Stores a [[ThunkFrame]] together with its [[ThunkFrame.Data associated data]] on the stack.
 * @param tag Type of this frame (must be registered).
 * @param data Extra data for this frame (e.g. already-collected arguments).
 */
case class BoxedThunk(tag: ThunkFrame, data: tag.Data):
  def narrow(to: ThunkFrame): Option[BoxedThunk { val tag: to.type; }] =
    if tag eq to then
      Some(asInstanceOf[BoxedThunk { val tag: to.type; }])
    else
      None
  def accept[T: ValueType](value: T) = tag.accept(data, value)
object BoxedThunk:
  inline def unapply[T](box: BoxedThunk): Option[(? <: Singleton & ThunkFrame { type Data = T; }, T)] =
    ???

trait BoxedValue:
  type T: ValueType
  val value: T
  def ==(other: BoxedValue): Boolean =
    import other.given
    summon[ValueType[T]].eq(value, other.value)
  def cast[R: {ValueType, ClassTag}]: Option[R] = summon[ValueType[T]].cast(value)
  def show: Text = summon[ValueType[T]].show(value)
object BoxedValue:
  def apply[T: ValueType](value: T): BoxedValue =
    type _T = T
    val _value = value
    new BoxedValue:
      override type T = _T
      override val value: _T = _value
//def dependentCodec[T, R](arg: Codec[T], rhs: [R] )

// y'know, I should really make a generic Boxed type
case class BoxedSideEffect(effect: SideEffect, data: effect.Data):
  def check()(using World): Unit = effect.check(data)
  def check(player: PlayerEntity)(using World): Unit = effect.check(data, player)
  def execute()(using World): (effect.Return, effect.RevertData) = effect.execute(data)
  def execute(player: PlayerEntity)(using World): (effect.Return, effect.RevertData) = effect.execute(data, player)
  def unexecute(`return`: effect.Return, revert: effect.RevertData)(using World): Unit = effect.unexecute(data, `return`, revert)

// divert(-1)
/**
 * Stores additional data out-of-band with existing world chunks. This is useful if you want to e.g. manage chunk loading independently, or
 */
trait ParallelSection

//object ExplodeRune extends Rune:
//	type Data = (BlockPos, Int)--

// divert

/**
 * Does nothing. Pushes and pops no frames, consumes no runes, etc. Used as a placeholder for missing runes.
 */
@register("empty")
object EmptyRune extends SimpleRune:
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
  override def execute(frame: BoxedThunk): BoxedThunk = frame
  register:
    item.register()

@register("quote")
object QuoteRune extends Rune:
  override type Data = Seq[(Rune, RuneRef)]
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (Seq[(Rune, RuneRef)], List[(Rune, RuneRef)]) =
    @throws[RuneError]
    def worker(rhs: List[(Rune, RuneRef)])(using ref: RuneRef): (List[(Rune, RuneRef)], List[(Rune, RuneRef)]) =
      rhs match
        case Nil => throw RuneError(Text.literal("Quote with no corresponding unquote"))
        case (QuoteRune, given RuneRef)::xs =>
          val (t, xt) = worker(xs)
          val (u, xu) = worker(xt)
          (t:::(EndQuoteRune, summon[RuneRef])::u, xu)
        case (EndQuoteRune, r)::xs => (Nil, xs)
        case x::xs =>
          val t = worker(xs)
          (x::t._1, t._2)
    worker(rhs)
  override def execute(data: Seq[(Rune, RuneRef)], frame: BoxedThunk): BoxedThunk =
    frame.tag.accept(frame.data, data.map(BoxedValue(_)))
  register:
    item.register()

/**
 * The registry key of the [[EmptyRune]]. This should be the default rune.
 */
lazy val emptyRuneKey = summon[Registry[Rune]].getId(EmptyRune)
private[mica] sealed trait ConcreteRuneStorage extends AbstractRuneStorage:
  // ifversion(>=2100, <[[
  override def readData(c: ReadView): Unit =
  // ]]>, <[[
  override def readFromNbt(c: NbtCompound): Unit =
  // ]]>)
    if c.getBoolean("m", true) then
      contents.clear()
      heap0.clear()
      heap1.clear()
      heap2.clear()
      heap3.clear()
    val n = c.getLong("c", -1L)
    if n != -1 then
      for
        i <- 0L until n
        k = c.getLong(s"k$i", 0L)
        v = c.getInt(s"v$i", 0)
        p = c.getString(s"p$v", emptyRuneKey.toString)
        r <- Option.fromNullable(Identifier.tryParse(p))
      do
        contents(k) = summon[Registry[Rune]].get(r)
    else
      boundary:
        var i = 0
        while true do
          // ifversion(>=2100, <[[
          val dh = c.getOptionalIntArray(s"h$i")
          val dl = c.getOptionalIntArray(s"d$i")
          val dx0 = c.getOptionalIntArray(s"x${i}0").orElse(Array.emptyIntArray)
          val dx1 = c.getOptionalIntArray(s"x${i}1").orElse(Array.emptyIntArray)
          val dx2 = c.getOptionalIntArray(s"x${i}2").orElse(Array.emptyIntArray)
          val dx3 = c.getOptionalIntArray(s"x${i}3").orElse(Array.emptyIntArray)
          // ]]>, <[[
          val dh = c.getIntArray(s"h$i")
          val dl = c.getIntArray(s"d$i")
          val dx0 = c.getIntArray(s"x${i}0").orElse(Array.emptyIntArray)
          val dx1 = c.getIntArray(s"x${i}1").orElse(Array.emptyIntArray)
          val dx2 = c.getIntArray(s"x${i}2").orElse(Array.emptyIntArray)
          val dx3 = c.getIntArray(s"x${i}3").orElse(Array.emptyIntArray)
          // ]]>)
          if dh.isEmpty || dl.isEmpty then boundary.break()
          val rune = registryFor[Rune].get(Identifier.of(
            c.getString(s"n$i", emptyRuneKey.getNamespace),
            c.getString(s"p$i", emptyRuneKey.getPath),
          ))
          if rune != null then
            for ((h: Int, l: Int), i: Int) <- dh.get.zip(dl.get).zipWithIndex do
              val k: Long = packLong(h, l);
              contents.put(k, rune)
              if dx0.length > i then heap0(k) = dx0(i)
              if dx1.length > i then heap1(k) = dx1(i)
              if dx2.length > i then heap2(k) = dx2(i)
              if dx3.length > i then heap3(k) = dx3(i)
          i += 1
  // ifversion(>=2100, <[[
  override def writeData(c: WriteView): Unit =
  // ]]>, <[[
  override def writeToNbt(c: NbtCompound): Unit =
  // ]]>)
    val palette = contents.groupMap(_._2)(_._1) - EmptyRune
    var i = 0
    for (k, v) <- palette do
      val ns = registryFor[Rune].getId(k)
      c.putString(s"n$i", ns.getNamespace)
      c.putString(s"p$i", ns.getPath)
      val (h, l) = v.map(unpackLong(_)).unzip
      c.putIntArray(s"h$i", h.toArray)
      c.putIntArray(s"d$i", l.toArray)
      val (p1, p2) = (for x <- v yield ((heap0(x), heap1(x)), (heap2(x), heap3(x)))).unzip
      val (x0, x1) = p1.unzip; val (x2, x3) = p2.unzip
      c.putIntArray(s"x${i}0", x0.toArray.map(x => if x == null then 0 else x.intValue))
      c.putIntArray(s"x${i}1", x1.toArray.map(x => if x == null then 0 else x.intValue))
      c.putIntArray(s"x${i}2", x2.toArray.map(x => if x == null then 0 else x.intValue))
      c.putIntArray(s"x${i}3", x3.toArray.map(x => if x == null then 0 else x.intValue))
      i += 1

@register("double")
object DoubleLiteralRune extends Rune:
  override type Data = Double
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (Double, List[(Rune, RuneRef)]) = (java.lang.Double.longBitsToDouble(packLong(ref.heap0, ref.heap1)), rhs)
  override def execute(data: Double, frame: BoxedThunk): BoxedThunk = frame.tag.accept(frame.data, data)
  register:
    DoubleLiteralRune.item.register()

// forloop(n, 0, 767, <[[
// pushdef(RuneStorage, <[[ifelse($#,0,<[[<[[$0]]>]]><[[n]]>,<[[$0]]><[[<[[(]]>]]><[[$@]]><[[)]]>)]]>)
private[mica] case class RuneStorage(world: World,
                                     contents: Long2ObjectMap[Rune] = Duck.mkMap(),
                                     heap0: Long2IntMap = Long2IntOpenHashMap(),
                                     heap1: Long2IntMap = Long2IntOpenHashMap(),
                                     heap2: Long2IntMap = Long2IntOpenHashMap(),
                                     heap3: Long2IntMap = Long2IntOpenHashMap()
                                    ) extends ConcreteRuneStorage:
  override type Concrete = RuneStorage
  contents.defaultReturnValue(EmptyRune)
private[mica] object RuneStorage:
  val shift = RuneShift(n)
  given key: ComponentKey[RuneStorage] = ComponentRegistry.getOrCreate(Identifier.of("mica", "runes<[[]]>n"), classOf[RuneStorage])
  // divert(1)
    /*dnl*/val factories: WorldComponentFactoryRegistry = erasedValue/*
*/Duck.register(factories, RuneStorage.key, classOf[RuneStorage], RuneStorage(_))
    AbstractRuneStorage.keys(n) = RuneStorage.key
    // divert
// popdef(<[[RuneStorage]]>)
// ]]>)
// forloop(n, 0, 63, <[[
// pushdef(FluxStorageInfo, <[[ifelse($#,0,<[[<[[$0]]>]]><[[n]]>,<[[$0]]><[[<[[(]]>]]><[[$@]]><[[)]]>)]]>)
private[mica] case class FluxStorageInfo(world: World, contents: Long2FloatMap) extends Component:
  override def readData(readView: ReadView): Unit = ???
  override def writeData(writeView: WriteView): Unit = ???
// divert
// popdef(<[[FluxStorageInfo]]>)
// ]]>)

given ValueType[Double]:
  override def eq[U: ValueType as v](x: Double, y: U): Boolean = v.cast[Double](y).exists(y => x =~ y)
  override def cast[R: ClassTag as r](x: Double): Option[R] =
    r match
      case ClassTag.Double => Some(x)
      case ClassTag.Int =>
        if x =~ x.round.toInt then
          Some(x.round.toInt)
        else
          None
      case _ => None

  override def show(x: Double): Text = Text.literal(s"$x").styled(_.withColor(0xab3900))

class RuneBeStartinShit private(val direction: Direction) extends Rune:
  override def surfaceSprite: Identifier = Rune.IMPETUS_TEXTURE
  override type Data = Unit
  override def read(rhs: List[(Rune, RuneRef)])(using RuneRef, World): (Unit, List[(Rune, RuneRef)]) = ((), rhs)
  override def execute(data: Unit, frame: BoxedThunk): BoxedThunk = frame
  override def computeNeighbors(using r: RuneRef): Set[RuneRef] = direction match
    case Direction.NORTH => Set(r.north.north)
    case Direction.SOUTH => Set(r.south.south)
    case Direction.WEST => Set(r.west.west)
    case Direction.EAST => Set(r.east.east)
  def register(): Unit =
    Registry.register(registryFor[Rune], Identifier.of("mica", s"arrow_$direction"), this)
    item.register()
object RuneBeStartinShit:
  val north = new RuneBeStartinShit(Direction.NORTH)
  val east = new RuneBeStartinShit(Direction.EAST)
  val south = new RuneBeStartinShit(Direction.SOUTH)
  val west = new RuneBeStartinShit(Direction.WEST)
  def apply(dir: Direction) = dir match
    case Direction.NORTH => north
    case Direction.SOUTH => east
    case Direction.WEST => south
    case Direction.EAST => west
  def unapply(r: RuneBeStartinShit): Some[Direction] = Some(r.direction)
  register:
    RuneBeStartinShit.north.register()
    RuneBeStartinShit.east.register()
    RuneBeStartinShit.south.register()
    RuneBeStartinShit.west.register()

@register("seq")
given ValueType[Seq[BoxedValue]]:
  val color = TextColor.fromRgb(0xb6fc03)
  override def eq[U: ValueType as v](x: Seq[BoxedValue], y: U): Boolean =
    v.cast[Seq[BoxedValue]](y).exists(y => x.zip(y).forall(p => p._1 == p._2))
  override def show(x: Seq[BoxedValue]): Text =
    x match
      case h+:t =>
        val m = Text.literal("(").styled(_.withColor(color))
        m.append(h.show)
        for x <- t do
          m.append(" ").append(x.show)
        m.append(")")
      case Seq() => Text.literal("()").styled(_.withColor(color))
given ValueType[(Rune, RuneRef)]:
  val color = TextColor.fromRgb(0xffe46e)
  override def eq[U: ValueType as v](x: (Rune, RuneRef), y: U): Boolean = v.cast[(Rune, RuneRef)](y).exists(y => x._1 eq y._1)
  override def show(x: (Rune, RuneRef)): Text = Text.literal(registryFor[Rune].getId(x._1).toTranslationKey("mica.runes"))

@register("tone_indicator")
object PosLiteral extends Rune:
  override type Data = RuneRef
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (RuneRef, List[(Rune, RuneRef)]) = (ref, rhs)
  override def execute(data: RuneRef, frame: BoxedThunk): BoxedThunk = frame.tag.accept(frame.data, data.floatPos)
  register:
    PosLiteral.item.register()

@register("teleport")
object TeleportRune extends Rune:
  override type Data = RuneRef
  override def surfaceSprite: Identifier = Rune.SPELL_TEXTURE
  @register("teleport")
  object Effect extends SideEffect:
    override type Data = (RuneRef, EntityRef)
    override type Return = Unit
    override type RevertData = Vec3d
    override def check(data: (RuneRef, EntityRef))(using world: World): Unit =
      given RuneRef = data._1
      if !world.getWorldBorder.contains(data._1.pos) then
        throw RuneError(Text.literal("Cannot teleport outside the world border"))
    override def execute(data: (RuneRef, EntityRef))(using world: World): (Unit, Vec3d) =
      val entity = data._2.entity
      if entity == null then
        throw RuneError(Text.literal("Could not find ").append(summon[ValueType[EntityRef]].show(data._2)).append(" in the world"))(using data._1)
      val pos = entity.getPos
      val newPos = data._1.floatPos
      entity.setPosition(newPos)
      entity.setVelocity(0, 0, 0)
      world.playSound(entity, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS)
      world.playSound(entity, newPos.x, newPos.y, newPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS)
      entity match
        case player: ServerPlayerEntity =>
          val delta = pos.distanceTo(newPos)
          println(s"${player} moved ${delta} blocks by teleporting")
          if delta >= 32 then
            println("and gets the advancement")
            val adv = player.getServer.getAdvancementLoader.get(Identifier.of(modid, "without_me"))
            player.getAdvancementTracker.grantCriterion(adv, "mojang_won\'t_let_me_be")
        case _ =>
      ((), pos)
    override def unexecute(data: (RuneRef, EntityRef), `return`: Unit, revert: Vec3d)(using world: World): Unit =
      data._2.entity.setPosition(revert)
  @register("teleport/0")
  object Frame extends ThunkFrame:
    override type Data = (RuneRef, BoxedThunk)
    override def accept[T: ValueType as v](data: (RuneRef, BoxedThunk), value: T): BoxedThunk =
      val entity = v.cast[EntityRef](value).get
      println(data)
      data._2.tag.accept(data._2.data, BoxedSideEffect(Effect, (data._1, entity)))
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (RuneRef, List[(Rune, RuneRef)]) = (ref, rhs)
  override def execute(data: RuneRef, frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, (data, frame))
  register:
    TeleportRune.item.register()
given Codec[RuneRef] = RecordCodecBuilder.create: i =>
  i.group(
    Codec.LONG.fieldOf("pos").xmap(BlockPos.fromLong, _.asLong).forGetter(_.pos),
    Codec.INT.fieldOf("dir").xmap(RuneShift.apply, _.value).forGetter(_.shift),
  ).apply(i, RuneRef(_, _))
//given Codec[((RuneRef, EntityRef))] = RecordCodecBuilder.create: i =>
//  i.group(
//    given_Codec_RuneRef.fieldOf("to").forGetter(_._1),
//    given_Codec_EntityRef.fieldOf("from").forGetter(_._2),
//  ).apply(i, (_, _))
//given Codec[((RuneRef, BoxedThunk))] = RecordCodecBuilder.create: i =>
//  i.group(
//    given_Codec_RuneRef.fieldOf("to").forGetter(_._1),
//    `dispatch codec for boxed thunk frames`.fieldOf("from").forGetter(_._2),
//  ).apply(i, (_, _))

given `given_Codec_java.lang.Double`: Codec[java.lang.Double] = Codec.DOUBLE

given [T: Codec as c] => Codec[Option[T]] = c.optionalFieldOf("present").codec().xmap(_.map[Option[T]](Some(_)).orElse(None), _.fold(Optional.empty)(Optional.of))

@register("foreach")
object ThothRune extends BinaryRune:
  register { item.register(); registerFrames() }
  override type Arg0 = Seq[BoxedValue]
  override type Arg1 = Seq[BoxedValue]
  override type Return = Seq[BoxedValue]
  @register("foreach/sub/_")
  object CollectFrame extends ThunkFrame:
    override type Data = Seq[BoxedValue]
    override given given_Codec_Data: Codec[Seq[BoxedValue]] = seqCodec
    override def accept[T: ValueType](data: Seq[BoxedValue], value: T): BoxedThunk = BoxedThunk(CollectFrame, data :+ BoxedValue(value))
  override def run(x: Seq[BoxedValue], y: Seq[BoxedValue]): Seq[BoxedValue] =
    val insts = parseRunes(x.map(_.cast[(Rune, RuneRef)].get).toList)(using null: World) // FIXME
    val base = BoxedThunk(CollectFrame, Vector())
    y.flatMap: y =>
      val frame = interpretRunes(insts, base)
      frame.tag.accept(frame.data, y.value).narrow(CollectFrame).get.data

case class Derived[T, R](value: T):
  private var state: Option[R] = None
  def apply(f: T => R): R = state.getOrElse { state = Some(f(value)); state.get }
object Derived:
  given [T: Codec as c, R] => Codec[Derived[T, R]] = c.xmap(Derived(_), _.value)

given ValueType[BoxedValue]:
  override def eq[U: ValueType](x: BoxedValue, y: U): Boolean =
    import x.given
    summon[ValueType[x.T]].eq(x.value, y)
  override def cast[R: ClassTag](x: BoxedValue): Option[R] =
    import x.given
    summon[ValueType[x.T]].cast(x.value)
  override def show(x: BoxedValue): Text =
    import x.given
    summon[ValueType[x.T]].show(x.value)

@register("defer")
object DeferEffectRune extends SimpleRune:
  @register("defer")
  object Effect extends SideEffect:
    override type Data = Derived[Seq[(Rune, RuneRef)], BoxedSideEffect]
    override type Return = BoxedValue
    override given given_ValueType_Return: ValueType[Return] = given_ValueType_BoxedValue
    override type RevertData = Any
    private def computeEffect(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect])(using World) = data: p =>
      val parse = parseRunes(p.toList)
      interpretForSideEffect(parse).get
    override def check(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect])(using World): Unit = computeEffect(data).check()
    override def check(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect], player: PlayerEntity)(using World): Unit = computeEffect(data).check(player)
    override def execute(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect])(using World): (BoxedValue, Any) =
      val effect = computeEffect(data)
      import effect.given
      val x = effect.execute()

      (BoxedValue(x._1)(using effect.effect.given_ValueType_Return), x._2)
    override def execute(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect], player: PlayerEntity)(using World): (BoxedValue, Any) =
      val effect = computeEffect(data)
      import effect.given
      val x = effect.execute(player)

      (BoxedValue(x._1)(using effect.effect.given_ValueType_Return), x._2)
    override def unexecute(data: Derived[Seq[(Rune, RuneRef)], BoxedSideEffect], `return`: BoxedValue, revert: Any)(using World): Unit =
      val effect = computeEffect(data)
      import effect.given
      effect.unexecute(`return`.cast[effect.effect.Return](using effect.effect.given_ValueType_Return).get, revert.asInstanceOf[effect.effect.RevertData])
  @register("defer/0")
  object Frame extends ThunkFrame:
    override type Data = BoxedThunk
    override def accept[T: ValueType as v](data: BoxedThunk, value: T): BoxedThunk = data.tag.accept(data.data, BoxedSideEffect(Effect, Derived(v.cast[Seq[BoxedValue]](value).get.map(_.cast[(Rune, RuneRef)].get))))
  override def execute(frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, frame)
  register { item.register() }

given [T: Codec as t, U: Codec as u] => Codec[(T, U)] =
  RecordCodecBuilder.create: i =>
    i.group(
      t.fieldOf("t").forGetter(_._1),
      u.fieldOf("u").forGetter(_._2)
    ).apply(i, (_, _))
given Codec[Text] = TextCodecs.CODEC

@register("doublecast")
object DoubleCastRune extends BinaryRune:
  override type Arg0 = BoxedSideEffect
  override type Arg1 = BoxedSideEffect
  override type Return = BoxedSideEffect
  override given given_ValueType_Arg0: ValueType[Return] = given_ValueType_BoxedSideEffect
  override given given_ValueType_Arg1: ValueType[Return] = given_ValueType_BoxedSideEffect
  override given given_ValueType_Return: ValueType[Return] = given_ValueType_BoxedSideEffect
  override given given_Codec_Arg0: Codec[BoxedSideEffect] = `dispatch codec for boxed side-effects`
  override given given_Codec_Arg1: Codec[BoxedSideEffect] = `dispatch codec for boxed side-effects`
//  given Codec[BoxedSideEffect] = `dispatch codec for boxed side-effects`
  object Effect extends SideEffect:
    override type Data = (BoxedSideEffect, BoxedSideEffect)
    override type Return = BoxedValue
    override type RevertData = (BoxedValue, Any, Any)
    override given dataCodec: Codec[Data] = given_Codec_T_U(using `dispatch codec for boxed side-effects`, `dispatch codec for boxed side-effects`)
    override given given_ValueType_Return: ValueType[Return] = given_ValueType_BoxedValue
    override def check(data: (BoxedSideEffect, BoxedSideEffect))(using world: World): Unit =
      data._1.check()
      data._2.check()
    override def check(data: (BoxedSideEffect, BoxedSideEffect), player: PlayerEntity)(using world: World): Unit =
      data._1.check(player)
      data._2.check(player)
    override def execute(data: (BoxedSideEffect, BoxedSideEffect))(using world: World): (BoxedValue, (BoxedValue, Any, Any)) =
      val l = data._1.execute()
      try
        val r = data._2.execute()

        (BoxedValue(r._1)(using data._2.effect.given_ValueType_Return), (BoxedValue(l._1)(using data._1.effect.given_ValueType_Return), l._2, r._2))
      catch case e: Exception =>
        data._1.unexecute(l._1, l._2)
        throw e

    override def execute(data: (BoxedSideEffect, BoxedSideEffect), player: PlayerEntity)(using World): (BoxedValue, (BoxedValue, Any, Any)) =
      val l = data._1.execute(player)
      try
        val r = data._2.execute(player)

        (BoxedValue(r._1)(using data._2.effect.given_ValueType_Return), (BoxedValue(l._1)(using data._1.effect.given_ValueType_Return), l._2, r._2))
      catch case e: Exception =>
        data._1.unexecute(l._1, l._2)
        throw e

    override def unexecute(data: (BoxedSideEffect, BoxedSideEffect), `return`: BoxedValue, revert: (BoxedValue, Any, Any))(using world: World): Unit =
      try data._2.unexecute(`return`.asInstanceOf[data._2.effect.Return], revert._3.asInstanceOf[data._2.effect.RevertData])
      finally data._1.unexecute(revert._1.value.asInstanceOf[data._1.effect.Return], revert._2.asInstanceOf[data._1.effect.RevertData])
  override def surfaceSprite: Identifier = Rune.SPELL_TEXTURE
  override def run(x: BoxedSideEffect, y: BoxedSideEffect): BoxedSideEffect = BoxedSideEffect(Effect, (x, y))
  register { item.register() }

val sideEffectComponent = ComponentType.builder().codec(codec[BoxedSideEffect]).packetCodec(PacketCodecs.codec(codec[BoxedSideEffect])).build()
val runicTrinketItem =
  val r = cursedRegister(Identifier.of("mica", "trinket"), Item.Settings().rarity(Rarity.UNCOMMON)):
    new Item(_):
      override def use(using world: World, user: PlayerEntity, hand: Hand): ActionResult =
        val stack = user.getStackInHand(hand)
        val effect = stack.get(sideEffectComponent)
        try
          effect.effect.execute(effect.data, user)
          ActionResult.SUCCESS
        catch case e: Exception =>
          ActionResult.FAIL
  r.register()
  r.value

@register("mk_trinket")
private[mica] object CraftTrinketRune extends Rune:
  override type Data = RuneRef
  @register("mk_trinket/0")
  object Frame extends ThunkFrame:
    override type Data = (RuneRef, BoxedThunk)
    override def accept[T: ValueType as v](data: (RuneRef, BoxedThunk), value: T): BoxedThunk = data._2.tag.accept(data._2.data, BoxedSideEffect(Effect, (data._1, v.cast[BoxedSideEffect](value).get)))
  @register("mk_trinket")
  object Effect extends SideEffect:
    override type Data = (RuneRef, BoxedSideEffect)
    override type Return = EntityRef
    override type RevertData = Unit
    override def check(data: (RuneRef, BoxedSideEffect))(using world: World): Unit = ()
    override def execute(data: (RuneRef, BoxedSideEffect))(using world: World): (EntityRef, Unit) =
      val stack = ItemStack(runicTrinketItem)
      stack.set(sideEffectComponent, data._2)
      val p = data._1.floatPos
      val entity = ItemEntity(world, p.x, p.y, p.z, stack)
      world.spawnEntity(entity)
      (EntityRef(entity), ())
    override def unexecute(data: (RuneRef, BoxedSideEffect), `return`: EntityRef, revert: Unit)(using world: World): Unit =
      `return`.entity.discard()
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (RuneRef, List[(Rune, RuneRef)]) = (ref, rhs)
  override def execute(data: RuneRef, frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, (data, frame))
  register:
    CraftTrinketRune.item.register()
//given Codec[(RuneRef, BoxedSideEffect)] = Codec.pair(codec[RuneRef], codec[BoxedSideEffect]).xmap(p => (p.getFirst, p.getSecond), p => Pair(p._1, p._2))
@register("reveal")
private[mica] object RevealRune extends Rune:
  override type Data = RuneRef
  @register("reveal")
  object Effect extends SideEffect:
    override type Data = (RuneRef, Text)
    override type Return = Unit
    override type RevertData = Unit
    override def check(data: (RuneRef, Text))(using world: World): Unit = ()
    override def execute(data: (RuneRef, Text), player: PlayerEntity)(using World): (Unit, Unit) =
      val p = data._1.floatPos
      summon[World].playSound(player, p.x, p.y, p.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS)
      player match
        case s: ServerPlayerEntity => s.sendMessage(data._2, false)
        case _ => ()
      ((), ())
    override def execute(data: (RuneRef, Text))(using world: World): (Unit, Unit) = ((), ())
    override def unexecute(data: (RuneRef, Text), `return`: Unit, revert: Unit)(using world: World): Unit = ()
  @register("reveal/0")
  object Frame extends ThunkFrame:
    override type Data = (RuneRef, BoxedThunk)
    override def accept[T: ValueType as v](data: (RuneRef, BoxedThunk), value: T): BoxedThunk = data._2.tag.accept(data._2.data, BoxedSideEffect(Effect, (data._1, v.show(value))))
//  given Codec[(RuneRef, BoxedThunk)] =
//    RecordCodecBuilder.create: i =>
//      i.group(
//        codec[RuneRef].fieldOf("_1").forGetter(_._1),
//        codec[BoxedThunk].fieldOf("_2").forGetter(_._2),
//      ).apply(i, (_, _))
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (RevealRune.Data, List[(Rune, RuneRef)]) = (summon, rhs)
  override def execute(data: RuneRef, frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, (data, frame))
  register:
    RevealRune.item.register()

private[mica] object JackBlack:
  def pickaxe(using ci: CallbackInfoReturnable[ActionResult])(ctx: ItemUsageContext): Unit =
    val p = ctx.getHitPos
    val q = BlockPos.Mutable((p.x * 4).round.toInt, (p.y * 8).round.toInt, (p.z * 4).round.toInt)
    val b = BlockPos.Mutable(p.x, p.y, p.z)
    given World = ctx.getWorld
    if q.getX == 4 then
      q.setX(0)
      b.setX(b.getX + 1)
    if q.getY == 8 then
      q.setY(0)
      b.setY(b.getY + 1)
    if q.getZ == 4 then
      q.setZ(0)
      b.setZ(b.getZ + 1)
    val h = RuneShift(q.getX, q.getY, q.getZ, ctx.getSide)
    val ref = RuneRef(b, h)
    if !ref.isEmpty then
      if !given_World.isClient then
        val stack = ItemStack(ref.rune.item.value, 1)
        assert(!stack.isEmpty)
        assert(Registries.ITEM.getId(stack.getItem) != null)
        stack.applyChanges(ComponentChanges.builder().add(DataComponentTypes.PROFILE,
          ProfileComponent(
            Optional.empty,
            Optional.of(Uuids.toUuid(Array(ref.heap0, ref.heap1, ref.heap2, ref.heap3))),
            PropertyMap())).build())
        val ent: ItemEntity = ItemEntity(given_World, p.x, p.y, p.z, stack)
        ent.setPosition(p)
        ent.addVelocity(h.facing.getDoubleVector.multiply(0.1))
        given_World.spawnEntity(ent)
      ref.rune = EmptyRune
      ci.setReturnValue(ActionResult.SUCCESS)
  def flintAndSTEEL_!!(using ci: CallbackInfoReturnable[ActionResult])(ctx: ItemUsageContext): Unit =
    val p = ctx.getHitPos
    val q = BlockPos.Mutable((p.x * 4).round.toInt, (p.y * 8).round.toInt, (p.z * 4).round.toInt)
    val b = BlockPos.Mutable(p.x, p.y, p.z)
    given World = ctx.getWorld
    if q.getX == 4 then
      q.setX(0)
      b.setX(b.getX + 1)
    if q.getY == 8 then
      q.setY(0)
      b.setY(b.getY + 1)
    if q.getZ == 4 then
      q.setZ(0)
      b.setZ(b.getZ + 1)
    val h = RuneShift(q.getX, q.getY, q.getZ, ctx.getSide)
    val ref = RuneRef(b, h)
    ref.rune match
      case RuneBeStartinShit(d) =>
        boundary:
          val runes = findRunes(ref, None).getOrElse:
            ci.setReturnValue(ActionResult.FAIL)
            given_World.playSound(ctx.getPlayer, p.x, p.y, p.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS)
            ctx.getPlayer.sendMessage(Text.literal("The spark fizzles out."), true)
            boundary.break()
          ci.setReturnValue(ActionResult.SUCCESS)
          try
            given_World.playSound(ctx.getPlayer, p.x, p.y, p.z, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS)
            val boxedRunes = parseRunes(runes)
            interpretForSideEffect(boxedRunes) match
              case Some(value) =>
                println(s"Hello ladies and gentlemen! Today we will be executing ${value}! Are we on the client? ${given_World.isClient}!")
                value.effect.check(value.data, ctx.getPlayer)
                value.effect.execute(value.data, ctx.getPlayer)
              case None =>
                ctx.getPlayer.sendMessage(Text.literal("Nothing seems to happen..."), true)
          catch
            case e: RuneError =>
              val p = e.ref.floatPos
              given_World.playSound(ctx.getPlayer, p.x, p.y, p.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS)
              ctx.getPlayer.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 5*20))
              ctx.getPlayer.sendMessage(e.message, true)
            case e: (NotImplementedError | MatchError) =>
              e.printStackTrace()
              ctx.getPlayer.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 5*20))
              given_World.playSound(ctx.getPlayer, p.x, p.y, p.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS)
              ctx.getPlayer.sendMessage(Text.literal("You feel strange..."), true)
            case e: Exception =>
              e.printStackTrace()
              ctx.getPlayer.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 15*20))
              ctx.getPlayer.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 15*20))
              given_World.playSound(ctx.getPlayer, p.x, p.y, p.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS)
              ctx.getPlayer.sendMessage(Text.literal("The world is falling apart at the seams..."), true)
      case _ =>

lazy val runeProbe: Registrar[Item] =
  cursedRegister(Identifier.of("mica", "rune_probe"), Item.Settings()):
    new Item(_):
      override def useOnBlock(ctx: ItemUsageContext): ActionResult =
        val p = ctx.getHitPos
        val q = BlockPos.Mutable((p.x * 4).round.toInt, (p.y * 8).round.toInt, (p.z * 4).round.toInt)
        val b = BlockPos.Mutable(p.x, p.y, p.z)
        given World = ctx.getWorld
        if q.getX == 4 then
          q.setX(0)
          b.setX(b.getX + 1)
        if q.getY == 8 then
          q.setY(0)
          b.setY(b.getY + 1)
        if q.getZ == 4 then
          q.setZ(0)
          b.setZ(b.getZ + 1)
        val h = RuneShift(q.getX, q.getY, q.getZ, ctx.getSide)
        val ref = RuneRef(b, h)
        given player: PlayerEntity = ctx.getPlayer
        if given_World.isClient then
          player.sendMessage(Text.literal("Client Rune Info:").styled(_.withColor(0xb36909)), false)
        else
          player.sendMessage(Text.literal("Server Rune Info:").styled(_.withColor(0x06bf72)), false)
        player.sendMessage(Text.literal(s"Ref: ${ref} (${ref.pos.asLong}/${ref.shift.value}) [${ref.shift.x}, ${ref.shift.y}, ${ref.shift.z}, ${ref.shift.facing}]"), false)
        player.sendMessage(Text.literal(s"Rune: ${ref.rune}"), false)
        player.sendMessage(Text.literal(s"Heap: ${ref.heap0} ${ref.heap1} ${ref.heap2} ${ref.heap3}"), false)
        ActionResult.SUCCESS
@register("player_literal")
object PlayerLiteral extends Rune:
  override type Data = EntityRef
  override def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (EntityRef, List[(Rune, RuneRef)]) =
    val uuid = EntityRef(Uuids.toUuid(Array(ref.heap0, ref.heap1, ref.heap2, ref.heap3)))

    (uuid, rhs)
  override def execute(data: EntityRef, frame: BoxedThunk): BoxedThunk =
    frame.tag.accept(frame.data, data)
  override def fillHeap(using ref: RuneRef, player: PlayerEntity, world: World)(): Unit =
    val uuid = Uuids.toIntArray(player.getUuid)
    ref.heap0 = uuid(0)
    ref.heap1 = uuid(1)
    ref.heap2 = uuid(2)
    ref.heap3 = uuid(3)
  register:
    PlayerLiteral.item.register()

@register("entity")
given ValueType[EntityRef]:
  override def eq[U: ValueType as v](x: EntityRef, y: U): Boolean = v.cast[EntityRef](y).exists(y => x.uuid == y.uuid)
  override def show(x: EntityRef): Text = Text.literal(x.uuid.toString.split('-').last).styled(_.withColor(0x9116c9)/*.withFont(Identifier.of("minecraft", "illageralt"))*/)

@register("side_effect")
given ValueType[BoxedSideEffect]:
  override def eq[U: ValueType as v](x: BoxedSideEffect, y: U): Boolean =
    v.cast[BoxedSideEffect](y).exists(y => x.effect == y.effect && x.data == y.data)

  override def show(x: BoxedSideEffect): Text = Text.translatable(registryFor[SideEffect].getId(x.effect).toTranslationKey("mica.side_effect"), x.effect.translationFields(x.data))

case class EntityRef(target: UUID | Entity):
  def uuid: UUID = target match
    case u: UUID => u
    case e: Entity => e.getUuid
  def entity(using w: World): Entity = target match
    case e: Entity => e
    case u: UUID => w.getEntity(u)
given Codec[EntityRef] = Uuids.STRICT_CODEC.xmap(EntityRef(_), _.uuid)
trait UnaryRune extends SimpleRune:
  type Arg0: {Codec, ValueType, ClassTag}
  type Return: ValueType
  private object ArgFrame extends ThunkFrame:
    type Data = BoxedThunk
    override def accept[T: ValueType as v](data: BoxedThunk, value: T): BoxedThunk =
      v.cast[Arg0](value) match
        case Some(value) =>
          data.tag.accept(data.data, run(value))
  override def execute(frame: BoxedThunk): BoxedThunk = BoxedThunk(ArgFrame, frame)
  def run(x: Arg0): Return
  protected def registerFrames(): Unit =
    val id = registryFor[Rune].getId(this)
    Registry.register(registryFor[ThunkFrame], id.withSuffixedPath("/0"), ArgFrame)
trait BinaryRune extends SimpleRune:
  type Arg0: {Codec, ValueType, ClassTag}
  type Arg1: {Codec, ValueType, ClassTag}
  type Return: ValueType
  private object Arg0Frame extends ThunkFrame:
    type Data = (frame: BoxedThunk)
    override def accept[T: ValueType as v](data: (frame: BoxedThunk), value: T): BoxedThunk =
      v.cast[Arg0](value) match
        case Some(value) =>
          BoxedThunk(Arg1Frame, (data.frame, value))
  private object Arg1Frame extends ThunkFrame:
    type Data = (frame: BoxedThunk, arg0: Arg0)
    override def accept[T: ValueType as v](data: (frame: BoxedThunk, arg0: Arg0), value: T): BoxedThunk =
      v.cast[Arg1](value) match
        case Some(value) =>
          val h = data.frame
          h.tag.accept(h.data, run(data.arg0, value))
  override def execute(frame: BoxedThunk): BoxedThunk = BoxedThunk(Arg0Frame, (frame = frame))
  def run(x: Arg0, y: Arg1): Return
  protected def registerFrames(): Unit =
    val id = registryFor[Rune].getId(this)
    if id == null then
      throw IllegalStateException("Rune frames registered before rune itself")
    Registry.register(registryFor[ThunkFrame], id.withSuffixedPath("/0"), Arg0Frame)
    Registry.register(registryFor[ThunkFrame], id.withSuffixedPath("/1"), Arg1Frame)

given `codec for just a frame`: Codec[((frame: BoxedThunk))] = codec[BoxedThunk].fieldOf("frame").codec().xmap((frame = _), _.frame)
given [T: Codec as c] => Codec[((frame: BoxedThunk, arg0: T))] =
  RecordCodecBuilder.create: i =>
    i.group(
      codec[BoxedThunk].fieldOf("frame").forGetter(_.frame),
      c.fieldOf("arg0").forGetter(_.arg0)
    ).apply(i, (_, _))

@register("add")
object AdditionRune extends BinaryRune:
  override type Arg0 = Double
  override type Arg1 = Double
  override type Return = Double

  override def run(x: Double, y: Double): Double = x + y

  register:
    registerFrames()
    AdditionRune.item.register()

given unmapsYourCodec: [T: MapCodec as c] => Codec[T] = c.codec

@register("sub")
object SubitionRune extends BinaryRune:
  override type Arg0 = Double
  override type Arg1 = Double
  override type Return = Double

  override def run(x: Double, y: Double): Double = x - y

  register:
    registerFrames()
    SubitionRune.item.register()

@register("mul")
object MulitionRune extends BinaryRune:
  override type Arg0 = Double
  override type Arg1 = Double
  override type Return = Double

  override def run(x: Double, y: Double): Double = x * y

  register:
    registerFrames()
    MulitionRune.item.register()

given Codec[Double] = Codec.DOUBLE.xmap(locally(_), locally(_))

@register("div")
object DivitionRune extends BinaryRune:
  override type Arg0 = Double
  override type Arg1 = Double
  override type Return = Double

  override def run(x: Double, y: Double): Double = x / y

  register:
    registerFrames()
    DivitionRune.item.register()

extension (x: Double) @targetName("~=") def =~(y: Double): Boolean = Math.abs(x - y) < 0.0001

given Codec[Vec3d] = Vec3d.CODEC
given ValueType[Vec3d]:
  override def eq[U: ValueType as v](x: Vec3d, y: U): Boolean =
    v.cast[Vec3d](y).exists: y =>
      x.x =~ y.x && x.y =~ y.y && x.z =~ y.z
  override def cast[R: ClassTag](x: Vec3d): Option[R] = super.cast(x)

  override def show(x: Vec3d): Text = Text.literal(s"{${x.x}, ${x.y}, ${x.z}}").styled(_.withColor(0xad8d1a))

inline def codec[T: Codec as c] = c

case class AttributeReference(target: LivingEntity | UUID, attribute: EntityAttribute):
  def uuid: UUID = target match
    case e: LivingEntity => e.getUuid
    case u: UUID => u
  def entity(using w: World): Option[LivingEntity] = target match
    case e: LivingEntity => Some(e)
    case u: UUID => w.getEntity(u) match
      case null => None
      case e: LivingEntity => Some(e)
      case _ => None
  def attributeInstance(using World): Option[EntityAttributeInstance] = entity.map(_.getAttributeInstance(Registries.ATTRIBUTE.getEntry(attribute)))
given `codec for entity attributes using the registry`: Codec[EntityAttribute] = Registries.ATTRIBUTE.getCodec
given `codec for references to entity attributes`: Codec[AttributeReference] =
  RecordCodecBuilder.create: i =>
    i.group(
      Uuids.INT_STREAM_CODEC.fieldOf("entity").forGetter(_.uuid),
      codec[EntityAttribute].fieldOf("attribute").forGetter(_.attribute),
    ).apply(i, AttributeReference(_, _))
given `hey did you know attributes are values`: ValueType[AttributeReference]:
  override def eq[U: ValueType as v](x: AttributeReference, y: U): Boolean = v.cast[AttributeReference](y).exists(y => (x.uuid == y.uuid) && (x.attribute eq y.target))
  override def show(x: AttributeReference): Text = Text.translatable(x.attribute.getTranslationKey).styled(_.withColor(0x07b891))

@register("void")
object VoidRune extends SimpleRune:
  @register("void/0")
  object Frame extends ThunkFrame:
    override type Data = BoxedThunk
    override def accept[T: ValueType](data: BoxedThunk, value: T): BoxedThunk = data
  override def execute(frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, frame)
  register { item.register() }

@register("twice")
object TwiceRune extends SimpleRune:
  @register("twice/0")
  object Frame extends ThunkFrame:
    override type Data = BoxedThunk
    override def accept[T: ValueType](data: BoxedThunk, value: T): BoxedThunk = data.accept(value).accept(value)
  override def execute(frame: BoxedThunk): BoxedThunk = BoxedThunk(Frame, frame)
  register { item.register() }

@register("nyaboom")
object ExplosionRune extends BinaryRune:
  override type Arg0 = Vec3d
  override type Arg1 = Double
  override type Return = BoxedSideEffect
  override def surfaceSprite: Identifier = Rune.SPELL_TEXTURE

  @register("nyaboom")
  object Effect extends SideEffect:
    override type Data = (pos: Vec3d, power: Double)
    override type Return = Unit
    override type RevertData = Unit // good luck un-exploding someone

    override def check(data: (pos: Vec3d, power: Double))(using world: World): Unit =
      // explosions are *probably* fine
      ()
    override def execute(data: (pos: Vec3d, power: Double))(using world: World): (Unit, Unit) =
      if !world.isClient then
        world.createExplosion(null: Entity, data.pos.x, data.pos.y, data.pos.z, data.power.toFloat, ExplosionSourceType.TRIGGER)
      ((), ())
    override def unexecute(data: (pos: Vec3d, power: Double), `return`: Unit, revert: Unit)(using world: World): Unit =
      // can't unexplode people
      ()

  override def run(pos: Vec3d, power: Double) = BoxedSideEffect(Effect, (pos, power))

  register:
    ExplosionRune.item.register()
    registerFrames()

given Codec[((pos: Vec3d, power: Double))] =
  RecordCodecBuilder.create[(pos: Vec3d, power: Double)]: i =>
    i.group(
      codec[Vec3d].fieldOf("pos").forGetter(_.pos),
      codec[Double].fieldOf("power").forGetter(_.power),
    ).apply(i, (pos, power) => (pos = pos, power = power))

@register("endquote")
object EndQuoteRune extends Rune:
  type Data = Nothing
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
  override def read(rhs: List[(Rune, RuneRef)])(using RuneRef, World): (Nothing, List[(Rune, RuneRef)]) = throw RuneError(Text.literal("Unquote with no corresponding quote"))
  override def execute(data: Nothing, frame: BoxedThunk): BoxedThunk = data

  register:
    EndQuoteRune.item.register()
// divert

given seqCodec: [T: Codec as c] => Codec[Seq[T]] = c.listOf().xmap(_.toSeq, locally(_))
given unitCodec: Codec[Unit] = Codec.unit(())
given nothingCodec: Codec[Nothing] = Codec.of(new Encoder[Nothing]:
  override def encode[T](input: Nothing, ops: DynamicOps[T], prefix: T): DataResult[T] = input, Decoder.error("Nothing codec"))

extension [T] (x: T)
  /**
   * Tries to cast the value to the given type.
   * @tparam R The destination type of the cast.
   * @return [[Some]] if the cast succeeds.
   */
  def cast[R: ClassTag]: Option[R] = x match
    case r: R => Some(r)
    case _ => None

private[mica] class ComponentInitializer extends WorldComponentInitializer:
  def registerWorldComponentFactories(factories: WorldComponentFactoryRegistry): Unit =
    /*dnl*/ () /*
    *///undivert(1)
def init(): Unit =
  runeProbe.register()
  Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(modid, "effect"), sideEffectComponent)
  register()
  println(s"Rune registry contains ${registryFor[Rune].size} runes")
  try
    val config = Path.of("config/mica:extra_classes.txt")
    if Files.exists(config) then
      logger.warn("Ignoring config/mica:extra_classes.txt as it is deprecated. Remove the file to silence this warning.")
  catch case e => ()