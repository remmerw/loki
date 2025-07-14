package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.newNott
import io.github.remmerw.loki.mdht.nodeId
import io.github.remmerw.loki.mdht.requestAnnounce
import io.github.remmerw.loki.mdht.requestGetPeers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class AnnounceTest {

    @Test
    fun announceTest(): Unit = runBlocking(Dispatchers.IO) {

        val key = createRandomKey(SHA1_HASH_LENGTH)

        withTimeoutOrNull(60 * 1000) {
            val mdht = newNott(nodeId(), 6001, bootstrap())
            try {
                val channel = requestAnnounce(mdht, key, 3443) {
                    5000
                }

                for (address in channel) {
                    println("announce to " + address.hostname)
                }
            } finally {
                mdht.shutdown()
            }
        }

        withTimeoutOrNull(30 * 1000) {

            val mdht = newNott(nodeId(), 6002, bootstrap())
            try {
                val channel = requestGetPeers(mdht, key) {
                    5000
                }

                for (address in channel) {
                    println("find from " + address.hostname)
                }
            } finally {
                mdht.shutdown()
            }

        }
    }
}