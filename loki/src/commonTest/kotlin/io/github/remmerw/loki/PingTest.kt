package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.findNode
import io.github.remmerw.loki.mdht.newNott
import io.github.remmerw.loki.mdht.nodeId
import io.github.remmerw.loki.mdht.requestPing
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class PingTest {

    @Test
    fun pingTest(): Unit = runBlocking(Dispatchers.IO) {

        val target = createRandomKey(SHA1_HASH_LENGTH) // random peer id

        val mdht = newNott(nodeId(), 6005, bootstrap())
        try {
            val addresses: MutableSet<InetSocketAddress> = mutableSetOf()
            withTimeoutOrNull(30 * 1000) {
                val channel = findNode(mdht, target) {
                    5000
                }
                for (address in channel) {
                    addresses.add(address)
                }
            }
            addresses.forEach { peer ->
                println(
                    "Success " + requestPing(mdht, peer, target)
                            + " ping to " + target.toHexString()
                )
            }
        } finally {
            mdht.shutdown()
        }
    }
}