package io.github.remmerw.loki.data

import io.github.remmerw.buri.bencodeArray
import io.github.remmerw.buri.bencodeArrayData
import io.github.remmerw.buri.bencodeEof
import io.github.remmerw.buri.bencodeMap
import io.github.remmerw.buri.bencodeMapKey
import io.github.remmerw.nott.Address
import java.nio.ByteBuffer

internal class PeerExchange(
    val added: Collection<Address>,
    val dropped: Collection<Address>,
) : ExtendedMessage {
    override val type: Type
        get() = Type.PeerExchange

    fun encode(buffer: ByteBuffer) {
        buffer.bencodeMap()
        val inet4Peers = filterByAddressLength(added, 4) // ipv4
        val inet6Peers = filterByAddressLength(added, 16) // ipv6

        buffer.bencodeMapKey("added")
        buffer.bencodePeers(inet4Peers, 4)
        buffer.bencodeMapKey("added.f")
        buffer.bencodePeerOptions(inet4Peers)
        buffer.bencodeMapKey("added6")
        buffer.bencodePeers(inet6Peers, 16)
        buffer.bencodeMapKey("added6.f")
        buffer.bencodePeerOptions(inet6Peers)

        buffer.bencodeMapKey("dropped")
        buffer.bencodePeers(filterByAddressLength(dropped, 4), 4)
        buffer.bencodeMapKey("dropped6")
        buffer.bencodePeers(filterByAddressLength(dropped, 16), 16)

        buffer.bencodeEof()
    }

    private fun filterByAddressLength(
        peers: Collection<Address>,
        addressLength: Int,
    ): Collection<Address> = peers.filter { peer -> peer.address.size == addressLength }
}

internal fun ByteBuffer.bencodePeers(
    peers: Collection<Address>,
    size: Int,
) {
    this.bencodeArray((size + 2) * peers.size)
    for (peer in peers) {
        this.bencodeArrayData(peer.address)
        this.bencodeArrayData(peer.port)
    }
}

internal fun ByteBuffer.bencodePeerOptions(peers: Collection<Address>) {
    this.bencodeArray(4 * peers.size)
    repeat(peers.size) {
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
        this.bencodeArrayData(0.toByte())
    }
}
