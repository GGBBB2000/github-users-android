package com.example.githubusers.core.testing

import com.example.githubusers.core.data.repository.AuthRepository
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.DeviceCode
import com.example.githubusers.core.model.GitHubException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * テスト用の [AuthRepository]。
 *
 * [awaitAuthorization] は [authorization] が完了するまで待つ。`Authorized` で完了すると、サインインした状態になる。
 * [nextDeviceCodeError] を設定すると、次の1回だけ [requestDeviceCode] がその例外を投げる。
 */
class FakeAuthRepository(
    override var isSignInAvailable: Boolean = true,
    initialState: AuthState = AuthState.SignedOut,
) : AuthRepository {
    override val authState = MutableStateFlow(initialState)
    override val sessionExpired = MutableSharedFlow<Unit>()

    var deviceCode: DeviceCode = TestData.deviceCode()
    var nextDeviceCodeError: GitHubException? = null
    var authorization = CompletableDeferred<DeviceAuthorizationResult>()

    val requestedCodes = mutableListOf<DeviceCode>()
    val awaitedCodes = mutableListOf<DeviceCode>()
    var signOutCount = 0
        private set

    override suspend fun requestDeviceCode(): DeviceCode {
        nextDeviceCodeError?.let { nextDeviceCodeError = null; throw it }
        requestedCodes += deviceCode
        return deviceCode
    }

    override suspend fun awaitAuthorization(deviceCode: DeviceCode): DeviceAuthorizationResult {
        awaitedCodes += deviceCode
        val result = authorization.await()
        if (result is DeviceAuthorizationResult.Authorized) {
            authState.value = AuthState.SignedIn(result.login, avatarUrl = null)
        }
        return result
    }

    override suspend fun signOut() {
        signOutCount++
        authState.value = AuthState.SignedOut
    }

    /** テストから、サインインした状態にする（サインイン画面を経由せずに）。 */
    fun signIn(login: String = "octocat") {
        authState.value = AuthState.SignedIn(login, avatarUrl = null)
    }
}
