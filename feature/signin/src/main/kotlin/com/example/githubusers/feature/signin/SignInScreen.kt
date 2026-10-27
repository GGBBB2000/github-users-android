package com.example.githubusers.feature.signin

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.component.StatusState
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GitHubUsersTheme
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.ui.displayUrl
import com.example.githubusers.core.ui.errorMessage
import com.example.githubusers.core.ui.rememberExternalActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** コードを入力するページ（GitHub が返すものと同じ）。コードを取得する前の表示に使う。 */
private const val DEFAULT_VERIFICATION_URI = "https://github.com/login/device"

/**
 * GitHub へのサインイン（Device Flow）。
 *
 * @param isDialog ダイアログで表示するか。false なら全画面
 * @param onClose 閉じるボタンを押したとき
 * @param onSignedIn サインインできたとき
 */
@Composable
fun SignInScreen(
    isDialog: Boolean,
    onClose: () -> Unit,
    onSignedIn: (login: String) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val signedIn = uiState as? SignInUiState.SignedIn
    LaunchedEffect(signedIn) {
        if (signedIn != null) currentOnSignedIn(signedIn.login)
    }
    SignInScreen(uiState = uiState, isDialog = isDialog, onClose = onClose, onRetry = viewModel::retry)
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    isDialog: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    if (isDialog) {
        SignInDialogFrame(onClose = onClose, snackbarHostState = snackbarHostState) {
            SignInContent(uiState, onRetry, snackbarHostState, isDialog = true)
        }
    } else {
        SignInFullScreenFrame(onClose = onClose, snackbarHostState = snackbarHostState) {
            SignInContent(uiState, onRetry, snackbarHostState, isDialog = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInFullScreenFrame(
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_signin_title)) },
                navigationIcon = { CloseButton(onClose) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun SignInDialogFrame(
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(24.dp).widthIn(max = 560.dp),
    ) {
        Box {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.feature_signin_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    // 外側のタップでも閉じられるが、気づきにくいのでボタンも置く
                    CloseButton(onClose)
                }
                content()
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(onClick = onClose) {
        Icon(GuIcons.Clear, contentDescription = stringResource(R.string.feature_signin_close))
    }
}

/** 本文。全画面のときは下端の注記を画面の下に寄せる。 */
@Composable
private fun SignInContent(
    uiState: SignInUiState,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState,
    isDialog: Boolean,
) {
    BoxWithConstraints {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .then(if (isDialog) Modifier else Modifier.heightIn(min = maxHeight))
                .padding(start = 24.dp, end = 24.dp, top = if (isDialog) 16.dp else 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when (uiState) {
                    SignInUiState.Loading -> CodeContent(code = null, snackbarHostState = snackbarHostState)
                    is SignInUiState.Waiting -> CodeContent(code = uiState, snackbarHostState = snackbarHostState)
                    SignInUiState.Expired -> SignInStatus(
                        icon = GuIcons.Expired,
                        title = stringResource(R.string.feature_signin_expired_title),
                        message = stringResource(R.string.feature_signin_expired_message),
                        action = stringResource(R.string.feature_signin_expired_action),
                        isError = false,
                        onAction = onRetry,
                    )
                    SignInUiState.Denied -> SignInStatus(
                        icon = GuIcons.Denied,
                        title = stringResource(R.string.feature_signin_denied_title),
                        message = stringResource(R.string.feature_signin_denied_message),
                        action = stringResource(R.string.feature_signin_denied_action),
                        isError = false,
                        onAction = onRetry,
                    )
                    is SignInUiState.Failed -> SignInStatus(
                        icon = if (uiState.error is GitHubException.Network) GuIcons.Offline else GuIcons.Error,
                        title = stringResource(
                            if (uiState.afterCodeIssued) {
                                R.string.feature_signin_failed_title
                            } else {
                                R.string.feature_signin_code_failed_title
                            },
                        ),
                        message = errorMessage(uiState.error),
                        action = stringResource(R.string.feature_signin_retry),
                        isError = true,
                        onAction = onRetry,
                    )
                    // すぐに画面が閉じるので、何も出さない
                    is SignInUiState.SignedIn -> Unit
                }
            }
            Note(Modifier.padding(top = 20.dp))
        }
    }
}

/** コードの表示、GitHub を開くボタン、待機中の表示。[code] が null なら取得中。 */
@Composable
private fun ColumnScope.CodeContent(
    code: SignInUiState.Waiting?,
    snackbarHostState: SnackbarHostState,
) {
    val clipboard = LocalClipboard.current
    val externalActions = rememberExternalActions()
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.feature_signin_copied)
    val copy: suspend (String) -> Unit = { userCode ->
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("GitHub code", userCode)))
        // Android 13 以降はシステムがコピーしたことを表示するので、出さない
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    Text(stringResource(R.string.feature_signin_instruction), style = MaterialTheme.typography.bodyLarge)
    CodeBox(code?.userCode, onCopy = { userCode -> scope.launch { copy(userCode) } })
    Button(
        onClick = {
            if (code != null) {
                scope.launch {
                    // GitHub のページで貼り付けるだけで済むように、先にコピーしておく
                    copy(code.userCode)
                    externalActions.openUrl(code.verificationUri)
                }
            }
        },
        enabled = code != null,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Icon(GuIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.feature_signin_open_github))
    }
    Text(
        text = stringResource(
            R.string.feature_signin_other_device,
            displayUrl(code?.verificationUri ?: DEFAULT_VERIFICATION_URI),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (code != null) WaitingCard(code.expiresAt)
}

@Composable
private fun CodeBox(userCode: String?, onCopy: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (userCode == null) {
            val loadingDescription = stringResource(R.string.feature_signin_code_loading)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = loadingDescription },
            ) {
                val skeletonColor = colors.surfaceContainerHigh
                SkeletonBlock(Modifier.size(96.dp, 28.dp), RoundedCornerShape(8.dp), skeletonColor)
                SkeletonBlock(Modifier.size(12.dp, 4.dp), RoundedCornerShape(2.dp), skeletonColor)
                SkeletonBlock(Modifier.size(96.dp, 28.dp), RoundedCornerShape(8.dp), skeletonColor)
            }
        } else {
            val spoken = spokenCode(userCode, hyphen = stringResource(R.string.feature_signin_hyphen))
            Text(
                text = userCode,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
                // 1文字ずつ読み上げる
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
        IconButton(
            onClick = { userCode?.let(onCopy) },
            enabled = userCode != null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        ) {
            Icon(GuIcons.Copy, contentDescription = stringResource(R.string.feature_signin_copy))
        }
    }
}

