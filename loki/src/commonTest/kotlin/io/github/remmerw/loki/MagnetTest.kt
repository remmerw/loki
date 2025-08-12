package io.github.remmerw.loki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.Test

class MagnetTest {

    @Test
    fun downloadMagnetUri(): Unit = runBlocking(Dispatchers.IO) {

        withTimeoutOrNull(60 * 1000) {

            val uri =
                "magnet:?xt=urn:btih:4CC70F206C85D80970F27D1611D28CDEF98180E0&dn=The+Gilded+Age+S03E08+1080p+HEVC+x265-MeGusta&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
            val magnetUri = parseMagnetUri(uri)
            val cacheDir =
                SystemTemporaryDirectory // temp directory where to store intermediate data

            val storage =
                download(magnetUri, cacheDir) { torrentState: State ->
                    val completePieces = torrentState.piecesComplete
                    val totalPieces = torrentState.piecesTotal

                    println(" pieces : $completePieces/$totalPieces")
                }

            val dataDir = Path(cacheDir, magnetUri.displayName ?: "empty")
            SystemFileSystem.createDirectories(dataDir)

            storage.storeTo(dataDir) // store files in the final directory
            storage.delete() // cleanup of intermediate files
        }

    }

}
