package io.github.remmerw.loki.core

import com.ditchoom.buffer.AllocationZone
import com.ditchoom.buffer.ByteOrder
import com.ditchoom.buffer.PlatformBuffer
import com.ditchoom.buffer.allocate


@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val size: Int,
    private val blockSize: Int,
    private val checksum: ByteArray
) {
    val data = PlatformBuffer.allocate(
        size,
        zone = AllocationZone.SharedMemory,
        byteOrder = ByteOrder.BIG_ENDIAN
    )

    private val blockSet = createBlockSet(size, blockSize)

    fun checksum(): ByteArray {
        return checksum
    }

    internal fun bytes(): ByteArray {
        data.position(0)
        return data.readByteArray(size)
    }

    fun writeBlock(offset: Int, bytes: ByteArray) {
        data.position(offset)
        data.writeBytes(bytes)
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

    suspend fun close() {
        data.close()
    }
}