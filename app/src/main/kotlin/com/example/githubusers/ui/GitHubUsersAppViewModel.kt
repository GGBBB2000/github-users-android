package com.example.githubusers.ui

import androidx.lifecycle.ViewModel
import com.example.githubusers.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 画面をまたぐ通知（自動でサインアウトしたことなど）を受け取る。 */
@HiltViewModel
class GitHubUsersAppViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    /** GitHub 側で許可が取り消されるなどして、自動でサインアウトしたとき。 */
    val sessionExpired: Flow<Unit> = authRepository.sessionExpired
}
