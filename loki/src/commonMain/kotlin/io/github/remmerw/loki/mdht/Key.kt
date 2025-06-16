package io.github.remmerw.loki.mdht

import kotlin.experimental.inv
import kotlin.random.Random


@Suppress("ArrayInDataClass")
internal data class Prefix(val hash: ByteArray, val depth: Int)

internal object Key {
    val MIN_KEY: ByteArray = ByteArray(SHA1_HASH_LENGTH)
    val MAX_KEY: ByteArray = ByteArray(SHA1_HASH_LENGTH)

    init {
        MAX_KEY.fill(0xFF.toByte())
    }
}


internal fun getInt(hash: ByteArray, offset: Int): Int {
    return (hash[offset].toUInt() shl 24 or (
            hash[offset + 1].toUInt() shl 16) or (
            hash[offset + 2].toUInt() shl 8) or
            hash[offset + 3].toUInt()).toInt()
}

internal fun distance(a: ByteArray, b: ByteArray): ByteArray {
    val hash = ByteArray(SHA1_HASH_LENGTH)
    for (i in a.indices) {
        hash[i] = (a[i].toInt() xor b[i].toInt()).toByte()
    }
    return hash
}


internal fun add(a: ByteArray, b: ByteArray): ByteArray {
    var carry = 0
    val hash = a.copyOf()
    for (i in (SHA1_HASH_LENGTH - 1) downTo 0) {
        carry += (hash[i].toUInt() + b[i].toUInt()).toInt()
        hash[i] = (carry and 0xff).toByte()
        carry = carry ushr 8
    }

    return hash
}

internal fun setBitKey(idx: Int): ByteArray {
    val hash = ByteArray(SHA1_HASH_LENGTH)
    hash[idx / 8] = (0x80 ushr (idx % 8)).toByte()
    return hash
}


internal fun createRandomKey(length: Int): ByteArray {
    return Random.nextBytes(ByteArray(length))
}

/**
 * Compares the distance of two keys relative to this one using the XOR metric
 *
 * @return -1 if h1 is closer to this key, 0 if h1 and h2 are equidistant, 1 if h2 is closer
 */
internal fun threeWayDistance(h0: ByteArray, h1: ByteArray, h2: ByteArray): Int {

    val mmi = mismatch(h1, h2)
    if (mmi == -1) return 0

    val h = h0[mmi].toUByte()
    val a = h1[mmi].toUByte()
    val b = h2[mmi].toUByte()

    return (a xor h).compareTo(b xor h)
}

internal fun createPrefixHash(hash: ByteArray, depth: Int): ByteArray {
    val hash = hash.copyOf()
    copyBits(hash, hash, depth)
    return hash
}

internal fun createPrefix(hash: ByteArray, depth: Int): Prefix {
    val hash = createPrefixHash(hash, depth)
    return Prefix(hash, depth)
}

internal fun splitPrefixBranch(prefix: Prefix, highBranch: Boolean): Prefix {

    val hash = prefix.hash.copyOf()
    val depth = prefix.depth + 1
    if (highBranch) hash[depth / 8] =
        (hash[depth / 8].toInt() or (0x80 shr (depth % 8)).toByte()
            .toInt()).toByte()
    else hash[depth / 8] =
        (hash[depth / 8].toInt() and (0x80 shr (depth % 8)).toByte()
            .inv()
            .toInt()).toByte()

    return Prefix(hash, depth)
}


internal fun isPrefixOf(prefix: Prefix, hash: ByteArray): Boolean {
    return bitsEqual(prefix.hash, hash, prefix.depth)
}

/**
 * @return true if the first bits up to the Nth bit of both keys are equal
 *
 * <pre>
 * n = -1 => no bits have to match
 * n = 0  => byte 0, MSB has to match
 *
</pre> */
private fun bitsEqual(h1: ByteArray, h2: ByteArray, n: Int): Boolean {
    if (n < 0) return true


    val lastToCheck = n ushr 3

    val mmi = mismatch(h1, h2)

    val diff = (h1[lastToCheck].toInt() xor h2[lastToCheck].toInt()) and 0xff

    val lastByteDiff = (diff and (0xff80 ushr (n and 0x07))) == 0

    return if (mmi == lastToCheck) lastByteDiff else mmi.toUInt() > lastToCheck.toUInt()
}

internal fun copyBits(source: ByteArray, data: ByteArray, depth: Int) {
    if (depth < 0) return

    // copy over all complete bytes
    source.copyInto(destination = data, startIndex = 0, endIndex = depth / 8, destinationOffset = 0)

    val idx = depth / 8
    val mask = 0xFF80.toUInt() shr depth % 8

    // mask out the part we have to copy over from the last prefix byte
    data[idx] = (data[idx].toUInt() and mask.inv()).toByte()
    // copy the bits from the last byte
    data[idx] = (data[idx].toUInt() or (source[idx].toUInt() and mask)).toByte()
}
