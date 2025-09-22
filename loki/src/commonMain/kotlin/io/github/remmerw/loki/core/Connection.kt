package io.github.remmerw.loki.core

import io.github.remmerw.buri.BEReader
import io.github.remmerw.grid.ByteArrayPool
import io.github.remmerw.grid.PooledByteArray
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
import io.github.remmerw.loki.data.KEEPALIVE
import io.github.remmerw.loki.data.KeepAlive
import io.github.remmerw.loki.data.Message
import io.github.remmerw.loki.data.NOT_INTERESTED_ID
import io.github.remmerw.loki.data.NotInterested
import io.github.remmerw.loki.data.PIECE_ID
import io.github.remmerw.loki.data.PORT_ID
import io.github.remmerw.loki.data.PROTOCOL_NAME
import io.github.remmerw.loki.data.Piece
import io.github.remmerw.loki.data.Port
import io.github.remmerw.loki.data.REQUEST_ID
import io.github.remmerw.loki.data.Request
import io.github.remmerw.loki.data.SHA1_HASH_LENGTH
import io.github.remmerw.loki.data.TORRENT_ID_LENGTH
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.data.UNCHOKE_ID
import io.github.remmerw.loki.data.Unchoke
import io.github.remmerw.loki.debug
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readTo
import java.net.InetSocketAddress
import java.net.Socket
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
) : ConnectionWorker(worker), AutoCloseable {
    @Volatile
    var lastActive: ValueTimeMark = TimeSource.Monotonic.markNow()

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)
    private val receiveChannel = socket.inputStream.asSource().buffered()
    private val sendChannel = socket.outputStream.asSink().buffered()
    private var writeBlock: PooledByteArray? = null


    fun writeBlock(): ByteArray {
        if (writeBlock == null) {
            writeBlock = ByteArrayPool.getInstance(BLOCK_SIZE).get()
        }
        return writeBlock!!.byteArray
    }


    fun address(): InetSocketAddress {
        return address
    }

    suspend fun reading() {
        while (!isClosed) {
            try {
                lastActive = TimeSource.Monotonic.markNow()

                val length = receiveChannel.readInt()
                require(length >= 0) { "Invalid read length received" }

                if (length == 0) {  // keep has length 0
                    handleConnection()
                } else {
                    val message = decode(receiveChannel, length)
                    if (message != null) {
                        worker.consume(message, this)
                    }
                    handleConnection()
                }
                yield()
            } catch (throwable: Throwable) {
                debug("Connection.reading " + throwable.message)
                close()
                break
            }
        }
    }

    fun receiveHandshake(): Handshake {

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

        return Handshake(name, reserved, TorrentId(infoHash), peerId)

    }


    @OptIn(ExperimentalAtomicApi::class)
    val isClosed: Boolean
        get() = closed.load()

    suspend fun posting() {
        while (!isClosed) {
            try {
                val send = worker.producedMessage(this)
                if (send != null) {
                    posting(send)
                }
                yield()
            } catch (_: Throwable) {
                break
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun posting(message: Message) {

        when (message) {
            is Handshake -> {
                val data = message.name
                sendChannel.writeByte(data.size.toByte())
                sendChannel.write(data)
                sendChannel.write(message.reserved)
                sendChannel.write(message.torrentId.bytes)
                sendChannel.write(message.peerId)
            }

            is KeepAlive -> {
                sendChannel.write(KEEPALIVE)
            }

            is Piece -> {

                val size =
                    Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + message.length
                sendChannel.writeInt(size)
                sendChannel.writeByte(PIECE_ID)
                sendChannel.writeInt(message.piece)
                sendChannel.writeInt(message.offset)

                ByteArrayPool.getInstance(BLOCK_SIZE).get().use { pooledByteArray ->
                    val readBlock = pooledByteArray.byteArray
                    dataStorage.readBlock(
                        message.piece, message.offset,
                        readBlock, message.length
                    )
                    sendChannel.write(readBlock)
                }

            }

            is Have -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(HAVE_ID)
                sendChannel.writeInt(message.piece)
            }

            is Request -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(REQUEST_ID)
                sendChannel.writeInt(message.piece)
                sendChannel.writeInt(message.offset)
                sendChannel.writeInt(message.length)
            }

            is Bitfield -> {
                val size = Byte.SIZE_BYTES + message.bitfield.size
                sendChannel.writeInt(size)
                sendChannel.writeByte(BITFIELD_ID)
                sendChannel.write(message.bitfield)
            }

            is Cancel -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(CANCEL_ID)
                sendChannel.writeInt(message.piece)
                sendChannel.writeInt(message.offset)
                sendChannel.writeInt(message.length)
            }

            is Choke -> {
                val size = Byte.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(CHOKE_ID)
            }

            is Unchoke -> {
                val size = Byte.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(UNCHOKE_ID)
            }

            is Interested -> {
                val size = Byte.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(INTERESTED_ID)
            }

            is NotInterested -> {
                val size = Byte.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(NOT_INTERESTED_ID)
            }

            is Port -> {
                val size = Byte.SIZE_BYTES + Short.SIZE_BYTES
                sendChannel.writeInt(size)
                sendChannel.writeByte(PORT_ID)
                sendChannel.writeShort(message.port.toShort())
            }

            is ExtendedMessage -> {

                val buffer = Buffer()
                extendedProtocol.doEncode(address(), message, buffer)
                val size = buffer.size
                sendChannel.writeInt(size.toInt())
                sendChannel.write(buffer, size)
            }
        }
        sendChannel.flush()

    }

    private fun consumeBitfield(
        bitfield: ByteArray
    ) {
        val piecesTotal = dataStorage.piecesTotal()
        val dataBitfield = DataBitfield(
            piecesTotal, Bitmask.decode(bitfield, piecesTotal)
        )
        setDataBitfield(dataBitfield)
        dataStorage.pieceStatistics()!!.addBitfield(dataBitfield)
    }


    private fun consumePiece(piece: Int, offset: Int, length: Int) {
        if (!dataStorage.initializeDone()) {
            return
        }

        // check that this block was requested in the first place
        if (!checkBlockIsExpected(piece, offset)) {
            return
        }

        // discard blocks for pieces that have already been verified
        if (dataStorage.isComplete(piece)) {
            return
        }

        val assignment = this.assignment
        if (assignment != null) {
            if (assignment.isAssigned(piece)) {
                assignment.check()
            }
        }

        val chunk = dataStorage.chunk(piece)

        if (chunk.isComplete) {
            return
        }

        val data = this.writeBlock()
        dataStorage.writeBlock(piece, offset, data, length)
        chunk.markAvailable(offset, length)

        if (chunk.isComplete) {
            if (dataStorage.digestChunk(piece, chunk)) {
                dataStorage.completePiece(piece)
            } else {
                // chunk was shit (for testing now - close connection)
                debug("Received shit chunk, close connection -> " + this.address())
                this.close()
            }
        }
    }


    private fun decode(channel: Source, length: Int): Message? {

        val messageType = channel.readByte()
        var size = length - Byte.SIZE_BYTES

        return when (messageType) {
            PIECE_ID -> {
                val piece = channel.readInt()
                size = size - Int.SIZE_BYTES
                val offset = channel.readInt()
                size = size - Int.SIZE_BYTES
                val data = writeBlock()
                channel.readTo(data, 0, size)
                consumePiece(piece, offset, size)
                return null
            }

            HAVE_ID -> {
                val piece = channel.readInt()

                if (dataStorage.initializeDone()) {
                    dataStorage.pieceStatistics()!!.addPiece(this, piece)
                } else {
                    worker.consumeHave(piece, this)
                }
                return null
            }

            REQUEST_ID -> {
                val piece = channel.readInt()
                val offset = channel.readInt()
                val length = channel.readInt()


                if (dataStorage.initializeDone()) {
                    if (!choking) {
                        if (dataStorage.isVerified(piece)) {
                            addRequest(Request(piece, offset, length))
                        }
                    }
                }
                return null
            }

            BITFIELD_ID -> {
                val data = channel.readByteArray(size)

                if (dataStorage.initializeDone()) {
                    consumeBitfield(data)
                } else {
                    worker.consumeBitfield(data, this)
                }
                return null
            }

            CANCEL_ID -> {
                val pieceIndex = channel.readInt()
                val blockOffset = channel.readInt()
                channel.readInt()
                this.cancelRequest(pieceIndex, blockOffset)
                return null
            }

            CHOKE_ID -> {
                this.isPeerChoking = true
                return null
            }

            UNCHOKE_ID -> {
                this.isPeerChoking = false
                return null
            }

            INTERESTED_ID -> {
                this.isPeerInterested = true
                return null
            }

            NOT_INTERESTED_ID -> {
                this.isPeerInterested = false
                return null
            }

            PORT_ID -> {
                val port = channel.readShort().toInt() and 0x0000FFFF
                debug("Port not yet used $port")
                return null
            }

            EXTENDED_MESSAGE_ID -> {
                val data = channel.readByteArray(size)
                require(size == data.size) { "Invalid number of data received" }
                val reader = BEReader(data, data.size)
                extendedProtocol.doDecode(address(), reader)
            }

            else -> {
                throw Exception("not supported message type $messageType")
            }
        }
    }

    suspend fun performHandshake(
        peerId: ByteArray,
        torrentId: TorrentId,
        handshakeHandlers: Collection<HandshakeHandler>
    ) {

        val handshake = Handshake(
            PROTOCOL_NAME,
            ByteArray(HANDSHAKE_RESERVED_LENGTH), torrentId, peerId
        )
        handshakeHandlers.forEach { handler: HandshakeHandler ->
            handler.processOutgoingHandshake(handshake)
        }

        posting(handshake)

        val peerHandshake = receiveHandshake()

        require(torrentId == peerHandshake.torrentId) { "Invalid torrent ID" }
        handshakeHandlers.forEach { handler: HandshakeHandler ->
            handler.processIncomingHandshake(this)
        }
        worker.addConnection(this)

    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {

        if (!closed.exchange(true)) {
            try {
                writeBlock?.close()
            } catch (_: Throwable) {
            }
            try {
                receiveChannel.close()
            } catch (_: Throwable) {
            }
            try {
                sendChannel.close()
            } catch (_: Throwable) {
            }
            try {
                socket.close()
            } catch (_: Throwable) {
            }
            try {
                worker.purgeConnection(this)
            } catch (throwable: Throwable) {
                debug(throwable)
            }
        }
    }
}