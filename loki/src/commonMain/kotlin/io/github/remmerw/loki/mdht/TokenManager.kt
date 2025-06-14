package io.github.remmerw.loki.mdht

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.kotlincrypto.hash.sha1.SHA1
import kotlin.random.Random
import kotlin.time.TimeSource

internal class TokenManager {
    private var currentStamp: Long = 0L
    private var previousStamp: Long = 0L
    private val sessionSecret = createRandomKey(SHA1_HASH_LENGTH)
    private var timeSource = TimeSource.Monotonic.markNow()
    private fun update() {
        if (timeSource.elapsedNow().inWholeMilliseconds > TOKEN_TIMEOUT) {
            timeSource = TimeSource.Monotonic.markNow()
            previousStamp = currentStamp
            currentStamp = Random.nextLong()
        }
    }


    fun checkToken(
        token: ByteArray,
        nodeId: ByteArray,
        address: ByteArray,
        lookup: ByteArray
    ): Boolean {
        update()
        return checkToken(token, nodeId, address, lookup, currentStamp)
                || checkToken(token, nodeId, address, lookup, previousStamp)

    }

    fun generateToken(
        nodeId: ByteArray,
        address: ByteArray,
        key: ByteArray
    ): ByteArray {

        update()

        // generate a hash of the ip port and the current time
        // should prevent anybody from crapping things up
        val bb = Buffer()
        bb.write(nodeId)
        bb.write(address)
        bb.writeLong(currentStamp)
        bb.write(key)
        bb.write(sessionSecret)

        // shorten 4bytes to not waste packet size
        // the chance of guessing correctly would be 1 : 4 million
        // and only be valid for a single infohash
        return SHA1().digest(bb.readByteArray()).copyOf(4)

    }

    private fun checkToken(
        token: ByteArray,
        nodeId: ByteArray,
        address: ByteArray,
        lookup: ByteArray,
        timeStamp: Long
    ): Boolean {

        val bb = Buffer()
        bb.write(nodeId)
        bb.write(address)
        bb.writeLong(timeStamp)
        bb.write(lookup)
        bb.write(sessionSecret)

        val rawToken = SHA1().digest(bb.readByteArray()).copyOf(4)

        return token.contentEquals(rawToken)
    }
}