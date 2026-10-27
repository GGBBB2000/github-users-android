package com.example.githubusers.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.example.githubusers.core.datastore.AuthData
import com.example.githubusers.core.datastore.AuthSessionStore
import com.example.githubusers.core.datastore.DataStoreAuthSessionStore
import com.example.githubusers.core.datastore.encryptedAuthDataSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataStoreModule {

    @Binds
    fun bindAuthSessionStore(impl: DataStoreAuthSessionStore): AuthSessionStore

    companion object {
        private const val AUTH_FILE_NAME = "auth_session"

        /** Tink の鍵を置く SharedPreferences。バックアップの対象外にする（app の backup_rules.xml） */
        private const val KEYSET_PREF_FILE = "githubusers_auth_keyset"
        private const val KEYSET_NAME = "auth_keyset"
        private const val MASTER_KEY_URI = "android-keystore://githubusers_auth_master_key"

        @Provides
        @Singleton
        fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<AuthData> =
            DataStoreFactory.create(
                serializer = encryptedAuthDataSerializer(
                    associatedData = AUTH_FILE_NAME.encodeToByteArray(),
                    aeadProvider = { createAead(context) },
                ),
                // 復号できないとき（鍵が失われたときなど）は、サインインしていない状態に戻す
                corruptionHandler = ReplaceFileCorruptionHandler { AuthData() },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.dataStoreFile(AUTH_FILE_NAME) },
            )

        /** 暗号化に使う AEAD。鍵の鍵（マスター鍵）は Android Keystore に置く。 */
        private fun createAead(context: Context): Aead {
            AeadConfig.register()
            val keysetHandle = try {
                buildKeysetHandle(context)
            } catch (e: GeneralSecurityException) {
                // Keystore のマスター鍵が失われたなどで鍵を読めないときは、作り直す
                // （保存済みのデータは復号できなくなり、サインインしていない状態に戻る）
                context.deleteSharedPreferences(KEYSET_PREF_FILE)
                buildKeysetHandle(context)
            } catch (e: IOException) {
                context.deleteSharedPreferences(KEYSET_PREF_FILE)
                buildKeysetHandle(context)
            }
            return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private fun buildKeysetHandle(context: Context) =
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
    }
}
