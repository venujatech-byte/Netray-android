package com.venujatech.netray.profiles

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProfileStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "profiles.bin"))

    fun load(): List<Profile> {
        if (!file.baseFile.exists()) return emptyList()
        val encrypted = file.openRead().use { it.readBytes() }
        require(encrypted.size > 13 && encrypted[0] == FORMAT) { "Saved profiles are invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
        val rows = JSONArray(cipher.doFinal(encrypted.copyOfRange(13, encrypted.size)).toString(Charsets.UTF_8))
        val list = mutableListOf<Profile>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            runCatching {
                Profile.parse(row.getString("link")).copy(id = row.getString("id"))
            }.onSuccess { list.add(it) }
        }
        return list
    }

    fun save(profiles: List<Profile>) {
        val rows = JSONArray()
        profiles.forEach { rows.put(JSONObject().put("id", it.id).put("link", it.link)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = byteArrayOf(FORMAT) + cipher.iv + cipher.doFinal(rows.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "netray-profile-key"
        private const val FORMAT: Byte = 1
    }
}
