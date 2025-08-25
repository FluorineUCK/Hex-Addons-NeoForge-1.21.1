package org.net.eu.pool.mica

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.longs.{Long2IntMap, Long2ObjectMap}
import net.minecraft.block.{AbstractBlock, Block}
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{Item, ItemUsageContext}
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.text.Text
import net.minecraft.util.math.Direction.{Axis, AxisDirection}
import net.minecraft.util.{ActionResult, Identifier, Uuids}
import net.minecraft.util.math.{BlockPos, Direction, Vec3d}
import net.minecraft.util.shape.{VoxelShape, VoxelShapes}
import net.minecraft.world.World
import org.net.eu.pool.mica.RevealRune.Frame
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

import scala.annotation.MacroAnnotation
import scala.collection.mutable
import scala.quoted.{Expr, Quotes}
import scala.reflect.ClassTag
import scala.util.chaining.scalaUtilChainingOps
// ifversion(>=2100, <[[
import net.minecraft.storage.{ReadView, WriteView}
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent
import org.ladysnake.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry
// ]]>, <[[
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry
// ]]>)

given [T] => (reg: RegistryKey[? <: Registry[T]]) => Conversion[Identifier, RegistryKey[T]] = id => RegistryKey.of(reg, id)
given [T]: Conversion[RegistryKey[T], Identifier] = _.getRegistry

/**
 * Holds a deferred promise to add a value to a registry. This is returned by e.g. [[cursedRegister]] for you to call in your init function.
 *
 * <b style="color: color-mix(in oklch, 15% currentcolor, red)">Caution:</b> If you have a forced Registrar (as opposed
 * to a by-name / lazy `=> Registrar[_]`), you '''must''' call [[register()]] at some point. '''Failing to do so may
 * cause an intrusive-holder crash!'''
 *
 * @tparam T Type of the value that will be registered.
 */
trait Registrar[T]:
  /**
   * Backing value. This is usually an [[Item]] or [[Block]].
   */
  lazy val value: T

  /**
   * Adds [[value]] to the associated registry.
   */
  def register(): Unit

// probably the least cursed part of this code
/**
 * Cross-version helper for registering items.
 *
 * @tparam T Concrete item type for generics purposes.
 * @param identifier  Final identifier of item — passed to the settings in >=1.21.2.
 * @param settings    Item settings — these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param itemFactory Produces the item from its settings. This '''must''' use the provided instance!
 * @return Object containing the final item, as well as a method that registers it.
 */
def cursedRegister[T <: Item](identifier: Identifier, settings: Item.Settings)(itemFactory: Item.Settings => T): Registrar[T] =
  assert(identifier != null)
  val key = RegistryKey.of(RegistryKeys.ITEM, identifier)
  lazy val item = itemFactory(settings /*ifversion(>=2102,<[[*/.registryKey(key) /*]]>)*/)
  new Registrar[T]:
    private var registered = false
    override lazy val value: T =
      if registered then
        item
      else
        throw IllegalStateException(s"Item $identifier was not registered")
    override def register(): Unit =
      registered = true
      Registry.register(Registries.ITEM, key, item)

/**
 * Cross-version helper for registering blocks.
 *
 * @tparam T Concrete block type for generics purposes.
 * @param identifier   Final identifier of block — passed to the settings in >=1.21.2.
 * @param settings     Block settings — these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param blockFactory Produces the block from its settings. This '''must''' use the provided instance! This function is also passed its return value for e.g. block entities.
 * @return Object containing the final block, as well as a method that registers it.
 */
def cursedRegister[T <: Block](identifier: Identifier, settings: AbstractBlock.Settings)(blockFactory: (=> AbstractBlock.Settings => T) => AbstractBlock.Settings => T): Registrar[T] =
  val key = RegistryKey.of(RegistryKeys.BLOCK, identifier)
  lazy val fixed: AbstractBlock.Settings => T = blockFactory(fixed)
  lazy val block = fixed(settings /*ifversion(>=2100,<[[*/.registryKey(key) /*]]>)*/)
  new Registrar[T]:
    override lazy val value: T = block

    override def register(): Unit =
      Registry.register(Registries.BLOCK, key, block)

/**
 * Converts an annotated function into a [[Rune]] implementation and as many [[ThunkFrame]] implementations as needed
 * for its arguments. Optional arguments are not allowed.
 */
