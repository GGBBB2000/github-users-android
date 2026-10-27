package com.example.githubusers.feature.userdetail

import androidx.compose.ui.unit.dp
import com.example.githubusers.core.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContributionLayoutTest {
    private val calendar = TestData.contributionCalendar(end = LocalDate.parse("2026-10-28")) { 1 }

    @Test
    fun `1列に 13dp 以上取れるなら全体を表示する`() {
        // タブレットの詳細ペイン（モックアップの W = 692dp）
        val layout = calendarLayout(areaWidth = 692.dp, columns = 53)

        assertTrue(layout.fits)
        assertEquals(13.dp, layout.pitch)
        assertEquals(10.dp, layout.cell)
    }

    @Test
    fun `幅が広くてもマスは 13dp まで`() {
        val layout = calendarLayout(areaWidth = 1200.dp, columns = 53)

        assertTrue(layout.fits)
        assertEquals(13.dp, layout.cell)
    }

    @Test
    fun `スマホの幅では横スクロールにする`() {
        // 幅 412dp − 左右の余白 16dp × 2 − カードの内側の余白 16dp × 2 − 曜日の列 20dp
        val layout = calendarLayout(areaWidth = 328.dp, columns = 53)

        assertFalse(layout.fits)
        assertEquals(14.dp, layout.pitch)
        assertEquals(11.dp, layout.cell)
    }

    @Test
    fun `月のラベルは1日を含む週に置き、近すぎるラベルは省く`() {
        // 2025-10-26（日）から始まる最初の列には 11月1日 が含まれるので、途中から始まる 10月 は省く
        val labels = monthLabels(calendar.weeks)

        assertEquals(0 to 11, labels.first())
        assertEquals(12, labels.size)
        assertEquals((1..12).toSet(), labels.map { it.second }.toSet())
        labels.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= 3) }
    }

    @Test
    fun `列と曜日から日を探し、ない日は null`() {
        assertEquals(LocalDate.parse("2025-10-26"), dayAt(calendar.weeks, column = 0, weekday = 0)?.date)
        // 最後の週は水曜日（2026-10-28）で終わる
        assertEquals(LocalDate.parse("2026-10-28"), dayAt(calendar.weeks, column = 52, weekday = 3)?.date)
        assertNull(dayAt(calendar.weeks, column = 52, weekday = 4))
    }

    @Test
    fun `TalkBack の操作で選択を動かす`() {
        val days = calendar.days

        // 選んでいなければ最新の日
        assertEquals(LocalDate.parse("2026-10-28"), moveSelection(days, null, -1))
        assertEquals(LocalDate.parse("2026-10-21"), moveSelection(days, LocalDate.parse("2026-10-28"), -7))
        // 範囲の外には出ない
        assertEquals(LocalDate.parse("2026-10-28"), moveSelection(days, LocalDate.parse("2026-10-28"), 1))
    }
}
