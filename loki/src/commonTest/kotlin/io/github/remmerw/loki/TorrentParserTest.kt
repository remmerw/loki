package io.github.remmerw.loki

import io.github.remmerw.buri.BEReader
import io.github.remmerw.grid.allocateMemory
import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.buildTorrent
import io.github.remmerw.loki.data.MetaType
import io.github.remmerw.loki.data.UtMetadata
import io.github.remmerw.loki.data.UtMetadataHandler
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
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

        val dataStorage = DataStorage(path)
        SystemFileSystem.source(file).buffered().use { source ->
            val bytes = source.readByteArray()
            val torrent = buildTorrent(bytes)
            assertNotNull(torrent)

            val metadata = allocateMemory(bytes.size)
            metadata.writeBytes(bytes, 0)
            dataStorage.metadata(metadata)
            dataStorage.initialize(torrent)
            val dataBitfield = dataStorage.dataBitfield()
            assertNotNull(dataBitfield)

            val files = dataStorage.torrentFiles()
            assertEquals(files.size, torrent.files.size)

        }
        dataStorage.delete()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testStorage(): Unit = runBlocking(Dispatchers.IO) {

        val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
        SystemFileSystem.createDirectories(path)

        val dataStorage = DataStorage(path)

        dataStorage.verifiedPieces(10)

        dataStorage.markVerified(0)
        dataStorage.markVerified(5)

        assertTrue(dataStorage.isVerified(0))
        assertTrue(dataStorage.isVerified(5))
        assertFalse(dataStorage.isVerified(1))
        assertFalse(dataStorage.isVerified(9))

        dataStorage.shutdown()
        dataStorage.delete()
    }

    @Test
    fun testMetadata() {
        val data = Buffer()
        val utMetadata = UtMetadata(
            MetaType.DATA, 0, 100,
            ByteArray(500)
        )
        val handler = UtMetadataHandler()

        val peer = InetSocketAddress("random", 999)

        handler.doEncode(utMetadata, data)

        val bytes = data.readByteArray()
        val reader = BEReader(bytes, bytes.size)


        val result = handler.doDecode(peer, reader)
        assertEquals(result, utMetadata)
    }
}
