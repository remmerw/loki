package io.github.remmerw.loki

import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.decodeToList
import io.github.remmerw.loki.benc.decodeToLong
import io.github.remmerw.loki.benc.decodeToMap
import io.github.remmerw.loki.benc.decodeToString
import io.github.remmerw.loki.benc.encodeInteger
import io.github.remmerw.loki.benc.encodeList
import io.github.remmerw.loki.benc.encodeMap
import io.github.remmerw.loki.benc.encodeString
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class BencodeTest {

    @Test
    fun encodeDecodeString() {
        val testData = "hi"
        val buffer = Buffer()
        encodeString(testData, buffer)
        val cmp = decodeToString(buffer)
        assertEquals(cmp, testData)
    }

    @Test
    fun encodeDecodeInteger() {
        val value = 6666L
        val buffer = Buffer()
        encodeInteger(value, buffer)
        val cmp = decodeToLong(buffer)
        assertEquals(cmp, value)
    }

    @Test
    fun encodeDecodeEmptyList() {
        val value: List<BEObject> = emptyList()
        val buffer = Buffer()
        encodeList(value, buffer)
        val cmp = decodeToList(buffer)
        assertEquals(cmp, value)
    }

    @Test
    fun encodeDecodeEmptyMap() {
        val value: Map<String, BEObject> = emptyMap()
        val buffer = Buffer()
        encodeMap(value, buffer)
        val cmp = decodeToMap(buffer)
        assertEquals(cmp, value)
    }
}