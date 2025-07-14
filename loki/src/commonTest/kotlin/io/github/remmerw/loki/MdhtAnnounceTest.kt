package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.hostname
import io.github.remmerw.loki.mdht.newNott
import io.github.remmerw.loki.mdht.peerId
import io.github.remmerw.loki.mdht.requestAnnounce
import io.github.remmerw.loki.mdht.requestGetPeers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class MdhtAnnounceTest {

    @Test
    fun announceTest(): Unit = runBlocking(Dispatchers.IO) {

        val key = createRandomKey(SHA1_HASH_LENGTH)

        withTimeoutOrNull(60 * 1000) {
            val mdht = newNott(peerId(), 6666, bootstrap())
            try {
                val channel = requestAnnounce(mdht, key, 3443) {
                    5000
                }

                for (peer in channel) {
                    println("announce to " + hostname(peer.resolveAddress()!!))
                }
            } finally {
                mdht.shutdown()
            }
        }

        withTimeoutOrNull(30 * 1000) {

            val mdht = newNott(peerId(), 8888, bootstrap())
            try {
                val channel = requestGetPeers(mdht, key) {
                    5000
                }

                for (peer in channel) {
                    println("find from " + hostname(peer.resolveAddress()!!))
                }
            } finally {
                mdht.shutdown()
            }

        }
    }
}