package io.github.remmerw.loki.mdht

import kotlin.math.ceil
import kotlin.math.max

internal class GraphNode internal constructor(
    val peer: Peer,
    val root: Boolean = false,
    val unreachable: Boolean = false
) {
    private val sources: MutableSet<GraphNode> = mutableSetOf()
    private val backingStore: MutableSet<GraphNode> = mutableSetOf()
    private val calls: MutableList<Call> = mutableListOf()

    private var acceptedResponse: Boolean = false

    fun addCall(call: Call) {
        calls.add(call)
    }

    fun hasNoCalls(): Boolean {
        return calls.isEmpty()
    }

    fun numSources(): Int {
        return sources.size
    }

    fun addSource(node: GraphNode) {
        sources.add(node)
    }

    fun hasSocketMismatchCalls(): Boolean {
        return calls.isNotEmpty() &&
                calls.any { call: Call -> call.hasSocketMismatch() }

    }

    private fun callsNotSuccessful(): Boolean {
        return calls.isNotEmpty() && !acceptedResponse
    }

    fun nonSuccessfulDescendants(): Boolean {

        return sources.isNotEmpty() && sources
            .none { source: GraphNode -> source.nonSuccessfulDescendantCalls() < 3 }

    }

    private fun nonSuccessfulDescendantCalls(): Int {
        return ceil(
            if (backingStore.isEmpty()) 0.0 else backingStore
                .filter { node: GraphNode -> node.callsNotSuccessful() }
                .sumOf { node: GraphNode ->
                    1.0 / max(
                        node.sources.size.toDouble(),
                        1.0
                    )
                }
        ).toInt()
    }

    fun addChildren(nodes: Collection<GraphNode>) {
        for (node in nodes) {
            if (backingStore.contains(node)) return
            backingStore.add(node)
        }
    }

    fun accept() {
        acceptedResponse = true
    }

    override fun equals(other: Any?): Boolean {
        if (other is GraphNode) {
            return peer.equals(other.peer)
        }
        return false
    }

    override fun hashCode(): Int {
        return peer.hashCode()
    }
}
