package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.hostname
import io.github.remmerw.loki.mdht.peerId
import io.github.remmerw.loki.mdht.putData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class MdhtPutTest {

    @Test
    fun putTest(): Unit = runBlocking(Dispatchers.IO) {

        val key = createRandomKey(SHA1_HASH_LENGTH)
        val data = "moin".encodeToByteArray()

        withTimeoutOrNull(60 * 1000) {
            val channel = putData(peerId(), 7777, bootstrap(), key, data) {
                5000
            }

            for (peer in channel) {
                println("put to " + hostname(peer.resolveAddress()!!))
            }
        }

    }
}