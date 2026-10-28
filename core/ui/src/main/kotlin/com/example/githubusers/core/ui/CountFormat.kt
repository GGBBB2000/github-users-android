package com.example.githubusers.core.ui

import com.example.githubusers.core.model.formatCompactCount

/**
 * 件数を短く表示する。1000 以上は `24.1k`、100 万以上は `1.2M` のようにする。
 *
 * 実際より多く見えないように、小数第 1 位で切り捨てる。
 */
fun formatCount(count: Int): String = formatCompactCount(count)
