package io.github.remmerw.loki.data

import io.github.remmerw.loki.benc.BEInteger
import io.github.remmerw.loki.benc.BEMap
import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.Bencode
import kotlinx.io.Buffer

/**
 * Extended handshake is sent during connection initialization procedure
 * by peers that support BEP-10: Extension Protocol.
 * It contains a dictionary of supported extended message types with
 * their corresponding numeric IDs, as well as any additional information,
 * that is specific to concrete BitTorrent clients and BEPs,
 * that utilize extended messaging.
 *
 */
internal data class ExtendedHandshake(val data: Map<String, BEObject>) : ExtendedMessage {


    override val type: Type
        get() = Type.ExtendedHandshake

    /**
     * @return Set of message type names, that are specified
     * in this handshake's message type mapping.
     */
    var supportedMessageTypes: Set<String>

    init {
        val supportedMessageTypes = data["m"] as BEMap?
        if (supportedMessageTypes != null) {
            this.supportedMessageTypes = supportedMessageTypes.toMap().keys.toSet()
        } else {
            this.supportedMessageTypes = emptySet()
        }
    }

    val port: BEInteger?
        /**
         * @return TCP port or null, if absent in message data
         */
        get() = data["p"] as BEInteger?

    fun encode(buffer: Buffer) {
        Bencode.encodeMap(data, buffer) // todo

    }
}
