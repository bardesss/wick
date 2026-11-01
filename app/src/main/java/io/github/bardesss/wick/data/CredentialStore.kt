package io.github.bardesss.wick.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.LEGACY_API_PATH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun auth(): Auth?
    val authFlow: StateFlow<Auth?>
    suspend fun save(auth: Auth)
    suspend fun clear()
}

interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

/** AES-GCM with a non-exportable Keystore key. The random IV is stored in front of the ciphertext. */
class KeystoreTokenCipher(private val alias: String = "wick_auth") : TokenCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

class KeystoreCredentialStore(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : CredentialStore {

    constructor(context: Context, cipher: TokenCipher) : this(
        // a damaged file means signing in again, not a crash on every start
        PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }) {
            context.preferencesDataStoreFile("credentials")
        },
        cipher,
    )

    private val key = stringPreferencesKey("auth")

    /** Absent in v0.1.0, which stored a bare server address instead of the API URL. */
    private val formatKey = intPreferencesKey("auth_format")
    private val lock = Mutex()

    private val state = MutableStateFlow(runBlocking { load() })

    override val authFlow: StateFlow<Auth?> = state.asStateFlow()

    override fun auth(): Auth? = state.value

    override suspend fun save(auth: Auth) = lock.withLock {
        write(auth)
        state.value = auth
    }

    override suspend fun clear() = lock.withLock {
        store.edit {
            it.remove(key)
            it.remove(formatKey)
        }
        state.value = null
    }

    private suspend fun write(auth: Auth) {
        val plain = "${auth.server}\n${auth.token}".toByteArray()
        val stored = Base64.getEncoder().encodeToString(cipher.encrypt(plain))
        store.edit {
            it[key] = stored
            it[formatKey] = FORMAT
        }
    }

    private suspend fun load(): Auth? {
        val prefs = store.data.first()
        val stored = prefs[key] ?: return null
        val auth = runCatching {
            val plain = cipher.decrypt(Base64.getDecoder().decode(stored)).toString(Charsets.UTF_8)
            val (server, token) = plain.split("\n", limit = 2)
            Auth(server, token)
        }.getOrNull()
        if (auth == null) {
            store.edit { it.remove(key) }
            return null
        }
        if (prefs[formatKey] == FORMAT) return auth
        val migrated = auth.copy(server = auth.server.trimEnd('/') + LEGACY_API_PATH)
        // a failed rewrite is redone on the next start, so it must not stop this one
        runCatching { write(migrated) }
        return migrated
    }

    private companion object {
        const val FORMAT = 2
    }
}
