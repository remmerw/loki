package io.github.remmerw.loki.mdht

import io.ktor.util.collections.ConcurrentMap

internal class RoutingTable internal constructor() {

    // note int key is not perfect (better would be long value or best the peer id)
    // but it is not yet really necessary (not enough peers in the routing table)
    private val entries: MutableMap<Int, Peer> = ConcurrentMap()

    fun insertOrRefresh(peer: Peer) {
        val entry = findPeerById(peer.id)
        if (entry != null) {
            refresh(peer)
        } else {
            entries.put(peer.hashCode(), peer)
        }
    }

    fun refresh(peer: Peer) {
        entries[peer.hashCode()]
            ?.mergeInTimestamps(peer)
    }

    fun closestPeers(key: ByteArray, take: Int): List<Peer> {
        return entries().filter { peer -> peer.eligibleForNodesList() }
            .sortedWith(Peer.DistanceOrder(key))
            .take(take)
    }


    fun entries(): List<Peer> {
        return entries.values.toList()
    }

    fun onTimeout(id: ByteArray) {
        val peer = findPeerById(id)
        if (peer != null) {
            peer.signalRequestTimeout()
            //only removes the entry if it is bad
            if (peer.needsReplacement()) {
                entries.remove(peer.hashCode())
            }
        }
    }


    fun findPeerById(id: ByteArray): Peer? {
        return entries[id.hashCode()]
    }

    fun notifyOfResponse(msg: Message) {
        entries[msg.id.hashCode()]?.signalResponse()
    }

}
