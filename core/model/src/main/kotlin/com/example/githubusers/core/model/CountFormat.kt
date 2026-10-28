package com.example.githubusers.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 件数を短く表示する。1000 以上は `24.1k`、100 万以上は `1.2M` のようにする。
 *
 * 実際より多く見えないように、小数第 1 位で切り捨てる。
 * 画面の表示と、AI の要約に渡す値の両方で使う（同じ表記にそろえるため）。
 */
fun formatCompactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> abbreviate(count, 1_000, "k")
    else -> abbreviate(count, 1_000_000, "M")
}

private fun abbreviate(count: Int, unit: Int, suffix: String): String {
    val value = BigDecimal(count).divide(BigDecimal(unit), 1, RoundingMode.DOWN).stripTrailingZeros()
    return value.toPlainString() + suffix
}
