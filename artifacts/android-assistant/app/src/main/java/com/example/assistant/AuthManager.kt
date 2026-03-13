package com.example.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Firebase Auth via REST API — no google-services.json required at build time.
 * Firebase Web API Key is loaded from:
 *   1. SharedPreferences (user-entered in Settings → Account)
 *   2. BuildConfig.FIREBASE_API_KEY (baked in at build time from GitHub secret)
 */
object AuthManager {

    private const val PREFS_NAME       = "assistant_prefs"
    private const val KEY_USER_EMAIL   = "user_email"
    private const val KEY_USER_NAME    = "user_name"
    private const val KEY_USER_PHOTO   = "user_photo"
    private const val KEY_ID_TOKEN     = "firebase_id_token"
    private const val KEY_REFRESH_TOKEN= "firebase_refresh_token"
    private const val KEY_FIREBASE_API_KEY = "firebase_api_key"
    private const val KEY_SKIP_LOGIN   = "skip_login"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Firebase API key resolution ───────────────────────────────────────────
    // Priority: 1) User-entered in Settings, 2) Build-time GitHub secret
    fun getFirebaseApiKey(ctx: Context): String? {
        val manual = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FIREBASE_API_KEY, null)
        if (!manual.isNullOrBlank()) return manual
        val baked = BuildConfig.FIREBASE_API_KEY
        return if (baked.isNotBlank()) baked else null
    }

    fun hasFirebaseKey(ctx: Context): Boolean = !getFirebaseApiKey(ctx).isNullOrBlank()

    fun saveFirebaseApiKey(ctx: Context, apiKey: String) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FIREBASE_API_KEY, apiKey).apply()
    }

    // ── Stored user info ──────────────────────────────────────────────────────

    fun isLoggedIn(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null) != null
    }

    fun getCurrentUserEmail(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_EMAIL, null)

    fun getCurrentUserName(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_NAME, null)

    fun signOut(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PHOTO)
            .remove(KEY_ID_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_SKIP_LOGIN)
            .apply()
    }

    fun skipLogin(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SKIP_LOGIN, true)
            .apply()
    }

    // ── Firebase REST API calls ───────────────────────────────────────────────

    data class AuthResult(val success: Boolean, val error: String? = null, val email: String? = null)

    suspend fun signInWithEmail(ctx: Context, email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val apiKey = getFirebaseApiKey(ctx)
                ?: return@withContext AuthResult(false,
                    "Firebase key not set.\n\nGo to Settings → Account → enter your Firebase Web API Key, then try again.")
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                    put("returnSecureToken", true)
                }.toString()

                val req = Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey")
                    .post(body.toRequestBody(JSON))
                    .build()

                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "{}")

                if (!resp.isSuccessful) {
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "Sign in failed"
                    return@withContext AuthResult(false, friendlyError(errMsg))
                }

                val userEmail    = json.optString("email")
                val idToken      = json.optString("idToken")
                val refreshToken = json.optString("refreshToken")

                saveUser(ctx, userEmail, null, null, idToken, refreshToken)
                AuthResult(true, email = userEmail)

            } catch (e: Exception) {
                AuthResult(false, "Network error: ${e.message}")
            }
        }

    suspend fun registerWithEmail(ctx: Context, email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val apiKey = getFirebaseApiKey(ctx)
                ?: return@withContext AuthResult(false,
                    "Firebase key not set.\n\nGo to Settings → Account → enter your Firebase Web API Key, then try again.")
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                    put("returnSecureToken", true)
                }.toString()

                val req = Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                    .post(body.toRequestBody(JSON))
                    .build()

                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "{}")

                if (!resp.isSuccessful) {
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "Registration failed"
                    return@withContext AuthResult(false, friendlyError(errMsg))
                }

                val userEmail    = json.optString("email")
                val idToken      = json.optString("idToken")
                val refreshToken = json.optString("refreshToken")

                saveUser(ctx, userEmail, null, null, idToken, refreshToken)
                AuthResult(true, email = userEmail)

            } catch (e: Exception) {
                AuthResult(false, "Network error: ${e.message}")
            }
        }

    suspend fun sendPasswordReset(ctx: Context, email: String): AuthResult =
        withContext(Dispatchers.IO) {
            val apiKey = getFirebaseApiKey(ctx)
                ?: return@withContext AuthResult(false,
                    "Firebase key not set. Add it in Settings → Account.")
            try {
                val body = JSONObject().apply {
                    put("requestType", "PASSWORD_RESET")
                    put("email", email)
                }.toString()

                val req = Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey")
                    .post(body.toRequestBody(JSON))
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "Reset failed"
                    return@withContext AuthResult(false, friendlyError(errMsg))
                }

                AuthResult(true)
            } catch (e: Exception) {
                AuthResult(false, "Network error: ${e.message}")
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun saveUser(
        ctx: Context,
        email: String?,
        name: String?,
        photo: String?,
        idToken: String?,
        refreshToken: String?
    ) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_USER_EMAIL, email)
            if (name != null) putString(KEY_USER_NAME, name)
            if (photo != null) putString(KEY_USER_PHOTO, photo)
            if (idToken != null) putString(KEY_ID_TOKEN, idToken)
            if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
            putBoolean(KEY_SKIP_LOGIN, false)
            apply()
        }
    }

    private fun friendlyError(code: String): String = when {
        code.contains("EMAIL_NOT_FOUND")        -> "No account found with this email."
        code.contains("INVALID_PASSWORD") || code.contains("INVALID_LOGIN_CREDENTIALS")
                                                -> "Incorrect email or password."
        code.contains("USER_DISABLED")          -> "This account has been disabled."
        code.contains("EMAIL_EXISTS")           -> "This email is already registered. Try signing in."
        code.contains("WEAK_PASSWORD")          -> "Password must be at least 6 characters."
        code.contains("INVALID_EMAIL")          -> "Please enter a valid email address."
        code.contains("TOO_MANY_ATTEMPTS")      -> "Too many attempts. Please try again later."
        code.contains("API_KEY_INVALID") || code.contains("INVALID_API_KEY")
                                                -> "Invalid Firebase API key. Check Settings → Account."
        else -> code.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}
