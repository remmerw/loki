package io.github.remmerw.loki.core

import io.github.remmerw.loki.BLOCK_SIZE
import io.github.remmerw.loki.Storage
import io.github.remmerw.loki.debug
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.concurrent.Volatile
import kotlin.math.min

internal data class DataStorage(val data: Data) : Storage, AutoCloseable {

    private val validPieces: MutableSet<Int> = mutableSetOf()
    private val chunks: MutableMap<Int, Chunk> = mutableMapOf()
    private val skeletons: MutableList<Skeleton> = mutableListOf()
    private var pieceFiles: MutableMap<Int, List<TorrentFile>> = mutableMapOf()
    private val bitmask = Path(data.directory(), "bitmask.db")

    @Volatile
    private var metadata: ByteArray? = null

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

    fun initialize(torrent: Torrent, metadata: ByteArray) {

        this.torrent = torrent
        this.metadata = metadata
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
        return metadata!!.copyOfRange(offset, offset + length)
    }

    internal fun metadataSize(): Int {
        return metadata?.size ?: -1
    }

    internal fun markVerified(piece: Int) {
        dataBitfield!!.markVerified(piece)
    }

    internal fun storeChunk(piece: Int, chunk: Chunk): Boolean {
        data.storeBlock(piece, chunk.bytes())
        if (data.verifyBlock(piece, chunk.checksum())) {
            chunks.remove(piece)
            dataBitfield!!.markVerified(piece)
            return true
        } else {
            data.deleteBlock(piece)
            chunk.reset()
            return false
        }
    }

    internal fun verifiedPieces(totalPieces: Int) {

        var mask: Bitmask? = null
        if (SystemFileSystem.exists(bitmask)) {
            val size = SystemFileSystem.metadataOrNull(bitmask)!!.size
            SystemFileSystem.source(bitmask).use { source ->
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

    override fun close() {
        try {
            if (dataBitfield != null) {
                val buffer = Buffer()
                buffer.write(dataBitfield!!.encode())

                SystemFileSystem.sink(bitmask, false).use { sink ->
                    sink.write(buffer, buffer.size)
                }
            }
        } catch (e: Exception) {
            debug("ContentStorageUnit", e)
        }
    }

    internal fun readBlock(piece: Int, offset: Int, length: Int): ByteArray {
        return data.readBlockSlice(piece, offset, length)
    }

    internal fun readBlock(piece: Int, offset: Int): ByteArray {
        return data.readBlockSlice(piece, offset)
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
        data.reset()
    }


    override fun storageUnits(): List<StorageUnit> {
        return torrentFiles().filter { torrentFile -> torrentFile.size > 0 }
            .map { torrentFile -> StorageUnit(this, torrent!!.chunkSize, torrentFile) }
    }

    internal fun torrentFiles(): List<TorrentFile> {
        return torrent!!.files
    }


    private fun buildChunk(skeleton: Skeleton): Chunk {
        return Chunk(skeleton.length, skeleton.blockSize, skeleton.checksum)
    }
}