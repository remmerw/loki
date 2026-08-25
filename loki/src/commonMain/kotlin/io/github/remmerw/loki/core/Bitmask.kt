package io.github.remmerw.loki.core

internal class Bitmask(
    bits: Int,
) {
    private var words: LongArray
    private var wordsInUse = 0
    private var sizeIsSticky = false

    init {
        if (bits < 0) throw Exception("bits < 0: $bits")
        words = LongArray(wordIndex(bits - 1) + 1)
        sizeIsSticky = true
    }

    private fun recalculateWordsInUse() {
        var i = wordsInUse - 1
        while (i >= 0 && words[i] == 0L) i--
        wordsInUse = i + 1
    }

    fun encode(piecesTotal: Int): ByteArray {
        val len = getBitmaskLength(piecesTotal)
        val bytes = ByteArray(len)

        // Pack bytes directly from words for performance
        var bitPos = 0
        for (byteIndex in 0 until len) {
            var b = 0
            // build byte from 8 bits; bit 0 in a byte is MSB per original impl
            for (bitInByte in 0..7) {
                if (bitPos >= piecesTotal) break
                val wi = wordIndex(bitPos)
                val offset = bitPos and 63
                if (wi < wordsInUse && ((words[wi] ushr offset) and 1L) != 0L) {
                    b = b or (1 shl (7 - bitInByte))
                }
                bitPos++
            }
            bytes[byteIndex] = b.toByte()
        }
        return bytes
    }

    private fun ensureCapacity(wordsRequired: Int) {
        if (words.size < wordsRequired) {
            val request = maxOf(2 * words.size, wordsRequired)
            words = words.copyOf(request)
            sizeIsSticky = false
        }
    }

    private fun expandTo(wordIndex: Int) {
        val wordsRequired = wordIndex + 1
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired)
            wordsInUse = wordsRequired
        }
    }

    fun set(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")
        val wi = wordIndex(bitIndex)
        expandTo(wi)
        val offset = bitIndex and 63
        words[wi] = words[wi] or (1L shl offset)
    }

    operator fun set(
        fromIndex: Int,
        toIndex: Int,
    ) {
        checkRange(fromIndex, toIndex)
        if (fromIndex == toIndex) return

        val startWordIndex = wordIndex(fromIndex)
        val endWordIndex = wordIndex(toIndex - 1)
        expandTo(endWordIndex)

        val firstOffset = fromIndex and 63
        val lastOffset = (toIndex - 1) and 63

        if (startWordIndex == endWordIndex) {
            val mask = (WORD_MASK shl firstOffset) and (WORD_MASK ushr (63 - lastOffset))
            words[startWordIndex] = words[startWordIndex] or mask
        } else {
            // first word
            words[startWordIndex] = words[startWordIndex] or (WORD_MASK shl firstOffset)
            // middle words
            for (i in startWordIndex + 1 until endWordIndex) words[i] = WORD_MASK
            // last word
            words[endWordIndex] = words[endWordIndex] or (WORD_MASK ushr (63 - lastOffset))
        }
    }

    fun clear() {
        if (wordsInUse > 0) {
            words.fill(0L, 0, wordsInUse)
            wordsInUse = 0
        }
    }

    operator fun get(bitIndex: Int): Boolean {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")
        val wi = wordIndex(bitIndex)
        val offset = bitIndex and 63
        return (wi < wordsInUse) && ((words[wi] and (1L shl offset)) != 0L)
    }

    fun cardinality(): Int {
        var sum = 0
        for (i in 0 until wordsInUse) sum += words[i].countOneBits()
        return sum
    }

    fun or(set: Bitmask) {
        if (this === set) return
        val wordsInCommon = minOf(wordsInUse, set.wordsInUse)
        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse)
            wordsInUse = set.wordsInUse
        }
        for (i in 0 until wordsInCommon) words[i] = words[i] or set.words[i]
        if (wordsInCommon < set.wordsInUse) {
            // copy remaining words from set
            set.words.copyInto(words, wordsInCommon, wordsInCommon, set.wordsInUse)
        }
    }

    fun andNot(set: Bitmask) {
        val common = minOf(wordsInUse, set.wordsInUse)
        for (i in common - 1 downTo 0) {
            words[i] = words[i] and set.words[i].inv()
        }
        // words beyond common remain unchanged
        recalculateWordsInUse()
    }

    override fun hashCode(): Int {
        var h: Long = 1234
        var i = wordsInUse
        while (--i >= 0) {
            h = h xor (words[i] * (i + 1).toLong())
        }
        return ((h ushr 32) xor h).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bitmask) return false
        if (wordsInUse != other.wordsInUse) return false
        for (i in 0 until wordsInUse) if (words[i] != other.words[i]) return false
        return true
    }

    fun copyOf(): Bitmask {
        if (!sizeIsSticky) trimToSize()
        val result = Bitmask(0)
        result.words = this.words.copyOf()
        result.wordsInUse = this.wordsInUse
        result.sizeIsSticky = this.sizeIsSticky
        return result
    }

    private fun trimToSize() {
        if (wordsInUse != words.size) {
            words = words.copyOf(wordsInUse)
        }
    }

    companion object {
        private const val ADDRESS_BITS_PER_WORD = 6
        private const val WORD_MASK = -0x1L

        private fun wordIndex(bitIndex: Int): Int = bitIndex shr ADDRESS_BITS_PER_WORD

        private fun checkRange(
            fromIndex: Int,
            toIndex: Int,
        ) {
            if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
            if (toIndex < 0) throw IndexOutOfBoundsException("toIndex < 0: $toIndex")
            if (fromIndex > toIndex) throw IndexOutOfBoundsException("fromIndex: $fromIndex > toIndex: $toIndex")
        }

        fun decode(
            bytes: ByteArray,
            piecesTotal: Int,
        ): Bitmask {
            val expectedBitmaskLength = getBitmaskLength(piecesTotal)
            require(bytes.size == expectedBitmaskLength) {
                "Invalid bitfield: total ($piecesTotal), bitmask length (${bytes.size}). Expected $expectedBitmaskLength"
            }
            val bitmask = Bitmask(piecesTotal)
            var bitPos = 0
            for (byteIndex in 0 until bytes.size) {
                val b = bytes[byteIndex].toInt() and 0xFF
                for (bitInByte in 0..7) {
                    if (bitPos >= piecesTotal) break
                    if ((b and (1 shl (7 - bitInByte))) != 0) {
                        bitmask.set(bitPos)
                    }
                    bitPos++
                }
            }
            return bitmask
        }

        fun decode(bytes: ByteArray): Bitmask {
            val size = bytes.size
            val bitmask = Bitmask(size)
            for (i in 0 until size) {
                if (bytes[i].toInt() == 1) bitmask.set(i)
            }
            return bitmask
        }

        private fun getBit(
            bytes: ByteArray,
            i: Int,
        ): Int {
            val byteIndex = i ushr 3
            if (byteIndex >= bytes.size) throw RuntimeException("bit index is too large: $i")
            val bitIndex = i and 7
            val shift = 7 - bitIndex
            val bitMask = 1 shl shift
            return (bytes[byteIndex].toInt() and bitMask) shr shift
        }

        private fun isSet(
            bytes: ByteArray,
            i: Int,
        ): Boolean = getBit(bytes, i) == 1

        private fun setBit(
            bytes: ByteArray,
            i: Int,
        ) {
            val byteIndex = i ushr 3
            if (byteIndex >= bytes.size) throw RuntimeException("bit index is too large: $i")
            val bitIndex = i and 7
            val shift = 7 - bitIndex
            val bitMask = 1 shl shift
            bytes[byteIndex] = (bytes[byteIndex].toInt() or bitMask).toByte()
        }

        fun getBitmaskLength(piecesTotal: Int): Int = (piecesTotal + 7) ushr 3
    }
}
