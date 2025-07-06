package io.github.remmerw.loki.mdht

import kotlin.random.Random

internal object Key {
    val MAX_KEY: ByteArray = ByteArray(SHA1_HASH_LENGTH)

    init {
        MAX_KEY.fill(0xFF.toByte())
    }
}


internal fun distance(a: ByteArray, b: ByteArray): ByteArray {
    val hash = ByteArray(SHA1_HASH_LENGTH)
    for (i in a.indices) {
        hash[i] = (a[i].toInt() xor b[i].toInt()).toByte()
    }
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


