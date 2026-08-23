package io.github.remmerw.loki.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PooledByteArray(
    size: Int,
    val pool: ByteArrayPool,
) : AutoCloseable {
    val byteArray: ByteArray = ByteArray(size)

    override fun close() {
        pool.release(this)
    }
}

class ByteArrayPool(
    val size: Int,
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
                val created = PooledByteArray(size, this)
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
        fun getInstance(size: Int): ByteArrayPool {
            if (instance == null) {
                synchronized(ByteArrayPool::class) {
                    if (instance == null) {
                        instance = ByteArrayPool(size)
                    }
                }
            }
            return instance!!
        }
    }
}