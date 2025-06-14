package io.github.remmerw.loki.core

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

    fun readBlockSlice(cid: Int, offset: Int, length: Int): ByteArray {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        SystemFileSystem.source(file).buffered().use { source ->
            source.skip(offset.toLong())
            return source.readByteArray(length)
        }
    }

    fun readBlockSlice(cid: Int, offset: Int): ByteArray {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        SystemFileSystem.source(file).buffered().use { source ->
            source.skip(offset.toLong())
            return source.readByteArray()
        }
    }

    fun storeBlock(cid: Int, bytes: List<ByteArray>) {
        val file = path(cid)
        SystemFileSystem.sink(file, false).buffered().use { source ->
            bytes.forEach { data -> source.write(data) }
        }
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

