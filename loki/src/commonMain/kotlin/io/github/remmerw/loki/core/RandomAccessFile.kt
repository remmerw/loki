package io.github.remmerw.loki.core

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.bytestring.getByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi

const val SPLITTER = 4096L

interface RandomAccessFile : AutoCloseable {
    fun read(
        position: Long,
        bytes: ByteArray,
    ): Int

    fun writeBoolean(
        position: Long,
        boolean: Boolean,
    )

    fun readBytes(
        position: Long,
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    )

    fun writeBytes(
        position: Long,
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    )

    fun writeMemory(
        position: Long,
        memory: Memory,
    )

    fun transferTo(
        position: Long,
        sink: Sink,
        length: Long,
    )

    override fun close()
}

interface Memory {
    fun writeBytes(
        bytes: ByteArray,
        offset: Int,
    )

    fun size(): Int

    fun readBytes(
        offset: Int,
        length: Int,
    ): ByteArray

    fun transferTo(sink: RawSink) {
        rawSource().buffered().transferTo(sink)
    }

    fun rawSource(): RawSource
}

fun allocateMemory(bytes: ByteArray): Memory {
    val memory = allocateMemory(bytes.size)
    memory.writeBytes(bytes, 0)
    return memory
}

fun allocateMemory(path: Path): Memory {
    require(SystemFileSystem.exists(path)) { "path does not exists" }
    val size = SystemFileSystem.metadataOrNull(path)!!.size.toInt()
    val memory = allocateMemory(size)
    val sink = Buffer()
    var offset = 0
    SystemFileSystem.source(path).use { source ->
        do {
            val read = source.readAtMostTo(sink, UShort.MAX_VALUE.toLong())
            if (read > 0) {
                memory.writeBytes(sink.readByteArray(), offset)
                offset += read.toInt()
            }
        } while (read > 0)
    }
    return memory
}

private class RandomAccessFileImpl(
    val raf: RandomAccessFile,
) : io.github.remmerw.loki.core.RandomAccessFile {
    override fun read(
        position: Long,
        bytes: ByteArray,
    ): Int {
        raf.seek(position)
        return raf.read(bytes)
    }

    override fun writeBoolean(
        position: Long,
        boolean: Boolean,
    ) {
        raf.seek(position)
        raf.writeBoolean(boolean)
    }

    override fun readBytes(
        position: Long,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        raf.seek(position)
        raf.read(bytes, offset, length)
    }

    override fun writeBytes(
        position: Long,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        raf.seek(position)
        raf.write(bytes, offset, length)
    }

    override fun writeMemory(
        position: Long,
        memory: Memory,
    ) {
        raf.seek(position)
        val buffer = Buffer()
        memory.rawSource().use { source ->
            do {
                val written = source.readAtMostTo(buffer, SPLITTER)
                if (written > 0) {
                    raf.write(buffer.readByteArray())
                }
            } while (written > 0)
        }
    }

    override fun transferTo(
        position: Long,
        sink: Sink,
        length: Long,
    ) {
        raf.seek(position)
        val data = ByteArray(SPLITTER.toInt())
        var stillToRead = length
        var read: Int
        var todo: Boolean
        var min: Int
        do {
            read = raf.read(data)
            todo = read > 0 && stillToRead > 0
            if (todo) {
                min = min(read.toLong(), stillToRead).toInt()
                sink.write(data, 0, min)
                stillToRead -= read
            }
        } while (todo)
    }

    override fun close() {
        raf.close()
    }
}

internal class MemoryImpl(
    val memory: ByteBuffer,
    val size: Int,
) : Memory {
    override fun size(): Int = size

    override fun readBytes(
        offset: Int,
        length: Int,
    ): ByteArray {
        memory.rewind()
        memory.position(offset)
        val result = ByteArray(length)
        memory.get(result)
        return result
    }

    override fun writeBytes(
        bytes: ByteArray,
        offset: Int,
    ) {
        memory.rewind()
        memory.position(offset)
        memory.put(bytes)
    }

    override fun rawSource(): RawSource {
        memory.rewind()

        return object : RawSource {
            override fun readAtMostTo(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                val read = min(byteCount, memory.remaining().toLong())
                if (read > 0) {
                    val data = memory.getByteString(read.toInt())
                    sink.write(data.toByteArray())
                    return read
                } else {
                    return -1
                }
            }

            override fun close() {
                // nothing to do
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
fun allocateMemory(size: Int): Memory {
    val memory = ByteBuffer.allocateDirect(size)

    return MemoryImpl(memory, size)
}

fun randomAccessFile(path: Path): io.github.remmerw.loki.core.RandomAccessFile {
    val raf = RandomAccessFile(path.toString(), "rw")
    return RandomAccessFileImpl(raf)
}

internal fun debug(throwable: Throwable) {
    if (ERROR) {
        throwable.printStackTrace()
    }
}

private const val ERROR: Boolean = true