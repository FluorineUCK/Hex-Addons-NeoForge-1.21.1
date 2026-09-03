package org.eu.net.pool
package phlib

import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.IotaType
import com.mojang.serialization.{Codec, MapCodec}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}

import java.util
import java.util.List => JavaList
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.given
import at.petrak.hexcasting.api.utils.HexUtils

case class MapIota(map: ListMap[NbtCompound, NbtCompound] = ListMap.empty)(using val world: ServerWorld = null) extends Iota(() => MapIota):
  def get(key: Iota): Option[Iota] = map.get(serialize(key)).map(deserialize(_, world))
  def apply(key: Iota): Iota = get(key) getOrElse NullIota()
  def -(keys: Iota*): MapIota = MapIota(map -- (keys map serialize))
  def --(other: MapIota): MapIota = MapIota(map -- other.map.keys)
  def +(pairs: (Iota, Iota)*): MapIota = MapIota(map ++ pairs.map(p => (serialize(p._1), serialize(p._2))))
  def ++(other: MapIota): MapIota = MapIota(map ++ other.map)
  def update(f: map.type => ListMap[NbtCompound, NbtCompound]): MapIota = MapIota(f(map))
  def head: (Iota, Iota) = (deserialize(map.head._1, world), deserialize(map.head._2, world))
  def tail: MapIota = MapIota(map.tail)
  def init: MapIota = MapIota(map.init)
  def last: (Iota, Iota) = (deserialize(map.last._1, world), deserialize(map.last._2, world))
  def headOption: Option[(Iota, Iota)] = map.headOption.map(e => (deserialize(e._1, world), deserialize(e._2, world)))
  def lastOption: Option[(Iota, Iota)] = map.lastOption.map(e => (deserialize(e._1, world), deserialize(e._2, world)))
  def &(other: MapIota): MapIota = MapIota(map.filter(_._1 pipe other.map.contains))
  def ^(other: MapIota): MapIota =
    if isDev then println(s"XOR: $map, ${other.map}")
    val out = (map /: other.map): (map, e) =>
      if isDev then println(s"• $map ← $e")
      e match
        case (k@map(v1), v2) =>
          if isDev then println(s"  [$k=$v1, $v2] ${map-k}")
          map - k
        case (k, v2) =>
          if isDev then println(s"  {$k, $v2} ${map+e}")
          map + e
    if isDev then println(s"→→→ $out")
    val iota = MapIota(out)
    if isDev then println(s"    $iota")
    iota
  def toMap: ListMap[Iota, Iota] = ListMap.from(map.map(p => (deserialize(p._1, world), deserialize(p._2, world))))
  def toList: java.util.List[Iota] = toMap.flatMap(p => Seq(p._1, p._2)).toSeq
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  def toEntryList: NbtList = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) tap l.add)
  def toEntryTags: JavaList[NbtCompound] =
    toEntryList.asScala.collect { case c: NbtCompound => c.copy() }.asJava
  override def size = map.toSeq.map(_.size + _.size - 1).sum + 1
  override def subIotas(): java.lang.Iterable[Iota] = toList
  override def display(): Text = MapIota.displayEntries(toEntryList)
  def asJavaMap: util.LinkedHashMap[Iota, Iota] =
    val ret = util.LinkedHashMap[Iota, Iota]()
    for k -> v <- toMap do
      ret(k) = v
    ret
object MapIota extends IotaType[MapIota]:
  private val ENTRY_LIST_CODEC: Codec[JavaList[NbtCompound]] = CompoundTag.CODEC.listOf()
  private val ENTRY_LIST_STREAM_CODEC: StreamCodec[RegistryFriendlyByteBuf, JavaList[NbtCompound]] =
    ByteBufCodecs.fromCodecWithRegistries(ENTRY_LIST_CODEC)

  override def color(): Int = 0xb0641c
  override def codec(): MapCodec[MapIota] =
    ENTRY_LIST_CODEC.xmap[MapIota](fromEntryTags, _.toEntryTags).fieldOf("entries")
  override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, MapIota] =
    ENTRY_LIST_STREAM_CODEC.map(fromEntryTags, _.toEntryTags)

  def deserialize(data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    val o = HexUtils.downcast(_, NbtCompound.TYPE)
    new MapIota(ListMap.from(l.asScala.map(o).map(c => (o(c("k")), o(c("v"))))))(using world)
  def fromEntryTags(data: JavaList[NbtCompound]): MapIota =
    new MapIota(ListMap.from(data.asScala.map: c =>
      val k = HexUtils.downcast(c("k"), NbtCompound.TYPE)
      val v = HexUtils.downcast(c("v"), NbtCompound.TYPE)
      k -> v
    ))(using null)
  def displayEntries(data: NbtElement): Text =
    val items = HexUtils.downcast(data, NbtList.TYPE)
    val output: MutableText = "["
    output.styled(_.withColor(color()))
    if items.nonEmpty then
      def castToCompound = HexUtils.downcast(_, NbtCompound.TYPE)
      val itemPair = items.map(castToCompound).iterator
      def writePair(pair: NbtCompound) =
        output.append(try getDisplay(pair("k") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
        output.append(" → ")
        output.append(try getDisplay(pair("v") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
      writePair(itemPair.next())
      while itemPair.hasNext do
        output.append(", ")
        writePair(itemPair.next())
    else
      output.append("→")
    output.append("]")
    output
  def fromMap(map: Map[Iota, Iota])(using ServerWorld): MapIota = MapIota(ListMap.from(map.map(e => serialize(e._1) -> serialize(e._2))))
  def fromJavaMap(map: java.util.Map[Iota, Iota])(using ServerWorld): MapIota = fromMap(ListMap.from(map.entrySet.map(p => p.getKey -> p.getValue)))
val mapArithmetic = 
  import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic.*
  arith("map",
    ADD -> ((a: MapIota, b: MapIota) => a ++ b),
    SUB -> ((a: MapIota, b: MapIota) => a -- b),
    ABS -> ((a: MapIota) => DoubleIota(a.map.size)),
    INDEX -> ((a: MapIota, k: Iota) => a(k)),
    UNAPPEND -> ((a: MapIota) => a.lastOption.fold(Seq(a, NullIota(), NullIota()))(p => Seq(a.init, p._1, p._2))),
    INDEX_OF -> ((a: MapIota, v: Iota) =>
      val c = serialize(v)
      a.map.find(_._2 == c).fold(NullIota())(p => deserialize(p._1, a.world))),
    REMOVE -> ((a: MapIota, k: Iota) => a - k),
    REPLACE -> ((a: MapIota, k: Iota, v: Iota) => a + (k -> v)),
    UNCONS -> ((a: MapIota) => a.headOption.fold(Seq(a, NullIota(), NullIota()))(p => Seq(a.tail, p._1, p._2))),
    AND -> ((a: MapIota, b: MapIota) => MapIota(a.map.collect { case (k@b.map(v), _) => k -> v })(using a.world)),
    OR -> ((a: MapIota, b: MapIota) => a ++ b ++ a),
    XOR -> ((a: MapIota, b: MapIota) => a ^ b),
    GREATER -> ((a: MapIota, b: MapIota) => b.map.forall(a.map.toSet) && a.map != b.map),
    LESS -> ((a: MapIota, b: MapIota) => a.map.forall(b.map.toSet) && a.map != b.map),
    GREATER_EQ -> ((a: MapIota, b: MapIota) => b.map.forall(a.map.toSet)),
    LESS_EQ -> ((a: MapIota, b: MapIota) => a.map.forall(b.map.toSet)),
  )
