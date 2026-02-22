package com.example.android_something

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.telephony.*
import android.util.Log
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CycleActivity : AppCompatActivity() {
    private val log_tag: String = "COMBINED_TRACKING"

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

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Telephony
    private lateinit var telephonyManager: TelephonyManager
    private val PHONE_PERMISSION_REQUEST_CODE = 1002

    // Tracking state
    private var isTracking = false
    private var trackingThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    // File path
    private val locationFilePath =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)}/combined_data.json"

    // ZeroMQ
    private var zmqContext: ZContext? = null
    private var zmqSocket: ZMQ.Socket? = null

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cycle)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Initialize UI
        initViews()

        // Set default values
        etServerIp.setText("10.122.153.134")
        etInterval.setText("30") // Default 30 seconds

        // Set click listeners
        setClickListeners()
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
            if (!isTracking) {
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

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Phone state permission for telephony
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PHONE_PERMISSION_REQUEST_CODE // Using combined request code
            )
        } else {
            startTracking()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
            startTracking()
        } else {
            Toast.makeText(this,
                "Некоторые разрешения не предоставлены. Функциональность может быть ограничена.",
                Toast.LENGTH_LONG).show()
            // Still try to start with what we have
            startTracking()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startTracking() {
        val serverIp = etServerIp.text.toString()
        val intervalStr = etInterval.text.toString()

        if (serverIp.isEmpty()) {
            logMessage("Введите IP адрес сервера")
            return
        }

        val interval = try {
            intervalStr.toLong() * 1000 // Convert to milliseconds
        } catch (e: Exception) {
            30000L // Default 30 seconds
        }

        isTracking = true
        updateUIForTracking(true)
        logMessage("Запуск трекинга. Сервер: $serverIp, интервал: ${interval/1000} сек")

        trackingThread = Thread {
            runTrackingLoop(serverIp, interval)
        }.apply { start() }
    }

    private fun stopTracking() {
        isTracking = false
        trackingThread?.interrupt()

        // Close ZMQ socket
        try {
            zmqSocket?.close()
            zmqContext?.close()
            zmqSocket = null
            zmqContext = null
        } catch (e: Exception) {
            Log.e(log_tag, "Error closing ZMQ: ${e.message}")
        }

        updateUIForTracking(false)
        logMessage("Трекинг остановлен")
    }

    private fun updateUIForTracking(tracking: Boolean) {
        handler.post {
            if (tracking) {
                tvStatus.text = "Статус: АКТИВЕН"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                btnStart.isEnabled = false
                btnStop.isEnabled = true
                etServerIp.isEnabled = false
                etInterval.isEnabled = false
            } else {
                tvStatus.text = "Статус: ОСТАНОВЛЕН"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                etServerIp.isEnabled = true
                etInterval.isEnabled = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun runTrackingLoop(serverIp: String, intervalMs: Long) {
        var packetCounter = 1
        var reconnectionAttempts = 0
        val maxReconnectionAttempts = 10

        while (isTracking) {
            try {
                // 1. Получаем локацию
                val location = getCurrentLocation()

                // 2. Получаем данные о сетях
                val cellInfoJson = getCellInfoJson()

                // 3. Обновляем UI
                if (location != null) {
                    updateLocationUI(location)
                }
                updateCellInfoUI(cellInfoJson)

                // 4. Создаем объединенный JSON
                val combinedData = createCombinedJson(location, cellInfoJson)

                // 5. Сохраняем в файл
                saveToFile(combinedData)

                // 6. Отправляем на сервер
                val sent = sendToServer(serverIp, combinedData.toString(), packetCounter)

                if (sent) {
                    packetCounter++
                    reconnectionAttempts = 0
                } else {
                    logMessage("Не удалось отправить данные на сервер")
                }

                // Ждем перед следующей итерацией
                Thread.sleep(intervalMs)

            } catch (e: Exception) {
                if (!isTracking) break
                logMessage("Ошибка в цикле трекинга: ${e.message}")
                e.printStackTrace()

                // Ждем перед повторной попыткой
                Thread.sleep(5000)
            }
        }
    }

    private fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).build()

        val locationResult = mutableListOf<Location>()
        val lock = Object()
        var isCompleted = false

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                synchronized(lock) {
                    result.lastLocation?.let { locationResult.add(it) }
                    isCompleted = true
                    lock.notify()
                }
            }
        }

        // Запрашиваем локацию
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener {
            synchronized(lock) {
                isCompleted = true
                lock.notify()
            }
        }

        // Ждем результат (максимум 15 секунд)
        synchronized(lock) {
            if (!isCompleted) {
                try {
                    lock.wait(15000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        // Удаляем колбэк
        fusedLocationClient.removeLocationUpdates(locationCallback)

        return if (locationResult.isNotEmpty()) locationResult[0] else null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getCellInfoJson(): JSONObject {
        val cellInfoJson = JSONObject()

        try {
            // Check for permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                cellInfoJson.put("error", "Missing location permission for cell info")
                return cellInfoJson
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                cellInfoJson.put("error", "Missing phone state permission")
                return cellInfoJson
            }

            val allCellInfo = telephonyManager.allCellInfo
            val cellsArray = JSONArray()

            if (allCellInfo != null && allCellInfo.isNotEmpty()) {
                for (cellInfo in allCellInfo) {
                    when {
                        cellInfo is CellInfoNr -> {
                            cellsArray.put(parseNrCellInfo(cellInfo))
                        }
                        cellInfo is CellInfoLte -> {
                            cellsArray.put(parseLteCellInfo(cellInfo))
                        }
                        cellInfo is CellInfoGsm -> {
                            cellsArray.put(parseGsmCellInfo(cellInfo))
                        }
                        else -> {
                            val unknownCell = JSONObject()
                            unknownCell.put("type", cellInfo.javaClass.simpleName)
                            unknownCell.put("registered", cellInfo.isRegistered)
                            cellsArray.put(unknownCell)
                        }
                    }
                }
            }

            cellInfoJson.put("cells", cellsArray)
            cellInfoJson.put("operator", telephonyManager.networkOperatorName ?: "Unknown")
            cellInfoJson.put("sim_operator", telephonyManager.simOperatorName ?: "Unknown")
            cellInfoJson.put("count", cellsArray.length())

        } catch (e: SecurityException) {
            cellInfoJson.put("error", "Security exception: ${e.message}")
        } catch (e: Exception) {
            cellInfoJson.put("error", "Error: ${e.message}")
        }

        return cellInfoJson
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun parseLteCellInfo(cellInfo: CellInfoLte): JSONObject {
        val cellJson = JSONObject()
        val cellIdentity = cellInfo.cellIdentity
        val cellSignal = cellInfo.cellSignalStrength

        cellJson.put("type", "LTE")
        cellJson.put("registered", cellInfo.isRegistered)

        val identity = JSONObject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            identity.put("band", cellIdentity.bands.joinToString())
        }
        identity.put("ci", cellIdentity.ci)
        identity.put("earfcn", cellIdentity.earfcn)
        identity.put("mcc", cellIdentity.mccString ?: "N/A")
        identity.put("mnc", cellIdentity.mncString ?: "N/A")
        identity.put("pci", cellIdentity.pci)
        identity.put("tac", cellIdentity.tac)
        cellJson.put("identity", identity)

        val signal = JSONObject()
        signal.put("asu_level", cellSignal.asuLevel)
        signal.put("cqi", cellSignal.cqi)
        signal.put("rsrp", cellSignal.rsrp)
        signal.put("rsrq", cellSignal.rsrq)
        signal.put("rssi", cellSignal.rssi)
        signal.put("rssnr", cellSignal.rssnr)
        signal.put("timing_advance", cellSignal.timingAdvance)
        cellJson.put("signal", signal)

        return cellJson
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun parseGsmCellInfo(cellInfo: CellInfoGsm): JSONObject {
        val cellJson = JSONObject()
        val cellIdentity = cellInfo.cellIdentity
        val cellSignal = cellInfo.cellSignalStrength

        cellJson.put("type", "GSM")
        cellJson.put("registered", cellInfo.isRegistered)

        val identity = JSONObject()
        identity.put("cid", cellIdentity.cid)
        identity.put("bsic", cellIdentity.bsic)
        identity.put("arfcn", cellIdentity.arfcn)
        identity.put("lac", cellIdentity.lac)
        identity.put("mcc", cellIdentity.mccString ?: "N/A")
        identity.put("mnc", cellIdentity.mncString ?: "N/A")
        identity.put("psc", cellIdentity.psc)
        cellJson.put("identity", identity)

        val signal = JSONObject()
        signal.put("dbm", cellSignal.dbm)
        signal.put("rssi", cellSignal.rssi)
        signal.put("timing_advance", cellSignal.timingAdvance)
        cellJson.put("signal", signal)

        return cellJson
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun parseNrCellInfo(cellInfo: CellInfoNr): JSONObject {
        val cellJson = JSONObject()
        val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
        val cellSignal = cellInfo.cellSignalStrength as CellSignalStrengthNr

        cellJson.put("type", "NR")
        cellJson.put("registered", cellInfo.isRegistered)

        val identity = JSONObject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            identity.put("band", cellIdentity.bands.joinToString())
        }
        identity.put("nci", cellIdentity.nci)
        identity.put("pci", cellIdentity.pci)
        identity.put("nrarfcn", cellIdentity.nrarfcn)
        identity.put("tac", cellIdentity.tac)
        identity.put("mcc", cellIdentity.mccString ?: "N/A")
        identity.put("mnc", cellIdentity.mncString ?: "N/A")
        cellJson.put("identity", identity)

        val signal = JSONObject()
        signal.put("ss_rsrp", cellSignal.ssRsrp)
        signal.put("ss_rsrq", cellSignal.ssRsrq)
        signal.put("ss_sinr", cellSignal.ssSinr)
        cellJson.put("signal", signal)

        return cellJson
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
            try {
                val sb = StringBuilder()

                // Основная информация об операторе
                sb.append("ОСНОВНАЯ ИНФОРМАЦИЯ\n")
                sb.append("Оператор: ${cellInfoJson.optString("operator", "Неизвестно")}\n")
                sb.append("SIM оператор: ${cellInfoJson.optString("sim_operator", "Неизвестно")}\n")
                sb.append("Всего сот: ${cellInfoJson.optInt("count", 0)}\n")

                // Проверка на ошибки
                val error = cellInfoJson.optString("error", null)
                if (error != null) {
                    sb.append("\nОШИБКА: $error\n")
                }

                // Информация о сотах
                val cells = cellInfoJson.optJSONArray("cells")
                if (cells != null && cells.length() > 0) {
                    sb.append("\n------ ИНФОРМАЦИЯ О СОТАХ ------\n")

                    for (i in 0 until cells.length()) {
                        val cell = cells.getJSONObject(i)
                        sb.append("------- СОТА ${i + 1}: -------\n")

                        val type = cell.optString("type", "Неизвестно")
                        sb.append("Тип: $type")
                        if (cell.optBoolean("registered", false)) {
                            sb.append(" (ЗАРЕГИСТРИРОВАНА)")
                        }
                        sb.append("\n")

                        // Identity информация
                        val identity = cell.optJSONObject("identity")
                        if (identity != null) {
                            sb.append("\n--- Идентификация ---\n")
                            val keys = identity.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = identity.get(key)
                                sb.append("  $key: $value\n")
                            }
                        }

                        // Signal информация
                        val signal = cell.optJSONObject("signal")
                        if (signal != null) {
                            sb.append("\n--- Сигнал ---\n")
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

            } catch (e: Exception) {
                tvCellInfo.text = "Ошибка отображения данных сот: ${e.message}"
                e.printStackTrace()
            }
        }
    }



    private fun createCombinedJson(location: Location?, cellInfo: JSONObject): JSONObject {
        val combined = JSONObject()
        val timestamp = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        combined.put("timestamp", timestamp)
        combined.put("readable_time", currentTime)

        // Location data
        val locationJson = JSONObject()
        if (location != null) {
            val altitude = if (location.altitude == 0.0) 120.0 else location.altitude
            locationJson.put("latitude", location.latitude)
            locationJson.put("longitude", location.longitude)
            locationJson.put("altitude", altitude)
            locationJson.put("accuracy", location.accuracy)
            locationJson.put("time", location.time)
            locationJson.put("provider", location.provider ?: "unknown")
        } else {
            locationJson.put("error", "Location not available")
        }
        combined.put("location", locationJson)

        // Cell info data
        combined.put("cell_info", cellInfo)

        // Device info
        val deviceInfo = JSONObject()
        deviceInfo.put("android_sdk", Build.VERSION.SDK_INT)
        deviceInfo.put("device", Build.DEVICE)
        deviceInfo.put("model", Build.MODEL)
        combined.put("device", deviceInfo)

        return combined
    }

    private fun saveToFile(data: JSONObject) {
        try {
            // Ищем или создаем файл через MediaStore
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("combined_data.json", Environment.DIRECTORY_DOCUMENTS + "/")

            val uri = contentResolver.query(collection, null, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    Uri.withAppendedPath(collection, id.toString())
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "combined_data.json")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/")
                    }
                    contentResolver.insert(collection, values)
                }
            }

            // Добавляем в конец файла
            uri?.let {
                contentResolver.openOutputStream(it, "wa")?.use { outputStream ->
                    outputStream.write("$data\n".toByteArray())
                }
                logMessage("Данные сохранены в файл")
            }

        } catch (e: Exception) {
            logMessage("Ошибка сохранения в файл: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendToServer(serverIp: String, data: String, packetNumber: Int): Boolean {
        return try {
            val address = "tcp://$serverIp:4789"

            // Создаем сокет если нужно
            if (zmqSocket == null) {
                zmqContext = ZContext()
                zmqSocket = zmqContext?.createSocket(ZMQ.REQ)
                zmqSocket?.receiveTimeOut = 5000
                zmqSocket?.sendTimeOut = 5000
                zmqSocket?.connect(address)
            }

            // Отправляем данные
            val sent = zmqSocket?.send(data.toByteArray(ZMQ.CHARSET), 0)

            if (sent == true) {
                // Ждем ответ
                val reply = zmqSocket?.recv(0)
                if (reply != null) {
                    val response = String(reply, ZMQ.CHARSET)
                    logMessage("[$packetNumber] Отправлено. Ответ: $response")
                    true
                } else {
                    throw Exception("Нет ответа от сервера")
                }
            } else {
                throw Exception("Ошибка отправки")
            }

        } catch (e: Exception) {
            logMessage("Ошибка отправки на сервер: ${e.message}")

            // Закрываем поврежденное соединение
            try {
                zmqSocket?.close()
                zmqContext?.close()
                zmqSocket = null
                zmqContext = null
            } catch (closeError: Exception) {
                // Игнорируем
            }

            false
        }
    }

    private fun logMessage(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timeStamp] $message\n"

        handler.post {
            tvLog.append(logMessage)
            Log.d(log_tag, message)

            // Автоскролл
            val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollViewLog)
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}