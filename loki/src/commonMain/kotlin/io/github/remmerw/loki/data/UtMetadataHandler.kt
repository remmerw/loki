package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEInteger
import io.github.remmerw.buri.BEMap
import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.BEReader
import io.github.remmerw.buri.decodeBencode
import io.github.remmerw.nott.Address
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray

internal class UtMetadataHandler : ExtendedMessageHandler {
    override fun supportedTypes(): Collection<Type> =
        setOf(
            Type.UtMetadata,
        )

    override fun doEncode(
        message: ExtendedMessage,
        sink: Sink,
    ) {
        message as UtMetadata

        message.encode(sink)
    }

    override fun doDecode(
        address: Address,
        reader: BEReader,
    ): ExtendedMessage = decodeMetadata(reader)

    override fun localTypeId(): Byte = 2

    override fun localName(): String = "ut_metadata"

    private fun decodeMetadata(reader: BEReader): ExtendedMessage {
        val map = (reader.decodeBencode() as BEMap).toMap()
        val messageType = getMessageType(map)
        val pieceIndex = getPieceIndex(map)
        val totalSize = getTotalSize(map)
        return when (messageType) {
            MetaType.REQUEST -> {
                UtMetadata(MetaType.REQUEST, pieceIndex)
            }

            MetaType.DATA -> {
                UtMetadata(MetaType.DATA, pieceIndex, totalSize, readByteArray(reader))
            }

            MetaType.REJECT -> {
                UtMetadata(MetaType.REJECT, pieceIndex)
            }
        }
    }

    private fun readByteArray(reader: BEReader): ByteArray {
        val buffer = reader.data
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return data
    }

    private fun getMessageType(map: Map<String, BEObject>): MetaType {
        val type = map["msg_type"] as BEInteger?
        val typeId = checkNotNull(type).toInt()
        return metaTypeForId(typeId)
    }

    private fun metaTypeForId(id: Int): MetaType {
        for (type in MetaType.entries) {
            if (type.id == id) {
                return type
            }
        }
        throw IllegalArgumentException("Unknown message id: $id")
    }

    private fun getPieceIndex(m: Map<String, BEObject>): Int = getIntAttribute("piece", m)

    private fun getTotalSize(m: Map<String, BEObject>): Int = getIntAttribute("total_size", m)

    private fun getIntAttribute(
        name: String,
        m: Map<String, BEObject>,
    ): Int {
        val value = (m[name] as BEInteger?)
        checkNotNull(value) { "Message attribute is missing: $name" }
        return value.toInt()
    }
}
