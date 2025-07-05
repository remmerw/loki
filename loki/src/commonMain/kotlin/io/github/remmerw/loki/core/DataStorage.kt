package io.github.remmerw.loki.core

import io.github.remmerw.grid.Memory
import io.github.remmerw.grid.allocateMemory
import io.github.remmerw.grid.randomAccessFile
import io.github.remmerw.loki.BLOCK_SIZE
import io.github.remmerw.loki.Storage
import io.github.remmerw.loki.debug
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.concurrent.Volatile
import kotlin.math.min

internal data class DataStorage(val directory: Path) : Storage {

    private val validPieces: MutableSet<Int> = mutableSetOf()
    private val chunks: MutableMap<Int, Chunk> = mutableMapOf()
    private val skeletons: MutableList<Skeleton> = mutableListOf()
    private var pieceFiles: MutableMap<Int, List<TorrentFile>> = mutableMapOf()
    private val bitmaskDatabase = Path(directory, "bitmask.db")
    private val torrentDatabase = Path(directory, "torrent.db")
    private val database = randomAccessFile(Path(directory, "database.db"))
    private val lock = reentrantLock()

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
        var transferSize = BLOCK_SIZE
        val totalSize = torrent.size
        val chunkSize = torrent.chunkSize

        if (transferSize > chunkSize) {
            transferSize = chunkSize
        }

        var remaining = totalSize
        while (remaining > 0) {
            val index = skeletons.size

            val chunkFiles: MutableList<TorrentFile> = mutableListOf()
            torrent.files.forEach { torrentFile ->
                if (torrentFile.size > 0) {
                    if (torrentFile.pieces.contains(index)) {
                        chunkFiles.add(torrentFile)
                    }
                }
            }

            pieceFiles[skeletons.size] = chunkFiles


            val lim = min(chunkSize.toLong(), remaining).toInt()
            val hash = torrent.chunkHashes[index]
            skeletons.add(Skeleton(lim, transferSize, hash))

            remaining -= chunkSize
        }

        val totalPieces = skeletons.size

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
    }

    internal fun storeChunk(piece: Int, chunk: Chunk): Boolean {
        val result = chunk.digest()
        if (result) {
            lock.withLock {
                database.writeMemory(chunk.memory, offset(piece))
            }
            chunks.remove(piece)
            dataBitfield!!.markVerified(piece)
            return true
        } else {
            chunk.reset()
            return false
        }
    }

    internal fun offset(piece: Int): Long {
        return (torrent!!.chunkSize * piece).toLong()
    }

    internal fun verifiedPieces(totalPieces: Int) {

        var mask: Bitmask? = null
        if (SystemFileSystem.exists(bitmaskDatabase)) {
            val size = SystemFileSystem.metadataOrNull(bitmaskDatabase)!!.size
            SystemFileSystem.source(bitmaskDatabase).use { source ->
                val buffer = Buffer()
                buffer.write(source, size)

                mask = Bitmask.decode(buffer.readByteArray(), totalPieces)
            }
        }

        if (mask == null) {
            mask = Bitmask(totalPieces)
        }

        dataBitfield = DataBitfield(totalPieces, mask)

    }

    fun shutdown() {
        try {
            database.close()
        } catch (throwable: Throwable) {
            debug("DataStorage", throwable)
        }
        try {
            if (dataBitfield != null) {
                val buffer = Buffer()
                buffer.write(dataBitfield!!.encode())

                SystemFileSystem.sink(bitmaskDatabase, false).use { sink ->
                    sink.write(buffer, buffer.size)
                }
            }
        } catch (throwable: Throwable) {
            debug("DataStorage", throwable)
        }
    }


    internal fun readBlock(piece: Int, offset: Int, length: Int): ByteArray {
        return lock.withLock {
            database.readBytes(offset(piece) + offset, length)
        }
    }


    internal fun chunk(piece: Int): Chunk {
        require(piece <= skeletons.size)
        return chunks.getOrPut(piece) { buildChunk(skeletons[piece]) }
    }

    override fun storeTo(directory: Path) {
        storageUnits().forEach { storageUnit ->
            storageUnit.storeTo(directory)
        }
    }

    override fun finish() {
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
            .map { torrentFile -> StorageUnit(database, torrentFile) }
    }

    internal fun torrentFiles(): List<TorrentFile> {
        return torrent!!.files
    }


    private fun buildChunk(skeleton: Skeleton): Chunk {
        return Chunk(skeleton.length, skeleton.blockSize, skeleton.checksum)
    }
}