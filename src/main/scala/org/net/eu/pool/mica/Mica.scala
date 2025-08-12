package org.net.eu.pool.mica
import com.mojang.datafixers.util.Pair
import com.mojang.serialization
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.{Codec, DataResult, Decoder, DynamicOps, Encoder, Lifecycle, MapCodec}
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry
import it.unimi.dsi.fastutil.longs.{Long2ObjectMap, Long2ObjectOpenHashMap}
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.{FabricBlockEntityType, FabricBlockEntityTypeBuilder}
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.minecraft.block.{AbstractBlock, Block, BlockState, BlockWithEntity}
import net.minecraft.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.item.{Item, ItemUsageContext}
import net.minecraft.nbt.NbtCompound
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
import scala.quoted.{Expr, Quotes}
import scala.reflect.ClassTag
import scala.tools.nsc.io.Path

private given ModID = ModID("mica")

val /* << */ minecraft_version: /* >> */ Int = minecraft_version
trait Abstract[C[_]]:
  type T: C
  val value: T
object Abstract:
  def apply[C[_], _T: C](x: _T): Abstract[C] = new Abstract[C]:
    type T = _T
    val value: _T = x
  def unapply[C[_]](x: Abstract[C]): Some[(x.T, C[x.T])] =
    import x.given
    Some((x.value, /* WITH THIS TREASURE I */ summon))

@hasRegistry
trait ThunkFrame:
  given Codec[this.type] = deferred
  val accept: PartialFunction[Abstract[ValueType], ThunkFrame]
object ThunkFrame

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

trait BitCodec[@specialized T]:
  def read(r: BitReader): T
  def write(w: BitWriter, x: T): Unit
object BitCodec:
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
trait ByteCodec[T]:
  def read(r: DataInput): T
  def write(w: DataOutput, x: T): Unit
// divert(-1)

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
// divert
/**
 * A rune is an element of a cast. Runes are read left-to-right and form execution frames on the thunk stack.
 */
@hasRegistry
trait Rune:
  type Data: Codec

  /**
   * Reads the rune's information from the list of runes.
   *
   * @param rhs The list of remaining runes, not including this rune.
   * @throws RunesParseError Thrown when the rune is unable to read all of its arguments.
   * @return The rune's metadata and the list of remaining runes iff parsing is successful.
   */
  @throws[RunesParseError]("when this rune cannot parse its arguments")
  def read(rhs: List[Rune]): (Data, List[Rune])

  /**
   * Executes this rune, given its metadata. Executing a rune pushes some [[ThunkFrame]]s to the stack, then pops some.
   * @param data The metadata returned from [[read]].
   * @param frame The top of the thunk stack. All runes usually will pass data to a thunk after pushing some of its own.
   * @return The new top of the thunk stack.
   */
  def execute(data: Data, frame: ThunkFrame): ThunkFrame

  val item =
    cursedRegister(summonUnlessSeeding[Registry[Rune]].getId(this), Item.Settings()):
      new Item(_):
        override def useOnBlock(context: ItemUsageContext): ActionResult =
          val p = context.getHitPos
          val q = BlockPos.Mutable((p.x * 4).round.toInt, (p.y * 8).round.toInt, (p.z * 4).round.toInt)
          val b = BlockPos.Mutable(p.x, p.y, p.z)
          if q.getX == 4 then
            q.setX(0)
            b.setX(b.getX + 1)
          if q.getY == 8 then
            q.setY(0)
            b.setY(b.getY + 1)
          if q.getZ == 4 then
            q.setZ(0)
            b.setZ(b.getZ + 1)
          // TODO: also check surrounding blocks
          val h = RuneShift(q.getX, q.getY, q.getZ, context.getSide)
          val s = AbstractRuneStorage.get(context.getWorld, h)
          if s(b) == EmptyRune then
            s(b) = Rune.this
            AbstractRuneStorage.sync(s.world, h)
            ActionResult.CONSUME
          else
            ActionResult.PASS
object Rune
class RuneShift(val value: Int) extends AnyVal:
  // changequote
  def x = value & 0b11
  def y = value >> 2 & 0b111
  def z = value >> 5 & 0b11
  def facing = Direction.values()(value >> 7 & 0b111)
  def apply(x: Int = this.x, y: Int = this.y, z: Int = this.z, facing: Direction = this.facing) = RuneShift(x, y, z, facing)
