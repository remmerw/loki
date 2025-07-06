package io.github.remmerw.loki.data

import io.github.remmerw.loki.buri.BEInteger
import io.github.remmerw.loki.buri.BEMap
import io.github.remmerw.loki.buri.BEObject
import io.github.remmerw.loki.buri.decode
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer

internal class ExtendedHandshakeHandler : MessageHandler {
    override fun supportedTypes(): Collection<Type> = setOf(Type.ExtendedHandshake)

    private val peerTypeMappings: MutableMap<InetSocketAddress, MutableMap<Int, String>> =
        mutableMapOf()


    private fun processTypeMapping(peer: InetSocketAddress, mappingObj: BEObject?) {
        if (mappingObj == null) {
            return
        }

        require(mappingObj is BEMap) { "Extended message type must be a dictionary." }

        val mapping = mappingObj.map
        if (mapping.isNotEmpty()) {
            // according to BEP-10, peers are only required to send a delta of changes
            // on subsequent handshakes, so we need to store all mappings received from the peer
            // and merge the changes..
            //
            // subsequent handshake messages can be used to enable/disable extensions
            // without restarting the connection
            val previous = peerTypeMappings.getOrElse(peer) { mutableMapOf() }
            peerTypeMappings[peer] = mergeMappings(previous, mapping)
        }
    }

    fun getPeerTypeMapping(peer: InetSocketAddress): Map<Int, String> {
        val mapping: Map<Int, String>? = peerTypeMappings[peer]
        return mapping?.toMap() ?: emptyMap()
    }

    override fun doDecode(address: InetSocketAddress, buffer: Buffer): ExtendedMessage {
        val map = decode(buffer)
        processTypeMapping(address, map["m"])

        return ExtendedHandshake(map)
    }

    override fun doEncode(message: ExtendedMessage, buffer: Buffer) {
        val extendedHandshake = message as ExtendedHandshake
        extendedHandshake.encode(buffer)

    }

    private fun mergeMappings(
        existing: MutableMap<Int, String>,
        changes: Map<String, BEObject>
    ): MutableMap<Int, String> {
        for ((typeName, value) in changes) {
            val typeId = (value as BEInteger).value.toInt()
            if (typeId == 0) {
                // by setting type ID to 0 peer signals that he has disabled this extension
                val iter = existing.keys.iterator()
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (typeName == existing[key]) {
                        iter.remove()
                        break
                    }
                }
            } else {
                existing[typeId] = typeName
            }
        }
        return existing
    }
}