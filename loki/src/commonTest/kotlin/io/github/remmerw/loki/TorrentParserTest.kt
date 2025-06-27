package io.github.remmerw.loki

import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.buildTorrent
import io.github.remmerw.loki.core.newData
import io.github.remmerw.loki.data.Peer
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.loki.data.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TorrentParserTest {
    private val path: String = "src/commonTest/resources"

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun parseTorrent(): Unit = runBlocking(Dispatchers.IO) {
        val resources = Path(path)

        val file = Path(resources, "lubuntu-24.04-desktop-amd64.iso.torrent")

        val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
        SystemFileSystem.createDirectories(path)
        val data = newData(path)
        val dataStorage = DataStorage(data)
        SystemFileSystem.source(file).buffered().use { source ->
            val bytes = source.readByteArray()
            val torrent = buildTorrent(bytes)
            assertNotNull(torrent)

            assertEquals(torrent.chunkHashes.size, 12576)

            dataStorage.initialize(torrent, bytes)
            val dataBitfield = dataStorage.dataBitfield()
            assertNotNull(dataBitfield)

            val files = dataStorage.torrentFiles()
            assertEquals(files.size, torrent.files.size)

        }
        data.reset()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testStorage(): Unit = runBlocking(Dispatchers.IO) {

        val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
        SystemFileSystem.createDirectories(path)
        val data = newData(path)
        val dataStorage = DataStorage(data)

        dataStorage.verifiedPieces(10)

        dataStorage.markVerified(0)
        dataStorage.markVerified(5)

        assertTrue(dataStorage.isVerified(0))
        assertTrue(dataStorage.isVerified(5))
        assertFalse(dataStorage.isVerified(1))
        assertFalse(dataStorage.isVerified(9))

        dataStorage.shutdown()
        data.reset()
    }

    @Test
    fun testMetadata() {
        val data = Buffer()
        val utMetadata = data(0, 100, ByteArray(500))
        val handler = UtMetadataHandler()

        val peer = Peer(
            Random.nextBytes(4), 999.toUShort()
        )

        handler.doEncode(peer, utMetadata, data)
        val result = handler.doDecode(peer, data)
        assertEquals(result, utMetadata)
    }
}
