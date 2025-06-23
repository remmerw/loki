package io.github.remmerw.loki.core


@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val size: Int,
    private val blockSize: Int,
    private val checksum: ByteArray
) {
    private val data: MutableMap<Int, ByteArray> = mutableMapOf() // todo Memory issue here
    private val blockSet = createBlockSet(size, blockSize)

    fun checksum(): ByteArray {
        return checksum
    }

    internal fun bytes(): List<ByteArray> {
        return data.keys.sorted().map { entry -> data[entry]!! }
    }

    fun writeBlock(offset: Int, bytes: ByteArray) {
        data.put(offset, bytes)
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
        data.clear()
        blockSet.clear()
    }
}