package io.github.remmerw.loki.core

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.SHA1
import io.github.remmerw.grid.Memory
import io.github.remmerw.grid.allocateMemory
import io.github.remmerw.loki.BLOCK_SIZE
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock


/**
 * BEP-9 torrent metadata, thread-safe
 */
internal data class ExchangedMetadata(
    val totalSize: Int
) {
    private val lock = reentrantLock()
    private val metadata = allocateMemory(totalSize)
    private val metadataBlocks: BlockSet = createBlockSet(totalSize, BLOCK_SIZE)

    fun isBlockPresent(blockIndex: Int): Boolean {
        return metadataBlocks.isPresent(blockIndex)
    }

    fun metadata(): Memory {
        return metadata
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


    @OptIn(DelicateCryptographyApi::class)
    fun digest(): ByteArray {
        lock.withLock {
            check(metadataBlocks.isComplete) { "Metadata is not complete" }
            return CryptographyProvider.Default
                .get(SHA1)
                .hasher()
                .hashBlocking(metadata.rawSource()).toByteArray()
        }
    }


    private fun validateBlockIndex(blockIndex: Int) {
        val blockCount = metadataBlocks.blockCount
        require(!(blockIndex < 0 || blockIndex >= blockCount)) {
            "Invalid block index: $blockIndex; expected 0..$blockCount"
        }
    }

}
