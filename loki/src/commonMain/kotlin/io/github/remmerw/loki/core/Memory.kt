package io.github.remmerw.loki.core

import kotlinx.io.RawSink
import kotlinx.io.buffered
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min
import java.io.FileOutputStream


fun ByteBuffer.transferTo(filePath: String) {
    rewind()

    FileOutputStream(filePath).use { fos ->
        fos.channel.use { channel ->
            while (this.hasRemaining()) {
                channel.write(this)
            }
        }
    }
}


internal fun ByteBuffer.toSha1(): ByteArray {
    rewind()
    val md = MessageDigest.getInstance("SHA-1")
    md.update(this)
    return md.digest()
}

internal fun ByteBuffer.readMemory(
    offset: Int,
    length: Int,
): ByteArray {
    rewind()
    position(offset)
    val result = ByteArray(length)
    get(result)
    return result
}

internal fun ByteBuffer.writeMemory(
    bytes: ByteArray,
    offset: Int,
) {
    rewind()
    position(offset)
    put(bytes)
}

internal fun ByteBuffer.transferTo(sink: RawSink) {
    rewind()
    val bufferedSink = sink.buffered()

    val bufferSize = 8192
    val tempArray = ByteArray(min(remaining(), bufferSize))

    while (hasRemaining()) {
        val bytesToRead = min(remaining(), tempArray.size)
        get(tempArray, 0, bytesToRead)
        bufferedSink.write(tempArray, 0, bytesToRead)
    }

    bufferedSink.flush()
}
