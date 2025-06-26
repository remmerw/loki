package io.github.remmerw.loki.core

import io.github.remmerw.loki.debug
import io.github.remmerw.loki.grid.Grid
import io.github.remmerw.loki.grid.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.grid.Handshake
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.Peer
import io.github.remmerw.loki.grid.SHA1_HASH_LENGTH
import io.github.remmerw.loki.grid.TORRENT_ID_LENGTH
import io.github.remmerw.loki.grid.TorrentId
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeBuffer
import kotlinx.io.Buffer
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Connection internal constructor(
    private val peer: Peer,
    private val worker: Worker,
    private val socket: Socket,
    private val grid: Grid
) : ConnectionWorker(worker) {
    @Volatile
    var lastActive: ValueTimeMark = TimeSource.Monotonic.markNow()

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)
    private val receiveChannel = socket.openReadChannel()
    private val sendChannel = socket.openWriteChannel(autoFlush = true)

    fun peer(): Peer {
        return peer
    }

    suspend fun reading(): Message? {
        try {
            val read = receiveChannel.readInt()
            require(read >= 0) { "Invalid read token received" }

            val buffer = receiveChannel.readBuffer(read)
            require(read == buffer.size.toInt()) { "Invalid number of data received" }

            return grid.decode(peer, buffer)
        } catch (_: Throwable) {
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
        val buffer = Buffer()
        try {
            grid.encode(peer, message, buffer)
            sendChannel.writeBuffer(buffer)
        } catch (_: Throwable) {
            close()
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