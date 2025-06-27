package io.github.remmerw.loki.core

import io.github.remmerw.grid.allocateMemory


@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val size: Int,
    private val blockSize: Int,
    private val checksum: ByteArray
) {
    val memory = allocateMemory(size)

    private val blockSet = createBlockSet(size, blockSize)

    fun checksum(): ByteArray {
        return checksum
    }

    internal fun bytes(): ByteArray {
        return memory.readBytes(0, size)
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