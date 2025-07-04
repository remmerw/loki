package io.github.remmerw.loki.core

import io.github.remmerw.grid.Memory
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


class Data(private val directory: Path) {
    fun directory(): Path {
        return directory
    }

    fun reset() {
        cleanupDirectory(directory)
    }


    fun rawSource(cid: Int): RawSource {
        val file = path(cid)
        require(SystemFileSystem.exists(file)) { "Block does not exists" }
        return SystemFileSystem.source(file)
    }

    fun storeBlock(cid: Int, memory: Memory) {
        val file = path(cid)
        SystemFileSystem.sink(file, false).use { source ->
            memory.transferTo(source)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    fun path(cid: Int): Path {
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

