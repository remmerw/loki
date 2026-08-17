package io.github.remmerw.loki.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.remmerw.nott.Address

interface Store {
    suspend fun addresses(limit: Int): List<Address>

    suspend fun store(address: Address)
}

@Suppress("unused")
class MemoryStore(
    private val maxSize: Int = 10000,
) : Store {
    private val mutex = Mutex()
    private val peers: MutableSet<Address> = LinkedHashSet(maxSize)

    override suspend fun addresses(limit: Int): List<Address> {
        mutex.withLock {
            return peers.take(limit).toList()
        }
    }

    override suspend fun store(address: Address) {
        mutex.withLock {
            if (peers.size >= maxSize) {
                // FIFO removal
                peers.firstOrNull()?.let { peers.remove(it) }
            }
            peers.add(address)
        }
    }
}
