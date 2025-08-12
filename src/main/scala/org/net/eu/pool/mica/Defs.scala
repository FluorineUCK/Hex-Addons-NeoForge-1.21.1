package org.net.eu.pool.mica

import com.mojang.serialization.Codec
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.block.{AbstractBlock, Block}
import net.minecraft.item.{Item, ItemUsageContext}
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.util.{ActionResult, Identifier}
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
// ifversion(>=2100, <<
import net.minecraft.storage.{ReadView, WriteView}
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent
import org.ladysnake.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry
// >>, <<
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.component.{Component, ComponentKey, ComponentRegistry}
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry
// >>)

trait Registrar[T]:
  val value: T
  private[mica] def register(): Unit

// probably the least cursed part of this code
/**
 * Cross-version helper for registering items.
 *
 * @tparam T Concrete item type for generics purposes.
 * @param identifier  Final identifier of item - passed to the settings in >=1.21.2.
 * @param settings    Item settings - these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param itemFactory Produces the item from its settings. This **must** use the provided instance!
 * @return Object containing the final item, as well as a method that registers it.
 */
def cursedRegister[T <: Item](identifier: Identifier, settings: Item.Settings)(itemFactory: Item.Settings => T): Registrar[T] =
  assert(identifier != null)
  val key = RegistryKey.of(RegistryKeys.ITEM, identifier)
  lazy val item = itemFactory(settings /*ifversion(>=2102,<<*/.registryKey(key) /*>>)*/)
  new Registrar[T]:
    override val value: T = item

    override def register(): Unit =
      Registry.register(Registries.ITEM, key, item)

/**
 * Cross-version helper for registering blocks.
 *
 * @tparam T Concrete block type for generics purposes.
 * @param identifier   Final identifier of block - passed to the settings in >=1.21.2.
 * @param settings     Block settings - these must be known ahead-of-time since 1.21.2 makes them hold the registry key.
 * @param blockFactory Produces the block from its settings. This **must** use the provided instance! This function is also passed its return value for e.g. block entities.
 * @return Object containing the final block, as well as a method that registers it.
 */
def cursedRegister[T <: Block](identifier: Identifier, settings: AbstractBlock.Settings)(blockFactory: (=> AbstractBlock.Settings => T) => AbstractBlock.Settings => T): Registrar[T] =
  val key = RegistryKey.of(RegistryKeys.BLOCK, identifier)
  lazy val fixed: AbstractBlock.Settings => T = blockFactory(fixed)
  lazy val block = fixed(settings /*ifversion(>=2100,<<*/.registryKey(key) /*>>)*/)
  new Registrar[T]:
    override val value: T = block

    override def register(): Unit =
      Registry.register(Registries.BLOCK, key, block)

trait ThunkFrame derives HasRegistry:
  given Codec[this.type] = compiletime.deferred
  val accept: PartialFunction[Abstract[ValueType], ThunkFrame]

/**
 * A rune is an element of a cast. Runes are read left-to-right and form execution frames on the thunk stack.
 */
trait Rune derives HasRegistry:
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

  lazy val item =
    val identifier = registryFor[Rune].getId(this)
    if identifier == null then
      throw IllegalStateException("Rune.item may not be referenced before the rune is registered")
    cursedRegister(identifier, Item.Settings()):
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

private[mica] trait AbstractRuneStorage extends Component, AutoSyncedComponent:
  type Concrete <: ConcreteRuneStorage
  val world: World
  val contents: Long2ObjectMap[Rune]
  given ComponentKey[Concrete] = compiletime.deferred
  def apply(pos: BlockPos): Rune = contents.get(Long.box(pos.asLong))
  def update(pos: BlockPos, rune: Rune): Unit = rune match
    // deprecated and slow, but Long <: Object so overload resolution fails
    case EmptyRune => contents.remove(Long.box(pos.asLong))
    case _ => contents.put(pos.asLong, rune)

object AbstractRuneStorage:
  private[mica] val keys: Array[ComponentKey[? <: AbstractRuneStorage]] = Array.fill(768)(null)
  def get(world: World, shift: RuneShift): AbstractRuneStorage = keys(shift.value).get(world)
  def sync(world: World, shift: RuneShift): Unit = keys(shift.value).sync(world)

@hasRegistry
trait ValueType[T: Codec]:
  def eq[U: ValueType](x: T, y: U): Boolean
object ValueType