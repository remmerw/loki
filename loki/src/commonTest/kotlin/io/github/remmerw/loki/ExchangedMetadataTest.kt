package io.github.remmerw.loki

import io.github.remmerw.loki.core.Bitmask
import io.github.remmerw.loki.core.ExchangedMetadata
import io.github.remmerw.loki.data.MetaType
import io.github.remmerw.loki.data.UtMetadata
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.nott.createInetSocketAddress
import io.github.remmerw.nott.nodeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class ExchangedMetadataTest {

    @Test
    fun testMagnetUri() {
        var uri =
            "magnet:?xt=urn:btih:54943684744B92BD30C7261A4806D9B5CA58F946&dn=Dogville+2003+HDRip+X264-PLAYNOW&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
        var magnetUri = parseMagnetUri(uri)
        assertNotNull(magnetUri)
        uri =
            "magnet:?xt=urn:btih:857D6AA0354A76ED738E51AF87AC6946FDBB528B&dn=The+Gorge+%282025%29+%5B1080p%5D+%5BWEBRip%5D+%5B5.1%5D&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
        magnetUri = parseMagnetUri(uri)
        assertNotNull(magnetUri)
        uri =
            "magnet:?xt=urn:btih:0FCDCE6BF93C1AAC42C7F2E9DB13A1402737E517&dn=The+Pitt+S01E07+1+00+P+M+720p+HEVC+x265-MeGusta&tr=http%3A%2F%2Fp4p.arenabg.com%3A1337%2Fannounce&tr=udp%3A%2F%2F47.ip-51-68-199.eu%3A6969%2Fannounce&tr=udp%3A%2F%2F9.rarbg.me%3A2780%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2710%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2730%2Fannounce&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.cyberia.is%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.tiny-vps.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
        magnetUri = parseMagnetUri(uri)
        assertNotNull(magnetUri)
    }

    @Test
    fun testId() {
        val nodeId = nodeId()
        assertEquals(nodeId.size, 20)

        val name = nodeId.decodeToString()
        println(name)
        assertTrue(name.startsWith("-TH0815-"))
    }

    @Test
    fun testUtMetadataMessageHandler() {
        val time = measureTime {
            val handler = UtMetadataHandler()
            val data = Random.nextBytes(100)
            val metadata = UtMetadata(
                metaType = MetaType.DATA,
                pieceIndex = 0,
                totalSize = 100,
                data = data
            )
            val inetSocketAddress = createInetSocketAddress(
                Random.nextBytes(4), 999
            )
            val buffer = Buffer()
            handler.doEncode(metadata, buffer)

            val cmp = handler.doDecode(inetSocketAddress, buffer)
            assertEquals(cmp, metadata)
        }
        println("Time UTMetadata " + time.inWholeMilliseconds)
    }


    @Test
    fun testBitmaskBig() {
        val bits = 10000
        val a = Bitmask(bits)
        a.set(5)
        val data = a.encode(bits)

        val cmp = Bitmask.decode(data, bits)
        assertEquals(a, cmp)
    }

    @Test
    fun testBitmask() {
        val bits = 1000
        val a = Bitmask(bits)
        a.set(5)
        a.set(34)
        a.set(35)
        a.set(999)

        assertFalse(a[0])
        assertFalse(a[4])
        assertTrue(a[5])
        assertTrue(a[34])
        assertTrue(a[35])
        assertFalse(a[36])
        assertTrue(a[999])


        val data = a.encode(bits)

        val b = Bitmask.decode(data, bits)
        assertEquals(a, b)
        assertTrue(b[5])
        assertTrue(b[34])
        assertTrue(b[35])
        assertTrue(b[999])
    }


    @Test
    fun createAndValidate(): Unit = runBlocking(Dispatchers.IO) {

        val totalSize = BLOCK_SIZE * 5 + 100

        val meta = ExchangedMetadata(totalSize)

        assertFalse(meta.isComplete)

        val pieces = totalSize / BLOCK_SIZE
        val rest = totalSize.mod(BLOCK_SIZE)
        assertEquals(rest, totalSize - (pieces * BLOCK_SIZE))

        repeat(pieces) { i ->
            meta.setBlock(i, Random.nextBytes(ByteArray(BLOCK_SIZE)))
        }

        if (rest > 0) {
            assertFalse(meta.isComplete)
            meta.setBlock(pieces, Random.nextBytes(ByteArray(rest)))
        }
        assertTrue(meta.isComplete)

        val digest = meta.digest()
        assertNotNull(digest)

        assertTrue(digest.contentEquals(meta.digest()))
    }
}