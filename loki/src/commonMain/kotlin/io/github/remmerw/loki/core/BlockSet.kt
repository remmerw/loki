package io.github.remmerw.loki.core

import kotlin.math.ceil
import kotlin.math.floor

internal data class BlockSet(
    val chunkSize: Int,
    val blockSize: Int,
    val blockCount: Int,
    private val lastBlockSize: Int,
    private val lastBlockOffset: Int,
    private val bitmask: Bitmask
) {

    fun isPresent(blockIndex: Int): Boolean {
        require(blockIndex in 0..<blockCount) {
            "Invalid block index: " + blockIndex + ". Expected 0.." + (blockCount - 1)
        }
        return bitmask[blockIndex]
    }

    val isComplete: Boolean
        get() = bitmask.cardinality() == blockCount


    fun clear() {
        bitmask.clear()
    }

    /*
     * This method implements a simple strategy to track which blocks have been written:
     * only those blocks are considered present that fit fully in the {@code block} passed
     * as an argument to this method.
     * E.g. if the array being passed to this method is 6 bytes long, and this chunk is split into 4-byte blocks,
     * and the {@code offset} is exactly the first index of some chunk's block,
     * then this array spans over 2 4-byte blocks, but from these 2 blocks the last one is not fully
     * represented in it (i.e. 2 trailing bytes are trimmed). In such case only the first block will be
     * considered saved (i.e. the corresponding index in the bitmask will be set to 1).
     */
    fun markAvailable(offset: Int, length: Int) {
        // update bitmask with the info about the new blocks;
        // if only a part of some block is written,
        // then don't count it

        // handle the case when the last block is smaller than the others
        // mark it as complete only when all of the block's data is present

        if (offset <= lastBlockOffset && offset + length >= chunkSize) {
            bitmask.set(blockCount - 1)
        }
        if (length >= blockSize) {
            val numberOfBlocks = floor((length.toDouble()) / blockSize).toInt()
            if (numberOfBlocks > 0) {
                val firstBlockIndex = ceil((offset.toDouble()) / blockSize).toInt()
                val lastBlockIndex = floor(((offset + length).toDouble()) / blockSize).toInt() - 1
                if (lastBlockIndex >= firstBlockIndex) {
                    bitmask[firstBlockIndex] = lastBlockIndex + 1
                }
            }
        }
    }
}