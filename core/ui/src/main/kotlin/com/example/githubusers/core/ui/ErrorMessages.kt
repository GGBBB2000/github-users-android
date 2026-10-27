package com.example.githubusers.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.githubusers.core.model.GitHubException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** エラーの補足説明（何が起きて、どうすればよいか）。 */
@Composable
fun errorMessage(error: GitHubException): String = when (error) {
    is GitHubException.Network -> stringResource(R.string.core_ui_error_network)
    is GitHubException.RateLimited -> rateLimitMessage(error.resetAt)
    is GitHubException.NotFound -> stringResource(R.string.core_ui_error_not_found)
    is GitHubException.Http, is GitHubException.Unexpected -> stringResource(R.string.core_ui_error_other)
}

/** レート制限に達したときのメッセージ。解除時刻がわかればそれも表示する。 */
@Composable
fun rateLimitMessage(resetAt: Instant?): String =
    if (resetAt != null) {
        stringResource(R.string.core_ui_error_rate_limit_with_time, formatTime(resetAt))
    } else {
        stringResource(R.string.core_ui_error_rate_limit)
    }

private fun formatTime(time: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time.atZone(ZoneId.systemDefault()))
