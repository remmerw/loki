package io.github.remmerw.loki

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.github.andreypfau.curve25519.ed25519.Ed25519PrivateKey
import io.github.andreypfau.curve25519.ed25519.Ed25519PublicKey
import io.github.remmerw.loki.benc.BEString
import io.github.remmerw.loki.benc.stringGet
import io.github.remmerw.loki.mdht.requestGet
import io.github.remmerw.loki.mdht.hostname
import io.github.remmerw.loki.mdht.peerId
import io.github.remmerw.loki.mdht.requestPut
import io.ktor.util.encodeBase64
import io.ktor.util.sha1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class MdhtPutTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun putTest(): Unit = runBlocking(Dispatchers.IO) {


        // https://www.bittorrent.org/beps/bep_0044.html

        val data ="moin".encodeToByteArray()

        val privateKey: Ed25519PrivateKey = Ed25519.generateKey(Random)
        val publicKey: Ed25519PublicKey = privateKey.publicKey()

        val v = BEString(data)
        val cas: Long? = null
        val k: ByteArray = publicKey.toByteArray()
        val salt: ByteArray? = null
        val seq: Long = Clock.System.now().toEpochMilliseconds()
        val signBuffer = Buffer()
        signBuffer.write("3:seqi".encodeToByteArray())
        signBuffer.write(seq.toString().encodeToByteArray())
        signBuffer.write("e1:v".encodeToByteArray())
        signBuffer.write(data.size.toString().encodeToByteArray())
        signBuffer.write(":".encodeToByteArray())
        signBuffer.write(data)
        val sig: ByteArray = privateKey.sign(signBuffer.readByteArray())

        val target = sha1(k)


        withTimeoutOrNull(60 * 1000) {
            val channel = requestPut(peerId(), 7777, bootstrap(),
                target, v, cas, k , salt, seq, sig) {
                5000
            }

            for (peer in channel) {
                println("put to " + hostname(peer.resolveAddress()!!))
            }
        }


        withTimeoutOrNull(30 * 1000) {

            val channel = requestGet(peerId(), 8888, bootstrap(), target) {
                5000
            }

            for (data in channel) {
                println("data received " + stringGet(data.data) + " " + data.k?.encodeBase64())
            }

        }

    }
}