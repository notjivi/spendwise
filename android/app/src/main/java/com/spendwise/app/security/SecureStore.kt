package com.spendwise.app.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted key-value store: DataStore for storage + Tink AES-256-GCM for confidentiality.
 * The Tink keyset is itself encrypted by a non-exportable Android Keystore master key.
 *
 * Why not EncryptedSharedPreferences? androidx.security-crypto is deprecated.
 * Note: Firebase already stores/refreshes its own tokens securely. This store holds OUR app state
 * (sync cursors, lock setting); we never copy Firebase tokens here.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "spendwise_keyset", "spendwise_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://spendwise_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    suspend fun putString(key: String, value: String) {
        // Binding the key name as associated data stops an attacker swapping ciphertexts between keys.
        val cipher = aead.encrypt(value.toByteArray(), key.toByteArray())
        dataStore.edit { it[stringPreferencesKey(key)] = Base64.encodeToString(cipher, Base64.NO_WRAP) }
    }

    fun observeString(key: String): Flow<String?> = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey(key)]?.let { enc ->
            // Undecryptable (tampered / keystore wiped) is treated as "not set", never a crash.
            runCatching { String(aead.decrypt(Base64.decode(enc, Base64.NO_WRAP), key.toByteArray())) }.getOrNull()
        }
    }

    suspend fun getString(key: String): String? = observeString(key).first()
}
