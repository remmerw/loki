package io.github.remmerw.loki.core

import io.github.remmerw.grid.allocateMemory
import io.github.remmerw.loki.BLOCK_SIZE
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import org.kotlincrypto.hash.sha1.SHA1


/**
 * BEP-9 torrent metadata, thread-safe
 */
internal data class ExchangedMetadata(
    val totalSize: Int
) {
    private val lock = reentrantLock()
    val metadata = allocateMemory(totalSize)!!
    private val metadataBlocks: BlockSet = createBlockSet(totalSize, BLOCK_SIZE)

    fun isBlockPresent(blockIndex: Int): Boolean {
        return metadataBlocks.isPresent(blockIndex)
    }


    fun setBlock(blockIndex: Int, block: ByteArray) {
        lock.withLock {
            validateBlockIndex(blockIndex)
            val offset = blockIndex * BLOCK_SIZE
            metadata.writeBytes(block, offset)
            metadataBlocks.markAvailable(offset, block.size)
        }
    }

    val blockCount: Int
        get() = metadataBlocks.blockCount

    val isComplete: Boolean
        get() = metadataBlocks.isComplete


    fun digest(): ByteArray {
        lock.withLock {
            check(metadataBlocks.isComplete) { "Metadata is not complete" }
            return SHA1().digest(metadata.readBytes(0, metadata.size()))
        }
    }


    private fun validateBlockIndex(blockIndex: Int) {
        val blockCount = metadataBlocks.blockCount
        require(!(blockIndex < 0 || blockIndex >= blockCount)) {
            "Invalid block index: $blockIndex; expected 0..$blockCount"
        }
    }

}
