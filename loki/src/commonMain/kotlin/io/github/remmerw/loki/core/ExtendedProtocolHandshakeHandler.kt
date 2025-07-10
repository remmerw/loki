package io.github.remmerw.loki.core

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.bencode
import io.github.remmerw.loki.data.ExtendedHandshake
import io.github.remmerw.loki.data.ExtendedMessageHandler
import io.github.remmerw.loki.data.Handshake

internal data class ExtendedProtocolHandshakeHandler(
    private val dataStorage: DataStorage,
    private val extendedMessages: List<ExtendedMessageHandler>,
    private val tcpAcceptorPort: Int,
    private val version: String,
) : HandshakeHandler {

    private val lazyHandshake = lazy { buildHandshake() }

    private fun buildHandshake(): ExtendedHandshake {
        val data: MutableMap<String, BEObject> = mutableMapOf()
        val messageTypeMap: MutableMap<String, BEObject> = mutableMapOf()


        data["e"] = 0L.bencode() // require no encryption (1 is encryption)
        data["p"] = tcpAcceptorPort.bencode()

        if (dataStorage.metadataSize() > 0) {
            data["metadata_size"] = dataStorage.metadataSize().bencode()
        }

        data["v"] = version.bencode()

        extendedMessages.forEach { handler: ExtendedMessageHandler ->

            if (messageTypeMap.containsKey(handler.localName())) {
                throw RuntimeException("Message type already defined: $handler.localName()")
            }

            messageTypeMap[handler.localName()] = handler.localTypeId().bencode()

        }
        if (!messageTypeMap.isEmpty()) {
            data["m"] = messageTypeMap.bencode()
        }
        return ExtendedHandshake(data.toMap())

    }

    override suspend fun processIncomingHandshake(connection: Connection) {
        val extendedHandshake = lazyHandshake.value
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (extendedHandshake.data.isNotEmpty()) {
            connection.posting(extendedHandshake, byteArrayOf())
        }
    }

    override fun processOutgoingHandshake(handshake: Handshake) {
        val extendedHandshake = lazyHandshake.value
        // do not advertise support for the extended protocol
        // if local client does not have any extensions turned on
        if (extendedHandshake.data.isNotEmpty()) {
            handshake.setReservedBit(43)
        }
    }

}