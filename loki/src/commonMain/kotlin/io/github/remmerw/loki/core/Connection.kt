package io.github.remmerw.loki.core

import io.github.remmerw.loki.BLOCK_SIZE
import io.github.remmerw.loki.data.BITFIELD_ID
import io.github.remmerw.loki.data.Bitfield
import io.github.remmerw.loki.data.CANCEL_ID
import io.github.remmerw.loki.data.CHOKE_ID
import io.github.remmerw.loki.data.Cancel
import io.github.remmerw.loki.data.Choke
import io.github.remmerw.loki.data.EXTENDED_MESSAGE_ID
import io.github.remmerw.loki.data.ExtendedMessage
import io.github.remmerw.loki.data.ExtendedProtocol
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.HAVE_ID
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.Have
import io.github.remmerw.loki.data.INTERESTED_ID
import io.github.remmerw.loki.data.Interested
import io.github.remmerw.loki.data.KeepAlive
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.NOT_INTERESTED_ID
import io.github.remmerw.loki.data.NotInterested
import io.github.remmerw.loki.data.PIECE_ID
import io.github.remmerw.loki.data.PORT_ID
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Port
import io.github.remmerw.loki.data.REQUEST_ID
import io.github.remmerw.loki.data.Request
import io.github.remmerw.loki.data.SHA1_HASH_LENGTH
import io.github.remmerw.loki.data.TORRENT_ID_LENGTH
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.data.UNCHOKE_ID
import io.github.remmerw.loki.data.Unchoke
import io.github.remmerw.loki.data.choke
import io.github.remmerw.loki.data.interested
import io.github.remmerw.loki.data.keepAlive
import io.github.remmerw.loki.data.notInterested
import io.github.remmerw.loki.data.unchoke
import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readShort
import io.ktor.utils.io.writeBuffer
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Connection internal constructor(
    private val address: InetSocketAddress,
    private val dataStorage: DataStorage,
    private val worker: Worker,
    private val socket: Socket,
    private val extendedProtocol: ExtendedProtocol
) : ConnectionWorker(worker) {
    @Volatile
    var lastActive: ValueTimeMark = TimeSource.Monotonic.markNow()

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)
    private val receiveChannel = socket.openReadChannel()
    private val sendChannel = socket.openWriteChannel(autoFlush = false)
    private var writeBlock: ByteArray? = null


    fun writeBlock(): ByteArray {
        if (writeBlock == null) {
            writeBlock = ByteArray(BLOCK_SIZE)
        }
        return writeBlock!!
    }


    fun address(): InetSocketAddress {
        return address
    }

    suspend fun reading() {
        lastActive = TimeSource.Monotonic.markNow()

        while (!isClosed) {
            try {
                val length = receiveChannel.readInt()
                require(length >= 0) { "Invalid read token received" }

                if (length == 0) {
                    worker.consume(this, keepAlive()) // keep has length 0
                } else {
                    val message = decode(receiveChannel, length)
                    worker.consume(this, message)
                }
                yield()
            } catch (throwable: Throwable) {
                println("Connection.reading " + throwable.message)
                close()
                break
            }
        }
    }

    suspend fun receiveHandshake(): Handshake? {
        try {
            val sizeName = receiveChannel.readByte()
            require(sizeName.toInt() > 0) { "Invalid size name received" }
            val name = receiveChannel.readByteArray(sizeName.toInt())
            require(sizeName.toInt() == name.size) { "Invalid size name received" }
            val reserved = receiveChannel.readByteArray(HANDSHAKE_RESERVED_LENGTH)
            require(HANDSHAKE_RESERVED_LENGTH == reserved.size) { "Invalid reserved received" }
            val infoHash = receiveChannel.readByteArray(TORRENT_ID_LENGTH)
            require(TORRENT_ID_LENGTH == infoHash.size) { "Invalid infoHash received" }
            val peerId = receiveChannel.readByteArray(SHA1_HASH_LENGTH)
            require(SHA1_HASH_LENGTH == peerId.size) { "Invalid peerId received" }

            return Handshake(
                name.decodeToString(),
                reserved, TorrentId(infoHash), peerId
            )
        } catch (_: Throwable) {
            close()
        }
        return null
    }


    @OptIn(ExperimentalAtomicApi::class)
    val isClosed: Boolean
        get() = closed.load()

    suspend fun posting() {
        val readBlock = ByteArray(BLOCK_SIZE)
        while (!isClosed) {
            try {
                val send = worker.produce(this)
                if (send != null) {
                    posting(send, readBlock)
                }
                yield()
            } catch (throwable: Throwable) {
                debug("Connection.posting  " + throwable.message)
                break
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun posting(message: Message, readBlock: ByteArray) { // todo make private
        lastActive = TimeSource.Monotonic.markNow()

        // only one coroutine should enter this section, because of Piece data

        try {
            when (message) {
                is Handshake -> {
                    message.encode(sendChannel)
                }

                is KeepAlive -> {
                    message.encode(sendChannel)
                }

                is Piece -> {

                    val size =
                        Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + message.length
                    sendChannel.writeInt(size)
                    sendChannel.writeByte(PIECE_ID)
                    sendChannel.writeInt(message.piece)
                    sendChannel.writeInt(message.offset)

                    dataStorage.readBlock(
                        message.piece, message.offset,
                        readBlock, message.length
                    )
                    sendChannel.writeByteArray(readBlock)

                }

                is Have -> {
                    message.encode(sendChannel)
                }

                is Request -> {
                    message.encode(sendChannel)
                }

                is Bitfield -> {
                    message.encode(sendChannel)
                }

                is Cancel -> {
                    message.encode(sendChannel)
                }

                is Choke -> {
                    message.encode(sendChannel)
                }

                is Unchoke -> {
                    message.encode(sendChannel)
                }

                is Interested -> {
                    message.encode(sendChannel)
                }

                is NotInterested -> {
                    message.encode(sendChannel)
                }

                is Port -> {
                    message.encode(sendChannel)
                }

                is ExtendedMessage -> {

                    val buffer = Buffer()
                    extendedProtocol.doEncode(address(), message, buffer)
                    val size = buffer.size
                    sendChannel.writeInt(size.toInt())
                    sendChannel.writeBuffer(buffer)
                }

                else -> {
                    debug("not supported message " + message.type.name)
                    throw Exception("not supported message " + message.type.name)
                }
            }
            sendChannel.flush()
        } catch (throwable: Throwable) {
            close()
            throw throwable
        }
    }


    private suspend fun decode(channel: ByteReadChannel, length: Int): Message {

        val messageType = channel.readByte()
        var size = length - Byte.SIZE_BYTES

        return when (messageType) {
            PIECE_ID -> {
                val pieceIndex = channel.readInt()
                size = size - Int.SIZE_BYTES
                val blockOffset = channel.readInt()
                size = size - Int.SIZE_BYTES
                val data = writeBlock()
                channel.readFully(data, 0, size)
                return Piece(pieceIndex, blockOffset, size)
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
                extendedProtocol.doDecode(address(), buffer)
            }

            else -> {
                throw Exception("not supported message type $messageType")
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun close() {

        if (!closed.exchange(true)) {
            try {
                socket.close()
            } catch (throwable: Throwable) {
                debug("SocketChannelHandler", throwable)
            }
            try {
                worker.purgeConnection(this)
            } catch (throwable: Throwable) {
                debug("SocketChannelHandler", throwable)
            }
        }
    }
}