package com.example.githubusers.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * 円形のアバター。
 *
 * 読み込み中と失敗時は、頭文字入りの tonal な丸を表示する。
 * [tone] で primary / secondary / tertiary のどのコンテナ色を使うかを決める（一覧では行ごとに順番に変える）。
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    login: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tone: Int = 0,
) {
    val (container, content) = avatarTone(tone)
    val description = stringResource(R.string.core_ui_avatar_description, login)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = login.take(1).uppercase(),
            color = content,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Medium,
        )
        // プレビューでは画像を読み込まない
        if (!LocalInspectionMode.current && avatarUrl != null) {
            val painter = rememberAsyncImagePainter(avatarUrl)
            val state by painter.state.collectAsStateWithLifecycle()
            if (state is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Composable
private fun avatarTone(tone: Int): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (Math.floorMod(tone, 3)) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
}
