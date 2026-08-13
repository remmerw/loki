package io.github.remmerw.loki.data

import io.github.remmerw.buri.bencodeArray
import io.github.remmerw.buri.bencodeArrayData
import io.github.remmerw.buri.bencodeEof
import io.github.remmerw.buri.bencodeMap
import io.github.remmerw.buri.bencodeMapKey
import kotlinx.io.Buffer
import java.net.InetSocketAddress

internal class PeerExchange(
    val added: Collection<InetSocketAddress>,
    val dropped: Collection<InetSocketAddress>,
) : ExtendedMessage {
    override val type: Type
        get() = Type.PeerExchange

    fun encode(buffer: Buffer) {
        val sink =
            io.github.remmerw.buri
                .Buffer(4096)
        sink.bencodeMap()
        val inet4Peers = filterByAddressLength(added, 4) // ipv4
        val inet6Peers = filterByAddressLength(added, 16) // ipv6

        sink.bencodeMapKey("added")
        sink.bencodePeers(inet4Peers, 4)
        sink.bencodeMapKey("added.f")
        sink.bencodePeerOptions(inet4Peers)
        sink.bencodeMapKey("added6")
        sink.bencodePeers(inet6Peers, 16)
        sink.bencodeMapKey("added6.f")
        sink.bencodePeerOptions(inet6Peers)

        sink.bencodeMapKey("dropped")
        sink.bencodePeers(filterByAddressLength(dropped, 4), 4)
        sink.bencodeMapKey("dropped6")
        sink.bencodePeers(filterByAddressLength(dropped, 16), 16)

        sink.bencodeEof()
        buffer.write(sink.data, 0, sink.length)
    }

    private fun filterByAddressLength(
        peers: Collection<InetSocketAddress>,
        addressLength: Int,
    ): Collection<InetSocketAddress> = peers.filter { peer -> peer.address.address.size == addressLength }
}

internal fun io.github.remmerw.buri.Buffer.bencodePeers(
    peers: Collection<InetSocketAddress>,
    size: Int,
) {
    this.bencodeArray((size + 2) * peers.size)
    for (peer in peers) {
        this.bencodeArrayData(peer.address.address)
        this.bencodeArrayData(peer.port.toUShort())
    }
}

internal fun io.github.remmerw.buri.Buffer.bencodePeerOptions(peers: Collection<InetSocketAddress>) {
    this.bencodeArray(4 * peers.size)
    repeat(peers.size) {
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
    }
}
