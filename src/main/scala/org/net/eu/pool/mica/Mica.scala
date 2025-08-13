package org.net.eu.pool.mica
import com.mojang.datafixers.util.Pair
import com.mojang.serialization
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.{Codec, DataResult, Decoder, DynamicOps, Encoder, Lifecycle, MapCodec}
import net.minecraft.util.shape.VoxelShapes
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
import scala.quoted.{Expr, Quotes}
import scala.reflect.ClassTag
import scala.tools.nsc.io.Path
import scala.util.chaining.scalaUtilChainingOps

private given ModID = ModID("mica")

val /* <[[ */ minecraft_version: /* ]]> */ Int = minecraft_version
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
object RuneShift:
  opaque type RuneShift = Int
  inline def apply(value: Int): RuneShift = value
  def apply(x: Int, y: Int, z: Int, facing: Direction): RuneShift = x & 0b11 | y << 2 & 0b11100 | z << 5 & 0b1100000 | facing.ordinal << 7
  private def x_impl(s: Expr[RuneShift], x: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(x = ${x})}.asTerm).asExprOf[Unit]
  private def y_impl(s: Expr[RuneShift], y: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(y = ${y})}.asTerm).asExprOf[Unit]
  private def z_impl(s: Expr[RuneShift], z: Expr[Int])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(z = ${z})}.asTerm).asExprOf[Unit]
  given ops: AnyRef:
    extension (s: RuneShift)
      @inline def value: Int = s
      @inline def x = s & 0b11
      @inline def y = s >> 2 & 0b111
      @inline def z = s >> 5 & 0b11
      @inline def facing = Direction.values()(s >> 7 & 0b111)
      @inline def apply(x: Int = s.x, y: Int = s.y, z: Int = s.z, facing: Direction = s.facing): RuneShift = RuneShift(x, y, z, facing)
  private def facing_impl(s: Expr[RuneShift], facing: Expr[Direction])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(facing = ${facing})}.asTerm).asExprOf[Unit]
  val shapeCache: Array[VoxelShape] = Array.ofDim[VoxelShape](768).tap: m =>
    for shift: RuneShift <- 0 until 768 do
      val middle = (shift.x / 4.0 - 0.5, shift.y / 8.0 - 0.5, shift.z / 4.0 - 0.5)
      m(shift) = VoxelShapes.cuboid(middle._1 - .25, middle._2, middle._3 - .25, middle._1 + .25, middle._2 + .0625, middle._3 + .25)
end RuneShift
export RuneShift.RuneShift

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
      // ifversion(>= 2100,<[[
        .fieldOf("value")
      // ]]>,)
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
@register("empty")
object EmptyRune extends Rune:
  override type Data = Unit
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
  override def read(rhs: List[Rune]): (Unit, List[Rune]) = ((), rhs)
  override def execute(data: Unit, frame: ThunkFrame): ThunkFrame = frame

@register("quote")
object QuoteRune extends Rune:
  override type Data = Seq[BoxedRune]
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
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

sealed trait ConcreteRuneStorage extends AbstractRuneStorage:
  // ifversion(>=2100, <[[
  override def readData(c: ReadView): Unit =
  // ]]>, <[[
  override def readFromNbt(c: NbtCompound): Unit =
  // ]]>)
    for
      i <- 0L until c.getLong("c", 0L)
      k = c.getLong(s"k$i", 0L)
      v = c.getInt(s"v$i", 0)
      p = c.getString(s"p$v", summonUnlessSeeding[Registry[Rune]].getId(EmptyRune).toString)
      r <- Option.fromNullable(Identifier.tryParse(p))
    do
      contents(k) = summonUnlessSeeding[Registry[Rune]].get(r)

  // ifversion(>=2100, <[[
  override def writeData(c: WriteView): Unit =
  // ]]>, <[[
  override def writeToNbt(c: NbtCompound): Unit =
  // ]]>)
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
        c.putString(s"p${i}", registryFor[Rune].getId(k).toString)
// forloop(n, 0, 767, <[[
// pushdef(RuneStorage, <[[ifelse($#,0,<[[<[[$0]]>]]><[[n]]>,<[[$0]]><[[<[[(]]>]]><[[$@]]><[[)]]>)]]>)
case class RuneStorage(world: World, contents: Long2ObjectMap[Rune]) extends ConcreteRuneStorage:
  override type Concrete = RuneStorage
  contents.defaultReturnValue(EmptyRune)
object RuneStorage:
  val shift = RuneShift(n)
  given key: ComponentKey[RuneStorage] = ComponentRegistry.getOrCreate(Identifier.of("mica", "runes<[[]]>n"), classOf[RuneStorage])
  // divert(1)
    /*dnl*/val factories: WorldComponentFactoryRegistry = erasedValue/*
*/Duck.register(factories, RuneStorage.key, classOf[RuneStorage], RuneStorage(_, Duck.mkMap()))
    AbstractRuneStorage.keys(n) = RuneStorage.key
    // divert
// popdef(<[[RuneStorage]]>)
// ]]>)

@register("endquote")
object EndQuoteRune extends Rune:
  type Data = Nothing
  override def surfaceSprite: Identifier = Rune.BASIC_TEXTURE
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

class ComponentInitializer extends WorldComponentInitializer:
  def registerWorldComponentFactories(factories: WorldComponentFactoryRegistry) =
    /*dnl*/ () /*
    *///undivert(1)
def init(): Unit =
  register()
  // eww
  println("BELIEVE IT OR NOT WE ARE HERE")
  EmptyRune.item.register()
  QuoteRune.item.register()
  EndQuoteRune.item.register()
  println("do you have hair?")