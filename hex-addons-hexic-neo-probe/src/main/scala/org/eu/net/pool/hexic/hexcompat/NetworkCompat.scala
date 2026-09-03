package org.eu.net.pool.hexic.hexcompat

import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.{FriendlyByteBuf, RegistryFriendlyByteBuf}
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import org.slf4j.LoggerFactory

import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

object NetworkCompat:
  private val log = LoggerFactory.getLogger("hexic")
  private val serverReceivers = ConcurrentHashMap[String, (ServerPlayer, FriendlyByteBuf) => Unit]()
  private val maxChannelLength = 64
  private val maxPayloadBytes = 32767

  final case class LegacyPayload(channel: String, data: Array[Byte]) extends CustomPacketPayload:
    override def `type`(): CustomPacketPayload.Type[? <: CustomPacketPayload] = LegacyPayload.TYPE

    override def equals(other: Any): Boolean =
      other match
        case that: LegacyPayload => channel == that.channel && Arrays.equals(data, that.data)
        case _ => false

    override def hashCode(): Int =
      31 * channel.hashCode + Arrays.hashCode(data)

  object LegacyPayload:
    val TYPE: CustomPacketPayload.Type[LegacyPayload] =
      CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("hexic", "legacy_channel"))

    val STREAM_CODEC: StreamCodec[RegistryFriendlyByteBuf, LegacyPayload] =
      new StreamCodec[RegistryFriendlyByteBuf, LegacyPayload]:
        override def encode(buf: RegistryFriendlyByteBuf, payload: LegacyPayload): Unit =
          buf.writeUtf(payload.channel, maxChannelLength)
          buf.writeByteArray(payload.data)

        override def decode(buf: RegistryFriendlyByteBuf): LegacyPayload =
          LegacyPayload(buf.readUtf(maxChannelLength), buf.readByteArray(maxPayloadBytes))

  def buffer(): FriendlyByteBuf =
    FriendlyByteBuf(Unpooled.buffer())

  def registerPayloadHandlers(event: RegisterPayloadHandlersEvent): Unit =
    event
      .registrar("1")
      .playBidirectional(LegacyPayload.TYPE, LegacyPayload.STREAM_CODEC, handlePayload)

  def sendString(player: ServerPlayer, channel: String, value: String): Unit =
    val buf = buffer()
    buf.writeUtf(value)
    sendBuffer(player, channel, buf)

  def sendComponent(player: ServerPlayer, keyId: String, tag: CompoundTag): Unit =
    val buf = buffer()
    buf.writeUtf(keyId)
    buf.writeNbt(tag)
    sendBuffer(player, "component", buf)

  def sendToServer(channel: String, buf: FriendlyByteBuf): Unit =
    PacketDistributor.sendToServer(LegacyPayload(channel, readableBytes(buf)))

  def registerServerReceiver(channel: String)(handler: (ServerPlayer, FriendlyByteBuf) => Unit): Unit =
    serverReceivers.put(channel, handler)
    ()

  def dispatchServerReceiver(channel: String, player: ServerPlayer, buf: FriendlyByteBuf): Either[String, Unit] =
    try
      Option(serverReceivers.get(channel)) match
        case Some(handler) =>
          handler(player, buf)
          Right(())
        case None =>
          Left(s"missing server receiver=$channel")
    catch
      case t: Throwable =>
        Left(s"${t.getClass.getName}: ${Option(t.getMessage).getOrElse("")}")

  private def sendBuffer(player: ServerPlayer, channel: String, buf: FriendlyByteBuf): Unit =
    PacketDistributor.sendToPlayer(player, LegacyPayload(channel, readableBytes(buf)))

  private def handlePayload(payload: LegacyPayload, context: IPayloadContext): Unit =
    context.enqueueWork(new Runnable:
      override def run(): Unit =
        try
          if context.flow() == PacketFlow.SERVERBOUND then
            context.player() match
              case player: ServerPlayer =>
                Option(serverReceivers.get(payload.channel)) match
                  case Some(handler) => handler(player, toBuffer(payload))
                  case None => log.warn("No Hexic server receiver registered for channel {}", payload.channel)
              case other =>
                log.warn("Ignoring Hexic serverbound payload {} from non-server player {}", payload.channel, other)
          else if context.flow() == PacketFlow.CLIENTBOUND then
            handleClientPayload(payload)
        catch
          case t: Throwable =>
            log.error("Failed handling Hexic legacy payload {}", payload.channel, t)
    )
    ()

  private def handleClientPayload(payload: LegacyPayload): Unit =
    payload.channel match
      case "msg" =>
        val buf = toBuffer(payload)
        HexicClientBridge.handleMessage(buf.readUtf())
      case "component" =>
        val buf = toBuffer(payload)
        HexicClientBridge.handleComponent(buf.readUtf(), buf.readNbt())
      case other =>
        log.warn("No Hexic client receiver registered for channel {}", other)

  private def readableBytes(buf: FriendlyByteBuf): Array[Byte] =
    val out = Array.ofDim[Byte](buf.readableBytes())
    buf.getBytes(buf.readerIndex(), out)
    out

  private def toBuffer(payload: LegacyPayload): FriendlyByteBuf =
    FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data))

  private object HexicClientBridge:
    private val helperClass = "org.eu.net.pool.hexic.hexcompat.HexicClientNetworkCompat"

    def handleMessage(message: String): Unit =
      val klass = Class.forName(helperClass)
      val method = klass.getMethod("handleMessage", classOf[String])
      method.invoke(null, message)

    def handleComponent(keyId: String, tag: CompoundTag): Unit =
      val klass = Class.forName(helperClass)
      val method = klass.getMethod("handleComponent", classOf[String], classOf[CompoundTag])
      method.invoke(null, keyId, tag)

  def probe(): Either[String, String] =
    val expected = LegacyPayload("message", Array[Byte](0, 4, 8, 15, 16, 23, 42))
    val encoded = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
    LegacyPayload.STREAM_CODEC.encode(encoded, expected)
    val decoded = LegacyPayload.STREAM_CODEC.decode(encoded)
    val missing = Seq("murmur", "message").filterNot(serverReceivers.containsKey)
    if decoded != expected then
      Left(s"codec mismatch channel=${decoded.channel} bytes=${decoded.data.length}")
    else if missing.nonEmpty then
      Left(s"missing server receivers=${missing.mkString(",")}")
    else
      Right(s"codec=PASS server_receivers=${serverReceivers.keySet().asScala.toSeq.sorted.mkString(",")}")

  def probeClientboundSend(player: ServerPlayer): Either[String, String] =
    try
      if player == null then
        Left("missing player")
      else if player.connection == null then
        Left("missing player connection")
      else
        sendString(player, "msg", "hexic probe clientbound msg")
        val tag = CompoundTag()
        tag.putInt("lineCount", 1)
        tag.putString("line0", "hexic probe clientbound reveal")
        sendComponent(player, "reveal", tag)
        Right("msg=PASS component=PASS")
    catch
      case t: Throwable =>
        Left(s"${t.getClass.getName}: ${Option(t.getMessage).getOrElse("")}")

  def probeClientboundDispatchNoConnection(): Either[String, String] =
    try
      val messageBuf = buffer()
      messageBuf.writeUtf("hexic probe clientbound dispatch msg")
      val decodedMessage = roundTripClientboundPayload(LegacyPayload("msg", readableBytes(messageBuf)))
      handleClientPayload(decodedMessage)

      val componentBuf = buffer()
      componentBuf.writeUtf("reveal")
      val tag = CompoundTag()
      tag.putInt("lineCount", 1)
      tag.putString("line0", "hexic probe clientbound dispatch reveal")
      componentBuf.writeNbt(tag)
      val decodedComponent = roundTripClientboundPayload(LegacyPayload("component", readableBytes(componentBuf)))
      handleClientPayload(decodedComponent)

      Right("msg_dispatch=PASS component_dispatch=PASS")
    catch
      case t: Throwable =>
        Left(s"${t.getClass.getName}: ${Option(t.getMessage).getOrElse("")}")

  private def roundTripClientboundPayload(payload: LegacyPayload): LegacyPayload =
    val encoded = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
    LegacyPayload.STREAM_CODEC.encode(encoded, payload)
    LegacyPayload.STREAM_CODEC.decode(encoded)
