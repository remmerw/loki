package io.github.remmerw.loki

import io.github.remmerw.loki.core.key
import kotlin.test.Test
import kotlin.test.assertEquals

class DataTest {
    @Test
    fun testKey() {
        val key = key(999, 35800)

        val hi = key.shr(32).toInt()
        val lo = key.toInt()

        assertEquals(hi, 999)
        assertEquals(lo, 35800)
    }
}