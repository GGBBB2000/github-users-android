package com.example.githubusers.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** DESIGN.md の「余白と形状のまとめ」に対応する値。 */
object GuShapes {
    /** カードとペイン */
    val Card = RoundedCornerShape(24.dp)

    /** カードの内側のリスト */
    val InnerList = RoundedCornerShape(16.dp)

    /** チップとラベル */
    val Chip = RoundedCornerShape(8.dp)
}

object GuSpacing {
    /** 画面の左右の余白（スマホ） */
    val ScreenHorizontalCompact = 16.dp

    /** 画面の左右の余白（タブレット） */
    val ScreenHorizontalExpanded = 24.dp
}
