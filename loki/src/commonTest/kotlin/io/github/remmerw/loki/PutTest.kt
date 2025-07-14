package io.github.remmerw.loki


import io.github.remmerw.borr.Ed25519Sign
import io.github.remmerw.buri.BEString
import io.github.remmerw.loki.mdht.newNott
import io.github.remmerw.loki.mdht.nodeId
import io.github.remmerw.loki.mdht.requestGet
import io.github.remmerw.loki.mdht.requestPut
import io.ktor.util.encodeBase64
import io.ktor.util.sha1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class PutTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun putTest(): Unit = runBlocking(Dispatchers.IO) {


        // https://www.bittorrent.org/beps/bep_0044.html

        val data = "moin".encodeToByteArray()

        val keys = Ed25519Sign.KeyPair.newKeyPair()


        val v = BEString(data)
        val cas: Long? = null
        val k: ByteArray = keys.getPublicKey()
        val salt: ByteArray? = null
        val seq: Long = Clock.System.now().toEpochMilliseconds()
        val signBuffer = Buffer()
        signBuffer.write("3:seqi".encodeToByteArray())
        signBuffer.write(seq.toString().encodeToByteArray())
        signBuffer.write("e1:v".encodeToByteArray())
        signBuffer.write(data.size.toString().encodeToByteArray())
        signBuffer.write(":".encodeToByteArray())
        signBuffer.write(data)

        val signer = Ed25519Sign(keys.getPrivateKey())
        val sig = signer.sign(signBuffer.readByteArray())

        val target = sha1(k)


        withTimeoutOrNull(60 * 1000) {
            val mdht = newNott(nodeId(), 6006, bootstrap())
            try {
                val channel = requestPut(
                    mdht, target, v, cas, k, salt, seq, sig
                ) {
                    5000
                }

                for (address in channel) {
                    println("put to " + address.hostname)
                }
            } finally {
                mdht.shutdown()
            }
        }


        withTimeoutOrNull(30 * 1000) {
            val mdht = newNott(nodeId(), 6007, bootstrap())
            try {
                val channel = requestGet(mdht, target) {
                    5000
                }

                for (data in channel) {
                    println("data received " + data.data.toString() + " " + data.k?.encodeBase64())
                }
            } finally {
                mdht.shutdown()
            }
        }

    }
}