case class rune[+T] private(reader: (List[Any]) => (T, List[Any])) extends MacroAnnotation:
  def this()(using ev: Unit <:< T) = this((ev(()), _))

  override def transform(using q: Quotes)(definition: q.reflect.Definition, companion: Option[q.reflect.Definition]): List[q.reflect.Definition] =
    import q.reflect._
//    definition match
//      case d@DefDef(name, params, returnType, body) =>
//        val buf = mutable.Buffer(definition)
//        buf ++= companion
//        val explicit = params.collect:
//          case c@TermParamClause(p) if !c.isGiven => p
//        if explicit.length > 1 then
//          report.error("method must have exactly one explicit parameter list")
//        val args = explicit(1)
//        val runeSymbol = Symbol.newClass(d.symbol.owner, s"$name", List(TypeRepr.of[Rune]), s => List(), None)
//        buf ++= new register(name).transform(ClassDef(runeSymbol, List(TypeTree.of[Rune]), List()), None)
//        // TODO
//        buf.toList
//      case _ =>
//        report.error("@rune may only be used on function definitions")
        List(definition) :++ companion

/**
 * A thunk frame represents a ''pending computation'' on the stack — for example, an addition operator waiting for
 * its arguments.
 */
trait ThunkFrame derives HasRegistry:
  type Data: Codec
  def accept[T: ValueType](data: Data, value: T): BoxedThunk

/**
 * Holds operations and constants for manipulating [[RuneShift!]] values.
 */
