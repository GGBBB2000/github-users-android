package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryFactKind
import java.text.Normalizer

/**
 * 要約の指示（プロンプト）と、出力の確認。
 *
 * Gemini Nano は小さいモデルなので、指示は短くし、`##` で区切る（Prompt API のプロンプト設計の推奨）。
 */
internal object SummaryPrompt {
    const val TEMPERATURE = 0.2f
    const val TOP_K = 10

    /** 2〜3文（全角で 80〜140 字ほど）に十分な長さ。これで切れたら、最後の「。」までを使う */
    const val MAX_OUTPUT_TOKENS = 256

    /** 名前は利用者が自由に決められるので、長さを抑えて改行を除く（指示を書き換えられにくくする） */
    private const val MAX_NAME_LENGTH = 40

    /** 日本語の文章とみなすのに必要な、ひらがな・カタカナの数 */
    private const val MIN_KANA = 10

    fun label(kind: SummaryFactKind): String = when (kind) {
        SummaryFactKind.Contributions -> "コントリビューション（過去1年間）"
        SummaryFactKind.ActiveDays -> "活動した日"
        SummaryFactKind.LongestStreak -> "最長の連続"
        SummaryFactKind.CurrentStreak -> "今の連続"
        SummaryFactKind.BusiestWeekday -> "よく活動する曜日"
        SummaryFactKind.BusiestMonth -> "最も多かった月"
        SummaryFactKind.TopLanguages -> "よく使う言語"
        SummaryFactKind.TopRepo -> "スターが最も多いリポジトリ"
        SummaryFactKind.YearsOnGitHub -> "登録してから"
        SummaryFactKind.Followers -> "フォロワー"
        SummaryFactKind.PublicRepos -> "公開リポジトリ"
    }

    fun sanitizeName(name: String): String =
        name.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH)

    fun build(name: String, facts: List<SummaryFact>): String = buildString {
        appendLine("## 指示")
        appendLine("次の「データ」だけを使って、GitHub のユーザー「${sanitizeName(name)}」の活動を、日本語の二、三文で紹介してください。")
        appendLine()
        appendLine("## 守ること")
        appendLine("- 「データ」にない数値や事実は書かない")
        appendLine("- 数値は「データ」の表記をそのまま使う")
        appendLine("- 別々の項目を結びつけない（たとえば「最も多かった月」と「最長の連続」は関係がない）")
        appendLine("- 箇条書き、見出し、記号、絵文字は使わない")
        appendLine("- 紹介の文章だけを出力する")
        appendLine()
        appendLine("## データ")
        facts.forEach { appendLine("- ${label(it.kind)}: ${it.value}") }
        appendLine()
        append("## 紹介")
    }

    /**
     * 出力を整えて、条件に合うかを確かめる。合わなければ [SummaryException.InvalidOutput] を投げる。
     *
     * - 見出しや強調の記号、改行を取り除く
     * - 途中で切れていたら、最後の「。」までにする
     * - ひらがな・カタカナが少ない（日本語でない）なら不可
     * - 文章の中の数（`1,269`、`2026年5月` の 2026 と 5 など）が、すべて [name] と [facts] の中にあるか。ないなら不可
     *
     * 漢数字（「十四日」など）や、値どうしを勝手に結びつけた文は見つけられない。そのため画面に数値の一覧と注記を出す。
     */
    fun validate(output: GeneratedText, name: String, facts: List<SummaryFact>): String {
        var text = output.text
            .replace(Regex("[*#`]"), "")
            .lines()
            .joinToString("") { it.trim().removePrefix("- ").removePrefix("・") }
            .trim()
            .removeSurrounding("「", "」")
            .removeSurrounding("\"")
            .trim()
        if (output.truncated) {
            val end = text.lastIndexOf('。')
            if (end < 0) throw SummaryException.InvalidOutput("truncated without a full sentence")
            text = text.substring(0, end + 1)
        }
        if (text.count { it in 'ぁ'..'ヿ' } < MIN_KANA) {
            throw SummaryException.InvalidOutput("not Japanese")
        }
        val allowed = buildSet {
            addAll(numbersIn(sanitizeName(name)))
            facts.forEach {
                addAll(numbersIn(label(it.kind)))
                addAll(numbersIn(it.value))
            }
        }
        val unknown = numbersIn(text) - allowed
        if (unknown.isNotEmpty()) throw SummaryException.InvalidOutput("unknown numbers: $unknown")
        return text
    }

    /** 文章の中の数。全角の数字も半角にそろえ、3桁区切りのカンマは取り除く（`1,269` と `1269` を同じに扱う）。 */
    fun numbersIn(text: String): Set<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return Regex("\\d+(?:,\\d{3})*(?:\\.\\d+)?").findAll(normalized)
            .map { it.value.replace(",", "") }
            .toSet()
    }
}
