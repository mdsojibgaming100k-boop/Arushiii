package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AdminConfig(
    val isPremiumRequired: Boolean = false,
    val monthlyPriceBdt: Int = 199,
    val yearlyPriceBdt: Int = 1499,
    val minAgeRequirement: Int = 18,
    val freeMessageLimit: Int = 15,
    val isBkashEnabled: Boolean = true,
    val bkashNumber: String = "01712345678",
    val isNagadEnabled: Boolean = true,
    val nagadNumber: String = "01812345678",
    val isRocketEnabled: Boolean = true,
    val rocketNumber: String = "01912345678",
    val paymentNotice: String = "বিকাশ, নগদ বা রকেটে 'Send Money' করে নিচের ঘরে ট্রানজেকশন আইডি (TrxID) দিন।"
)

data class PaymentSubmission(
    val trxId: String,
    val method: String,
    val amount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isApproved: Boolean = false
)

class AdminRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sojib_ai_admin_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AdminConfig> = _configFlow.asStateFlow()

    private val _isUserPremium = MutableStateFlow(prefs.getBoolean("user_is_premium", false))
    val isUserPremium: StateFlow<Boolean> = _isUserPremium.asStateFlow()

    private val _userMessageCount = MutableStateFlow(prefs.getInt("user_msg_count", 0))
    val userMessageCount: StateFlow<Int> = _userMessageCount.asStateFlow()

    private val _submissions = MutableStateFlow(loadSubmissions())
    val submissions: StateFlow<List<PaymentSubmission>> = _submissions.asStateFlow()

    private fun loadConfig(): AdminConfig {
        return AdminConfig(
            isPremiumRequired = prefs.getBoolean("cfg_is_premium_req", false),
            monthlyPriceBdt = prefs.getInt("cfg_monthly_price", 199),
            yearlyPriceBdt = prefs.getInt("cfg_yearly_price", 1499),
            minAgeRequirement = prefs.getInt("cfg_min_age", 18),
            freeMessageLimit = prefs.getInt("cfg_free_limit", 15),
            isBkashEnabled = prefs.getBoolean("cfg_bkash_enabled", true),
            bkashNumber = prefs.getString("cfg_bkash_num", "01712345678") ?: "01712345678",
            isNagadEnabled = prefs.getBoolean("cfg_nagad_enabled", true),
            nagadNumber = prefs.getString("cfg_nagad_num", "01812345678") ?: "01812345678",
            isRocketEnabled = prefs.getBoolean("cfg_rocket_enabled", true),
            rocketNumber = prefs.getString("cfg_rocket_num", "01912345678") ?: "01912345678",
            paymentNotice = prefs.getString("cfg_payment_notice", "বিকাশ, নগদ বা রকেটে 'Send Money' করে নিচে ট্রানজেকশন আইডি দিন।") ?: ""
        )
    }

    fun saveConfig(config: AdminConfig) {
        prefs.edit().apply {
            putBoolean("cfg_is_premium_req", config.isPremiumRequired)
            putInt("cfg_monthly_price", config.monthlyPriceBdt)
            putInt("cfg_yearly_price", config.yearlyPriceBdt)
            putInt("cfg_min_age", config.minAgeRequirement)
            putInt("cfg_free_limit", config.freeMessageLimit)
            putBoolean("cfg_bkash_enabled", config.isBkashEnabled)
            putString("cfg_bkash_num", config.bkashNumber)
            putBoolean("cfg_nagad_enabled", config.isNagadEnabled)
            putString("cfg_nagad_num", config.nagadNumber)
            putBoolean("cfg_rocket_enabled", config.isRocketEnabled)
            putString("cfg_rocket_num", config.rocketNumber)
            putString("cfg_payment_notice", config.paymentNotice)
            apply()
        }
        _configFlow.value = config
    }

    fun submitTrxId(method: String, trxId: String, amount: Int): Boolean {
        if (trxId.isBlank()) return false
        val newSub = PaymentSubmission(
            trxId = trxId.trim().uppercase(),
            method = method,
            amount = amount,
            isApproved = false // Pending admin manual verification - NO auto verify
        )
        val current = _submissions.value.toMutableList()
        current.add(0, newSub)
        _submissions.value = current
        saveSubmissions(current)

        // Do NOT auto-activate premium
        return true
    }

    fun approvePayment(trxId: String): Boolean {
        val current = _submissions.value.toMutableList()
        val index = current.indexOfFirst { it.trxId == trxId.trim().uppercase() }
        if (index != -1) {
            current[index] = current[index].copy(isApproved = true)
            _submissions.value = current
            saveSubmissions(current)
            setUserPremium(true)
            return true
        }
        return false
    }

    fun setUserPremium(isPremium: Boolean) {
        prefs.edit().putBoolean("user_is_premium", isPremium).apply()
        _isUserPremium.value = isPremium
    }

    fun incrementMessageCount(): Boolean {
        val current = _userMessageCount.value + 1
        prefs.edit().putInt("user_msg_count", current).apply()
        _userMessageCount.value = current

        val cfg = _configFlow.value
        if (cfg.isPremiumRequired && !_isUserPremium.value && current > cfg.freeMessageLimit) {
            return false // Limit reached, requires upgrade
        }
        return true
    }

    private fun loadSubmissions(): List<PaymentSubmission> {
        val jsonStr = prefs.getString("payment_subs_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<PaymentSubmission>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PaymentSubmission(
                        trxId = obj.getString("trxId"),
                        method = obj.getString("method"),
                        amount = obj.getInt("amount"),
                        timestamp = obj.getLong("timestamp"),
                        isApproved = obj.getBoolean("isApproved")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSubmissions(list: List<PaymentSubmission>) {
        try {
            val arr = org.json.JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("trxId", item.trxId)
                    put("method", item.method)
                    put("amount", item.amount)
                    put("timestamp", item.timestamp)
                    put("isApproved", item.isApproved)
                }
                arr.put(obj)
            }
            prefs.edit().putString("payment_subs_json", arr.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
