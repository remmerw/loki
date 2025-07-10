package io.github.remmerw.loki.data

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.BEString
import io.github.remmerw.loki.benc.bencode
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort

internal class PeerExchange(
    val added: Collection<InetSocketAddress>,
    val dropped: Collection<InetSocketAddress>
) :
    ExtendedMessage {
    override val type: Type
        get() = Type.PeerExchange

    fun encode(buffer: Buffer) {
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

        map.bencode().encodeTo(buffer)
    }


    private fun filterByAddressLength(
        peers: Collection<InetSocketAddress>, addressLength: Int
    ): Collection<InetSocketAddress> {
        return peers.filter { peer -> peer.resolveAddress()!!.size == addressLength }
    }

    private fun encodePeers(peers: Collection<InetSocketAddress>): BEString {
        val bos = Buffer()
        for (peer in peers) {
            bos.write(peer.resolveAddress()!!)
            bos.writeUShort(peer.port.toUShort())
        }
        return bos.readByteArray().bencode()
    }

    private fun encodePeerOptions(peers: Collection<InetSocketAddress>): BEString {
        val bos = Buffer()
        repeat(peers.size) { bos.writeInt(0) }
        return bos.readByteArray().bencode()
    }

}
