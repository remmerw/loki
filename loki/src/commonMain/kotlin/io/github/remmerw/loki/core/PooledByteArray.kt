package io.github.remmerw.loki.core


import io.github.remmerw.loki.BLOCK_SIZE
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class Block(
    private val pool: BlockPool,
) : AutoCloseable {
    val data: ByteArray = ByteArray(BLOCK_SIZE)

    override fun close() {
        pool.release(this)
    }
}

internal class BlockPool private constructor() {
    private val lock = ReentrantLock()
    
    private val free = ArrayDeque<Block>()

    internal fun release(item: Block) {
        lock.withLock {
            free.addLast(item)
        }
    }

    fun get(): Block {
        lock.withLock {
            return free.removeLastOrNull() ?: Block(this)
        }
    }

    companion object {
        val instance: BlockPool by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { 
            BlockPool() 
        }
    }
}

internal fun blockInstance(): Block {
    return BlockPool.instance.get()
}


