package io.github.remmerw.loki.data

import io.github.remmerw.loki.benc.BEInteger
import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.decodeToMap
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal class UtMetadataHandler : ExtendedMessageHandler {
    override fun supportedTypes(): Collection<Type> = setOf(
        Type.UtMetadata
    )

    override fun doEncode(message: ExtendedMessage, buffer: Buffer) {
        val utMetadata = message as UtMetadata

        return utMetadata.encode(buffer)
    }

    override fun doDecode(address: InetSocketAddress, buffer: Buffer): ExtendedMessage {
        return decodeMetadata(buffer)
    }

    override fun localTypeId(): Byte {
        return 2
    }

    override fun localName(): String {
        return "ut_metadata"
    }


    private fun decodeMetadata(buffer: Buffer): ExtendedMessage {

        val map = decodeToMap(buffer)
        val messageType = getMessageType(map)
        val pieceIndex = getPieceIndex(map)
        val totalSize = getTotalSize(map)
        return when (messageType) {
            MetaType.REQUEST -> {
                UtMetadata(MetaType.REQUEST, pieceIndex)
            }

            MetaType.DATA -> {
                UtMetadata(MetaType.DATA, pieceIndex, totalSize, buffer.readByteArray())
            }

            MetaType.REJECT -> {
                UtMetadata(MetaType.REJECT, pieceIndex)
            }
        }

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

    private fun getPieceIndex(m: Map<String, BEObject>): Int {
        return getIntAttribute("piece", m)
    }

    private fun getTotalSize(m: Map<String, BEObject>): Int {
        return getIntAttribute("total_size", m)
    }

    private fun getIntAttribute(name: String, m: Map<String, BEObject>): Int {
        val value = (m[name] as BEInteger?)
        checkNotNull(value) { "Message attribute is missing: $name" }
        return value.toInt()
    }
}
