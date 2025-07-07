package io.github.remmerw.loki

import io.github.remmerw.loki.benc.BEString
import io.github.remmerw.loki.benc.stringGet
import io.github.remmerw.loki.mdht.getData
import io.github.remmerw.loki.mdht.hostname
import io.github.remmerw.loki.mdht.peerId
import io.github.remmerw.loki.mdht.putData
import io.ktor.util.sha1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test

class MdhtPutTest {

    @Test
    fun putTest(): Unit = runBlocking(Dispatchers.IO) {


        val data = BEString("moin".encodeToByteArray())

        val buffer = Buffer()
        data.writeTo(buffer)
        val target = sha1(buffer.readByteArray())

        withTimeoutOrNull(60 * 1000) {
            val channel = putData(peerId(), 7777, bootstrap(), target, data) {
                5000
            }

            for (peer in channel) {
                println("put to " + hostname(peer.resolveAddress()!!))
            }
        }


        withTimeoutOrNull(30 * 1000) {

            val channel = getData(peerId(), 8888, bootstrap(), target) {
                5000
            }

            for (data in channel) {
                println("data received " + stringGet(data))
            }

        }

    }
}