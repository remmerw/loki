package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Bitfield
import io.github.remmerw.loki.data.Cancel
import io.github.remmerw.loki.data.Choke
import io.github.remmerw.loki.data.ExtendedMessage
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.Have
import io.github.remmerw.loki.data.Interested
import io.github.remmerw.loki.data.KeepAlive
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.Messages
import io.github.remmerw.loki.data.NotInterested
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Port
import io.github.remmerw.loki.data.Request
import io.github.remmerw.loki.data.SHA1_HASH_LENGTH
import io.github.remmerw.loki.data.TORRENT_ID_LENGTH
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.data.Unchoke
import io.github.remmerw.loki.data.keepAlive
import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeBuffer
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Connection internal constructor(
    private val address: InetSocketAddress,
    private val worker: Worker,
    private val socket: Socket,
    private val messages: Messages
) : ConnectionWorker(worker) {
    @Volatile
    var lastActive: ValueTimeMark = TimeSource.Monotonic.markNow()

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)
    private val receiveChannel = socket.openReadChannel()
    private val sendChannel = socket.openWriteChannel(autoFlush = false)
    private val mutex = Mutex()

    fun address(): InetSocketAddress {
        return address
    }

    suspend fun reading(): Message? {
        try {
            val length = receiveChannel.readInt()
            require(length >= 0) { "Invalid read token received" }

            if (length == 0) {
                return keepAlive() // keep has length 0
            }

            return messages.decode(address, receiveChannel, length)
        } catch (throwable: Throwable) {
            debug("Error Connection.reading " + throwable.message)
            debug("Connection", throwable)
            close()
        }
        return null
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


    @OptIn(ExperimentalAtomicApi::class)
    suspend fun posting(message: Message) {
        lastActive = TimeSource.Monotonic.markNow()
        mutex.withLock {
            try {
                when (message) {
                    is Handshake -> {
                        message.encode(sendChannel)
                    }

                    is KeepAlive -> {
                        message.encode(sendChannel)
                    }

                    is Piece -> {
                        message.encode(sendChannel)
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
                        messages.encode(address, message, buffer)
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
                debug("Error Connection.posting " + throwable.message)
                debug("Connection", throwable)
                close()
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