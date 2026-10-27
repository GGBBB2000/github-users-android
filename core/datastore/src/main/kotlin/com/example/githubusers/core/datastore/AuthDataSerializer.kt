package com.example.githubusers.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** [AuthData] を JSON にする。暗号化は [encryptedAuthDataSerializer] で行う。 */
internal object AuthDataJsonSerializer : Serializer<AuthData> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: AuthData = AuthData()

    override suspend fun readFrom(input: InputStream): AuthData = try {
        json.decodeFromString(AuthData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read auth data", e)
    }

    override suspend fun writeTo(t: AuthData, output: OutputStream) {
        output.write(json.encodeToString(AuthData.serializer(), t).encodeToByteArray())
    }
}

/**
 * Tink の AEAD で暗号化して読み書きする Serializer。
 *
 * 鍵の準備（Android Keystore へのアクセス）は時間がかかることがあるので、最初に読み書きするとき
 * （DataStore の IO スレッド）まで遅らせる。復号できないときは [CorruptionException] になる。
 */
internal fun encryptedAuthDataSerializer(
    associatedData: ByteArray,
    aeadProvider: () -> Aead,
): Serializer<AuthData> = object : Serializer<AuthData> {
    private val delegate by lazy {
        AeadSerializer(
            aead = aeadProvider(),
            wrappedSerializer = AuthDataJsonSerializer,
            // 別のファイルの暗号文と入れ替えられても、復号に失敗するようにする
            associatedData = associatedData,
        )
    }

    override val defaultValue: AuthData = AuthData()

    override suspend fun readFrom(input: InputStream): AuthData = delegate.readFrom(input)

    override suspend fun writeTo(t: AuthData, output: OutputStream) = delegate.writeTo(t, output)
}
