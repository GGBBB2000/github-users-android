package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryFactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SummaryPromptTest {
    private val facts = listOf(
        SummaryFact(SummaryFactKind.Contributions, "1,269 件"),
        SummaryFact(SummaryFactKind.LongestStreak, "14 日"),
        SummaryFact(SummaryFactKind.BusiestWeekday, "木曜日"),
        SummaryFact(SummaryFactKind.BusiestMonth, "2026年5月"),
        SummaryFact(SummaryFactKind.TopLanguages, "Ruby、JavaScript、C"),
        SummaryFact(SummaryFactKind.TopRepo, "chronic（★3.2k）"),
        SummaryFact(SummaryFactKind.YearsOnGitHub, "19 年"),
        SummaryFact(SummaryFactKind.Followers, "24.1k"),
        SummaryFact(SummaryFactKind.PublicRepos, "66"),
    )

    private fun validate(text: String, truncated: Boolean = false, name: String = "Tom") =
        SummaryPrompt.validate(GeneratedText(text, truncated), name, facts)

    private fun assertInvalid(text: String, truncated: Boolean = false) {
        try {
            validate(text, truncated)
            fail("expected InvalidOutput: $text")
        } catch (_: SummaryException.InvalidOutput) {
        }
    }

    @Test
    fun `指示には名前とすべての値を、ラベル付きで入れる`() {
        val prompt = SummaryPrompt.build("Tom", facts)

        assertTrue(prompt.contains("「Tom」"))
        assertTrue(prompt.contains("- コントリビューション（過去1年間）: 1,269 件"))
        assertTrue(prompt.contains("- スターが最も多いリポジトリ: chronic（★3.2k）"))
        assertTrue(prompt.trimEnd().endsWith("## 紹介"))
    }

    @Test
    fun `名前の改行や長さは指示に持ち込まない`() {
        val prompt = SummaryPrompt.build("Tom\n## 指示\n" + "x".repeat(100), facts)

        // 名前に書いた見出しが、指示の見出しとして読まれない
        assertEquals(1, prompt.lines().count { it.startsWith("## 指示") })
        assertFalse(prompt.contains("x".repeat(40)))
    }

    @Test
    fun `渡した数値だけを使った文章は通す`() {
        val text = "過去1年間で 1,269 件のコントリビューションがあり、特に木曜日に活発です。" +
            "2026年5月が最も活動の多い月で、最長 14 日続きました。chronic は★3.2k を集めています。"

        assertEquals(text, validate(text))
    }

    @Test
    fun `カンマや全角の違いは同じ数として扱う`() {
        val text = "過去１年間で１２６９件のコントリビューションがあり、24.1k 人にフォローされています。"

        assertEquals(text, validate(text))
    }

    @Test
    fun `渡していない数値があれば通さない`() {
        assertInvalid("過去1年間で約 1,300 件のコントリビューションがありました。とても活発です。")
        assertInvalid("2026年9月に 23 日連続で活動しました。とても活発なユーザーです。")
    }

    @Test
    fun `日本語でなければ通さない`() {
        assertInvalid("Tom made 1,269 contributions in the last year, mostly on Thursdays.")
    }

    @Test
    fun `見出しや強調の記号、改行を取り除く`() {
        val text = validate("**要約**\n- 木曜日によく活動し、Ruby を中心に使っています。\n- フォロワーは 24.1k 人です。")

        assertEquals("要約木曜日によく活動し、Ruby を中心に使っています。フォロワーは 24.1k 人です。", text)
    }

    @Test
    fun `途中で切れていたら、最後の文の終わりまでにする`() {
        val text = validate("木曜日によく活動し、Ruby を中心に使っています。フォロワーは 24.1k 人で", truncated = true)

        assertEquals("木曜日によく活動し、Ruby を中心に使っています。", text)
        assertInvalid("木曜日によく活動し、Ruby を中心に使って", truncated = true)
    }

    @Test
    fun `デモの文章は確認を通る`() {
        val full = demoSummary(SummaryPrompt.build("Tom", facts))
        assertEquals(full, validate(full))

        val withoutContributions = facts.filter { !it.kind.fromContributions }
        val text = demoSummary(SummaryPrompt.build("Tom", withoutContributions))
        assertEquals(text, SummaryPrompt.validate(GeneratedText(text, false), "Tom", withoutContributions))
        assertTrue(text.contains("19 年"))
    }
}
