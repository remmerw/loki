package io.github.remmerw.loki.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PooledByteArray(
    val pool: ByteArrayPool,
) : AutoCloseable {
    val byteArray: ByteArray = ByteArray(BLOCK_SIZE)

    override fun close() {
        pool.release(this)
    }
}

class ByteArrayPool(
) {
    private val lock = ReentrantLock()
    private val used = mutableSetOf<PooledByteArray>()
    private val free = mutableSetOf<PooledByteArray>()

    internal fun release(item: PooledByteArray) {
        lock.withLock {
            used.remove(item)
            free.add(item)
        }
    }

    fun get(): PooledByteArray {
        lock.withLock {
            val array = free.firstOrNull()
            if (array == null) {
                val created = PooledByteArray(this)
                used.add(created)
                return created
            } else {
                free.remove(array)
                used.add(array)
                return array
            }
        }
    }

    companion object {
        @Volatile
        private var instance: ByteArrayPool? = null

        @JvmStatic
        fun getInstance(): ByteArrayPool {
            if (instance == null) {
                synchronized(ByteArrayPool::class) {
                    if (instance == null) {
                        instance = ByteArrayPool()
                    }
                }
            }
            return instance!!
        }
    }
}
