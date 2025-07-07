package io.github.remmerw.loki.core

import io.github.remmerw.loki.benc.BEInteger
import io.github.remmerw.loki.benc.BEMap
import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.BEString
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


        data["e"] = BEInteger(0) // require no encryption (1 is encryption)
        data["p"] = BEInteger(tcpAcceptorPort.toLong())

        if (dataStorage.metadataSize() > 0) {
            data["metadata_size"] = BEInteger(dataStorage.metadataSize().toLong())
        }

        data["v"] = BEString(version.encodeToByteArray())

        extendedMessages.forEach { handler: ExtendedMessageHandler ->

            if (messageTypeMap.containsKey(handler.localName())) {
                throw RuntimeException("Message type already defined: $handler.localName()")
            }

            messageTypeMap[handler.localName()] = BEInteger(handler.localTypeId().toLong())

        }
        if (!messageTypeMap.isEmpty()) {
            data["m"] = BEMap(messageTypeMap)
        }
        return ExtendedHandshake(data.toMap())

    }

    override suspend fun processIncomingHandshake(connection: Connection) {
        val extendedHandshake = lazyHandshake.value
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (extendedHandshake.data.isNotEmpty()) {
            connection.posting(extendedHandshake)
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