@Composable
private fun WaitingCard(expiresAt: Instant) {
    val colors = MaterialTheme.colorScheme
    val minutes by produceState(remainingMinutes(expiresAt), expiresAt) {
        while (true) {
            value = remainingMinutes(expiresAt)
            delay(1_000)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.feature_signin_waiting), style = MaterialTheme.typography.bodyMedium)
            // 毎分の読み上げを避けるため、liveRegion にはしない
            Text(
                text = stringResource(R.string.feature_signin_expires_in, minutes),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignInStatus(
    icon: ImageVector,
    title: String,
    message: String,
    action: String,
    isError: Boolean,
    onAction: () -> Unit,
) {
    StatusState(
        icon = icon,
        title = title,
        message = message,
        isError = isError,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Note(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(GuIcons.Lock, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(stringResource(R.string.feature_signin_note), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** 残り時間（分）。1分未満でも「あと 1 分」と表示する。 */
private fun remainingMinutes(expiresAt: Instant): Int {
    val seconds = Duration.between(Instant.now(), expiresAt).seconds
    return ((seconds + 59) / 60).toInt().coerceAtLeast(1)
}

/** 読み上げ用に、コードを1文字ずつ区切る（例：「W D J B ハイフン M J H T」）。 */
internal fun spokenCode(code: String, hyphen: String): String =
    code.map { if (it == '-') hyphen else it.toString() }.joinToString(" ")

@Preview
@Composable
private fun SignInWaitingPreview() {
    GitHubUsersTheme {
        SignInScreen(
            uiState = SignInUiState.Waiting(
                userCode = "WDJB-MJHT",
                verificationUri = DEFAULT_VERIFICATION_URI,
                expiresAt = Instant.now().plusSeconds(14 * 60),
            ),
            isDialog = false,
            onClose = {},
            onRetry = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SignInDialogPreview() {
    GitHubUsersTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SignInScreen(uiState = SignInUiState.Loading, isDialog = true, onClose = {}, onRetry = {})
        }
    }
}

@Preview
@Composable
private fun SignInExpiredPreview() {
    GitHubUsersTheme {
        SignInScreen(uiState = SignInUiState.Expired, isDialog = false, onClose = {}, onRetry = {})
    }
}
