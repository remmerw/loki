package io.github.remmerw.loki.core

import io.github.remmerw.grid.Memory
import io.github.remmerw.grid.allocateMemory
import io.github.remmerw.grid.randomAccessFile
import io.github.remmerw.loki.Storage
import io.github.remmerw.loki.debug
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.kotlincrypto.hash.sha1.SHA1
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

internal data class DataStorage(val directory: Path) : Storage, AutoCloseable {

    private val validPieces: MutableSet<Int> = mutableSetOf()
    private var pieceFiles: MutableMap<Int, List<TorrentFile>> = mutableMapOf()
    private val bitmaskDatabase = randomAccessFile(Path(directory, "bitmask.db"))
    private val torrentDatabase = Path(directory, "torrent.db")
    private val database = randomAccessFile(Path(directory, "database.db"))
    private val completedPieces: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val databaseLock = reentrantLock()
    private val bitmaskLock = reentrantLock()

    init {
        require(
            SystemFileSystem.metadataOrNull(directory)?.isDirectory == true
        ) {
            "Path is not a directory."
        }
        if (SystemFileSystem.exists(torrentDatabase)) {
            SystemFileSystem.source(torrentDatabase).buffered().use { source ->
                val bytes = source.readByteArray()
                val torrent = buildTorrent(bytes)
                val metadata = allocateMemory(bytes.size)
                metadata.writeBytes(bytes, 0)
                metadata(metadata)
                initialize(torrent)
            }
        }
    }

    @Volatile
    private var metadata: Memory? = null

    @Volatile
    private var dataBitfield: DataBitfield? = null

    @Volatile
    private var torrent: Torrent? = null

    @Volatile
    private var initializeDone: Boolean = false

    @Volatile
    private var pieceStatistics: PieceStatistics? = null


    fun completePiece(piece: Int) {
        completedPieces.add(piece)
    }

    fun completePieces(): Set<Int> {
        return completedPieces
    }

    fun initializeDone(): Boolean {
        return initializeDone
    }

    fun pieceStatus(piece: Int): DataBitfield.PieceStatus {
        return dataBitfield!!.pieceStatus(piece)
    }

    fun pieceStatistics(): PieceStatistics? {
        return pieceStatistics
    }

    fun dataBitfield(): DataBitfield? {
        return dataBitfield
    }

    fun piecesTotal(): Int {
        return dataBitfield?.piecesTotal ?: -1
    }

    fun isComplete(piece: Int): Boolean {
        return dataBitfield?.isComplete(piece) == true
    }

    fun isVerified(piece: Int): Boolean {
        return dataBitfield?.isVerified(piece) == true
    }

    fun metadata(metadata: Memory) {
        this.metadata = metadata
        if (!SystemFileSystem.exists(torrentDatabase)) {
            SystemFileSystem.sink(torrentDatabase, false).use { sink ->
                metadata.transferTo(sink)
            }
        }
    }

    fun initialize(torrent: Torrent) {
        require(!initializeDone) { "Initialisation is already done" }

        this.torrent = torrent

        val totalSize = torrent.size
        val chunkSize = torrent.chunkSize

        var index = 0
        var remaining = totalSize
        while (remaining > 0) {


            val chunkFiles: MutableList<TorrentFile> = mutableListOf()
            torrent.files.forEach { torrentFile ->
                if (torrentFile.size > 0) {
                    if (torrentFile.pieces.contains(index)) {
                        chunkFiles.add(torrentFile)
                    }
                }
            }

            pieceFiles[index] = chunkFiles

            index++
            remaining -= chunkSize
        }

        val totalPieces = torrent.chunks.size

        verifiedPieces(totalPieces)
        pieceStatistics = PieceStatistics(totalPieces)

        val selectedFiles: Set<TorrentFile> = torrent.files.toSet()

        repeat(totalPieces) { pieceIndex: Int ->
            for (file in pieceFiles[pieceIndex]!!) {
                if (selectedFiles.contains(file)) {
                    validPieces.add(pieceIndex)
                    break
                }
            }
        }

        repeat(totalPieces) { pieceIndex ->
            if (!validPieces.contains(pieceIndex)) {
                dataBitfield!!.skip(pieceIndex)
            }
        }

        initializeDone = true

    }

