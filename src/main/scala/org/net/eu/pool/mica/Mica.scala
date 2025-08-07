package org.net.eu.pool.mica

import com.mojang.datafixers.util.Pair
import com.mojang.serialization
import com.mojang.serialization.{Codec, DataResult, Decoder, DynamicOps, Encoder, Lifecycle, MapCodec}
import net.minecraft.registry.{MutableRegistry, Registry, RegistryKey, SimpleDefaultedRegistry, SimpleRegistry}
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.dynamic.Codecs
import net.minecraft.util.math.BlockPos

import scala.annotation.{MacroAnnotation, compileTimeOnly, tailrec}
import scala.collection.convert.ImplicitConversions.given
import scala.compiletime.deferred
import scala.quoted.Quotes
import scala.reflect.ClassTag

// changequote(\[,\])
// define(ifversion, \[ifelse(eval(minecraft_version $1), $2, $3)\])
val /* \[ */ minecraft_version: /* \] */ Int = minecraft_version

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

@hasRegistry(Identifier("mica", "thunk_frame"))
trait ThunkFrame:
  given Codec[this.type] = deferred
  val accept: PartialFunction[Abstract[ValueType], ThunkFrame]
object ThunkFrame

/**
 * A rune is an element of a cast. Runes are read left-to-right and form execution frames on the thunk stack.
 */
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

given runeRegistryKey: RegistryKey[Registry[Rune]] = RegistryKey.ofRegistry(Identifier.of("mica", "rune"))
given runeRegistry: MutableRegistry[Rune] = SimpleDefaultedRegistry[Rune]("mica:unknown", summon, Lifecycle.stable(), false)
given Codec[Rune] = runeRegistry.getCodec

/**
 * Exception for when a [[Rune]] cannot fully read its arguments.
 * @param distance The distance from the *end* the error was thrown.
 * @param message
 */
case class RunesParseError(distance: Int, message: Text) extends Exception

case class BoxedRune(rune: Rune, data: rune.Data)
given Codec[BoxedRune] =
  summon[Registry[Rune]].getCodec
    .dispatch[BoxedRune](_.rune, (r: Rune) =>
      import r.given
      summon[Codec[r.Data]].xmap(BoxedRune(r, _),
        // FIXME: Rewrite [dispatch] to avoid this unsafe cast.
        _.data.asInstanceOf[r.Data])
    )

//def dependentCodec[T, R](arg: Codec[T], rhs: [R] )

/**
 * Stores additional data out-of-band with existing world chunks. This is useful if you want to e.g. manage chunk loading independently, or
 */
trait ParallelSection

//object ExplodeRune extends Rune:
//	type Data = (BlockPos, Int)

/**
 * Does nothing. Pushes and pops no frames, consumes no runes, etc.
 */
@register(Identifier("mica", "empty"))
object EmptyRune extends Rune:
  override type Data = Unit
  override def read(rhs: List[Rune]): (Unit, List[Rune]) = ((), rhs)
  override def execute(data: Unit, frame: ThunkFrame): ThunkFrame = frame

@register(Identifier("mica", "quote"))
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
        val r = rhs.head // needed for associated type
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

@register(Identifier("mica", "unquote"))
object EndQuoteRune extends Rune:
  type Data = Nothing
  override def read(rhs: List[Rune]): (Nothing, List[Rune]) = throw RunesParseError(rhs.length, Text.literal("Unquote with no corresponding quote"))
  override def execute(data: Nothing, frame: ThunkFrame): ThunkFrame = data

given [T](using Codec[T]): Codec[Seq[T]] = Codec.list[T](summon).xmap(_.toSeq, locally(_))
given Codec[Unit] = Codec.unit(())
given Codec[Nothing] = Codec.of(new Encoder[Nothing]:
  override def encode[T](input: Nothing, ops: DynamicOps[T], prefix: T): DataResult[T] = input, Decoder.error("Nothing codec"))

extension [T] (x: T)
  /**
   * Tries to cast the value to the given type.
   * @tparam R The destination type of the cast.
   * @return [[Some]] if and only if `T <: R` or `x` is dynamically a subtype of `R`.
   */
  def cast[R: ClassTag]: Option[R] = x match
    case r: R => Some(r)
    case _ => None

@hasRegistry(Identifier("mica", "values"))
trait ValueType[T: Codec]:
  def eq[U: ValueType](x: T, y: U): Boolean
object ValueType

def init(): Unit =
  println(/*[*/"HELLO"/*]*/)
  register()
