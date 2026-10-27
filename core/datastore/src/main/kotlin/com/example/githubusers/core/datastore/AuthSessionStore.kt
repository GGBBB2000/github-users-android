package com.example.githubusers.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** サインイン情報を暗号化して保存する。 */
interface AuthSessionStore {
    /** 保存しているサインイン情報。サインインしていなければ null。 */
    val session: Flow<AuthSession?>

    suspend fun save(session: AuthSession)

    suspend fun clear()
}

internal class DataStoreAuthSessionStore @Inject constructor(
    private val dataStore: DataStore<AuthData>,
) : AuthSessionStore {
    override val session: Flow<AuthSession?> = dataStore.data
        // ファイルを読めないときは、サインインしていないものとして扱う
        .catch { e -> if (e is IOException) emit(AuthData()) else throw e }
        .map { it.session }

    override suspend fun save(session: AuthSession) {
        dataStore.updateData { it.copy(session = session) }
    }

    override suspend fun clear() {
        dataStore.updateData { AuthData() }
    }
}
