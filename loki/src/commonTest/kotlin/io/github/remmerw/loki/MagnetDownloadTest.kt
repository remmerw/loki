package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.lookupKey
import io.github.remmerw.loki.mdht.newMdht
import io.github.remmerw.loki.mdht.peerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MagnetDownloadTest {

    @Test
    fun downloadMagnetUri(): Unit = runBlocking(Dispatchers.IO) {
        val uri =
            "magnet:?xt=urn:btih:54943684744B92BD30C7261A4806D9B5CA58F946&dn=Dogville+2003+HDRip+X264-PLAYNOW&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"

        val magnetUri = parseMagnetUri(uri)
        val torrentId = magnetUri.torrentId

        val dataDir = Path(SystemTemporaryDirectory, magnetUri.displayName ?: "empty")
        SystemFileSystem.createDirectories(dataDir)


        val storage =
            downloadTorrent(SystemTemporaryDirectory, 7777, torrentId) { torrentState: State ->
                val completePieces = torrentState.piecesComplete
                val totalPieces = torrentState.piecesTotal

                println(" pieces : $completePieces/$totalPieces")
            }
        storage.storeTo(dataDir)
        storage.finish()
    }


    @Test
    fun randomPeers(): Unit = runBlocking(Dispatchers.IO) {
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1000) {
                val mdht = newMdht(peerId(), 4657)
                mdht.startup(bootstrap())

                val key = createRandomKey(SHA1_HASH_LENGTH) // note random key (probably nobody has)

                val channel = lookupKey(mdht, key)

                for (peer in channel) {
                    println(peer.toString())
                }
                mdht.shutdown()
            }
        }
    }

    @Test
    fun bootstrapTest() {
        val address = bootstrap()
        assertNotNull(address)
        assertTrue(address.isNotEmpty())
    }


}
