package io.github.remmerw.loki

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.Bencode
import kotlinx.io.Buffer
import kotlin.test.Test
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
}