package com.example.githubusers.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.githubusers.core.designsystem.component.ErrorState
import com.example.githubusers.core.designsystem.component.StatusState
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.designsystem.R as DesignSystemR

/**
 * 初回の読み込みに失敗したときの全面表示。エラーの種類に合わせてアイコンと文言を変える。
 *
 * @param onSignIn サインインできる（未ログインで、サインインの設定がある）ときだけ渡す。
 *   レート制限のときに、サインインを主な操作として出す
 */
@Composable
fun GitHubErrorState(
    error: GitHubException,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSignIn: (() -> Unit)? = null,
) {
    if (error is GitHubException.RateLimited) {
        RateLimitedState(error, onRetry, onSignIn, modifier)
        return
    }
    ErrorState(
        title = stringResource(R.string.core_ui_error_title),
        message = errorMessage(error),
        icon = if (error is GitHubException.Network) GuIcons.Offline else GuIcons.Error,
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun RateLimitedState(
    error: GitHubException.RateLimited,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)?,
    modifier: Modifier,
) {
    if (onSignIn == null) {
        ErrorState(
            title = stringResource(R.string.core_ui_error_rate_limit_title),
            message = rateLimitMessage(error.resetAt),
            icon = GuIcons.RateLimited,
            onRetry = onRetry,
            modifier = modifier,
        )
        return
    }
    val message = error.resetAt?.let {
        stringResource(R.string.core_ui_error_rate_limit_sign_in_with_time, formatTime(it))
    } ?: stringResource(R.string.core_ui_error_rate_limit_sign_in)
    // 上限が解除されるまでは再試行しても失敗するので、サインインを主な操作にする
    StatusState(
        icon = GuIcons.RateLimited,
        title = stringResource(R.string.core_ui_error_rate_limit_title),
        message = message,
        modifier = modifier,
    ) {
        Button(onClick = onSignIn, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(GuIcons.SignIn, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.core_ui_sign_in))
        }
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(DesignSystemR.string.core_designsystem_retry))
        }
    }
}
