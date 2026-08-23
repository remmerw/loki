package io.github.remmerw.loki.core

import kotlinx.io.Sink
import kotlinx.io.files.Path
import java.io.RandomAccessFile
import kotlin.math.min

const val SPLITTER = 4096L

fun randomAccessFile(path: Path): RandomAccessFile = RandomAccessFile(path.toString(), "rw")

fun RandomAccessFile.read(
    position: Long,
    bytes: ByteArray,
): Int {
    seek(position)
    return read(bytes)
}

fun RandomAccessFile.writeBoolean(
    position: Long,
    boolean: Boolean,
) {
    seek(position)
    writeBoolean(boolean)
}

fun RandomAccessFile.readBytes(
    position: Long,
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
) {
    seek(position)
    read(bytes, offset, length)
}

fun RandomAccessFile.writeBytes(
    position: Long,
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
) {
    seek(position)
    write(bytes, offset, length)
}

fun RandomAccessFile.transferTo(
    position: Long,
    sink: Sink,
    length: Long,
) {
    seek(position)
    val data = ByteArray(SPLITTER.toInt())
    var stillToRead = length

    while (stillToRead > 0) {
        val toRead = min(SPLITTER, stillToRead).toInt()
        val read = read(data, 0, toRead)

        if (read == -1) break

        sink.write(data, 0, read)
        stillToRead -= read
    }
}
