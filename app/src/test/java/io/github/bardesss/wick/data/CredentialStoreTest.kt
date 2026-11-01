package io.github.bardesss.wick.data

import io.github.bardesss.wick.api.Auth
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialStoreTest {
    /** Reverses the bytes; enough to prove the stored value is not plain text. */
    private class FakeCipher(var broken: Boolean = false) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("key lost")
            return data.reversedArray()
        }
    }

    @Test
    fun saved_auth_survives_a_restart() = runBlocking {
        val ds = MemoryPreferences()
        val first = KeystoreCredentialStore(ds, FakeCipher())
        assertNull(first.auth())
        first.save(Auth("https://example.com", "me/key"))
        assertEquals(Auth("https://example.com", "me/key"), first.authFlow.value)

        val second = KeystoreCredentialStore(ds, FakeCipher())
        assertEquals(Auth("https://example.com", "me/key"), second.auth())
    }

    @Test
    fun v010_bare_root_becomes_the_api_url_once() = runBlocking {
        val ds = MemoryPreferences()
        val cipher = FakeCipher()
        // what v0.1.0 wrote: the bare server address and the token, with no format marker
        val legacy = Base64.getEncoder().encodeToString(cipher.encrypt("https://example.com/\nme/key".toByteArray()))
        ds.edit { it[stringPreferencesKey("auth")] = legacy }

        val migrated = KeystoreCredentialStore(ds, cipher)
        assertEquals(Auth("https://example.com/rest/googlereader", "me/key"), migrated.auth())

        // loading again must not append the path a second time
        assertEquals(Auth("https://example.com/rest/googlereader", "me/key"), KeystoreCredentialStore(ds, cipher).auth())
    }

    @Test
    fun clear_signs_out() = runBlocking {
        val ds = MemoryPreferences()
        val store = KeystoreCredentialStore(ds, FakeCipher())
        store.save(Auth("https://example.com", "me/key"))
        store.clear()
        assertNull(store.auth())
        assertNull(KeystoreCredentialStore(ds, FakeCipher()).auth())
    }

    @Test
    fun undecryptable_value_means_signed_out_and_is_cleared() = runBlocking {
        val ds = MemoryPreferences()
        KeystoreCredentialStore(ds, FakeCipher()).save(Auth("https://example.com", "me/key"))
        val lost = KeystoreCredentialStore(ds, FakeCipher(broken = true))
        assertNull(lost.auth())
        assertNull(KeystoreCredentialStore(ds, FakeCipher()).auth())
    }
}
