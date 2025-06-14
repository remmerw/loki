package io.github.remmerw.loki.idun

/*
* We need to detect when the closest set is stable
*  - in principle we're done as soon as there is no request candidates
*/
internal class ClosestSet(private val key: ByteArray) {
    private val closest: MutableSet<Peer> = mutableSetOf()
    private var insertAttemptsSinceTailModification = 0


    fun candidateAheadOf(
        candidate: Peer
    ): Boolean {
        return !reachedTargetCapacity() ||
                threeWayDistance(key, head(), candidate.id) > 0
    }

    fun candidateAheadOfTail(
        candidate: Peer
    ): Boolean {
        return !reachedTargetCapacity() ||
                threeWayDistance(key, tail(), candidate.id) > 0
    }

    fun maxAttemptsSinceTailModificationFailed(): Boolean {
        return insertAttemptsSinceTailModification > MAX_ENTRIES_PER_BUCKET
    }

    fun reachedTargetCapacity(): Boolean {
        return closest.size >= MAX_ENTRIES_PER_BUCKET
    }


    fun insert(peer: Peer) {
        if (closest.add(peer)) {
            if (closest.size > MAX_ENTRIES_PER_BUCKET) {
                val last = closest.sortedWith(
                    Peer.DistanceOrder(key)
                ).last()
                closest.remove(last)
                if (last === peer) {
                    insertAttemptsSinceTailModification++
                } else {
                    insertAttemptsSinceTailModification = 0
                }
            }
        }
    }

    fun entries(): List<Peer> {
        return closest.toList()
    }

    fun tail(): ByteArray {
        if (closest.isEmpty()) return distance(key, Key.MAX_KEY)
        return closest.last().id
    }

    fun head(): ByteArray {
        if (closest.isEmpty()) return distance(key, Key.MAX_KEY)
        return closest.first().id
    }

}
