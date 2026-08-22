package io.github.remmerw.loki.core

import java.nio.ByteBuffer
import java.security.MessageDigest
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
        rawSource().buffered().transferTo(sink)
    }


        internal fun ByteBuffer.rawSource(): RawSource {
        rewind()

        return object : RawSource {
            override fun readAtMostTo(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                val read = min(byteCount, remaining().toLong())
                if (read > 0) {
                    val data = getByteString(read.toInt())
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
