package io.github.remmerw.loki.data

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readShort
import kotlinx.io.Buffer

class Messages(extendedMessagesHandler: List<ExtendedMessageHandler>) {

    private val extended = ExtendedProtocol(extendedMessagesHandler)

    suspend fun decode(peer: InetSocketAddress, channel: ByteReadChannel, length: Int): Message {

        val messageType = channel.readByte()
        var size = length - Byte.SIZE_BYTES

        return when (messageType) {
            PIECE_ID -> {
                val pieceIndex = channel.readInt()
                size = size - Int.SIZE_BYTES
                val blockOffset = channel.readInt()
                size = size - Int.SIZE_BYTES
                val data = ByteArray(size) // todo this can be optimized maybe
                channel.readFully(data)
                return Piece(pieceIndex, blockOffset, data)
            }

            HAVE_ID -> {
                val pieceIndex = channel.readInt()
                return Have(pieceIndex)
            }

            REQUEST_ID -> {
                val pieceIndex = channel.readInt()
                val blockOffset = channel.readInt()
                val blockLength = channel.readInt()

                return Request(pieceIndex, blockOffset, blockLength)
            }

            BITFIELD_ID -> {
                val data = ByteArray(size)
                channel.readFully(data)
                return Bitfield(data)
            }

            CANCEL_ID -> {
                val pieceIndex = channel.readInt()
                val blockOffset = channel.readInt()
                val blockLength = channel.readInt()
                return Cancel(pieceIndex, blockOffset, blockLength)
            }

            CHOKE_ID -> {
                return choke()
            }

            UNCHOKE_ID -> {
                return unchoke()
            }

            INTERESTED_ID -> {
                return interested()
            }

            NOT_INTERESTED_ID -> {
                return notInterested()
            }

            PORT_ID -> {
                val port = channel.readShort().toInt() and 0x0000FFFF
                return Port(port)
            }

            EXTENDED_MESSAGE_ID -> {
                val buffer = channel.readBuffer(size)
                require(size == buffer.size.toInt()) { "Invalid number of data received" }
                extended.doDecode(peer, buffer)
            }

            else -> {
                throw Exception("not supported message type $messageType")
            }
        }
    }


    fun encode(peer: InetSocketAddress, message: ExtendedMessage, buffer: Buffer) {
        extended.doEncode(peer, message, buffer)
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

internal const val EXTENDED_HANDSHAKE_TYPE_ID: Byte = 0

internal const val PROTOCOL_NAME = "BitTorrent protocol"

internal val KEEPALIVE = byteArrayOf(0, 0, 0, 0)

