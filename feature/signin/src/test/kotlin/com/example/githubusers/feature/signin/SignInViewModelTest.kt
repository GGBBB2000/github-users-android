package com.example.githubusers.feature.signin

import androidx.lifecycle.SavedStateHandle
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.testing.FakeAuthRepository
import com.example.githubusers.core.testing.MainDispatcherRule
import com.example.githubusers.core.testing.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-28T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val authRepository = FakeAuthRepository().apply {
        deviceCode = TestData.deviceCode(expiresAt = now.plusSeconds(900))
    }
    private val savedStateHandle = SavedStateHandle()

    private fun createViewModel() = SignInViewModel(authRepository, savedStateHandle, clock)

    @Test
    fun `コードを取得して、許可されるのを待つ`() = runTest {
        val viewModel = createViewModel()

        assertEquals(
            SignInUiState.Waiting("WDJB-MJHT", "https://github.com/login/device", now.plusSeconds(900)),
            viewModel.uiState.value,
        )
        assertEquals(listOf(authRepository.deviceCode), authRepository.awaitedCodes)
    }

    @Test
    fun `許可されたらサインインした状態になる`() = runTest {
        val viewModel = createViewModel()

        authRepository.authorization.complete(DeviceAuthorizationResult.Authorized("octocat"))

        assertEquals(SignInUiState.SignedIn("octocat"), viewModel.uiState.value)
    }

    @Test
    fun `有効期限が切れたら、新しいコードを取得してやり直せる`() = runTest {
        val viewModel = createViewModel()
        authRepository.authorization.complete(DeviceAuthorizationResult.Expired)
        assertEquals(SignInUiState.Expired, viewModel.uiState.value)

        authRepository.authorization = CompletableDeferred()
        authRepository.deviceCode = TestData.deviceCode(userCode = "ABCD-EFGH", expiresAt = now.plusSeconds(900))
        viewModel.retry()

        assertEquals("ABCD-EFGH", (viewModel.uiState.value as SignInUiState.Waiting).userCode)
        assertEquals(2, authRepository.requestedCodes.size)
    }

    @Test
    fun `拒否されたら拒否の状態になる`() = runTest {
        val viewModel = createViewModel()

        authRepository.authorization.complete(DeviceAuthorizationResult.Denied)

        assertEquals(SignInUiState.Denied, viewModel.uiState.value)
    }

    @Test
    fun `コードを取得できなかったら失敗の状態になり、再試行できる`() = runTest {
        val error = GitHubException.Network(IOException())
        authRepository.nextDeviceCodeError = error
        val viewModel = createViewModel()
        assertEquals(SignInUiState.Failed(error, afterCodeIssued = false), viewModel.uiState.value)

        viewModel.retry()

        assertTrue(viewModel.uiState.value is SignInUiState.Waiting)
    }

    @Test
    fun `許可を待っている間に失敗したら、コードの表示後の失敗として扱う`() = runTest {
        val viewModel = createViewModel()
        val error = GitHubException.OAuth("device_flow_disabled")

        authRepository.authorization.completeExceptionally(error)

        assertEquals(SignInUiState.Failed(error, afterCodeIssued = true), viewModel.uiState.value)
    }

    @Test
    fun `作り直されても、有効期限内なら同じコードで待ち続ける`() = runTest {
        createViewModel()

        // 回転やプロセスの終了の後、同じ SavedStateHandle で作り直される
        val restored = createViewModel()

        assertEquals("WDJB-MJHT", (restored.uiState.value as SignInUiState.Waiting).userCode)
        assertEquals(1, authRepository.requestedCodes.size)
        assertEquals(2, authRepository.awaitedCodes.size)
    }

    @Test
    fun `保存したコードの有効期限が切れていたら、新しいコードを取得する`() = runTest {
        authRepository.deviceCode = TestData.deviceCode(expiresAt = now.minusSeconds(1))
        createViewModel()
        authRepository.deviceCode = TestData.deviceCode(userCode = "ABCD-EFGH", expiresAt = now.plusSeconds(900))

        val restored = createViewModel()

        assertEquals("ABCD-EFGH", (restored.uiState.value as SignInUiState.Waiting).userCode)
        assertEquals(2, authRepository.requestedCodes.size)
    }

    @Test
    fun `読み上げ用にコードを1文字ずつ区切る`() {
        assertEquals("W D J B ハイフン M J H T", spokenCode("WDJB-MJHT", hyphen = "ハイフン"))
    }
}
