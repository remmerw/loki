package io.github.remmerw.loki.core

import io.github.remmerw.buri.BEInteger
import io.github.remmerw.buri.BEList
import io.github.remmerw.buri.BEMap
import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.BEReader
import io.github.remmerw.buri.BEString
import io.github.remmerw.buri.decodeBencode
import io.github.remmerw.loki.BLOCK_SIZE
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.math.ceil
import kotlin.math.min


internal interface Agent

internal interface Consumers : Agent {
    val consumers: List<MessageConsumer>
}

internal interface Produces : Agent {
    fun produce(connection: Connection)
}


internal data class TorrentFile(val size: Long, val pathElements: List<String>) {
    val pieces: MutableList<Int> = mutableListOf()

    private var startPos = 0L

    fun endPosition(): Long {
        return startPos + size
    }

    fun startPosition(): Long {
        return startPos
    }

    fun startPosition(value: Long) {
        this.startPos = value
    }

    fun addPiece(piece: Int) {
        pieces.add(piece)
    }
}

internal fun createTorrentFile(size: Long, pathElements: List<String>): TorrentFile {
    if (size < 0) {
        throw RuntimeException("Invalid torrent file size: $size")
    }
    if (pathElements.isEmpty()) {
        throw RuntimeException("Can't create torrent file without path")
    }

    return TorrentFile(size, pathElements)
}

private const val UNDEFINED_TORRENT_NAME: String = "undefined"
private const val CHUNK_HASH_LENGTH = 20


internal data class Torrent(
    val name: String,
    val chunks: List<Chunk>,
    val files: List<TorrentFile>,
    val size: Long,
    val chunkSize: Int,
    val isPrivate: Boolean,
    val createdBy: String,
    val singleFile: Boolean
)

private fun buildChunks(
    hashes: List<ByteArray>,
    files: List<TorrentFile>,
    size: Long, chunkSize: Int
): List<Chunk> {
    val chunks: MutableList<Chunk> = mutableListOf()
    var startRange = 0L
    files.forEach { file ->
        file.startPosition(startRange)
        startRange += file.size
    }

    var blockSize = BLOCK_SIZE
    if (blockSize > chunkSize) {
        blockSize = chunkSize
    }

    var chunk = 0
    var off: Long
    var lim: Long
    var remaining = size
    while (remaining > 0) {
        off = (chunk * chunkSize).toLong()
        lim = min(chunkSize.toLong(), remaining)

        val maxPos = lim + off

        files.forEach { file ->
            val startPos = file.startPosition()
            val endPos = file.endPosition()
            if (startPos <= maxPos) {
                if (off <= endPos) {
                    file.addPiece(chunk)
                }
            }
        }

        chunks.add(Chunk(lim.toInt(), blockSize, hashes[chunk]))
        chunk++

        remaining -= chunkSize
    }
    require(hashes.size == chunks.size) { "Hashes size is not equal chunks size" }
    return chunks
}

private fun buildHashes(hashes: ByteArray): List<ByteArray> {
    val result: MutableList<ByteArray> = mutableListOf()
    var read = 0
    while (read < hashes.size) {
        val start = read
        read += CHUNK_HASH_LENGTH
        result.add(hashes.copyOfRange(start, read))
    }
    return result
}

internal fun buildTorrent(bytes: ByteArray): Torrent {
    require(bytes.isNotEmpty()) { "Can't parse bytes array: null or empty" }
    val reader = BEReader(bytes, bytes.size)

    val root = (reader.decodeBencode() as BEMap).toMap()

    val infoMap = if (root.containsKey(INFOMAP_KEY)) {
        // standard BEP-3 format
        (root[INFOMAP_KEY] as BEMap).toMap()
    } else {
        // BEP-9 exchanged metadata (just the info dictionary)
        root
    }


    var name = UNDEFINED_TORRENT_NAME
    if (infoMap[TORRENT_NAME_KEY] != null) {
        name = (checkNotNull(
            infoMap[TORRENT_NAME_KEY]
        ) as BEString).toString()
    }

    val chunkSize = (checkNotNull(
        infoMap[CHUNK_SIZE_KEY]
    ) as BEInteger).toLong()


    val chunkHashes =
        (checkNotNull(infoMap[CHUNK_HASHES_KEY]) as BEString).toByteArray()


    val torrentFiles: MutableList<TorrentFile> = mutableListOf()
    val size: Long
    if (infoMap[TORRENT_SIZE_KEY] != null) {
        val torrentSize = (checkNotNull(
            infoMap[TORRENT_SIZE_KEY]
        ) as BEInteger).toLong()
        size = torrentSize
    } else {
        val files =
            (checkNotNull(infoMap[FILES_KEY]) as BEList).toList()
        var torrentSize = 0L
        for (data in files) {
            val file = data as BEMap
            val fileMap = file.toMap()

            val fileSize = (checkNotNull(
                fileMap[FILE_SIZE_KEY]
            ) as BEInteger).toLong()

            torrentSize += fileSize

            val objectList = (checkNotNull(fileMap[FILE_PATH_ELEMENTS_KEY]) as BEList).toList()

            val pathElements: MutableList<BEString> = mutableListOf()
            objectList.forEach { beObject: BEObject ->
                pathElements.add(
                    beObject as BEString
                )
            }

            torrentFiles.add(
                createTorrentFile(
                    fileSize, pathElements
                        .map { obj: BEString -> obj.toString() })
            )
        }

        size = torrentSize
    }
    var isPrivate = false
    if (infoMap[PRIVATE_KEY] != null) {
        if (1 == (checkNotNull(
                infoMap[PRIVATE_KEY]
            ) as BEInteger).toInt()
        ) {
            isPrivate = true
        }
    }

    var createdBy = ""

    if (root[CREATED_BY_KEY] != null) {
        createdBy = (checkNotNull(
            root[CREATED_BY_KEY]
        ) as BEString).toString()
    }
    return createTorrent(
        name, torrentFiles,
        chunkHashes, size, chunkSize.toInt(),
        isPrivate, createdBy
    )
}

