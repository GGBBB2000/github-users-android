package com.example.githubusers.core.model

/**
 * 端末上の AI で作った、ユーザーの活動の要約。
 *
 * @param facts AI に渡した数値。画面にも同じ値を出し、文章と食い違っていないかを確かめられるようにする
 * @param includesContributions 草（コントリビューション）の値を含めて作ったか
 */
data class ActivitySummary(
    val login: String,
    val text: String,
    val facts: List<SummaryFact>,
    val includesContributions: Boolean,
)

/**
 * 要約のもとにする値。[value] はアプリで計算して整形した文字列で、AI への指示と画面の表示の両方に同じものを使う。
 */
data class SummaryFact(val kind: SummaryFactKind, val value: String)

/** 要約のもとにする値の種類。画面に並べる順番でもある。 */
enum class SummaryFactKind {
    /** 過去1年間のコントリビューションの合計 */
    Contributions,

    /** 過去1年間で活動した日数 */
    ActiveDays,
    LongestStreak,
    CurrentStreak,
    BusiestWeekday,
    BusiestMonth,
    TopLanguages,

    /** スターが最も多いリポジトリ */
    TopRepo,
    YearsOnGitHub,
    Followers,
    PublicRepos,
    ;

    /** 草（要サインイン）から計算する値か */
    val fromContributions: Boolean
        get() = this in setOf(Contributions, ActiveDays, LongestStreak, CurrentStreak, BusiestWeekday, BusiestMonth)
}

/** 端末上の AI のモデルの状態。アプリ全体で1つ。 */
sealed interface SummaryModelState {
    /** 確かめている途中。何も出さない */
    data object Checking : SummaryModelState

    /** 端末が対応していない。要約に関する表示を一切出さない */
    data object Unavailable : SummaryModelState

    /** モデルをダウンロードすれば使える */
    data object Downloadable : SummaryModelState

    /** @param totalBytes 全体の量。わからなければ null */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : SummaryModelState

    data object DownloadFailed : SummaryModelState
    data object Available : SummaryModelState
}

/** 要約を作れなかったとき。 */
sealed class SummaryException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** 短い時間に何度も頼んだ（`BUSY`）。しばらくすれば作れる */
    class Busy(cause: Throwable? = null) : SummaryException(cause = cause)

    /** 1日あたりの電池の使用量の上限に達した（`PER_APP_BATTERY_USE_QUOTA_EXCEEDED`）。今日はもう作れない */
    class QuotaExceeded(cause: Throwable? = null) : SummaryException(cause = cause)

    /** アプリが前面でなくなった（`BACKGROUND_USE_BLOCKED`）。中止と同じに扱う */
    class Interrupted(cause: Throwable? = null) : SummaryException(cause = cause)

    /** 出力が条件に合わない（渡していない数値がある、日本語でない、空など） */
    class InvalidOutput(message: String) : SummaryException(message)

    /** モデルのダウンロードに失敗した */
    class DownloadFailed(cause: Throwable? = null) : SummaryException(cause = cause)

    class Failed(cause: Throwable? = null) : SummaryException(cause = cause)
}
