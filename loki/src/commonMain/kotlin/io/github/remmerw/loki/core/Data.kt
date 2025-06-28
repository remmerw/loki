package io.github.remmerw.loki.core

import io.github.remmerw.grid.Memory
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray


class Data(private val directory: Path) {
    fun directory(): Path {
        return directory
    }

    fun reset() {
        cleanupDirectory(directory)
    }

    // todo random file access
    fun readBlockSlice(cid: Int, offset: Int, length: Int): ByteArray {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        SystemFileSystem.source(file).buffered().use { source ->
            source.skip(offset.toLong())
            return source.readByteArray(length)
        }
    }

    // todo random file access
    fun readBlockSlice(cid: Int, offset: Int): ByteArray {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        SystemFileSystem.source(file).buffered().use { source ->
            source.skip(offset.toLong())
            return source.readByteArray()
        }
    }

    fun storeBlock(cid: Int, memory: Memory) {
        val file = path(cid)
        SystemFileSystem.sink(file, false).use { source ->
            memory.transferTo(source)
        }
    }

    fun deleteBlock(cid: Int) {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        SystemFileSystem.delete(file, true)
    }


    @OptIn(ExperimentalStdlibApi::class)
    private fun path(cid: Int): Path {
        return Path(directory, cid.toHexString())
    }
}


fun cleanupDirectory(dir: Path) {
    if (SystemFileSystem.exists(dir)) {
        val files = SystemFileSystem.list(dir)
        for (file in files) {
            SystemFileSystem.delete(file)
        }
    }
}

fun newData(directory: Path): Data {
    SystemFileSystem.createDirectories(directory)
    require(
        SystemFileSystem.metadataOrNull(directory)?.isDirectory == true
    ) {
        "Path is not a directory."
    }
    return Data(directory)
}

