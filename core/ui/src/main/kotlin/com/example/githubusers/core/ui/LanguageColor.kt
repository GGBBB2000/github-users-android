package com.example.githubusers.core.ui

import androidx.compose.ui.graphics.Color

/**
 * GitHub（linguist）でのプログラミング言語の色。
 *
 * 言語の識別色はデータの一部なので、テーマの色ロールではなく固定の色を使う。
 * 一覧にない言語は null を返すので、呼び出し側でテーマの色にフォールバックする。
 */
fun languageColor(language: String): Color? = LANGUAGE_COLORS[language]?.let { Color(it) }

private val LANGUAGE_COLORS: Map<String, Long> = mapOf(
    "ActionScript" to 0xFF882B0F,
    "C" to 0xFF555555,
    "C#" to 0xFF178600,
    "C++" to 0xFFF34B7D,
    "CSS" to 0xFF663399,
    "Clojure" to 0xFFDB5855,
    "CoffeeScript" to 0xFF244776,
    "Dart" to 0xFF00B4AB,
    "Elixir" to 0xFF6E4A7E,
    "Erlang" to 0xFFB83998,
    "Go" to 0xFF00ADD8,
    "Groovy" to 0xFF4298B8,
    "HTML" to 0xFFE34C26,
    "Haskell" to 0xFF5E5086,
    "Java" to 0xFFB07219,
    "JavaScript" to 0xFFF1E05A,
    "Jupyter Notebook" to 0xFFDA5B0B,
    "Kotlin" to 0xFFA97BFF,
    "Lua" to 0xFF000080,
    "Makefile" to 0xFF427819,
    "Objective-C" to 0xFF438EFF,
    "PHP" to 0xFF4F5D95,
    "Perl" to 0xFF0298C3,
    "PowerShell" to 0xFF012456,
    "Python" to 0xFF3572A5,
    "R" to 0xFF198CE7,
    "Ruby" to 0xFF701516,
    "Rust" to 0xFFDEA584,
    "Scala" to 0xFFC22D40,
    "Shell" to 0xFF89E051,
    "Swift" to 0xFFF05138,
    "TypeScript" to 0xFF3178C6,
    "Vim Script" to 0xFF199F4B,
    "Vue" to 0xFF41B883,
)
