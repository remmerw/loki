package io.github.remmerw.loki.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class Bitmask(bits: Int) {

    private var words: LongArray

    private var wordsInUse = 0

    private var sizeIsSticky = false


    init {
        // bits can't be negative; size 0 is OK
        if (bits < 0) throw Exception("bits < 0: $bits")
        words = LongArray(wordIndex(bits - 1) + 1)
        sizeIsSticky = true
    }

    /**
     * Sets the field wordsInUse to the logical size in words of the bit set.
     * WARNING:This method assumes that the number of words actually in use is
     * less than or equal to the current value of wordsInUse!
     */
    private fun recalculateWordsInUse() {
        // Traverse the bitmask until a used word is found
        var i: Int = wordsInUse - 1
        while (i >= 0) {
            if (words[i] != 0L) break
            i--
        }

        wordsInUse = i + 1 // The new logical size
    }


    fun encode(piecesTotal: Int): ByteArray {
        val len = getBitmaskLength(piecesTotal)

        val bytes = ByteArray(len)

        repeat(piecesTotal) { i ->
            if (get(i)) {
                setBit(bytes, i)
            }
        }
        return bytes
    }

    /**
     * Ensures that the Bitmask can hold enough words.
     *
     * @param wordsRequired the minimum acceptable number of words.
     */
    private fun ensureCapacity(wordsRequired: Int) {
        if (words.size < wordsRequired) {
            // Allocate larger of doubled size or required size
            val request = max(2 * words.size, wordsRequired)
            words = words.copyOf(request)
            sizeIsSticky = false
        }
    }

    /**
     * Ensures that the Bitmask can accommodate a given wordIndex,
     * temporarily violating the invariants.  The caller must
     * restore the invariants before returning to the user,
     * possibly using recalculateWordsInUse().
     *
     * @param wordIndex the index to be accommodated.
     */
    private fun expandTo(wordIndex: Int) {
        val wordsRequired = wordIndex + 1
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired)
            wordsInUse = wordsRequired
        }
    }

    /**
     * Sets the bit at the specified index to `true`.
     *
     * @param bitIndex a bit index
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    fun set(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")

        val wordIndex: Int = wordIndex(bitIndex)
        expandTo(wordIndex)

        words[wordIndex] = words[wordIndex] or (1L shl bitIndex) // Restores invariants

    }


    /**
     * Sets the bits from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to `true`.
     *
     * @param fromIndex index of the first bit to be set
     * @param toIndex   index after the last bit to be set
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     */
    operator fun set(fromIndex: Int, toIndex: Int) {
        checkRange(fromIndex, toIndex)

        if (fromIndex == toIndex) return

        // Increase capacity if necessary
        val startWordIndex: Int = wordIndex(fromIndex)
        val endWordIndex: Int = wordIndex(toIndex - 1)
        expandTo(endWordIndex)

        val firstWordMask: Long = WORD_MASK shl fromIndex
        val lastWordMask: Long = WORD_MASK ushr -toIndex
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] = words[startWordIndex] or (firstWordMask and lastWordMask)
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] = words[startWordIndex] or firstWordMask

            // Handle intermediate words, if any
            for (i in startWordIndex + 1..<endWordIndex) words[i] = WORD_MASK

            // Handle last word (restores invariants)
            words[endWordIndex] = words[endWordIndex] or lastWordMask
        }

    }

    /**
     * Sets all of the bits in this Bitmask to `false`.
     */
    fun clear() {
        while (wordsInUse > 0) words[--wordsInUse] = 0
    }

    /**
     * Returns the value of the bit with the specified index. The value
     * is `true` if the bit with the index `bitIndex`
     * is currently set in this `Bitmask`; otherwise, the result
     * is `false`.
     *
     * @param bitIndex the bit index
     * @return the value of the bit with the specified index
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    operator fun get(bitIndex: Int): Boolean {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")


        val wordIndex: Int = wordIndex(bitIndex)
        return (wordIndex < wordsInUse)
                && ((words[wordIndex] and (1L shl bitIndex)) != 0L)
    }


    /**
     * Returns the number of bits set to `true` in this `Bitmask`.
     *
     * @return the number of bits set to `true` in this `Bitmask`
     */
    fun cardinality(): Int {
        var sum = 0
        for (i in 0..<wordsInUse) sum += words[i].countOneBits()
        return sum
    }


    /**
     * Performs a logical **OR** of this bit set with the bit set
     * argument. This bit set is modified so that a bit in it has the
     * value `true` if and only if it either already had the
     * value `true` or the corresponding bit in the bit set
     * argument has the value `true`.
     *
     * @param set a bit set
     */
    fun or(set: Bitmask) {
        if (this === set) return

        val wordsInCommon = min(wordsInUse, set.wordsInUse)

        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse)
            wordsInUse = set.wordsInUse
        }

        // Perform logical OR on words in common
        for (i in 0..<wordsInCommon) words[i] = words[i] or set.words[i]


        // Copy any remaining words
        if (wordsInCommon < set.wordsInUse) {
            set.words.copyInto(words, wordsInCommon, wordsInCommon, wordsInUse)
        }

    }


    /**
     * Clears all of the bits in this `Bitmask` whose corresponding
     * bit is set in the specified `Bitmask`.
     *
     * @param set the `Bitmask` with which to mask this
     * `Bitmask`
     */
    fun andNot(set: Bitmask) {
        // Perform logical (a & !b) on words in common
        for (i in min(wordsInUse, set.wordsInUse) - 1 downTo 0) words[i] =
            words[i] and set.words[i].inv()

        recalculateWordsInUse()

    }

    /**
     * Returns the hash code value for this bit set. The hash code depends
     * only on which bits are set within this `Bitmask`.
     *
     *
     * The hash code is defined to be the result of the following
     * calculation:
     * <pre> `public int hashCode() {
     * long h = 1234;
     * long[] words = toLongArray();
     * for (int i = words.length; --i >= 0; )
     * h ^= words['i'] * (i + 1);
     * return (int)((h >> 32) ^ h);
     * }`</pre>
     * Note that the hash code changes if the set of bits is altered.
     *
     * @return the hash code value for this bit set
     */
    override fun hashCode(): Int {
        var h: Long = 1234
        var i = wordsInUse
        while (--i >= 0) {
            h = h xor words[i] * (i + 1)
        }

        return ((h shr 32) xor h).toInt()
    }

    /**
     * Compares this object against the specified object.
     * The result is `true` if and only if the argument is
     * not `null` and is a `Bitmask` object that has
     * exactly the same set of bits set to `true` as this bit
     * set. That is, for every non negative `int` index `k`,
     * <pre>((Bitmask)obj).get(k) == this.get(k)</pre>
     * must be true. The current sizes of the two bit sets are not compared.
     *
     * @param other the object to compare with
     * @return `true` if the objects are the same;
     * `false` otherwise
     * @see .size
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bitmask) return false

        val set = other


        if (wordsInUse != set.wordsInUse) return false

        // Check words in use by both Bitmasks
        for (i in 0..<wordsInUse) if (words[i] != set.words[i]) return false

        return true
    }

    /**
     * Cloning this `Bitmask` produces a new `Bitmask`
     * that is equal to it.
     * The clone of the bit set is another bit set that has exactly the
     * same bits set to `true` as this bit set.
     *
     * @return a clone of this bit set
     * @see .size
     */
    fun copyOf(): Bitmask {
        if (!sizeIsSticky) trimToSize()
        val result = Bitmask(0)
        result.words = this.words.copyOf()
        result.wordsInUse = this.wordsInUse
        result.sizeIsSticky = this.sizeIsSticky
        return result
    }

    /**
     * Attempts to reduce internal storage used for the bits in this bit set.
     * Calling this method may, but is not required to, affect the value
     * returned by a subsequent call to the [.size] method.
     */
    private fun trimToSize() {
        if (wordsInUse != words.size) {
            words = words.copyOf(wordsInUse)
        }
    }

    companion object {

        private const val ADDRESS_BITS_PER_WORD = 6

        /* Used to shift left or right for a partial word mask */
        private const val WORD_MASK = -0x1L


        /**
         * Given a bit index, return word index containing it.
         */
        private fun wordIndex(bitIndex: Int): Int {
            return bitIndex shr ADDRESS_BITS_PER_WORD
        }

        /**
         * Checks that fromIndex ... toIndex is a valid range of bit indices.
         */
        private fun checkRange(fromIndex: Int, toIndex: Int) {
            if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
            if (toIndex < 0) throw IndexOutOfBoundsException("toIndex < 0: $toIndex")
            if (fromIndex > toIndex) throw IndexOutOfBoundsException(
                "fromIndex: " + fromIndex +
                        " > toIndex: " + toIndex
            )
        }

        fun decode(bytes: ByteArray, piecesTotal: Int): Bitmask {
            val expectedBitmaskLength = getBitmaskLength(piecesTotal)
            require(bytes.size == expectedBitmaskLength) {
                "Invalid bitfield: total (" + piecesTotal +
                        "), bitmask length (" + bytes.size + "). Expected bitmask length: " + expectedBitmaskLength
            }

            val bitmask = Bitmask(piecesTotal)
            for (i in 0 until piecesTotal) {
                if (isSet(bytes, i)) {
                    bitmask.set(i)
                }
            }
            return bitmask
        }

        fun decode(bytes: ByteArray): Bitmask {
            val size = bytes.size
            val bitmask = Bitmask(size)
            for (i in 0 until size) {
                if (bytes[i] == 1.toByte()) bitmask.set(i)
            }
            return bitmask
        }


        /**
         * Gets i-th bit in a bitmask.
         *
         * @param bytes    Bitmask.
         * @param i        Bit index (0-based)
         * @return 1 if bit is set, 0 otherwise
         */
        private fun getBit(bytes: ByteArray, i: Int): Int {
            val byteIndex = (i / 8.0).toInt()
            if (byteIndex >= bytes.size) {
                throw RuntimeException("bit index is too large: $i")
            }

            val bitIndex = i % 8
            val shift = 7 - bitIndex
            val bitMask = 1 shl shift
            return (bytes[byteIndex].toInt() and bitMask) shr shift
        }


        /**
         * Check if i-th bit in the bitmask is set.
         *
         * @param bytes    Bitmask.
         * @param i        Bit index (0-based)
         * @return true if i-th bit in the bitmask is set, false otherwise
         */
        private fun isSet(bytes: ByteArray, i: Int): Boolean {
            return getBit(bytes, i) == 1
        }

        /**
         * Sets i-th bit in a bitmask.
         *
         * @param bytes    Bitmask.
         * @param i        Bit index (0-based)
         */
        private fun setBit(bytes: ByteArray, i: Int) {
            val byteIndex = (i / 8.0).toInt()
            if (byteIndex >= bytes.size) {
                throw RuntimeException("bit index is too large: $i")
            }

            val bitIndex = i % 8
            val shift = 7 - bitIndex
            val bitMask = 1 shl shift
            val currentByte = bytes[byteIndex]
            bytes[byteIndex] = (currentByte.toInt() or bitMask).toByte()
        }

        fun getBitmaskLength(piecesTotal: Int): Int {
            return ceil(piecesTotal / 8.0).toInt()
        }
    }
}