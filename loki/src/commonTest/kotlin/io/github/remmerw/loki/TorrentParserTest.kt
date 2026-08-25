package io.github.remmerw.loki

import io.github.remmerw.buri.BEReader
import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.buildTorrent
import io.github.remmerw.loki.core.createBuffer
import io.github.remmerw.loki.core.writeMemory
import io.github.remmerw.loki.data.MetaType
import io.github.remmerw.loki.data.UtMetadata
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.nott.createAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TorrentParserTest {
    // @Test Todo not working content missing
    @OptIn(ExperimentalUuidApi::class)
    fun parseTorrent(): Unit =
        runBlocking(Dispatchers.IO) {
            val content = "" // Todo add torrent file content

            val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
            SystemFileSystem.createDirectories(path)

            val dataStorage = DataStorage(path)
            val data: ByteArray = content.toByteArray(Charsets.ISO_8859_1)

            val metadata = createBuffer(data.size)
            metadata.putAt(0,data)

            val torrent = buildTorrent(metadata)
            assertNotNull(torrent)

            dataStorage.metadata(metadata)
            dataStorage.initialize(torrent)
            val dataBitfield = dataStorage.dataBitfield()
            assertNotNull(dataBitfield)

            val files = dataStorage.torrentFiles()
            assertEquals(files.size, torrent.files.size)

            dataStorage.delete()
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testStorage(): Unit =
        runBlocking(Dispatchers.IO) {
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

            dataStorage.close()
            dataStorage.delete()
        }

    @Test
    fun testMetadata() {
        val buffer = ByteBuffer.allocate(600)
        val utMetadata =
            UtMetadata(
                MetaType.DATA,
                0,
                100,
                ByteArray(500),
            )
        val handler = UtMetadataHandler()

        val peer = createAddress(byteArrayOf(10, 20, 30, 40), 999.toUShort())

        handler.doEncode(utMetadata, buffer)
        buffer.flip()
        val reader = BEReader(buffer)

        val result = handler.doDecode(peer, reader)
        assertEquals(result, utMetadata)
    }
}
