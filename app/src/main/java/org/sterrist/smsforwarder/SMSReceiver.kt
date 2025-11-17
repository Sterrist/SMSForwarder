package org.sterrist.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMSForwarder"
        private val processedMessages = ConcurrentHashMap<String, Long>()
    }

    private lateinit var prefsManager: SharedPreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMSReceiver: получено сообщение")

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            prefsManager = SharedPreferencesManager(context)

            if (!prefsManager.isServiceRunning()) {
                Log.d(TAG, "Сервис не активен, игнорируем SMS")
                return
            }

            val bundle = intent.extras
            if (bundle != null) {
                try {
                    val pdus = bundle.get("pdus") as Array<*>?
                    Log.d(TAG, "Получено PDUs: ${pdus?.size ?: 0}")

                    pdus?.let {
                        processSmsMessages(context, it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка обработки SMS: ${e.message}")
                }
            }
        }
    }

    private fun processSmsMessages(context: Context, pdus: Array<*>) {
        val messages = Array(pdus.size) { i ->
            SmsMessage.createFromPdu(pdus[i] as ByteArray)
        }

        val fullMessage = StringBuilder()
        var sender: String? = null
        var timestamp: Long = 0

        messages.forEach { sms ->
            sender = sms.originatingAddress ?: "Unknown"
            fullMessage.append(sms.messageBody ?: "")
            timestamp = sms.timestampMillis
        }

        val messageBody = fullMessage.toString()
        val messageKey = "$sender|$messageBody|$timestamp"

        if (isMessageProcessedRecently(messageKey)) {
            Log.d(TAG, "Сообщение уже было обработано, пропускаем: $messageKey")
            return
        }

        Log.i(TAG, "Получено SMS от: $sender, текст: $messageBody")

        if (!passesFilters(sender!!, messageBody)) {
            Log.d(TAG, "SMS не прошло фильтрацию")
            return
        }

        val formattedMessage = formatMessage(sender!!, messageBody)
        Log.d(TAG, "Отправка в Telegram: $formattedMessage")
        sendToTelegram(formattedMessage)

        tryMarkSmsAsRead(context)
    }

    private fun isMessageProcessedRecently(messageKey: String): Boolean {
        val now = System.currentTimeMillis()
        val lastProcessed = processedMessages[messageKey]

        processedMessages.entries.removeAll { now - it.value > 60000 }

        return if (lastProcessed != null && now - lastProcessed < 30000) {
            true
        } else {
            processedMessages[messageKey] = now
            false
        }
    }

    private fun tryMarkSmsAsRead(context: Context) {
        try {
            Log.d(TAG, "Попытка пометить SMS как прочитанные")
            abortBroadcast()
            Log.d(TAG, "Broadcast абортирован - SMS не будет показано в стандартном приложении")
        } catch (e: Exception) {
            Log.d(TAG, "Не удалось абортировать broadcast: ${e.message}")
        }
    }

    private fun passesFilters(sender: String, message: String): Boolean {
        val allowedNumbers = prefsManager.getAllowedNumbers()
        if (allowedNumbers.isNotEmpty()) {
            val numbers = allowedNumbers.split(",")
            val numberAllowed = numbers.any { number ->
                sender.contains(number.trim())
            }
            if (!numberAllowed) return false
        }

        val keywords = prefsManager.getKeywords()
        if (keywords.isNotEmpty()) {
            val keywordArray = keywords.split(",")
            val keywordFound = keywordArray.any { keyword ->
                message.contains(keyword.trim(), ignoreCase = true)
            }
            if (!keywordFound) return false
        }

        return true
    }

    private fun formatMessage(sender: String, message: String): String {
        val sb = StringBuilder()

        if (prefsManager.getIncludeSender()) {
            sb.append("📱 От: $sender\n")
        }

        sb.append("💬 $message")

        if (prefsManager.getIncludeTimestamp()) {
            val timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                .format(Date())
            sb.append("\n⏰ $timestamp")
        }

        return sb.toString()
    }

    private fun sendToTelegram(message: String) {
        val botToken = prefsManager.getBotToken()
        val chatId = prefsManager.getChatId()

        if (botToken.isEmpty() || chatId.isEmpty()) {
            Log.e(TAG, "Bot Token или Chat ID не установлены")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Начало отправки в Telegram")
                val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val postData = """{"chat_id":"$chatId","text":"${message.replace("\"", "\\\"")}"}"""
                Log.d(TAG, "Отправляемые данные: $postData")

                connection.doOutput = true
                val os: OutputStream = connection.outputStream
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                val responseCode = connection.responseCode
                Log.d(TAG, "Ответ от Telegram: $responseCode")

                if (responseCode == 200) {
                    Log.i(TAG, "SMS успешно отправлено в Telegram")
                } else {
                    Log.e(TAG, "Ошибка отправки в Telegram: $responseCode")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Исключение при отправке в Telegram: ${e.message}")
            }
        }
    }
}