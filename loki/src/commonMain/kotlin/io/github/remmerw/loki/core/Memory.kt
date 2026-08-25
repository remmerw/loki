package io.github.remmerw.loki.core

import io.github.remmerw.loki.BLOCK_SIZE
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.nio.ByteBuffer

internal class Memory (
    private val pool: MemoryPool,
) : AutoCloseable {
    val buffer: ByteBuffer = ByteBuffer.allocateDirect(BLOCK_SIZE + 1000)

    override fun close() {
        buffer.clear()
        pool.release(this)
    }
}

internal class MemoryPool private constructor() {
    private val lock = ReentrantLock()

    private val free = ArrayDeque<Memory>()

    internal fun release(item: Memory) {
        lock.withLock {
            free.addLast(item)
        }
    }

    fun get(): Memory {
        lock.withLock {
            return free.removeLastOrNull() ?: Memory(this)
        }
    }

    companion object {
        val instance: MemoryPool by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            MemoryPool()
        }
    }
}

internal fun memoryInstance(): Memory = MemoryPool.instance.get()
