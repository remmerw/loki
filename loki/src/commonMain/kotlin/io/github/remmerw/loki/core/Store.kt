package io.github.remmerw.loki.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress

interface Store {
    suspend fun addresses(limit: Int): List<InetSocketAddress>

    suspend fun store(address: InetSocketAddress)
}

@Suppress("unused")
class MemoryStore(
    private val maxSize: Int = 10000,
) : Store {
    private val mutex = Mutex()
    private val peers: MutableSet<InetSocketAddress> = LinkedHashSet(maxSize)

    override suspend fun addresses(limit: Int): List<InetSocketAddress> {
        mutex.withLock {
            return peers.take(limit).toList()
        }
    }

    override suspend fun store(address: InetSocketAddress) {
        mutex.withLock {
            if (peers.size >= maxSize) {
                // FIFO removal
                peers.firstOrNull()?.let { peers.remove(it) }
            }
            peers.add(address)
        }
    }
}