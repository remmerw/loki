package io.github.remmerw.loki.core

import io.github.remmerw.buri.BEReader
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
import io.github.remmerw.nott.Address
import kotlinx.coroutines.yield
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Connection internal constructor(
    private val address: Address,
    private val dataStorage: DataStorage,
    private val worker: Worker,
    private val socket: Socket,
    private val extendedProtocol: ExtendedProtocol,
) : ConnectionWorker(worker),
    AutoCloseable {
    @Volatile
    var lastActive: ValueTimeMark = TimeSource.Monotonic.markNow()

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)

    private val receiveChannel = Channels.newChannel(socket.inputStream)
    private val sendChannel = Channels.newChannel(socket.outputStream)

    
    

    fun address(): Address = address

    suspend fun reading() {
        while (!isClosed) {
            try {
                lastActive = TimeSource.Monotonic.markNow()
                memoryInstance().use { pooled ->
                val reading= pooled.buffer
                val length = receiveChannel.read(reading)
                

                if (length == 0) { // keep has length 0
                    handleConnection()
                } else {
                    reading.flip()
                    val message = decode(reading, length) // Todo length
                    if (message != null) {
                        worker.consume(message, this)
                    }
                    handleConnection()
                }
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
memoryInstance().use { pooled ->
                val reading= pooled.buffer
        val length = receiveChannel.read(reading)
        require(length >= 0) { "Invalid read length received" }
        reading.flip()
        val sizeName = reading.get()
        require(sizeName.toInt() > 0) { "Invalid size name received" }

        val name = reading.getByteArray(sizeName.toInt())

        val reserved = reading.getByteArray(HANDSHAKE_RESERVED_LENGTH)

        val infoHash = reading.getByteArray(TORRENT_ID_LENGTH)

        val peerId = reading.getByteArray(SHA1_HASH_LENGTH)

        }
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
        memoryInstance().use{ pooled ->
        val sending = pooled.buffer
        when (message) {
            is Handshake -> {
                val data = message.name
                sending.put(data.size.toByte())
                sending.put(data)
                sending.put(message.reserved)
                sending.put(message.torrentId.bytes)
                sending.put(message.peerId)
            }

            is KeepAlive -> {
                sending.put(KEEPALIVE)
            }

            is Piece -> {
                val size =
                    Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + message.length
                sending.putInt(size)
                sending.put(PIECE_ID)
                sending.putInt(message.piece)
                sending.putInt(message.offset)

                blockInstance().use { pooledBlock ->
                    val readBlock = pooledBlock.data
                    dataStorage.readBlock(
                        message.piece,
                        message.offset,
                        readBlock,
                        message.length,
                    )
                    sending.put(readBlock)
                }
            }

            is Have -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES
                sending.putInt(size)
                sending.put(HAVE_ID)
                sending.putInt(message.piece)
            }

            is Request -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
                sending.putInt(size)
                sending.put(REQUEST_ID)
                sending.putInt(message.piece)
                sending.putInt(message.offset)
                sending.putInt(message.length)
            }

            is Bitfield -> {
                val size = Byte.SIZE_BYTES + message.bitfield.size
                sending.putInt(size)
                sending.put(BITFIELD_ID)
                sending.put(message.bitfield)
            }

            is Cancel -> {
                val size = Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
                sending.putInt(size)
                sending.put(CANCEL_ID)
                sending.putInt(message.piece)
                sending.putInt(message.offset)
                sending.putInt(message.length)
            }

            is Choke -> {
                val size = Byte.SIZE_BYTES
                sending.putInt(size)
                sending.put(CHOKE_ID)
            }

            is Unchoke -> {
                val size = Byte.SIZE_BYTES
                sending.putInt(size)
                sending.put(UNCHOKE_ID)
            }

            is Interested -> {
                val size = Byte.SIZE_BYTES
                sending.putInt(size)
                sending.put(INTERESTED_ID)
            }

            is NotInterested -> {
                val size = Byte.SIZE_BYTES
                sending.putInt(size)
                sending.put(NOT_INTERESTED_ID)
            }

            is Port -> {
                val size = Byte.SIZE_BYTES + Short.SIZE_BYTES
                sending.putInt(size)
                sending.put(PORT_ID)
                sending.putShort(message.port.toShort())
            }

            is ExtendedMessage -> {
                val pos = sending.position()
                sending.putInt(0) // placeholder

                val size = extendedProtocol.doEncode(address(), message, sending)
                val last = sending.position()
                sending.position(pos)
                sending.putInt(size.toInt())
                sending.position(last)
            }
        }
        sending.flip()
        while (sending.hasRemaining()) {
            sendChannel.write(sending)
        }
        }
    }

    private fun consumePiece(
        piece: Int,
        offset: Int,
        length: Int,
        data: ByteArray,
    ) {
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

    private fun decode(reading: ByteBuffer, length: Int): Message? {
        val messageType = reading.get()
        var size = length - Byte.SIZE_BYTES

        return when (messageType) {
            PIECE_ID -> {
                val piece = reading.getInt()
                size -= Int.SIZE_BYTES
                val offset = reading.getInt()
                size -= Int.SIZE_BYTES
                blockInstance().use { pooled ->
                    val data = pooled.data

                    reading.get(data, 0, size)
                    consumePiece(piece, offset, size, data)
                    null
                }
            }

            HAVE_ID -> {
                val piece = reading.getInt()

                if (dataStorage.initializeDone()) {
                    dataStorage.pieceStatistics()!!.addPiece(this, piece)
                } else {
                    worker.consumeHave(piece, this)
                }
                null
            }

            REQUEST_ID -> {
                val piece = reading.getInt()
                val offset = reading.getInt()
                val length = reading.getInt()

                if (dataStorage.initializeDone()) {
                    if (!choking) {
                        if (dataStorage.isVerified(piece)) {
                            addRequest(Request(piece, offset, length))
                        }
                    }
                }
                null
            }

            BITFIELD_ID -> {
                if (dataStorage.initializeDone()) {
                    val pieces = dataStorage.piecesTotal()
                    val bitmask = reading.getBitmask(pieces)
                    val dataBitfield =
                        DataBitfield(
                            pieces,
                            bitmask,
                        )
                    setDataBitfield(dataBitfield)
                    dataStorage.pieceStatistics()!!.addBitfield(dataBitfield)
                } else {
                    val data = reading.getByteArray(size)
                    worker.consumeBitfield(data, this)
                }
                null
            }

            CANCEL_ID -> {
                val pieceIndex = reading.getInt()
                val blockOffset = reading.getInt()
                reading.getInt()
                this.cancelRequest(pieceIndex, blockOffset)
                null
            }

            CHOKE_ID -> {
                this.isPeerChoking = true
                null
            }

            UNCHOKE_ID -> {
                this.isPeerChoking = false
                null
            }

            INTERESTED_ID -> {
                this.isPeerInterested = true
                null
            }

            NOT_INTERESTED_ID -> {
                this.isPeerInterested = false
                null
            }

            PORT_ID -> {
                val port = reading.getShort().toInt() and 0x0000FFFF
                debug("Port not yet used $port")
                null
            }

            EXTENDED_MESSAGE_ID -> {
                require(size == reading.remaining()) { "Invalid number of data received" }
                val reader = BEReader(reading)
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
        handshakeHandlers: Collection<HandshakeHandler>,
    ) {
        val handshake =
            Handshake(
                PROTOCOL_NAME,
                ByteArray(HANDSHAKE_RESERVED_LENGTH),
                torrentId,
                peerId,
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
