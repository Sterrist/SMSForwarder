package org.sterrist.smsforwarder

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setBotToken(token: String) {
        sharedPreferences.edit().putString(KEY_BOT_TOKEN, token).apply()
    }

    fun getBotToken(): String =
        sharedPreferences.getString(KEY_BOT_TOKEN, "") ?: ""

    fun setChatId(chatId: String) {
        sharedPreferences.edit().putString(KEY_CHAT_ID, chatId).apply()
    }

    fun getChatId(): String =
        sharedPreferences.getString(KEY_CHAT_ID, "") ?: ""

    fun setAllowedNumbers(numbers: String) {
        sharedPreferences.edit().putString(KEY_ALLOWED_NUMBERS, numbers).apply()
    }

    fun getAllowedNumbers(): String =
        sharedPreferences.getString(KEY_ALLOWED_NUMBERS, "") ?: ""

    fun setKeywords(keywords: String) {
        sharedPreferences.edit().putString(KEY_KEYWORDS, keywords).apply()
    }

    fun getKeywords(): String =
        sharedPreferences.getString(KEY_KEYWORDS, "") ?: ""

    fun setOnlyImportant(onlyImportant: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONLY_IMPORTANT, onlyImportant).apply()
    }

    fun getOnlyImportant(): Boolean =
        sharedPreferences.getBoolean(KEY_ONLY_IMPORTANT, false)

    fun setIncludeSender(includeSender: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_INCLUDE_SENDER, includeSender).apply()
    }

    fun getIncludeSender(): Boolean =
        sharedPreferences.getBoolean(KEY_INCLUDE_SENDER, true)

    fun setIncludeTimestamp(includeTimestamp: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_INCLUDE_TIMESTAMP, includeTimestamp).apply()
    }

    fun getIncludeTimestamp(): Boolean =
        sharedPreferences.getBoolean(KEY_INCLUDE_TIMESTAMP, false)

    fun setServiceRunning(running: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
    }

    fun isServiceRunning(): Boolean =
        sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)

    companion object {
        private const val PREFS_NAME = "SMSForwarderPrefs"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_ALLOWED_NUMBERS = "allowed_numbers"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_ONLY_IMPORTANT = "only_important"
        private const val KEY_INCLUDE_SENDER = "include_sender"
        private const val KEY_INCLUDE_TIMESTAMP = "include_timestamp"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}