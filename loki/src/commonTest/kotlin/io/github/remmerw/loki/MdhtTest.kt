package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.lookupKey
import io.github.remmerw.loki.mdht.peerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith


class MdhtTest {
    @Test
    fun randomKey(): Unit = runBlocking(Dispatchers.IO) {
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(120 * 1000) {
                val key = createRandomKey(SHA1_HASH_LENGTH)

                val channel = lookupKey(peerId(), 4657, bootstrap(), key)

                for (peer in channel) {
                    println(peer.toString())
                }
            }
        }
    }
}