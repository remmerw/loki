package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEMap
import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.BEReader
import io.github.remmerw.buri.BEString
import io.github.remmerw.buri.decodeBencode
import io.github.remmerw.loki.debug
import kotlinx.io.Sink
import io.github.remmerw.nott.Address
import java.net.InetAddress
import java.net.InetSocketAddress

internal class PeerExchangeHandler : ExtendedMessageHandler {
    override fun supportedTypes(): Collection<Type> = setOf(Type.PeerExchange)

    override fun doDecode(
        address: Address,
        reader: BEReader,
    ): ExtendedMessage {
        val map = (reader.decodeBencode() as BEMap).toMap()
        val added: MutableSet<Address> = mutableSetOf()
        extractPeers(map, "added", "added.f", 4, added) // ipv4
        extractPeers(map, "added6", "added6.f", 16, added) // ipv6

        val dropped: MutableSet<Address> = mutableSetOf()
        extractPeers(map, "dropped", null, 4, dropped) // ipv4
        extractPeers(map, "dropped6", null, 16, dropped) // ipv6
        return PeerExchange(added, dropped)
    }

    override fun doEncode(
        message: ExtendedMessage,
        sink: Sink,
    ) {
        val exchange = message as PeerExchange
        exchange.encode(sink)
    }

    override fun localTypeId(): Byte = 1

    override fun localName(): String = "ut_pex"

    private fun extractPeers(
        map: Map<String, BEObject>,
        peersKey: String,
        flagsKey: String?,
        addressLength: Int,
        destination: MutableCollection<Address>,
    ) {
        if (map.containsKey(peersKey)) {
            val peers = (checkNotNull(map[peersKey]) as BEString).toByteArray()
            if (flagsKey != null && map.containsKey(flagsKey)) {
                val flags = (checkNotNull(map[flagsKey]) as BEString).toByteArray()
                extractPeers(peers, flags, addressLength, destination)
            } else {
                extractPeers(peers, addressLength, destination)
            }
        }
    }

    private fun extractPeers(
        peers: ByteArray,
        flags: ByteArray,
        addressLength: Int,
        destination: MutableCollection<Address>,
    ) {
        val cryptoFlags = ByteArray(flags.size)
        for (i in flags.indices) {
            cryptoFlags[i] = (flags[i].toInt() and 0x01).toByte()
        }
        parsePeers(peers, addressLength, destination, cryptoFlags)
    }

    private fun extractPeers(
        peers: ByteArray,
        addressLength: Int,
        destination: MutableCollection<Address>,
    ) {
        parsePeers(peers, addressLength, destination)
    }

    private fun parsePeers(
        peers: ByteArray,
        cryptoFlags: ByteArray?,
        addressLength: Int,
    ): List<Address> {
        var pos = 0
        var index = 0

        val result = mutableListOf<Address>()
        while (pos < peers.size) {
            var from = pos
            pos += addressLength
            var to = pos

            val address = peers.copyOfRange(from, to)

            from = to
            pos += 2 // port length
            to = pos

            val port = (
                ((peers[from].toInt() shl 8) and 0xFF00) +
                    (peers[to - 1].toInt() and 0x00FF)
            )

            val requiresEncryption = cryptoFlags != null && cryptoFlags[index].toInt() == 1
            if (!requiresEncryption) {
                // only not required encryption peers are supported
                try {
                    result.add(Address(address, port.toUShort()))
                } catch (throwable: Throwable) {
                    debug(throwable)
                }
            }
            index++
        }
        return result
    }

    private fun parsePeers(
        peers: ByteArray,
        addressLength: Int,
        destination: MutableCollection<Address>,
        cryptoFlags: ByteArray? = null,
    ) {
        val peerLength = addressLength + 2 // 2 is port length
        require(peers.size % peerLength == 0) {
            "Invalid peers string (" + addressLength + ") -- length (" +
                peers.size + ") is not divisible by " + peerLength
        }
        val numOfPeers = peers.size / peerLength
        require(!(cryptoFlags != null && cryptoFlags.size != numOfPeers)) {
            "Number of peers (" + numOfPeers +
                ") is different from the number of crypto flags (" + cryptoFlags!!.size + ")"
        }

        destination.addAll(parsePeers(peers, cryptoFlags, addressLength))
    }
}
