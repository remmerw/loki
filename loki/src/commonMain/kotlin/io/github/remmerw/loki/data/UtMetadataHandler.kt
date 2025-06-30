package io.github.remmerw.loki.data

import io.github.remmerw.loki.buri.BEInteger
import io.github.remmerw.loki.buri.BEObject
import io.github.remmerw.loki.buri.decode
import io.github.remmerw.loki.buri.encode
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal class UtMetadataHandler : ExtendedMessageHandler {
    override fun supportedTypes(): Collection<Type> = setOf(
        Type.UtMetadata
    )

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val utMetadata = message as UtMetadata

        return encodeMetadata(utMetadata, buffer)
    }

    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        return decodeMetadata(buffer)
    }

    override fun localTypeId(): Byte {
        return 2
    }

    override fun localName(): String {
        return "ut_metadata"
    }


    private fun decodeMetadata(buffer: Buffer): Message {

        val map = decode(buffer)
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

    private fun encodeMetadata(utMetadata: UtMetadata, buffer: Buffer) {
        writeMessage(utMetadata, buffer)
    }

    private fun writeMessage(message: UtMetadata, buffer: Buffer) {
        val map = mutableMapOf<String, BEObject>()

        map.put(
            "msg_type", BEInteger(
                message.metaType.id.toLong()
            )
        )
        map.put(
            "piece", BEInteger(
                message.pieceIndex.toLong()
            )
        )
        if (message.totalSize > 0) {
            map.put(
                "total_size", BEInteger(
                    message.totalSize.toLong()
                )
            )
        }

        encode(map, buffer)

        if (message.data.isNotEmpty()) {
            buffer.write(message.data)
        }
    }

    private fun getMessageType(map: Map<String, BEObject>): MetaType {
        val type = map["msg_type"] as BEInteger?
        val typeId = checkNotNull(type).value.toInt()
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
        return value.value.toInt()
    }
}