object RuneShift:
  /**
   * Selects a rune's offset from the block grid. A rune offset is a composition of three offsets (0 to 3, 0 to 7, 0 to 3)
   * and the rune's facing direction.
   *
   * @note The RuneShift type is represented as an opaque [[Int]], with all values bit-packed within. Using [[asInstanceOf]]
   *       from or to [[Int]] is safe, but this is subject to change.
   */
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
      /**
       * Gets the backing value of the rune shift as a bit-packed [[Int]]. This has no runtime cost.
       */
      @inline def value: Int = s
      /**
       * Returns the X-offset of runes from the block grid: an integer 0 to 3 (inclusive).
       */
      @inline def x = s & 0b11
      /**
       * Returns the Y-offset of runes from the block grid: an integer 0 to 7 (inclusive).
       */
      @inline def y = s >> 2 & 0b111
      /**
       * Returns the Z-offset of runes from the block grid: an integer 0 to 3 (inclusive).
       */
      @inline def z = s >> 5 & 0b11
      /**
       * Returns the facing direction of these runes.
       */
      @inline def facing = Direction.values()(s >> 7 & 0b111)
      /**
       * Updates the rune shift with new values.
       * @param x New x-offset (0 to 3). <b>Invalid offsets are undefined behavior!</b>
       * @param y New y-offset (0 to 7). <b>Invalid offsets are undefined behavior!</b>
       * @param z New z-offset (0 to 3). <b>Invalid offsets are undefined behavior!</b>
       * @param facing New facing direction (any of the 6).
       * @return A [[RuneShift!]] value with the new properties. It should be used for its value: it may or may not be [[eq]] to any other [[RuneShift!]], or even itself.
       */
      @inline def apply(x: Int = s.x, y: Int = s.y, z: Int = s.z, facing: Direction = s.facing): RuneShift = RuneShift(x, y, z, facing)
      def storage(using w: World) = w.getComponent(AbstractRuneStorage.keys(s.value))
  private def facing_impl(s: Expr[RuneShift], facing: Expr[Direction])(using q: Quotes): Expr[Unit] =
    import q.reflect.*
    Assign(s.asTerm, '{${s}(facing = ${facing})}.asTerm).asExprOf[Unit]

  /**
   * Cache of [[VoxelShape]]s for every rune offset, used for speed. Clients are expected to not mutate this array.
   */
  val shapeCache: Array[VoxelShape] = Array.ofDim[VoxelShape](768).tap: m =>
    for shift: RuneShift <- 0 until 768 do
      val middle = (shift.x / 4.0 - 0.5, shift.y / 8.0 - 0.5, shift.z / 4.0 - 0.5)
      m(shift) = VoxelShapes.cuboid(middle._1 - .25, middle._2, middle._3 - .25, middle._1 + .25, middle._2 + .0625, middle._3 + .25)
end RuneShift
export RuneShift.RuneShift

/**
 * A rune is the basic element of a cast. Runes fundamentally operate on a list of [[ThunkFrame]]s, but may also access
 * the parsing stream (e.g. [[QuoteRune]]) to store internal data.
 *
 * <b>Note for implementors:</b> Almost all runes don't need access to the parsing stream. In this case, you can use
 * [[SimpleRune]] to avoid implementing extra things.
 *
 * @see [[BoxedRune]]
 */
trait Rune derives HasRegistry:
  /**
   * Any data this rune needs from parsing to execute. For most runes, this is [[Unit]] (no information), but in some
   * cases other values may be accurate:
   *
   *  - [[QuoteRune]] stores a `List[BoxedRune]` preserved for later evaluation.
   *  - [[EndQuoteRune]] stores [[Nothing]], as parsing it alone will never succeed.
   */
  type Data: Codec

  /**
   * Determines the sprite used for this rune's surface, as the name of a texture in the block atlas.
   *
   * Mods may add custom sprites, but generally, surfaces should use one of the built-in types: the [[Rune.BASIC_TEXTURE basic texture]], [[Rune.SPELL_TEXTURE spell texture]], or [[Rune.IMPETUS_TEXTURE starter texture]].
   *
   * @return The rune's surface texture. The path `foo:bar` corresponds to `assets/foo/textures/bar.png`.
   */
  def surfaceSprite: Identifier = Rune.BASIC_TEXTURE

  /**
   * Reads the rune's information from the list of runes. For most runes, this method does nothing.
   *
   * @param rhs The list of remaining runes, not including this rune.
   * @throws RuneError Thrown when the rune is unable to read all of its arguments.
   * @return The rune's metadata and the list of remaining runes iff parsing is successful.
   */
  def read(rhs: List[(Rune, RuneRef)])(using ref: RuneRef, world: World): (Data, List[(Rune, RuneRef)])

  /**
   * Reads and executes this rune. This method usually pops then pushes frames on `frame`.
   *
   * Here's an example of a simple rune that supplies a Unit:
   *
   * {{{
   * // Pass a Unit into the top frame
   * def execute(data: Data, frame: BoxedThunk): BoxedThunk =
   *   Unit match
   *     case frame.accept(newFrame) => newFrame
   *     case _ => ???
   * }}}
   *
   * @param data Extra information returned by [[read]].
   * @param frame The current top of the thunk stack before execution.
   * @return The new top of the thunk stack.
   */
  def execute(data: Data, frame: BoxedThunk): BoxedThunk

  /**
   * Generated item for this rune to use. You need to handle [[Registrar.register registering this item]] yourself, but not doing so
   * will not incur an intrusive-holders crash unless someone else forces this field first.<hr style="border-style:hidden">
   * <b style="color: color-mix(in oklch, 15% currentcolor, red)">Caution:</b> You must not force this field before the owning rune has been added to the registry. Doing so
   * will crash the game!
   */
  lazy val item =
    val identifier = registryFor[Rune].getId(this)
    if identifier == null then
      throw IllegalStateException("Rune.item may not be referenced before the rune is registered")
    cursedRegister(identifier, Item.Settings()):
      new Item(_):
        override def useOnBlock(using context: ItemUsageContext): ActionResult =
          println("GOT HERE 2")
          val p = context.getHitPos
          val q = BlockPos.Mutable((p.x * 4).round.toInt, (p.y * 8).round.toInt, (p.z * 4).round.toInt)
          val b = BlockPos.Mutable(p.x, p.y, p.z)
          given World = context.getWorld
          if q.getX == 4 then
            q.setX(0)
            b.setX(b.getX + 1)
          if q.getY == 8 then
            q.setY(0)
            b.setY(b.getY + 1)
          if q.getZ == 4 then
            q.setZ(0)
            b.setZ(b.getZ + 1)
          val h = RuneShift(q.getX, q.getY, q.getZ, context.getSide)
          given ref: RuneRef = RuneRef(b, h)
          if Rune.this != EmptyRune && ref.isEmpty && ref.neighbors.forall(_.isEmpty) then
            ref.rune = Rune.this
            try
              println(s"Loading placed NBT")
              val nbt = Uuids.toIntArray(context.getStack.getComponents.get(DataComponentTypes.PROFILE).uuid.get)
              println(s"Loaded array: ${nbt.mkString("Array(", ", ", ")")}")
              ref.heap0 = nbt(0)
              ref.heap1 = nbt(1)
              ref.heap2 = nbt(2)
              ref.heap3 = nbt(3)
            catch case e: Exception =>
              println(s"nvm, caught a ${e} - generating it")
              given PlayerEntity = context.getPlayer
              ref.rune.fillHeap()
            println(s"Now the heap at ${ref} is ${ref.heap0} ${ref.heap1} ${ref.heap2} ${ref.heap3}")
            AbstractRuneStorage.sync(summon, h)
            println("done !")
            ActionResult.SUCCESS
          else
            println("nah")
            ActionResult.PASS

  def computeNeighbors(using r: RuneRef): Set[RuneRef] = r.adjacent
  def ignite(using r: RuneRef, ci: CallbackInfoReturnable[ActionResult])(ctx: ItemUsageContext): Unit = ()
  def fillHeap(using RuneRef, PlayerEntity, World)(): Unit = ()

/**
 * A special case of [[Rune]] that only operates on the [[ThunkFrame]] stack, not modifying parsing. Most runes fall into
 * this category. Using SimpleRune in this case provides a stronger guarantee and could assist in compiler optimization
 * in the future.
 */
trait SimpleRune extends Rune:
  override type Data = Unit
  override given Codec[Data] = summon[Codec[Unit]]
  override def read(rhs: List[(Rune, RuneRef)])(using RuneRef, World): (Data, List[(Rune, RuneRef)]) = ((), rhs)
  override def execute(remainder: Unit, frame: BoxedThunk): BoxedThunk = execute(frame)
  def execute(frame: BoxedThunk): BoxedThunk

sealed trait AnyRegistryKey:
  type T
  val key: RegistryKey[T]
  def registry: Registry[T] = Registries.REGISTRIES.get(key.getRegistry).asInstanceOf[Registry[T]]
  def value: Any = key.getValue
object AnyRegistryKey:
  def apply[T](key: RegistryKey[T]): AnyRegistryKey =
    type _T = T
    val _key = key
    new AnyRegistryKey:
      override type T = _T
      override val key: RegistryKey[T] = _key
  def apply[T](key: Identifier)(using RegistryKey[? <: Registry[T]]): AnyRegistryKey = apply(key: RegistryKey[T])
  given Codec[AnyRegistryKey] = Identifier.CODEC.dispatch("registry", _.key.getRegistry, RegistryKey.createCodec(_).fieldOf("value").xmap(AnyRegistryKey(_), _.key.asInstanceOf[RegistryKey[Object]]))

given [T]: RegistryKey[? <: Registry[T]] = Registries.REGISTRIES.getKey.asInstanceOf[RegistryKey[? <: Registry[T]]]

@register("registry_key")
given ValueType[AnyRegistryKey]:
  override def eq[U: ValueType as v](x: AnyRegistryKey, y: U): Boolean = v.cast[AnyRegistryKey](y).contains(x)
  override def show(x: AnyRegistryKey): Text = Text.translatable(x.key.toTranslationKey(x.key.getRegistry.toShortTranslationKey)).withColor(0x2dccfc)

/**
 * Holder object for [[Rune! Rune]] registries and common values of [[Rune.surfaceSprite]].
 */
object Rune:
  /**
   * Texture used for runes that do not mutate the world.
   */
  final val BASIC_TEXTURE = Identifier.of("mica", "block/basic_rune")
  /**
   * Texture used for runes that create [[SideEffect! side effects]] mutating the world.
   */
  final val SPELL_TEXTURE = Identifier.of("mica", "block/spell_rune")
  /**
   * Texture used for special runes that fire off a rune-chain.
   */
  final val IMPETUS_TEXTURE = Identifier.of("mica", "block/start_rune")

case class RuneRef(pos: BlockPos, shift: RuneShift):
  def floatPos = Vec3d(pos.getX + shift.x / 4.0, pos.getY + shift.y / 8.0, pos.getZ + shift.z / 4.0)

  def offset(dir: Direction): RuneRef =
    dir match
      case Direction.DOWN =>
        if shift.y == 0 then RuneRef(pos.down, shift (y = 7))
        else RuneRef(pos, shift (y = shift.y - 1))

      case Direction.UP =>
        if shift.y == 7 then RuneRef(pos.up, shift (y = 0))
        else RuneRef(pos, shift (y = shift.y + 1))

      case Direction.NORTH =>
        if shift.z == 0 then RuneRef(pos.north, shift (z = 3))
        else RuneRef(pos, shift (z = shift.z - 1))

      case Direction.SOUTH =>
        if shift.z == 3 then RuneRef(pos.south, shift (z = 0))
        else RuneRef(pos, shift (z = shift.z + 1))

      case Direction.WEST =>
        if shift.x == 0 then RuneRef(pos.west, shift (x = 3))
        else RuneRef(pos, shift (x = shift.x - 1))

      case Direction.EAST =>
        if shift.x == 3 then RuneRef(pos.east, shift (x = 0))
        else RuneRef(pos, shift (x = shift.x + 1))

  def rune(using World): Rune = shift.storage(pos)
  def rune_=(r: Rune)(using World): Unit =
    if r eq EmptyRune then
      heap0 = 0
      heap1 = 0
      heap2 = 0
      heap3 = 0
    shift.storage(pos) = r
  def isEmpty(using World): Boolean = rune == null || rune == EmptyRune
  def heap0(using World): Int = shift.storage.heap0.get(pos.asLong)
  def heap0_=(r: Int)(using World): Unit = shift.storage.heap0.put(pos.asLong, r)
  def heap1(using World): Int = shift.storage.heap1.get(pos.asLong)
  def heap1_=(r: Int)(using World): Unit = shift.storage.heap1.put(pos.asLong, r)
  def heap2(using World): Int = shift.storage.heap2.get(pos.asLong)
  def heap2_=(r: Int)(using World): Unit = shift.storage.heap2.put(pos.asLong, r)
  def heap3(using World): Int = shift.storage.heap3.get(pos.asLong)
  def heap3_=(r: Int)(using World): Unit = shift.storage.heap3.put(pos.asLong, r)

  def north: RuneRef = offset(Direction.NORTH)
  def south: RuneRef = offset(Direction.SOUTH)
  def east: RuneRef = offset(Direction.EAST)
  def west: RuneRef = offset(Direction.WEST)

  lazy val neighbors: Set[RuneRef] = Set(
    north,      east,       south,      west,
    north.east, north.west, south.east, south.west
  )
  lazy val adjacent: Set[RuneRef] = Set(
    north.north,      east.east,       south.south,      west.west,
    north.north.east, east.east.north, south.south.east, west.west.north,
    north.north.west, east.east.south, south.south.west, west.west.south,
  )

private[mica] trait AbstractRuneStorage extends Component, AutoSyncedComponent:
  type Concrete <: AbstractRuneStorage
  val world: World
  val contents: Long2ObjectMap[Rune]
  val heap0: Long2IntMap
  val heap1: Long2IntMap
  val heap2: Long2IntMap
  val heap3: Long2IntMap
  given ComponentKey[Concrete] = compiletime.deferred
  def apply(pos: BlockPos): Rune = contents.get(Long.box(pos.asLong))
  def update(pos: BlockPos, rune: Rune): Unit = rune match
    // deprecated and slow, but Long <: Object so overload resolution fails
    case EmptyRune => contents.remove(Long.box(pos.asLong))
    case _ => contents.put(pos.asLong, rune)

def packLong(h: Int, l: Int): Long = (h.toLong << 32) | (l & 0xFFFFFFFFL)
def unpackLong(n: Long): (Int, Int) = ((n >>> 32).toInt, n.toInt)

object AbstractRuneStorage:
  private[mica] val keys: Array[ComponentKey[? <: AbstractRuneStorage]] = Array.fill(768)(null)
  def get(world: World, shift: RuneShift): AbstractRuneStorage = keys(shift.value).get(world)
  def sync(world: World, shift: RuneShift): Unit = keys(shift.value).sync(world)
trait ValueType[T: Codec]:
  def eq[U: ValueType](x: T, y: U): Boolean
  def cast[R: ClassTag](x: T): Option[R] =
    x match
      case x: R => Some(x)
      case _ => None
  def show(x: T): Text
  def codec: Codec[T] = summon
  def present(x: T): Boolean = true
  def typeof(x: T): ValueType[?] = this
trait UntypedValue
object ValueType:
  given HasRegistry[ValueType[?]] = HasRegistry.derived