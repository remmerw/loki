package io.github.remmerw.loki

import io.github.remmerw.buri.BEReader
import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.buildTorrent
import io.github.remmerw.loki.core.createMemory
import io.github.remmerw.loki.core.writeMemory
import io.github.remmerw.loki.data.MetaType
import io.github.remmerw.loki.data.UtMetadata
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.nott.createAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.fail
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
    fun parseTorrent(): Unit =
        runBlocking(Dispatchers.IO) {

            val content = "d8:announce37:http://ubuntu.com lengthi16384e6:pieces20:12345678901234567890ee"
            

            val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
            SystemFileSystem.createDirectories(path)

            val dataStorage = DataStorage(path)
            
            val data = content.encodeToByteArray()
            val metadata = createMemory(data.size)
            metadata.writeMemory(data, 0)
            
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
        val data = Buffer()
        val utMetadata =
            UtMetadata(
                MetaType.DATA,
                0,
                100,
                ByteArray(500),
            )
        val handler = UtMetadataHandler()

        val peer = createAddress(byteArrayOf(10, 20, 30, 40), 999.toUShort())

        handler.doEncode(utMetadata, data)

        val bytes = data.readByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        val reader = BEReader(buffer)

        val result = handler.doDecode(peer, reader)
        assertEquals(result, utMetadata)
    }
}
