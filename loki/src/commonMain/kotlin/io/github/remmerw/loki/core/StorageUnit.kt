package io.github.remmerw.loki.core

import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class StorageUnit internal constructor(
    private val database: Path,
    private val torrentFile: TorrentFile,
) {
    private val relPaths: List<String> = relPaths(torrentFile)
    private val startPos = torrentFile.startPosition()

    @Suppress("unused")
    fun name(): String = relPaths.last()

    @Suppress("unused")
    fun relPaths(): List<String> = relPaths

    fun size(): Long = torrentFile.size

    fun transferTo(sink: Sink) {
        randomAccessFile(database).use { database ->
            database.transferTo(startPos, sink, size())
        }
    }

    fun storeTo(directory: Path) {
        val file = getFilePath(directory, torrentFile)
        SystemFileSystem.sink(file, false).buffered().use { sink ->
            transferTo(sink)
        }
    }
}
