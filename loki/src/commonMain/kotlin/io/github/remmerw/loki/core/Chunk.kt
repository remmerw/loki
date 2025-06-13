package io.github.remmerw.loki.core

import org.kotlincrypto.hash.sha1.SHA1

@Suppress("ArrayInDataClass")
internal data class Chunk(
    private val size: Int,
    private val blockSize: Int,
    private val checksum: ByteArray
) {
    private val data: MutableMap<Int, ByteArray> = mutableMapOf()
    private val blockSet = createBlockSet(size, blockSize)

    private fun digest(): ByteArray {
        val digest = SHA1()
        bytes().forEach { data -> digest.update(data) }
        return digest.digest()
    }

    internal fun bytes(): List<ByteArray> {
        return data.keys.sorted().map { entry -> data[entry]!! }
    }

    internal fun verify(): Boolean {
        val actual = digest()
        return checksum.contentEquals(actual)
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