package io.github.remmerw.loki

import io.github.remmerw.loki.benc.BEInteger
import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.BEString
import io.github.remmerw.loki.benc.Bencode
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BencodeTest {

    @Test
    fun encodeDecodeString() {
        val testData = "hi"
        val buffer = Buffer()
        Bencode.encodeString(testData, buffer)
        val cmp = Bencode.decodeToString(buffer)
        assertEquals(cmp, testData)
    }

    @Test
    fun encodeDecodeInteger() {
        val value = 6666L
        val buffer = Buffer()
        Bencode.encodeInteger(value, buffer)
        val cmp = Bencode.decodeToLong(buffer)
        assertEquals(cmp, value)
    }

    @Test
    fun encodeDecodeEmptyList() {
        val value: List<BEObject> = emptyList()
        val buffer = Buffer()
        Bencode.encodeList(value, buffer)
        val cmp = Bencode.decodeToList(buffer)
        assertEquals(cmp, value)
    }

    @Test
    fun encodeDecodeEmptyMap() {
        val value: Map<String, BEObject> = emptyMap()
        val buffer = Buffer()
        Bencode.encodeMap(value, buffer)
        val cmp = Bencode.decodeToMap(buffer)
        assertEquals(cmp, value)
    }


    @Test
    fun encodeDecodeList() {
        val value: List<BEObject> = listOf(
            BEInteger(555),
            BEString("hello".encodeToByteArray())
        )
        val buffer = Buffer()
        Bencode.encodeList(value, buffer)
        val list = Bencode.decodeToList(buffer)
        assertEquals(value.size, list.size)
        val a = value.first() as BEInteger
        assertEquals(a.toInt(), 555)
        val b = value.last() as BEString
        assertContentEquals(b.toByteArray(), "hello".encodeToByteArray())
    }
}