package io.github.bardesss.wick.data

import io.github.bardesss.wick.api.Auth
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.IOException
import java.security.ProviderException
import javax.crypto.AEADBadTagException

class CredentialStoreTest {
    /** Reverses the bytes; enough to prove the stored value is not plain text. */
    private class FakeCipher(var broken: Boolean = false) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray): ByteArray {
            if (broken) throw AEADBadTagException("key lost")
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

    /** Fails with [failures] in turn, then behaves like [FakeCipher]. */
    private class FailingCipher(private val failures: MutableList<Exception>) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray): ByteArray {
            if (failures.isNotEmpty()) throw failures.removeAt(0)
            return data.reversedArray()
        }
    }

    private class AlwaysFailing(private val error: Exception) : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray): ByteArray = throw error
    }

    private val saved = Auth("https://example.com", "me/key")

    private suspend fun seeded(): MemoryPreferences {
        val ds = MemoryPreferences()
        KeystoreCredentialStore(ds, FakeCipher()).save(saved)
        return ds
    }

    @Test
    fun transient_decrypt_failure_keeps_the_stored_sign_in() = runBlocking {
        val ds = seeded()
        assertNull(KeystoreCredentialStore(ds, AlwaysFailing(ProviderException("busy"))).auth())
        assertEquals(saved, KeystoreCredentialStore(ds, FakeCipher()).auth())
    }

    @Test
    fun decrypt_is_retried_before_giving_up() = runBlocking {
        val ds = seeded()
        val cipher = FailingCipher(mutableListOf(ProviderException("a"), ProviderException("b")))
        assertEquals(saved, KeystoreCredentialStore(ds, cipher).auth())
    }

    @Test
    fun bad_tag_drops_the_stored_sign_in() = runBlocking {
        val ds = seeded()
        assertNull(KeystoreCredentialStore(ds, AlwaysFailing(AEADBadTagException("tag"))).auth())
        assertNull(KeystoreCredentialStore(ds, FakeCipher()).auth())
    }

    @Test
    fun plaintext_without_a_newline_drops_the_stored_sign_in() = runBlocking {
        val ds = MemoryPreferences()
        val stored = Base64.getEncoder().encodeToString("nonewline".toByteArray().reversedArray())
        ds.edit { it[stringPreferencesKey("auth")] = stored }
        assertNull(KeystoreCredentialStore(ds, FakeCipher()).auth())
        assertNull(ds.data.first()[stringPreferencesKey("auth")])
    }

    @Test
    fun failed_removal_does_not_crash() = runBlocking {
        val ds = seeded()
        val failing = object : DataStore<Preferences> by ds {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk")
        }
        assertNull(KeystoreCredentialStore(failing, AlwaysFailing(AEADBadTagException("tag"))).auth())
    }
}
