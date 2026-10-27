package com.example.githubusers.feature.userdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.theme.GuShapes
import com.example.githubusers.core.designsystem.theme.GuSpacing

/** タブレットでユーザーを選んでいないときの詳細ペイン。背景だけを表示する。 */
@Composable
fun UserDetailPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = GuShapes.Card,
        modifier = modifier.expandedPaneFrame(),
    ) {}
}

/** タブレットの詳細ペインの外枠（システムバーを避け、上下と画面の右端に余白を取る）。 */
internal fun Modifier.expandedPaneFrame(): Modifier = this
    .safeDrawingPadding()
    .padding(top = 16.dp, bottom = 16.dp, end = GuSpacing.ScreenHorizontalExpanded)
    .fillMaxSize()
