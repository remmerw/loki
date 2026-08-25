package io.github.remmerw.loki.core

import io.github.remmerw.loki.BLOCK_SIZE
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class Block(
    val pool: BlockPool,
) : AutoCloseable {
    val data: ByteArray = ByteArray(BLOCK_SIZE)

    override fun close() {
        pool.release(this)
    }
}

internal class BlockPool(
) {
    private val lock = ReentrantLock()
    private val used = mutableSetOf<Block>()
    private val free = mutableSetOf<Block>()

    internal fun release(item: Block) {
        lock.withLock {
            used.remove(item)
            free.add(item)
        }
    }

    fun get(): Block {
        lock.withLock {
            val array = free.firstOrNull()
            if (array == null) {
                val created = Block(this)
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
        private var instance: BlockPool? = null

        @JvmStatic
        fun getInstance(): BlockPool {
            if (instance == null) {
                synchronized(BlockPool::class) {
                    if (instance == null) {
                        instance = BlockPool()
                    }
                }
            }
            return instance!!
        }
    }
}

internal fun blockInstance() : Block {
     return                BlockPool.getInstance().get()
}