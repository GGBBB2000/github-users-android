package com.example.githubusers.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormatTest {
    @Test
    fun `1000 未満はそのまま表示する`() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `1000 以上は k で略す`() {
        assertEquals("1k", formatCount(1_000))
        assertEquals("24.1k", formatCount(24_100))
        assertEquals("24.1k", formatCount(24_199))
    }

    @Test
    fun `繰り上がって 1000k にならない`() {
        assertEquals("999.9k", formatCount(999_999))
    }

    @Test
    fun `100 万以上は M で略す`() {
        assertEquals("1M", formatCount(1_000_000))
        assertEquals("1.2M", formatCount(1_250_000))
    }
}
