package io.github.remmerw.loki.mdht

/*
* We need to detect when the closest set is stable
*  - in principle we're done as soon as there is no request candidates
*/
internal class ClosestSet(
    private val nott: Nott,
    private val target: ByteArray
) {
    private val closest: MutableSet<Peer> = mutableSetOf()
    private val candidates = Candidates(target)
    private var insertAttemptsSinceTailModification = 0


    init {
        val entries = nott.closestPeers(target, 32)
        candidates.addCandidates(null, entries)
    }

    fun nextCandidate(inFlight: Set<Call>): Peer? {
        return candidates.next { peer: Peer ->
            goodForRequest(peer, inFlight)
        }
    }

    suspend fun requestCall(call: Call, peer: Peer) {
        candidates.addCall(call, peer)
        nott.doRequestCall(call)
    }

    fun checkTimeoutOrFailure(call: Call): Boolean {
        val state = call.state()
        if (state != CallState.RESPONDED) {
            if (state == CallState.ERROR || state == CallState.STALLED) {
                return true
            } else {
                val sendTime = call.sentTime

                if (sendTime != null) {
                    val elapsed = sendTime.elapsedNow().inWholeMilliseconds
                    if (elapsed > 3000) { // 3 sec
                        candidates.increaseFailures(call)
                        nott.timeout(call)
                    }
                }
            }
        }
        return false
    }


    fun acceptResponse(call: Call): Peer? {
        candidates.decreaseFailures(call)
        val match = candidates.acceptResponse(call)
        if (match != null) {
            val message = call.response
            if (message is NodesResponse) {
                val returnedNodes: MutableSet<Peer> = mutableSetOf()

                message.nodes6.filter { peer: Peer ->
                    !nott.isLocalId(peer.id)
                }.forEach { e: Peer -> returnedNodes.add(e) }

                message.nodes.filter { peer: Peer ->
                    !nott.isLocalId(peer.id)
                }.forEach { e: Peer -> returnedNodes.add(e) }

                addCandidates(match, returnedNodes)
            }
        }
        return match
    }

    private fun addCandidates(source: Peer?, entries: Collection<Peer>) {
        candidates.addCandidates(source, entries)
    }

    fun candidateAheadOf(
        candidate: Peer
    ): Boolean {
        return !reachedTargetCapacity() ||
                threeWayDistance(target, head(), candidate.id) > 0
    }

    fun candidateAheadOfTail(
        candidate: Peer
    ): Boolean {
        return !reachedTargetCapacity() ||
                threeWayDistance(target, tail(), candidate.id) > 0
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
                    Peer.DistanceOrder(target)
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
        if (closest.isEmpty()) return distance(target, Key.MAX_KEY)
        return closest.last().id
    }

    fun head(): ByteArray {
        if (closest.isEmpty()) return distance(target, Key.MAX_KEY)
        return closest.first().id
    }


    private fun inStabilization(): Boolean {
        val suggestedCounts = entries().map { k: Peer ->
            candidates.nodeForEntry(
                k
            )!!.numSources()
        }

        return suggestedCounts.any { i: Int -> i >= 5 } ||
                suggestedCounts.count { i: Int -> i >= 4 } >= 2
    }


    private fun terminationPrecondition(
        candidate: Peer
    ): Boolean {
        return !candidateAheadOfTail(candidate) && (
                inStabilization() || maxAttemptsSinceTailModificationFailed())
    }

    /* algo:
    * 1. check termination condition
    * 2. allow if free slot
    * 3. if stall slot check
    * a) is candidate better than non-stalled in flight
    * b) is candidate better than head (homing phase)
    * c) is candidate better than tail (stabilizing phase)
    */
    private fun goodForRequest(
        candidate: Peer,
        inFlight: Set<Call>
    ): Boolean {

        var result = candidateAheadOf(candidate)

        if (candidateAheadOfTail(candidate) && inStabilization()) result =
            true
        if (!terminationPrecondition(
                candidate,
            ) && activeInFlight(inFlight) == 0
        ) result = true

        return result
    }


    private fun activeInFlight(inFlight: Set<Call>): Int {
        return inFlight.filter { call: Call ->
            val state = call.state()
            state == CallState.UNSENT || state == CallState.SENT
        }.map { call: Call -> call.expectedID!! }.count()
    }


}
