package io.github.remmerw.loki.mdht

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.util.collections.ConcurrentMap
import kotlin.random.Random

internal class Database internal constructor() {
    private val tokenManager = TokenManager()
    private val items: ConcurrentMap<Int, MutableList<InetSocketAddress>> = ConcurrentMap()

    fun store(key: ByteArray, address: InetSocketAddress) {

        val keyEntry = items[key.contentHashCode()]
        if (keyEntry != null) {
            add(keyEntry, address)
        } else {
            val peers = mutableListOf<InetSocketAddress>()
            peers.add(address)
            items[key.contentHashCode()] = peers
        }

    }

    fun sample(key: ByteArray, maxEntries: Int): List<InetSocketAddress> {

        val keyEntry = items[key.contentHashCode()] ?: return emptyList()
        return snapshot(keyEntry, maxEntries)

    }


    fun insertForKeyAllowed(key: ByteArray): Boolean {

        val entries = items[key.contentHashCode()] ?: return true

        val size = entries.size

        if (size < MAX_DB_ENTRIES_PER_KEY / 5) return true

        if (size >= MAX_DB_ENTRIES_PER_KEY) return false

        return size < Random.nextInt(MAX_DB_ENTRIES_PER_KEY)

    }


    fun generateToken(
        nodeId: ByteArray,
        address: ByteArray,
        key: ByteArray
    ): ByteArray {

        return tokenManager.generateToken(nodeId, address, key)

    }


    fun checkToken(
        token: ByteArray,
        nodeId: ByteArray,
        address: ByteArray,
        lookup: ByteArray
    ): Boolean {
        return tokenManager.checkToken(token, nodeId, address, lookup)
    }

    private fun add(items: MutableList<InetSocketAddress>, toAdd: InetSocketAddress) {

        val idx = items.indexOf(toAdd)
        if (idx >= 0) {
            return
        }
        items.add(toAdd)

    }

    private fun snapshot(items: MutableList<InetSocketAddress>, maxEntries: Int)
            : List<InetSocketAddress> {
        return items.shuffled().take(maxEntries).toList()

    }

}
