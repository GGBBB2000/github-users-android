package com.example.githubusers.core.datastore

import androidx.datastore.core.CorruptionException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Android Keystore は使わず、Tink の鍵を直接作って暗号化を確かめる。 */
class AuthDataSerializerTest {
    private val data = AuthData(
        AuthSession(
            accessToken = "ghu_secret",
            accessTokenExpiresAtEpochSecond = 1_800_000_000,
            refreshToken = "ghr_secret",
            refreshTokenExpiresAtEpochSecond = 1_900_000_000,
            login = "octocat",
            avatarUrl = null,
        ),
    )

    private fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private suspend fun encrypt(aead: Aead, associatedData: String = "auth_session"): ByteArray {
        val output = ByteArrayOutputStream()
        encryptedAuthDataSerializer(associatedData.encodeToByteArray()) { aead }.writeTo(data, output)
        return output.toByteArray()
    }

    @Test
    fun `暗号化して保存し、読み戻せる`() = runTest {
        val aead = newAead()
        val bytes = encrypt(aead)

        // 保存した中身にトークンがそのまま入っていない
        assertFalse(bytes.decodeToString(throwOnInvalidSequence = false).contains("ghu_secret"))

        val read = encryptedAuthDataSerializer("auth_session".encodeToByteArray()) { aead }
            .readFrom(ByteArrayInputStream(bytes))
        assertEquals(data, read)
    }

    @Test
    fun `別の鍵では復号できず、壊れたデータとして扱う`() = runTest {
        val bytes = encrypt(newAead())

        try {
            encryptedAuthDataSerializer("auth_session".encodeToByteArray()) { newAead() }
                .readFrom(ByteArrayInputStream(bytes))
            fail()
        } catch (_: CorruptionException) {
        }
    }

    @Test
    fun `別のファイルの暗号文は復号できない`() = runTest {
        val aead = newAead()
        val bytes = encrypt(aead, associatedData = "other_file")

        try {
            encryptedAuthDataSerializer("auth_session".encodeToByteArray()) { aead }
                .readFrom(ByteArrayInputStream(bytes))
            fail()
        } catch (_: CorruptionException) {
        }
    }

    @Test
    fun `ログに出してもトークンは含まれない`() {
        assertFalse(data.session.toString().contains("ghu_secret"))
    }
}