    fun torrent(): Torrent? {
        return torrent
    }

    internal fun nextPieces(): Array<Int> {
        val selectedFilesPieces = validPieces()
        val pieceStatistics = pieceStatistics()!!
        return pieceStatistics.rarestFirst().filter { pieceIndex ->
            selectedFilesPieces.contains(pieceIndex) &&
                    !isPieceComplete(pieceIndex)
        }.toTypedArray()
    }


    private fun isPieceComplete(pieceIndex: Int): Boolean {
        val pieceStatus = pieceStatus(pieceIndex)
        return pieceStatus == DataBitfield.PieceStatus.COMPLETE ||
                pieceStatus == DataBitfield.PieceStatus.COMPLETE_VERIFIED
    }

    internal fun validPieces(): Set<Int> {
        return validPieces
    }

    internal fun sliceMetadata(offset: Int, length: Int): ByteArray {
        return metadata!!.readBytes(offset, length)
    }

    internal fun metadataSize(): Int {
        return metadata?.size() ?: -1
    }

    internal fun markVerified(piece: Int) {
        dataBitfield!!.markVerified(piece)
        bitmaskLock.withLock {
            bitmaskDatabase.writeBoolean(piece.toLong(), true)
        }
    }

    internal fun digestChunk(piece: Int, chunk: Chunk): Boolean {
        databaseLock.withLock {
            val bytes = ByteArray(chunk.chunkSize)
            database.readBytes(position(piece), bytes)
            val digest = SHA1().digest(bytes)
            val result = digest.contentEquals(chunk.checksum)
            if (result) {
                markVerified(piece)
                return true
            } else {
                chunk.reset()
                return false
            }
        }
    }

    private fun chunkSize(): Int {
        return torrent!!.chunkSize
    }

    private fun position(piece: Int, offset: Int = 0): Long {
        return (chunkSize() * piece).toLong() + offset
    }

    internal fun verifiedPieces(totalPieces: Int) {
        bitmaskLock.withLock {
            val bytes = ByteArray(totalPieces)
            val size = bitmaskDatabase.read(0, bytes)

            debug("Bitmask size $size total $totalPieces")
            val mask = Bitmask.decode(bytes)

            dataBitfield = DataBitfield(totalPieces, mask)
        }
    }

    override fun close() {
        databaseLock.withLock {
            try {
                database.close()
            } catch (throwable: Throwable) {
                debug(throwable)
            }
        }
        bitmaskLock.withLock {
            try {
                bitmaskDatabase.close()
            } catch (throwable: Throwable) {
                debug(throwable)
            }
        }
    }


    internal fun readBlock(piece: Int, offset: Int, bytes: ByteArray, length: Int) {
        return databaseLock.withLock {
            database.readBytes(position(piece, offset), bytes, 0, length)
        }
    }

    internal fun writeBlock(piece: Int, offset: Int, data: ByteArray, length: Int) {
        return databaseLock.withLock {
            database.writeBytes(position(piece, offset), data, 0, length)
        }
    }


    internal fun chunk(piece: Int): Chunk {
        return torrent!!.chunks[piece]
    }

    override fun storeTo(directory: Path) {
        storageUnits().forEach { storageUnit ->
            storageUnit.storeTo(directory)
        }
    }

    override fun delete() {
        cleanupDirectory(directory)
    }

    private fun cleanupDirectory(dir: Path) {
        if (SystemFileSystem.exists(dir)) {
            val files = SystemFileSystem.list(dir)
            for (file in files) {
                SystemFileSystem.delete(file)
            }
        }
    }

    override fun storageUnits(): List<StorageUnit> {
        return torrentFiles().filter { torrentFile -> torrentFile.size > 0 }
            .map { torrentFile ->
                StorageUnit(
                    Path(directory, "database.db"),
                    torrentFile
                )
            }

    }

    internal fun torrentFiles(): List<TorrentFile> {
        return torrent!!.files
    }


}