package io.github.remmerw.loki.core

import io.ktor.utils.io.core.writeFully
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class StorageUnit internal constructor(
    private val data: Data,
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

    @Suppress("unused")
    fun relPaths(): List<String> {
        return relPaths
    }

    fun size(): Long {
        return torrentFile.size
    }

    fun transferTo(sink: Sink) {
        while (index <= lastIndex) {
            val piece = torrentFile.pieces[index]
            try {
                if (index == 0) {
                    val offset = startPos.rem(chunkSize)
                    if (lastIndex == 0) {
                        val remaining = endPos.rem(chunkSize) - offset
                        data.rawSource(piece).buffered().use { source ->
                            source.skip(offset)
                            val bytes = source.readByteArray(remaining.toInt())
                            sink.writeFully(bytes)
                        }
                    } else {
                        data.rawSource(piece).buffered().use { source ->
                            source.skip(offset)
                            source.transferTo(sink)
                        }
                    }
                } else if (index == lastIndex) {
                    val remaining = endPos.rem(chunkSize)
                    data.rawSource(piece).buffered().use { source ->
                        val bytes = source.readByteArray(remaining.toInt())
                        sink.writeFully(bytes)
                    }
                } else {
                    data.rawSource(piece).buffered().use { source ->
                        source.transferTo(sink)
                    }
                }
            } finally {
                index++
            }
        }

    }

    fun storeTo(directory: Path) {
        val file = getFilePath(directory, torrentFile)
        SystemFileSystem.sink(file, false).buffered().use { sink ->
            transferTo(sink)
        }
    }
}
