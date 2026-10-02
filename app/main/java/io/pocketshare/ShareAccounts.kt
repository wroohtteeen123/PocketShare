package io.pocketshare

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ShareAccount(val name: String, val uid: Int, val enabled: Boolean, val writable: Boolean, val secret: String)

/** Pure allow-list generation: never use filesystem identity (force user) as authorization. */
object AccountPolicy {
    // Unknown identities may receive guest rights, never an existing user's bad password.
    fun guestMapping(guest: Boolean) = "map to guest = ${if (guest) "Bad User" else "Never"}"
    fun validName(name: String) = name.matches(Regex("[a-z][a-z0-9_-]{0,31}")) && name !in setOf("root", "nobody", "guest")
    fun validPassword(password: String) = password.length in 1..256 && password.none { it == '\n' || it == '\r' || it == '\u0000' }
    fun validate(users: List<ShareAccount>) {
        require(users.size <= 32)
        require(users.map { it.name }.distinct().size == users.size)
        require(users.map { it.uid }.distinct().size == users.size)
        require(users.all { validName(it.name) && it.uid in 200000000..200999999 && it.secret.isNotEmpty() })
    }
    fun config(users: List<ShareAccount>, guest: Boolean, guestWrite: Boolean): String {
        validate(users)
        val active = users.filter { it.enabled }
        require(guest || active.isNotEmpty())
        val allowed = active.map { it.name } + if (guest) listOf("root") else emptyList()
        val writers = active.filter { it.writable }.map { it.name } + if (guest && guestWrite) listOf("root") else emptyList()
        return "read only = yes\nvalid users = ${allowed.joinToString(" ")}\nwrite list = ${writers.joinToString(" ")}"
    }
}

class ShareAccounts(context: Context) {
    private val prefs = context.getSharedPreferences("share_accounts", Context.MODE_PRIVATE)
    fun load(): List<ShareAccount> {
        val array = JSONArray(prefs.getString("users", "[]"))
        return List(array.length()) { i -> array.getJSONObject(i).let {
            ShareAccount(it.getString("name"), it.getInt("uid"), it.getBoolean("enabled"), it.getBoolean("writable"), it.getString("secret"))
        } }.also(AccountPolicy::validate)
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("pocketshare-accounts", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pocketshare-accounts", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(password: String, name: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun password(user: ShareAccount): String {
        val bytes = Base64.decode(user.secret, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(user.name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun save(original: ShareAccount?, name: String, password: String, enabled: Boolean, writable: Boolean) {
        val normalized = name.trim().lowercase(Locale.ROOT)
        require(AccountPolicy.validName(normalized))
        val users = load().toMutableList()
        require(users.none { it.name == normalized && it.name != original?.name })
        require(original != null || users.size < 32)
        require(password.isEmpty() && original != null || AccountPolicy.validPassword(password))
        // A rename must rebind the encrypted credential to the new account name.
        val secret = if (password.isNotEmpty()) encrypt(password, normalized)
            else if (original!!.name != normalized) encrypt(this.password(original), normalized) else original.secret
        val uid = original?.uid ?: prefs.getInt("next_uid", 200000000)
        users.removeAll { it.name == original?.name }
        users.add(ShareAccount(normalized, uid, enabled, writable, secret))
        write(users, if (original == null) uid + 1 else prefs.getInt("next_uid", 200000000))
    }
    fun remove(user: ShareAccount) = write(load().filter { it.name != user.name }, prefs.getInt("next_uid", 200000000))
    private fun write(users: List<ShareAccount>, next: Int) {
        AccountPolicy.validate(users)
        val json = JSONArray()
        users.forEach { json.put(JSONObject().put("name", it.name).put("uid", it.uid).put("enabled", it.enabled).put("writable", it.writable).put("secret", it.secret)) }
        check(prefs.edit().putString("users", json.toString()).putInt("next_uid", next).commit())
    }
}
