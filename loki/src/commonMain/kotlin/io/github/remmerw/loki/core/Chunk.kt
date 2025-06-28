package io.github.remmerw.loki.core

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.SHA1
import io.github.remmerw.grid.allocateMemory


@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val size: Int,
    private val blockSize: Int,
    private val checksum: ByteArray
) {
    internal val memory = allocateMemory(size)
    private val blockSet = createBlockSet(size, blockSize)

    @OptIn(DelicateCryptographyApi::class)
    internal fun digest(): Boolean {
        val digest = CryptographyProvider.Default
            .get(SHA1)
            .hasher()
            .hashBlocking(memory.rawSource()).toByteArray()
        return digest.contentEquals(checksum)
    }

    fun writeBlock(offset: Int, bytes: ByteArray) {
        memory.writeBytes(bytes, offset)
        blockSet.markAvailable(offset, bytes.size)
    }

    fun blockCount(): Int {
        return blockSet.blockCount
    }

    fun chunkSize(): Int {
        return size
    }

    fun blockSize(): Int {
        return blockSize
    }

    fun isPresent(blockIndex: Int): Boolean {
        return blockSet.isPresent(blockIndex)
    }

    val isComplete: Boolean
        get() = blockSet.isComplete

    fun reset() {
        blockSet.clear()
    }

}