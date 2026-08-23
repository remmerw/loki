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

   

    fun transferTo(
        position: Long,
        sink: Sink,
        length: Long,
    )

    override fun close()
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



fun randomAccessFile(path: Path): io.github.remmerw.loki.core.RandomAccessFile {
    val raf = RandomAccessFile(path.toString(), "rw")
    return RandomAccessFileImpl(raf)
}

