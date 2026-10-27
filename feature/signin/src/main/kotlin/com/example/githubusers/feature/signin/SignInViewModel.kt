package com.example.githubusers.feature.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubusers.core.data.repository.AuthRepository
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.DeviceCode
import com.example.githubusers.core.model.GitHubException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed interface SignInUiState {
    /** コードを取得中 */
    data object Loading : SignInUiState

    /** コードを表示して、許可されるのを待っている */
    data class Waiting(
        val userCode: String,
        val verificationUri: String,
        val expiresAt: Instant,
    ) : SignInUiState

    data object Expired : SignInUiState

    data object Denied : SignInUiState

    /**
     * @param afterCodeIssued コードを表示した後（許可を待っている間やトークンの保存中）に失敗したか
     */
    data class Failed(val error: GitHubException, val afterCodeIssued: Boolean) : SignInUiState

    /** サインインできた。画面を閉じる */
    data class SignedIn(val login: String) : SignInUiState
}

/**
 * Device Flow のサインイン。
 *
 * 画面を閉じると（ViewModel が破棄されると）問い合わせも止まる。
 * コードは [SavedStateHandle] に保存し、回転やプロセスの終了の後も、有効期限内なら同じコードで問い合わせを続ける。
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Loading)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        val saved = restoreDeviceCode()
        if (saved != null && clock.instant() < saved.expiresAt) {
            job = viewModelScope.launch { await(saved) }
        } else {
            requestCode()
        }
    }

    /** 期限切れ・拒否・失敗のときに、新しいコードを取得してやり直す。 */
    fun retry() {
        requestCode()
    }

    private fun requestCode() {
        job?.cancel()
        clearDeviceCode()
        _uiState.value = SignInUiState.Loading
        job = viewModelScope.launch {
            val code = try {
                authRepository.requestDeviceCode()
            } catch (e: GitHubException) {
                _uiState.value = SignInUiState.Failed(e, afterCodeIssued = false)
                return@launch
            }
            saveDeviceCode(code)
            await(code)
        }
    }

    private suspend fun await(code: DeviceCode) {
        _uiState.value = SignInUiState.Waiting(code.userCode, code.verificationUri, code.expiresAt)
        val state = try {
            when (val result = authRepository.awaitAuthorization(code)) {
                is DeviceAuthorizationResult.Authorized -> SignInUiState.SignedIn(result.login)
                DeviceAuthorizationResult.Expired -> SignInUiState.Expired
                DeviceAuthorizationResult.Denied -> SignInUiState.Denied
            }
        } catch (e: GitHubException) {
            SignInUiState.Failed(e, afterCodeIssued = true)
        }
        // コードは一度しか使えないので、結果が出たら消す
        clearDeviceCode()
        _uiState.value = state
    }

    private fun saveDeviceCode(code: DeviceCode) {
        savedStateHandle[KEY_DEVICE_CODE] = code.deviceCode
        savedStateHandle[KEY_USER_CODE] = code.userCode
        savedStateHandle[KEY_VERIFICATION_URI] = code.verificationUri
        savedStateHandle[KEY_EXPIRES_AT] = code.expiresAt.toEpochMilli()
        savedStateHandle[KEY_INTERVAL] = code.intervalSeconds
    }

    private fun restoreDeviceCode(): DeviceCode? {
        return DeviceCode(
            deviceCode = savedStateHandle[KEY_DEVICE_CODE] ?: return null,
            userCode = savedStateHandle[KEY_USER_CODE] ?: return null,
            verificationUri = savedStateHandle[KEY_VERIFICATION_URI] ?: return null,
            expiresAt = Instant.ofEpochMilli(savedStateHandle[KEY_EXPIRES_AT] ?: return null),
            intervalSeconds = savedStateHandle[KEY_INTERVAL] ?: return null,
        )
    }

    private fun clearDeviceCode() {
        listOf(KEY_DEVICE_CODE, KEY_USER_CODE, KEY_VERIFICATION_URI, KEY_EXPIRES_AT, KEY_INTERVAL)
            .forEach { savedStateHandle.remove<Any>(it) }
    }

    private companion object {
        const val KEY_DEVICE_CODE = "device_code"
        const val KEY_USER_CODE = "user_code"
        const val KEY_VERIFICATION_URI = "verification_uri"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_INTERVAL = "interval"
    }
}
