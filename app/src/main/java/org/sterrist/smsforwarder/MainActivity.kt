package org.sterrist.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SMSForwarder"
    }

    private lateinit var prefsManager: SharedPreferencesManager
    private lateinit var etBotToken: TextInputEditText
    private lateinit var etChatId: TextInputEditText
    private lateinit var etAllowedNumbers: TextInputEditText
    private lateinit var etKeywords: TextInputEditText
    private lateinit var switchOnlyImportant: SwitchMaterial
    private lateinit var switchIncludeSender: SwitchMaterial
    private lateinit var switchIncludeTimestamp: SwitchMaterial
    private lateinit var btnSave: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.i(TAG, "Все разрешения получены, запуск сервиса")
            startForegroundService()
        } else {
            Log.w(TAG, "Не все разрешения получены")
            addToLog("Не все разрешения получены")
            showToast("Для работы приложения необходимы все разрешения")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "MainActivity onCreate")

        prefsManager = SharedPreferencesManager(this)
        initViews()
        loadSettings()
        setupClickListeners()
        updateServiceStatus()

        if (prefsManager.isServiceRunning()) {
            Log.i(TAG, "Сервис был активен, перезапускаем")
            startSmsService()
        }
    }

    private fun initViews() {
        Log.d(TAG, "Инициализация View элементов")
        etBotToken = findViewById(R.id.etBotToken)
        etChatId = findViewById(R.id.etChatId)
        etAllowedNumbers = findViewById(R.id.etAllowedNumbers)
        etKeywords = findViewById(R.id.etKeywords)

        switchOnlyImportant = findViewById(R.id.switchOnlyImportant)
        switchIncludeSender = findViewById(R.id.switchIncludeSender)
        switchIncludeTimestamp = findViewById(R.id.switchIncludeTimestamp)

        btnSave = findViewById(R.id.btnSave)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        Log.d(TAG, "Все View элементы успешно инициализированы")
    }

    private fun loadSettings() {
        etBotToken.setText(prefsManager.getBotToken())
        etChatId.setText(prefsManager.getChatId())
        etAllowedNumbers.setText(prefsManager.getAllowedNumbers())
        etKeywords.setText(prefsManager.getKeywords())

        switchOnlyImportant.isChecked = prefsManager.getOnlyImportant()
        switchIncludeSender.isChecked = prefsManager.getIncludeSender()
        switchIncludeTimestamp.isChecked = prefsManager.getIncludeTimestamp()
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener { saveSettings() }
        btnStart.setOnClickListener { startSmsService() }
        btnStop.setOnClickListener { stopSmsService() }
    }

    private fun saveSettings() {
        prefsManager.setBotToken(etBotToken.text?.toString()?.trim() ?: "")
        prefsManager.setChatId(etChatId.text?.toString()?.trim() ?: "")
        prefsManager.setAllowedNumbers(etAllowedNumbers.text?.toString()?.trim() ?: "")
        prefsManager.setKeywords(etKeywords.text?.toString()?.trim() ?: "")

        prefsManager.setOnlyImportant(switchOnlyImportant.isChecked)
        prefsManager.setIncludeSender(switchIncludeSender.isChecked)
        prefsManager.setIncludeTimestamp(switchIncludeTimestamp.isChecked)

        addToLog("Настройки сохранены")
        showToast("Настройки сохранены успешно")
    }

    private fun startSmsService() {
        Log.d(TAG, "Запуск сервиса SMS")

        if (prefsManager.getBotToken().isEmpty() || prefsManager.getChatId().isEmpty()) {
            Log.w(TAG, "Bot Token или Chat ID не заполнены")
            showToast("Заполните Bot Token и Chat ID")
            return
        }

        val requiredPermissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (requiredPermissions.isNotEmpty()) {
            Log.d(TAG, "Запрос недостающих разрешений: ${requiredPermissions.joinToString()}")
            requestPermissionLauncher.launch(requiredPermissions)
        } else {
            Log.d(TAG, "Все разрешения уже есть, запускаем сервис")
            startForegroundService()
        }
    }

    private fun startForegroundService() {
        Log.d(TAG, "Запуск foreground service")

        val serviceIntent = Intent(this, ForegroundService::class.java)
        try {
            startForegroundService(serviceIntent)
            prefsManager.setServiceRunning(true)
            updateServiceStatus()
            addToLog("Сервис запущен")
            Log.i(TAG, "Сервис успешно запущен")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска сервиса: ${e.message}", e)
            addToLog("Ошибка запуска: ${e.message}")
            showToast("Ошибка запуска сервиса")
        }
    }

    private fun stopSmsService() {
        Log.d(TAG, "Остановка сервиса")
        val serviceIntent = Intent(this, ForegroundService::class.java)
        stopService(serviceIntent)

        prefsManager.setServiceRunning(false)
        updateServiceStatus()
        addToLog("Сервис остановлен")
        showToast("Сервис остановлен")
        Log.i(TAG, "Сервис остановлен")
    }

    private fun updateServiceStatus() {
        if (prefsManager.isServiceRunning()) {
            tvStatus.text = "Статус: Сервис активен"
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "Статус: Сервис остановлен"
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun addToLog(message: String) {
        val currentLog = tvLog.text.toString()
        val newLog = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} - $message\n$currentLog"
        tvLog.text = newLog
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}