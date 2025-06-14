package io.github.remmerw.loki.grid

import io.ktor.utils.io.core.remaining
import kotlinx.io.Buffer

class Grid(extendedMessagesHandler: List<ExtendedMessageHandler>) {
    private val handlers: Map<Byte, MessageHandler>
    private val idMap: Map<Type, Byte>

    init {
        val handlers: MutableMap<Byte, MessageHandler> = mutableMapOf()
        handlers[CHOKE_ID] = ChokeHandler()
        handlers[UNCHOKE_ID] = UnchokeHandler()
        handlers[INTERESTED_ID] = InterestedHandler()
        handlers[NOT_INTERESTED_ID] = NotInterestedHandler()
        handlers[HAVE_ID] = HaveHandler()
        handlers[BITFIELD_ID] = BitfieldHandler()
        handlers[REQUEST_ID] = RequestHandler()
        handlers[PIECE_ID] = PieceHandler()
        handlers[CANCEL_ID] = CancelHandler()
        handlers[PORT_ID] = PortHandler()
        handlers[EXTENDED_MESSAGE_ID] =
            ExtendedProtocol(extendedMessagesHandler)

        val idMap: MutableMap<Type, Byte> = mutableMapOf()

        handlers.forEach { (messageId: Byte, handler: MessageHandler) ->
            if (handler.supportedTypes().isEmpty()) {
                throw RuntimeException("No supported types declared in handler: $handler")
            }
            handler.supportedTypes().forEach { messageType ->
                if (idMap.containsKey(messageType)) {
                    throw RuntimeException("Duplicate handler for message type: $messageType")
                }
                idMap[messageType] = messageId
            }
        }

        this.handlers = handlers
        this.idMap = idMap
    }


    fun decode(peer: Peer, buffer: Buffer): Message {
        if (buffer.remaining.toInt() == 0) {
            return keepAlive() // keep has length 0
        }
        val messageType = buffer.readByte()
        val handler = checkNotNull(handlers[messageType])
        return handler.doDecode(peer, buffer)
    }


    fun encode(peer: Peer, message: Message, buffer: Buffer) {

        val messageId = idMap[message.type]

        if (message is Handshake) {
            writeHandshake(message, buffer)
            return
        }
        if (message is KeepAlive) {
            writeKeepAlive(buffer)
            return
        }

        requireNotNull(messageId) { "Unknown message type: $messageId" }

        return encode(checkNotNull(handlers[messageId]), peer, message, buffer)
    }

    fun encode(messageHandler: MessageHandler, peer: Peer, message: Message, buffer: Buffer) {

        val bufferMsg = Buffer()
        messageHandler.doEncode(peer, message, bufferMsg)
        val payloadLength = bufferMsg.size
        if (payloadLength < 0) {
            throw RuntimeException("Unexpected payload length: $payloadLength")
        }
        val size = (payloadLength + MESSAGE_TYPE_SIZE).toInt()
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
        buffer.transferFrom(bufferMsg)
    }


    // keep-alive: <len=0000>
    private fun writeKeepAlive(buffer: Buffer) {
        buffer.write(KEEPALIVE)
    }

    // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
    private fun writeHandshake(
        handshake: Handshake,
        buffer: Buffer
    ) {
        val data = handshake.name.encodeToByteArray()
        buffer.writeByte(data.size.toByte())
        buffer.write(data)
        buffer.write(handshake.reserved)
        buffer.write(handshake.torrentId.bytes)
        buffer.write(handshake.peerId)
    }

}


private val keepAlive = KeepAlive()

internal fun keepAlive(): KeepAlive {
    return keepAlive
}

private val choke = Choke()

internal fun choke(): Choke {
    return choke
}


private val interested = Interested()

internal fun interested(): Interested {
    return interested
}


private val notInterested = NotInterested()

internal fun notInterested(): NotInterested {
    return notInterested
}


private val unchoke = Unchoke()

internal fun unchoke(): Unchoke {
    return unchoke
}

internal fun request(pieceIndex: Int): UtMetadata {
    return UtMetadata(MetaType.REQUEST, pieceIndex)
}

internal fun data(pieceIndex: Int, totalSize: Int, data: ByteArray): UtMetadata {
    return UtMetadata(MetaType.DATA, pieceIndex, totalSize, data)
}

internal fun reject(pieceIndex: Int): UtMetadata {
    return UtMetadata(MetaType.REJECT, pieceIndex)
}


const val UPPER_RESERVED_BOUND = 8 * 8 - 1
internal const val HANDSHAKE_RESERVED_LENGTH = 8
internal const val TORRENT_ID_LENGTH: Int = 20
internal const val SHA1_HASH_LENGTH: Int = 20

internal const val CHOKE_ID: Byte = 0x0

internal const val UNCHOKE_ID: Byte = 0x1

internal const val INTERESTED_ID: Byte = 0x2

internal const val NOT_INTERESTED_ID: Byte = 0x3

internal const val HAVE_ID: Byte = 0x4

internal const val BITFIELD_ID: Byte = 0x5

internal const val REQUEST_ID: Byte = 0x6

internal const val PIECE_ID: Byte = 0x7

internal const val CANCEL_ID: Byte = 0x8

internal const val PORT_ID: Byte = 9

internal const val EXTENDED_MESSAGE_ID: Byte = 20

internal const val MESSAGE_TYPE_SIZE: Int = 1

internal const val EXTENDED_HANDSHAKE_TYPE_ID: Byte = 0

internal const val PROTOCOL_NAME = "BitTorrent protocol"

internal val KEEPALIVE = byteArrayOf(0, 0, 0, 0)

