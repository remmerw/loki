package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.Peer
import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.findNode
import io.github.remmerw.loki.mdht.newNott
import io.github.remmerw.loki.mdht.peerId
import io.github.remmerw.loki.mdht.requestPing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class PingTest {

    @Test
    fun pingTest(): Unit = runBlocking(Dispatchers.IO) {

        val target = createRandomKey(SHA1_HASH_LENGTH) // random peer id

        val mdht = newNott(peerId(), 6005, bootstrap())
        try {
            val peers: MutableSet<Peer> = mutableSetOf()
            withTimeoutOrNull(30 * 1000) {
                val channel = findNode(mdht, target) {
                    5000
                }
                for (peer in channel) {
                    peers.add(peer)
                }
            }
            peers.forEach { peer ->
                println("Success " + requestPing(mdht, peer) + " ping to " + peer.id.toHexString())
            }
        } finally {
            mdht.shutdown()
        }
    }
}