const val INFOMAP_KEY: String = "info"
const val TORRENT_NAME_KEY: String = "name"
const val CHUNK_SIZE_KEY: String = "piece length"
const val CHUNK_HASHES_KEY: String = "pieces"
const val TORRENT_SIZE_KEY: String = "length"
const val FILES_KEY: String = "files"
const val FILE_SIZE_KEY: String = "length"
const val FILE_PATH_ELEMENTS_KEY: String = "path"
const val PRIVATE_KEY: String = "private"
const val CREATED_BY_KEY: String = "created by"


internal fun createTorrent(
    name: String, torrentFiles: MutableList<TorrentFile>, chunkHashes: ByteArray,
    size: Long, chunkSize: Int, isPrivate: Boolean, createdBy: String
): Torrent {
    require(chunkHashes.size.mod(CHUNK_HASH_LENGTH) == 0) {
        "Invalid chunk hashes string -- length (" + chunkHashes.size +
                ") is not divisible by " + CHUNK_HASH_LENGTH
    }
    var singleFile = false
    if (torrentFiles.isEmpty()) {
        torrentFiles.add(createTorrentFile(size, listOf(name)))
        singleFile = true
    }

    val hashes = buildHashes(chunkHashes)
    val chunks = buildChunks(hashes, torrentFiles, size, chunkSize)

    val torrent = Torrent(
        name, chunks, torrentFiles, size, chunkSize, isPrivate, createdBy, singleFile
    )

    return torrent
}


internal fun key(hi: Int, lo: Int): Long {
    return ((hi.toLong() shl 32) or lo.toLong())
}


fun String.normalizeForFileName(replacementChar: String = "_"): String {
    // 1. Trim whitespace
    var normalized = this.trim()

    // Handle empty string after trim
    if (normalized.isEmpty()) {
        return replacementChar // Or some default name like "default_filename"
    }

    // 2. Define invalid characters (adjust as needed for your target systems)
    //    Common invalid chars: / \ : * ? " < > |
    //    Control characters (0-31 and 127) should also be removed.
    val invalidCharsRegex = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    normalized = normalized.replace(invalidCharsRegex, replacementChar)

    // 3. Replace multiple occurrences of the replacement character with a single one
    normalized = normalized.replace(Regex("$replacementChar{2,}"), replacementChar)

    // 4. Handle leading/trailing replacement characters (optional, but good practice)
    normalized = normalized.removePrefix(replacementChar).removeSuffix(replacementChar)


    // 5. Handle names that are just dots (or became dots after normalization)
    if (normalized == "." || normalized == "") {
        return replacementChar + normalized + replacementChar // e.g., "_._" or "_.._"
    }

    // Re-check for empty after all replacements
    if (normalized.isEmpty()) {
        return replacementChar
    }

    // 6. Optional: Truncate to a max length (e.g., 255 is common for many filesystems)
    // val maxLength = 250 // Be conservative
    // if (normalized.length > maxLength) {
    //     normalized = normalized.take(maxLength)
    //     // Potentially re-trim if truncation creates leading/trailing replacement chars
    //     normalized = normalized.removePrefix(replacementChar).removeSuffix(replacementChar)
    //     if (normalized.isEmpty()) return replacementChar
    // }


    return normalized
}


internal fun relPaths(torrentFile: TorrentFile): List<String> {
    val paths: MutableList<String> = mutableListOf()
    for (path in torrentFile.pathElements) {
        val normalized = path.normalizeForFileName()
        paths.add(normalized)
    }
    return paths
}

internal fun getFilePath(root: Path, torrentFile: TorrentFile): Path {
    require(SystemFileSystem.exists(root)) { "Root directory does not exists" }
    var pathToFile = root
    for (path in torrentFile.pathElements) {
        val normalizePath = path.normalizeForFileName()
        pathToFile = Path(pathToFile, normalizePath)
    }
    if (SystemFileSystem.exists(pathToFile)) {
        return pathToFile
    }
    SystemFileSystem.createDirectories(pathToFile.parent!!)
    return pathToFile
}


internal fun createBlockSet(chunkSize: Int, blockSize: Int): BlockSet {
    // intentionally allow length to be greater than block size
    require(!(chunkSize < 0 || blockSize < 0)) {
        "Illegal arguments: length ($chunkSize), block size ($blockSize)"
    }

    val blockCount = ceil((chunkSize.toDouble()) / blockSize).toInt()
    require(blockCount <= Int.MAX_VALUE) {
        "Too many blocks: length (" + chunkSize +
                "), block size (" + blockSize + "), total blocks (" + blockCount + ")"
    }

    // handle the case when the last block is smaller than the others
    var lastBlockSize: Int = (chunkSize % blockSize)
    val lastBlockOffset: Int
    if (lastBlockSize > 0) {
        lastBlockOffset = chunkSize - lastBlockSize
    } else {
        lastBlockSize = blockSize
        lastBlockOffset = chunkSize - blockSize
    }
    return BlockSet(
        chunkSize, blockSize, blockCount, lastBlockSize, lastBlockOffset, Bitmask(blockCount)
    )
}

