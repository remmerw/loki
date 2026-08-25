package io.github.remmerw.loki.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

internal fun directBuffer(size: Int): ByteBuffer = ByteBuffer.allocateDirect(size)

internal fun directBuffer(filePath: String): ByteBuffer {
    val file = File(filePath)

    val buffer = directBuffer(file.length().toInt())

    FileInputStream(file).use { fis ->
        fis.channel.use { channel ->
            while (channel.read(buffer) != -1) {
            }
        }
    }

    return buffer
}

internal fun ByteBuffer.transferTo(filePath: String) {
    rewind()

    FileOutputStream(filePath).use { fos ->
        fos.channel.use { channel ->
            while (this.hasRemaining()) {
                channel.write(this)
            }
        }
    }
}

internal fun ByteBuffer.getBitmask(piecesTotal: Int): Bitmask {
    val bitmask = Bitmask(piecesTotal)
    var bitPos = 0
    while (hasRemaining()) {
        val b = get().toInt() and 0xFF
        for (bitInByte in 0..7) {
            if (bitPos >= piecesTotal) break
            if ((b and (1 shl (7 - bitInByte))) != 0) {
                bitmask.set(bitPos)
            }
            bitPos++
        }
    }
    return bitmask
}

internal fun ByteBuffer.getByteArray(size: Int): ByteArray {
    val data = ByteArray(size)
    this.get(data)
    return data
}

internal fun ByteBuffer.toSha1(): ByteArray {
    rewind()
    val md = MessageDigest.getInstance("SHA-1")
    md.update(this)
    return md.digest()
}

internal fun ByteBuffer.getByteArrayAt(
    offset: Int,
    length: Int,
): ByteArray {
    rewind()
    position(offset)
    val result = ByteArray(length)
    get(result)
    return result
}

internal fun ByteBuffer.putAt(
    offset: Int,
    bytes: ByteArray,
) {
    rewind()
    position(offset)
    put(bytes)
}