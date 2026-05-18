package com.vaultdex.app.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * VULN-D (Advanced): Profile data caching misconfiguration.
 *
 * This class simulates a network response for a user profile/balance and
 * intentionally persists the raw JSON payload directly to context.cacheDir.
 *
 * Problems:
 *  1. RAW JSON IN CACHE: Sensitive data (account number, balance, card details)
 *     is written as unencrypted JSON to:
 *       /data/data/com.vaultdex.app/cache/profile_cache.json
 *
 *  2. SURVIVES LOGOUT: The cache file is NOT deleted when the user logs out.
 *     An attacker can access previous user's financial data after they log out.
 *
 *  3. NO EXPIRY: No TTL or cache invalidation is implemented.
 *
 * To read the cache after logout:
 *   adb shell run-as com.vaultdex.app cat cache/profile_cache.json
 */
class ProfileCacheManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // VULN-D: Cache file in cacheDir — survives logout, not encrypted
    private val cacheFile: File
        get() = File(context.cacheDir, "profile_cache.json")

    data class CachedProfile(
        val userId: String,
        val displayName: String,
        val email: String,
        val accountNumber: String,   // Sensitive — stored unencrypted in cache
        val balance: Double,          // Financial data in plaintext cache
        val cardNumber: String,       // Masked card number in cache
        val cardExpiry: String,
        val creditScore: Int,
        val lastLogin: Long,
        val cachedAt: Long = System.currentTimeMillis()
    )

    /**
     * Simulates fetching a profile from network and caching the raw response.
     * VULN-D: Writes unencrypted JSON to cacheDir without expiry or logout cleanup.
     */
    fun cacheProfile(profile: CachedProfile) {
        try {
            // VULN-D: Writing raw sensitive JSON directly to cache directory
            val jsonPayload = gson.toJson(profile)
            cacheFile.writeText(jsonPayload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCachedProfile(): CachedProfile? {
        return try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                gson.fromJson(json, CachedProfile::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * VULN-D: This method is intentionally NOT called during logout.
     * The cache persists across user sessions, leaking the previous user's data.
     * See AuthViewModel.logout() — clearCache() is omitted there deliberately.
     */
    fun clearCache() {
        if (cacheFile.exists()) cacheFile.delete()
    }

    fun hasCachedProfile(): Boolean = cacheFile.exists()

    /**
     * Simulates a mock "network" response to populate cache data.
     * In a real app this would come from a Retrofit/OkHttp call with bad cache headers.
     */
    fun fetchAndCacheMockProfile(
        userId: String,
        displayName: String,
        email: String,
        accountNumber: String
    ): CachedProfile {
        // Simulate network delay result — mock sensitive financial profile
        val profile = CachedProfile(
            userId        = userId,
            displayName   = displayName,
            email         = email,
            accountNumber = accountNumber,                         // Full account number
            balance       = (5000..250000).random().toDouble() / 100.0,
            cardNumber    = "**** **** **** ${(1000..9999).random()}",
            cardExpiry    = "0${(3..9).random()}/${(26..30).random()}",
            creditScore   = (620..850).random(),
            lastLogin     = System.currentTimeMillis()
        )
        // VULN-D: Cache the sensitive profile — will NOT be cleared on logout
        cacheProfile(profile)
        return profile
    }
}
