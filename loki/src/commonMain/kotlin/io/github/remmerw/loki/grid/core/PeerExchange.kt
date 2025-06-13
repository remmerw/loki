package io.github.remmerw.loki.grid.core

import io.github.remmerw.loki.buri.core.BEObject
import io.github.remmerw.loki.buri.core.BEString
import io.github.remmerw.loki.buri.encode
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort

internal class PeerExchange(val added: Collection<Peer>, val dropped: Collection<Peer>) :
    ExtendedMessage {
    override val type: Type
        get() = Type.PeerExchange

    fun encodeTo(buffer: Buffer) {
        val map = mutableMapOf<String, BEObject>()
        val inet4Peers = filterByAddressLength(added, 4) // ipv4
        val inet6Peers = filterByAddressLength(added, 16) // ipv6

        map.put("added", encodePeers(inet4Peers))
        map.put("added.f", encodePeerOptions(inet4Peers))
        map.put("added6", encodePeers(inet6Peers))
        map.put("added6.f", encodePeerOptions(inet6Peers))

        map.put(
            "dropped",
            encodePeers(filterByAddressLength(dropped, 4)) // ipv4
        )
        map.put(
            "dropped6",
            encodePeers(filterByAddressLength(dropped, 16)) // ipv6
        )

        encode(map, buffer)
    }


    private fun filterByAddressLength(
        peers: Collection<Peer>, addressLength: Int
    ): Collection<Peer> {
        return peers.filter { peer: Peer -> peer.address.size == addressLength }
    }

    private fun encodePeers(peers: Collection<Peer>): BEString {
        val bos = Buffer()
        for (peer in peers) {
            bos.write(peer.address)
            bos.writeUShort(peer.port)
        }
        return BEString(bos.readByteArray())
    }

    private fun encodePeerOptions(peers: Collection<Peer>): BEString {
        val bos = Buffer()
        repeat(peers.size) { bos.writeInt(0) }
        return BEString(bos.readByteArray())
    }

    init {
        require(!(added.isEmpty() && dropped.isEmpty())) {
            "Can't create PEX message: no peers added/dropped"
        }
    }
}
