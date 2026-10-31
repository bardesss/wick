package io.github.bardesss.wick.data

import io.github.bardesss.wick.api.Auth
import kotlinx.coroutines.runBlocking
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