object RuneShift:
  def apply(x: Int, y: Int, z: Int, facing: Direction) = new RuneShift(x & 0b11 | y << 2 & 0b11100 | z << 5 & 0b1100000 | facing.ordinal << 7)
  extension (inline s: RuneShift)
    inline def x_=(x: Int) = ${ x_impl('s, 'x) }
    inline def y_=(y: Int) = ${ y_impl('s, 'y) }
    inline def z_=(z: Int) = ${ z_impl('s, 'z) }
    inline def facing_=(facing: Direction) = ${ facing_impl('s, 'facing) }
  private def x_impl(s: Expr[RuneShift], x: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(x = ${x})}.asTerm).asExprOf[Unit]
  private def y_impl(s: Expr[RuneShift], y: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(y = ${y})}.asTerm).asExprOf[Unit]
  private def z_impl(s: Expr[RuneShift], z: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(z = ${z})}.asTerm).asExprOf[Unit]
  private def facing_impl(s: Expr[RuneShift], facing: Expr[Direction])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(facing = ${facing})}.asTerm).asExprOf[Unit]
  // changequote(<<,>>)
end RuneShift

class sparse extends Annotation

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
        // changequote([[,]])
        .foldLeft(0)(_ << 7 | _)
    override def write(w: DataOutput, x: VarInt): Unit =
      assume(x >= 0)
      if x == 0 then
        w.write(STOP_BIT)
      else
        Iterator.unfold[Int, Int](x): x =>
          Option.unless(x == 0):
            val x1 = x >>> 7
            // changequote(<<,>>)
            (x1, x & DATA_BIT)
        .toSeq.reverse match
          case h :+ t =>
            h.foreach(w.write)
            w.write(t | STOP_BIT)
          case _ => w.write(STOP_BIT)

/**
 * Exception for when a [[Rune]] cannot fully read its arguments.
 *
 * @param distance The distance from the *end* the error was thrown.
 * @param message
 */
case class RunesParseError(distance: Int, message: Text) extends Exception

case class BoxedRune(rune: Rune, data: rune.Data)
inline given Codec[BoxedRune] =
  summonUnlessSeeding[Codec[Rune]]
    .dispatch[BoxedRune](_.rune, (r: Rune) =>
      import r.given
      summon[Codec[r.Data]].xmap(BoxedRune(r, _),
        // FIXME: Rewrite [dispatch] to avoid this unsafe cast.
        _.data.asInstanceOf[r.Data])
      // ifversion(>= 2100,<<
        .fieldOf("value")
      // >>,)
    )

//def dependentCodec[T, R](arg: Codec[T], rhs: [R] )

/**
 * Stores additional data out-of-band with existing world chunks. This is useful if you want to e.g. manage chunk loading independently, or
 */
trait ParallelSection

//object ExplodeRune extends Rune:
//	type Data = (BlockPos, Int)--

/**
 * Does nothing. Pushes and pops no frames, consumes no runes, etc.
 */
@register
object EmptyRune extends Rune:
  override type Data = Unit
  override def read(rhs: List[Rune]): (Unit, List[Rune]) = ((), rhs)
  override def execute(data: Unit, frame: ThunkFrame): ThunkFrame = frame

@register
object QuoteRune extends Rune:
  override type Data = Seq[BoxedRune]
  override def read(rhs: List[Rune]): (Seq[BoxedRune], List[Rune]) =
    @throws[RunesParseError]
    def worker(rhs: List[Rune]): (List[BoxedRune], List[Rune]) =
      if rhs.isEmpty then
        throw RunesParseError(0, Text.literal("Quote with no corresponding unquote"))
      else if rhs.head == EndQuoteRune then
        (List.empty, rhs)
      else
        val r = rhs.head
        val (d, t) = r.read(rhs.tail)
        val (b, t2) = try
          worker(t)
        catch
          // FIXME: this +1 is going to bite me, isn't it?
          case RunesParseError(distance, message) => throw RunesParseError(distance + 1, message)
        locally:
          (BoxedRune(r, d)::b, t2)
    worker(rhs)
  override def execute(data: Seq[BoxedRune], frame: ThunkFrame): ThunkFrame = ???

sealed trait AbstractRuneStorage extends Component, AutoSyncedComponent:
  type Concrete <: AbstractRuneStorage
  val world: World
  val contents: Long2ObjectMap[Rune]
  override def readFromNbt(c: NbtCompound): Unit =
    for
      i <- 0L until c.getLong("c", 0L)
      k = c.getLong(s"k$i", 0L)
      v = c.getInt(s"v$i", 0)
      p = c.getString(s"p$v", summonUnlessSeeding[Registry[Rune]].getId(EmptyRune).toString)
      r <- Option.fromNullable(Identifier.tryParse(p))
    do
      contents(k) = summonUnlessSeeding[Registry[Rune]].get(r)
  override def writeToNbt(c: NbtCompound): Unit =
    val palette = contents.values.toSeq.distinct.zipWithIndex.toMap
    var i = 0
    contents.forEach: (k, v) =>
      if v != EmptyRune then
        c.putLong(s"k$i", k)
        c.putInt(s"v$i", palette(v))
        i += 1
    c.putLong("c", i)
    palette.foreach: (k, i) =>
      if k != EmptyRune then
        c.putString(s"p${i}", summonUnlessSeeding[Registry[Rune]].getId(k).toString)
  given ComponentKey[Concrete] = deferred
  def apply(pos: BlockPos): Rune = contents.get(Long.box(pos.asLong))
  def update(pos: BlockPos, rune: Rune): Unit = rune match
    // deprecated and slow, but Long <: Object so overload resolution fails
    case EmptyRune => contents.remove(Long.box(pos.asLong))
    case _ => contents.put(pos.asLong, rune)
