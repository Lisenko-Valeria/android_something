package com.example.android_something

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CycleActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var tvStatus: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvLocationTime: TextView
    private lateinit var tvCellInfo: TextView
    private lateinit var tvLog: TextView
    private lateinit var etServerIp: EditText
    private lateinit var etInterval: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnBack: Button

    private val handler = Handler(Looper.getMainLooper())
    private val PHONE_PERMISSION_REQUEST_CODE = 1002

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BackgroundService.ACTION_STATUS -> {
                    val msg = intent.getStringExtra(BackgroundService.EXTRA_MESSAGE) ?: ""
                    updateStatus(msg)
                }
                BackgroundService.ACTION_LOCATION -> {
                    val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BackgroundService.EXTRA_LOCATION, Location::class.java)
                    } else {
                        intent.getParcelableExtra(BackgroundService.EXTRA_LOCATION)
                    }
                    location?.let { updateLocationUI(it) }
                }
                BackgroundService.ACTION_CELL_INFO -> {
                    val cellInfoStr = intent.getStringExtra(BackgroundService.EXTRA_CELL_INFO)
                    cellInfoStr?.let { updateCellInfoUI(JSONObject(it)) }
                }
                BackgroundService.ACTION_LOG -> {
                    val msg = intent.getStringExtra(BackgroundService.EXTRA_MESSAGE) ?: ""
                    logMessage(msg)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cycle)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        etServerIp.setText("10.121.42.134")
        etInterval.setText("5")

        setClickListeners()

        val filter = IntentFilter().apply {
            addAction(BackgroundService.ACTION_STATUS)
            addAction(BackgroundService.ACTION_LOCATION)
            addAction(BackgroundService.ACTION_CELL_INFO)
            addAction(BackgroundService.ACTION_LOG)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceReceiver, filter)
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvAccuracy = findViewById(R.id.tvAccuracy)
        tvLocationTime = findViewById(R.id.tvLocationTime)
        tvCellInfo = findViewById(R.id.tvCellInfo)
        tvLog = findViewById(R.id.tvLog)
        etServerIp = findViewById(R.id.etServerIp)
        etInterval = findViewById(R.id.etInterval)
        btnStart = findViewById(R.id.btnStartTracking)
        btnStop = findViewById(R.id.btnStopTracking)
        btnBack = findViewById(R.id.btnBack)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setClickListeners() {
        btnBack.setOnClickListener {
            stopTracking()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnStart.setOnClickListener {
            if (btnStart.isEnabled) {
                checkAllPermissionsAndStart()
            }
        }

        btnStop.setOnClickListener {
            stopTracking()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkAllPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
            }
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }


        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PHONE_PERMISSION_REQUEST_CODE
            )
            return
        }
        startTracking()
    }

    private fun startTracking() {
        val serverIp = etServerIp.text.toString()
        val intervalStr = etInterval.text.toString()

        if (serverIp.isEmpty()) {
            logMessage("Введите IP адрес сервера")
            return
        }

        val interval = try {
            intervalStr.toLong() * 1000
        } catch (e: Exception) {
            5000L
        }

        val intent = Intent(this, BackgroundService::class.java).apply {
            action = "START_TRACKING"
            putExtra("SERVER_IP", serverIp)
            putExtra("INTERVAL", interval)
        }
        startService(intent)

        btnStart.isEnabled = false
        btnStop.isEnabled = true
        etServerIp.isEnabled = false
        etInterval.isEnabled = false
        tvStatus.text = "Статус: АКТИВЕН"
    }

    private fun stopTracking() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = "STOP_TRACKING"
        }
        startService(intent)

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        etServerIp.isEnabled = true
        etInterval.isEnabled = true
        tvStatus.text = "Статус: ОСТАНОВЛЕН"
    }

    private fun updateStatus(message: String) {
        logMessage("$message")
    }

    private fun updateLocationUI(location: Location) {
        val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val altitude = if (location.altitude == 0.0) 120.0 else location.altitude

        handler.post {
            tvLatitude.text = "Широта: ${"%.6f".format(location.latitude)}"
            tvLongitude.text = "Долгота: ${"%.6f".format(location.longitude)}"
            tvAltitude.text = "Высота: ${"%.2f".format(altitude)}"
            tvAccuracy.text = "Точность: ${"%.2f".format(location.accuracy)}"
            tvLocationTime.text = "Время: $currentTime"
        }
    }

    private fun updateCellInfoUI(cellInfoJson: JSONObject) {
        handler.post {
            val sb = StringBuilder()
            val cells = cellInfoJson.optJSONArray("cells")
            if (cells != null && cells.length() > 0) {
                sb.append("\n------ ИНФОРМАЦИЯ О СОТАХ ------\n")
                for (i in 0 until cells.length()) {
                    val cell = cells.getJSONObject(i)
                    sb.append("------- СОТА ${i + 1}: -------\n")
                    val type = cell.optString("type", "Неизвестно")
                    sb.append("Тип: $type\n")

                    val identity = cell.optJSONObject("identity")
                    if (identity != null) {
                        sb.append("--- Идентификация ---\n")
                        val keys = identity.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = identity.get(key)
                            sb.append("  $key: $value\n")
                        }
                    }

                    val signal = cell.optJSONObject("signal")
                    if (signal != null) {
                        sb.append("--- Сигнал ---\n")
                        val keys = signal.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = signal.get(key)
                            sb.append("  $key: $value\n")
                        }
                    }
                    sb.append("\n")
                }
            } else {
                sb.append("\nИнформация о сотах недоступна\n")
            }
            tvCellInfo.text = sb.toString()
        }
    }

    private fun logMessage(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timeStamp] $message\n"

        handler.post {
            tvLog.append(logMessage)
            val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollViewLog)
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startTracking()
            } else {
                Toast.makeText(this, "Не все разрешения предоставлены", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver)
        // stopTracking()
    }
}