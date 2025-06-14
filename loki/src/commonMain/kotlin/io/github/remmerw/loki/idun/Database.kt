package io.github.remmerw.loki.idun

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.random.Random

internal class Database internal constructor() {
    private val tokenManager = TokenManager()
    private val items: MutableMap<Int, MutableList<Address>> = mutableMapOf()
    private val lock = reentrantLock()

    fun store(key: ByteArray, address: Address) {
        lock.withLock {
            val keyEntry = items[key.contentHashCode()]
            if (keyEntry != null) {
                add(keyEntry, address)
            } else {
                val peers = mutableListOf<Address>()
                peers.add(address)
                items[key.contentHashCode()] = peers
            }
        }
    }

    fun sample(key: ByteArray, maxEntries: Int): List<Address> {
        lock.withLock {
            val keyEntry = items[key.contentHashCode()] ?: return emptyList()
            return snapshot(keyEntry, maxEntries)
        }
    }


    fun insertForKeyAllowed(key: ByteArray): Boolean {
        lock.withLock {
            val entries = items[key.contentHashCode()] ?: return true

            val size = entries.size

            if (size < MAX_DB_ENTRIES_PER_KEY / 5) return true

            if (size >= MAX_DB_ENTRIES_PER_KEY) return false

            return size < Random.nextInt(MAX_DB_ENTRIES_PER_KEY)
        }
    }


    fun generateToken(
        nodeId: ByteArray,
        address: ByteArray,
        key: ByteArray
    ): ByteArray {
        lock.withLock {
            return tokenManager.generateToken(nodeId, address, key)
        }
    }


    fun checkToken(
        token: ByteArray,
        nodeId: ByteArray,
        address: ByteArray,
        lookup: ByteArray
    ): Boolean {
        lock.withLock {
            return tokenManager.checkToken(token, nodeId, address, lookup)
        }
    }

    private fun add(items: MutableList<Address>, toAdd: Address) {

        val idx = items.indexOf(toAdd)
        if (idx >= 0) {
            return
        }
        items.add(toAdd)

    }

    private fun snapshot(items: MutableList<Address>, maxEntries: Int): List<Address> {
        return items.shuffled().take(maxEntries).toList()

    }

}
