package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.bencode
import kotlinx.io.Buffer

internal data class UtMetadata(
    val metaType: MetaType,
    val pieceIndex: Int,
    val totalSize: Int,
    val data: ByteArray,
) : ExtendedMessage {
    override val type: Type
        get() = Type.UtMetadata

    constructor(type: MetaType, pieceIndex: Int) : this(
        type,
        pieceIndex,
        0,
        byteArrayOf(),
    )

    init {
        require(pieceIndex >= 0) { "Invalid piece index: $pieceIndex" }
        require(totalSize >= 0) { "Invalid total size: $totalSize" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtMetadata) return false

        if (pieceIndex != other.pieceIndex) return false
        if (totalSize != other.totalSize) return false
        if (metaType != other.metaType) return false
        if (!data.contentEquals(other.data)) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pieceIndex
        result = 31 * result + totalSize
        result = 31 * result + metaType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    fun encode(buffer: Buffer) {

        val sink = io.github.remmerw.buri.Buffer(1024)
        
        sink.bencodeMap()
        
        sink.bencodeMapKey("msg_type")
        sink.bencode(metaType.id.)
        sink.bencodeMapKey("piece")
        sink.bencode(pieceIndex)
        if (totalSize > 0) {
            sink.bencodeMapKey("total_size")
            sink.bencode(totalSize)
        }

        sink.bencodeEof()

        buffer.write(sink.data,0,sink.length)

        if (data.isNotEmpty()) {
            buffer.write(data)
        }
    }
}