// forloop(n, 0, 767, <<
// pushdef(RuneStorage, <<ifelse($#,0,<<<<$0>>>><<n>>,<<$0>><<<<(>>>><<$@>><<)>>)>>)
case class RuneStorage(world: World, contents: Long2ObjectMap[Rune]) extends AbstractRuneStorage:
  override type Concrete = RuneStorage
  contents.defaultReturnValue(EmptyRune)
object RuneStorage:
  val shift = new RuneShift(n)
  given ComponentKey[RuneStorage] = ComponentRegistry.getOrCreate(Identifier.of("mica", "runes<<>>n"), classOf[RuneStorage])
  // divert(1)
  /*dnl*/val factories: WorldComponentFactoryRegistry = erasedValue/*
  */Duck.register(factories, summonUnlessSeeding, classOf[RuneStorage], RuneStorage(_, Duck.mkMap()))
  // divert
  /* divert(3)case n => summonUnlessSeeding[ComponentKey[RuneStorage]].get(world)
      divert */
  /* divert(4)case n => summonUnlessSeeding[ComponentKey[RuneStorage]].sync(world)
      divert */
// popdef(<<RuneStorage>>)
// >>)

object AbstractRuneStorage:
  def get(world: World, shift: RuneShift): AbstractRuneStorage =
    shift.value match
      undivert(3)
  def sync(world: World, shift: RuneShift): Unit =
    shift.value match
      undivert(4)

trait Registrar[T]:
  val value: T
  def register(): Unit

// probably the least part of this code
/**
 * Cross-version helper for registering items.
 * @tparam T Concrete item type for generics purposes.
 * @param identifier Final identifier of item - passed to the settings in >=1.21.2.
 * @param settings Item settings - these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param itemFactory Produces the item from its settings. This **must** use the provided instance!
 * @return Object containing the final item, as well as a method that registers it.
 */
def cursedRegister[T <: Item](identifier: Identifier, settings: Item.Settings)(itemFactory: Item.Settings => T): Registrar[T] =
  val key = RegistryKey.of(RegistryKeys.ITEM, identifier)
  lazy val item = itemFactory(settings/*ifversion(>=2102,<<*/.registryKey(key)/*>>)*/)
  new Registrar[T]:
    override val value: T = item
    override def register(): Unit =
      Registry.register(Registries.ITEM, key, item)

/**
 * Cross-version helper for registering blocks.
 * @tparam T Concrete block type for generics purposes.
 * @param identifier Final identifier of block - passed to the settings in >=1.21.2.
 * @param settings Block settings - these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param blockFactory Produces the block from its settings. This **must** use the provided instance! This function is also passed its return value for e.g. block entities.
 * @return Object containing the final block, as well as a method that registers it.
 */
def cursedRegister[T <: Block](identifier: Identifier, settings: AbstractBlock.Settings)(blockFactory: (=> AbstractBlock.Settings => T) => AbstractBlock.Settings => T): Registrar[T] =
  val key = RegistryKey.of(RegistryKeys.BLOCK, identifier)
  lazy val fixed: AbstractBlock.Settings => T = blockFactory(fixed)
  lazy val block = fixed(settings/*ifversion(>=2100,<<*/.registryKey(key)/*>>)*/)
  new Registrar[T]:
    override val value: T = block
    override def register(): Unit =
      Registry.register(Registries.BLOCK, key, block)

@register
object EndQuoteRune extends Rune:
  type Data = Nothing
  override def read(rhs: List[Rune]): (Nothing, List[Rune]) = throw RunesParseError(rhs.length, Text.literal("Unquote with no corresponding quote"))
  override def execute(data: Nothing, frame: ThunkFrame): ThunkFrame = data
// divert

given [T](using Codec[T]): Codec[Seq[T]] = Codec.list[T](summon).xmap(_.toSeq, locally(_))
given Codec[Unit] = Codec.unit(())
given Codec[Nothing] = Codec.of(new Encoder[Nothing]:
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

@hasRegistry
trait ValueType[T: Codec]:
  def eq[U: ValueType](x: T, y: U): Boolean
object ValueType

def initComponents(factories: WorldComponentFactoryRegistry) =
  /*dnl*/()/*
  *///undivert(1)
def init(): Unit =
  register()