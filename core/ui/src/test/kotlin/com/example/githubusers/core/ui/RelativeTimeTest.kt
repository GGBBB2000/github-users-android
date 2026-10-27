package com.example.githubusers.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class RelativeTimeTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    private fun between(time: String) = RelativeTime.between(Instant.parse(time), now, ZoneOffset.UTC)

    @Test
    fun `1 分未満と未来はたった今`() {
        assertEquals(RelativeTime.JustNow, between("2026-10-27T11:59:30Z"))
        assertEquals(RelativeTime.JustNow, between("2026-10-28T00:00:00Z"))
    }

    @Test
    fun `分と時間`() {
        assertEquals(RelativeTime.Minutes(5), between("2026-10-27T11:55:00Z"))
        assertEquals(RelativeTime.Hours(3), between("2026-10-27T09:00:00Z"))
    }

    @Test
    fun `日・月・年はカレンダーで数える`() {
        assertEquals(RelativeTime.Days(1), between("2026-10-26T11:00:00Z"))
        assertEquals(RelativeTime.Months(1), between("2026-09-27T11:00:00Z"))
        assertEquals(RelativeTime.Years(19), between("2007-10-20T05:24:19Z"))
    }
}
