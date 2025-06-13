package io.github.remmerw.loki.core

import io.ktor.utils.io.core.writeFully
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class StorageUnit internal constructor(
    private val dataStorage: DataStorage,
    private val chunkSize: Int,
    private val torrentFile: TorrentFile
) {
    private val relPaths: List<String> = relPaths(torrentFile)
    private val startPos = torrentFile.startPosition()
    private val endPos = torrentFile.endPosition()
    private val lastIndex = torrentFile.pieces.size - 1
    private var index = 0

    fun name(): String {
        return relPaths.last()
    }

    fun relPaths(): List<String> {
        return relPaths
    }

    fun size(): Long {
        return torrentFile.size
    }

    fun hasData(): Boolean {
        return index <= lastIndex
    }

    fun data(): ByteArray {
        val piece = torrentFile.pieces[index]
        try {
            if (index == 0) {
                val offset = startPos.rem(chunkSize)
                if (lastIndex == 0) {
                    val remaining = endPos.rem(chunkSize) - offset
                    return dataStorage.readBlock(piece, offset.toInt(), remaining.toInt())
                } else {
                    return dataStorage.readBlock(piece, offset.toInt())
                }
            } else if (index == lastIndex) {
                val remaining = endPos.rem(chunkSize)
                return dataStorage.readBlock(piece, 0, remaining.toInt())
            } else {
                return dataStorage.readBlock(piece, 0)
            }
        } finally {
            index++
        }
    }

    fun storeTo(directory: Path) {

        val file = getFilePath(directory, torrentFile)
        SystemFileSystem.sink(file, false).buffered().use { sink ->
            while (hasData()) {
                sink.writeFully(data())
            }
        }
    }
}
