package io.github.remmerw.loki

import io.github.remmerw.loki.mdht.SHA1_HASH_LENGTH
import io.github.remmerw.loki.mdht.createRandomKey
import io.github.remmerw.loki.mdht.requestGetPeers
import io.github.remmerw.loki.mdht.peerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test


class MdhtLookupTest {
    @Test
    fun randomKey(): Unit = runBlocking(Dispatchers.IO) {

        withTimeoutOrNull(60 * 1000) {
            val key = createRandomKey(SHA1_HASH_LENGTH)

            val channel = requestGetPeers(peerId(), 4657, bootstrap(), key) {
                5000
            }

            for (peer in channel) {
                println(peer.toString())
            }
        }

    }

    @Test
    fun realKey(): Unit = runBlocking(Dispatchers.IO) {


        val uri =
            "magnet:?xt=urn:btih:DE1EC3249C3D9EC5DE8C2099C0058E2E26113AAD&dn=Eden+2024+1080p+WEB-DL+HEVC+x265+5.1+BONE&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"

        val magnetUri = parseMagnetUri(uri)

        withTimeoutOrNull(60 * 1000) {
            val key = magnetUri.torrentId.bytes

            val channel = requestGetPeers(peerId(), 4657, bootstrap(), key) {
                5000
            }

            for (peer in channel) {
                println(peer.toString())
            }
        }

    }
}