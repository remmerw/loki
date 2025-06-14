package io.github.remmerw.loki.idun

import io.ktor.network.sockets.InetSocketAddress
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
* Issues:
*
* - spurious packet loss
* - remotes might fake IDs. possibly with collusion.
* - invalid results
*   - duplicate IPs
*   - duplicate IDs
*   - wrong IDs -> might trip up fake ID detection!
*   - IPs not belonging to DHT nodes -> DoS abuse
*
* Solution:
*
* - generally avoid querying an IP more than once
* - dedup result lists from each node
* - ignore responses with unexpected IDs. normally this could be abused to silence others, but...
* - allow duplicate requests if many *separate* sources suggest precisely the same <id, ip, port> tuple
*
* -> we can recover from all the above-listed issues because the terminal set of nodes should
* have some partial agreement about their neighbors
*
*/
internal class Candidates internal constructor(
    private val target: ByteArray
) {
    private val failures: MutableMap<InetSocketAddress, Int> = mutableMapOf()
    private val calls: MutableMap<Call, Peer> = mutableMapOf()
    private val callsByIp: MutableMap<InetSocketAddress, MutableSet<Call>> = mutableMapOf()
    private val acceptedInets: MutableCollection<InetSocketAddress> = mutableSetOf()
    private val acceptedKeys: MutableCollection<Int> = mutableSetOf()
    private val candidates: MutableMap<Peer, GraphNode> = mutableMapOf()


    private fun lookup(node: GraphNode): Boolean {
        val peer = node.peer
        if (node.unreachable) return false

        // skip retransmits if we previously got a response but from the wrong socket address
        if (node.hasSocketMismatchCalls()) return false

        val addr = peer.address

        if (acceptedInets.contains(addr) || acceptedKeys.contains(peer.id.contentHashCode()))
            return false

        // only do requests to nodes which have at least one source where the source
        // has not given us lots of bogus candidates
        if (node.nonSuccessfulDescendants()) return false

        var dups = 0

        // also check other calls based on matching IP instead of strictly matching ip+port+id
        val byIp: Set<Call>? = callsByIp[addr]
        if (byIp != null) {

            for (c in byIp) {
                // in flight, not stalled
                if (c.state() == CallState.SENT || c.state() == CallState.UNSENT) return false

                // already got a response from that addr that does not match what we would expect from this candidate anyway
                if (c.state() == CallState.RESPONDED && !c.response!!.id.contentEquals(
                        peer.id
                    )
                ) return false
                // we don't strictly check the presence of IDs in error messages, so we can't compare those here
                if (c.state() == CallState.ERROR) return false
                dups++
            }

        }
        // log2 scale
        val sources =
            max(1.0, (node.numSources() + (if (node.root) 1 else 0)).toDouble()).toInt()

        val scaledSources = 31 - sources.countLeadingZeroBits()
        scaledSources >= dups
        return true
    }


    fun addCall(call: Call, peer: Peer) {
        calls[call] = peer

        val byIp = callsByIp.getOrPut(call.request.address) { mutableSetOf() }
        byIp.add(call)

        checkNotNull(candidates[peer]).addCall(call)

    }

    fun acceptResponse(call: Call): Peer? {
        // we ignore on mismatch, node will get a 2nd chance if sourced from multiple
        // nodes and hasn't sent a successful reply yet

        if (!call.matchesExpectedID()) return null

        val peer = calls[call]
        checkNotNull(peer)

        val node = candidates[peer]
        checkNotNull(node)
        val insertOk = !acceptedInets.contains(peer.address) && !acceptedKeys.contains(
            peer.id.contentHashCode()
        )
        if (insertOk) {
            acceptedInets.add(peer.address)
            acceptedKeys.add(peer.id.contentHashCode())
            node.accept()
            return peer
        }
        return null

    }

    fun decreaseFailures(call: Call) {
        val addr = call.request.address
        val oldEntry = failures[addr]
        if (oldEntry != null) {
            if (oldEntry > 1) {
                failures[addr] = oldEntry shr 1 // multiplicative decrease
            } else {
                failures.remove(addr)
            }
        }
    }

    fun increaseFailures(call: Call) {
        val addr = call.request.address
        val oldValue: Int? = failures[addr]
        val newValue: Int? = if (oldValue == null) 1 else (oldValue + 1)
        if (newValue == null) failures.remove(addr)
        else failures.put(addr, newValue)
    }


    private fun failures(address: InetSocketAddress): Int {
        return failures[address] ?: 0
    }

    fun addCandidates(source: Peer?, entries: Collection<Peer>) {


        val dedup: MutableSet<Any> = HashSet()

        val sourceNode = if (source != null) candidates[source] else null

        val children: MutableList<GraphNode> = mutableListOf()

        for (peer in entries) {

            // no double entries
            if (!dedup.add(peer.id.contentHashCode())
                || !dedup.add(peer.address)
            ) continue


            val node = candidates.getOrPut(peer) {
                val root = source == null
                val failures = failures(peer.address)
                // 0-20
                val rnd = Random.nextInt(21)
                // -2 - 19 -> 5% chance to let even the worst stuff still
                // through to keep the counters going up
                val unreachable = min((failures - 2).toDouble(), 19.0) > rnd
                GraphNode(peer, root, unreachable)
            }

            if (sourceNode != null) node.addSource(sourceNode)
            children.add(node)
        }

        sourceNode?.addChildren(children)

    }


    private fun sortedLookups(): List<GraphNode> {
        return candidates.values.sortedWith { a, b ->
            val res = threeWayDistance(target, a.peer.id, b.peer.id)
            if (res == 0) {
                b.numSources() - a.numSources()
            } else {
                res
            }
        }
    }

    fun next(postFilter: (Peer) -> Boolean): Peer? {

        // sort + filter + findAny should be faster than filter + min in
        // this case since findAny reduces the invocations of the filter,
        // and that is more expensive than the sorting

        var node = sortedLookups()
            .filter { node: GraphNode -> node.hasNoCalls() }.firstOrNull { node ->
                lookup(node)
            }

        var kbe = node?.peer
        if (kbe != null) {
            if (postFilter.invoke(kbe)) {
                return kbe
            }
        }

        node = sortedLookups().firstOrNull { node -> lookup(node) }
        kbe = node?.peer
        if (kbe != null) {
            if (postFilter.invoke(kbe)) {
                return kbe
            }
        }

        return null

    }

    fun nodeForEntry(e: Peer): GraphNode? {
        return candidates[e]
    }


}
