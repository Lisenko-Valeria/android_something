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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Context
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog


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

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

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

    // Traffic stats
    private lateinit var trafficStatsHelper: TrafficStatsHelper
    private val TRAFFIC_PERMISSION_REQUEST_CODE = 1003
    private var lastTrafficData: TrafficStatsData? = null
    private lateinit var tvTotalTraffic: TextView
    private lateinit var tvTopApps: TextView
    private var lastFilteredTopApps: List<AppTrafficInfo>? = null

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        initViews()

        trafficStatsHelper = TrafficStatsHelper(this)

        etServerIp.setText("10.111.70.134")
        etInterval.setText("5")

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
        tvTotalTraffic = findViewById(R.id.tvTotalTraffic)
        tvTopApps = findViewById(R.id.tvTopApps)
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

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PHONE_PERMISSION_REQUEST_CODE
            )
            return
        }/*
        if (!hasUsageDataAccess()) {
            requestUsageDataAccess()
            return
        }*/
        startTracking()
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
            intervalStr.toLong() * 1000
        } catch (e: Exception) {
            30000L
        }

        isTracking = true
        updateUIForTracking(true)
        logMessage("Запуск трекинга. Сервер: $serverIp, интервал: ${interval/1000} сек")

        @SuppressLint("MissingPermission")
        trackingThread = Thread {
            runTrackingLoop(serverIp, interval)
        }.apply { start() }
    }

    private fun stopTracking() {
        isTracking = false
        trackingThread?.interrupt()

        try {
            zmqSocket?.close()
            zmqContext?.close()
        } catch (e: Exception) {
        } finally {
            zmqSocket = null
            zmqContext = null
        }


        updateUIForTracking(false)
        logMessage("Трекинг остановлен")
    }

    private fun updateUIForTracking(tracking: Boolean) {
        handler.post {
            if (tracking) {
                tvStatus.text = "Статус: АКТИВЕН"
                btnStart.isEnabled = false
                btnStop.isEnabled = true
                etServerIp.isEnabled = false
                etInterval.isEnabled = false
            } else {
                tvStatus.text = "Статус: ОСТАНОВЛЕН"
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                etServerIp.isEnabled = true
                etInterval.isEnabled = true
            }
        }
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun runTrackingLoop(serverIp: String, intervalMs: Long) {
        var packetCounter = 1
        val maxReconnectionAttempts = 10

        while (isTracking) {
            try {
                val location = getCurrentLocation() ?: lastKnownLocation
                val cellInfoJson = getCellInfoJson()
                val combinedData = createCombinedJson(location, cellInfoJson)

                if (location != null) updateLocationUI(location)
                updateCellInfoUI(cellInfoJson)
                //updateTrafficUI()

                saveToFile(combinedData)


                var attempts = 0
                var success = false
                while (!success && attempts < maxReconnectionAttempts && isTracking) {
                    try {
                        val response = sendToServer(serverIp, combinedData.toString())
                        if (response != null) {
                            logMessage("[$packetCounter] Локация отправлена на сервер")
                            logMessage("Ответ сервера: $response")
                            packetCounter++
                            success = true
                        } else {
                            attempts++
                            logMessage("error: No response from server")
                            if (attempts < maxReconnectionAttempts && isTracking) {
                                logMessage("Попытка переподключения $attempts...")
                                Thread.sleep(5000)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isTracking) break
                        attempts++
                        logMessage("error: ${e.message}")
                        if (attempts < maxReconnectionAttempts && isTracking) {
                            logMessage("Попытка переподключения $attempts...")
                            Thread.sleep(5000)
                        }
                    }
                }

                if (!success) {
                    if (isTracking) {
                        logMessage("Достигнут максимум попыток переподключения. Остановка.")
                        handler.post { stopTracking() }
                    }
                    break
                }

                Thread.sleep(intervalMs)

            } catch (e: Exception) {
                if (!isTracking) break
                logMessage("Ошибка в сборе данных: ${e.message}")
                e.printStackTrace()
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

        synchronized(lock) {
            if (!isCompleted) {
                try {
                    lock.wait(15000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)

        return if (locationResult.isNotEmpty()) locationResult[0] else null
    }

    @SuppressLint("SuspiciousIndentation")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getCellInfoJson(): JSONObject {
        val cellInfoJson = JSONObject()

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
        lastKnownLocation = location
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
                        sb.append("Тип: $type")


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


        }
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    private fun createCombinedJson(location: Location?, cellInfo: JSONObject): JSONObject {
        val combined = JSONObject()
        val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        combined.put("readable_time", currentTime)

        val locationJson = JSONObject()
        if (location != null) {
            val altitude = if (location.altitude == 0.0) 120.0 else location.altitude
            locationJson.put("latitude", location.latitude)
            locationJson.put("longitude", location.longitude)
            locationJson.put("altitude", altitude)
            locationJson.put("accuracy", location.accuracy)
            locationJson.put("time", location.time)
        } else {
            locationJson.put("error", "Location not available")
        }
        combined.put("location", locationJson)

        combined.put("cell_info", cellInfo)
/*
        val trafficJson = getTrafficStatsJson()
        combined.put("traffic_stats", trafficJson)
*/
        return combined
    }

    private fun saveToFile(data: JSONObject) {

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

            uri?.let {
                contentResolver.openOutputStream(it, "wa")?.use { outputStream ->
                    outputStream.write("$data\n".toByteArray())
                }
            }
    }

    private fun sendToServer(serverIp: String, data: String): String? {
        return try {
            val address = "tcp://$serverIp:4789"
            if (zmqSocket == null) {
                zmqContext = ZContext()
                zmqSocket = zmqContext?.createSocket(ZMQ.REQ)
                zmqSocket?.receiveTimeOut = 5000
                zmqSocket?.sendTimeOut = 5000
                zmqSocket?.connect(address)
            }

            val sent = zmqSocket?.send(data.toByteArray(ZMQ.CHARSET), 0)
            if (sent == true) {
                val reply = zmqSocket?.recv(0)
                reply?.let { String(it, ZMQ.CHARSET) }
            } else {
                null
            }
        } catch (e: Exception) {
            zmqSocket?.close()
            zmqContext?.close()
            zmqSocket = null
            zmqContext = null
            null
        }
    }

/*
    private fun hasUsageDataAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestUsageDataAccess() {
        AlertDialog.Builder(this)
            .setTitle("Разрешение на доступ к статистике")
            .setMessage("Для сбора информации о трафике приложений необходимо предоставить разрешение на доступ к статистике использования. Вы будете перенаправлены в настройки.")
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivityForResult(intent, TRAFFIC_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Отмена") { _, _ ->
                Toast.makeText(this, "Без этого разрешения данные о трафике не будут собираться", Toast.LENGTH_LONG).show()
                startTracking()
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == TRAFFIC_PERMISSION_REQUEST_CODE) {
            startTracking()
        }
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    private fun getTrafficStatsJson(): JSONObject {
        val trafficJson = JSONObject()


            val trafficData = trafficStatsHelper.getTrafficStats(24)
            lastTrafficData = trafficData

            trafficJson.put("total_rx_bytes", trafficData.totalRxBytes)
            trafficJson.put("total_tx_bytes", trafficData.totalTxBytes)
            trafficJson.put("total_rx_packets", trafficData.totalRxPackets)
            trafficJson.put("total_tx_packets", trafficData.totalTxPackets)

            val topAppsBySigma = trafficStatsHelper.getTopAppsByTwoSigma(trafficData)
            lastFilteredTopApps = topAppsBySigma
            val top5Apps = topAppsBySigma.take(3)

            val topAppsArray = JSONArray()
            for (app in top5Apps) {
                val appJson = JSONObject()
                appJson.put("app_name", app.appName)
                appJson.put("total_bytes", app.totalBytes)
                appJson.put("percentage", "%.2f".format(app.percentage).toDouble())
                topAppsArray.put(appJson)
            }

            trafficJson.put("top_apps", topAppsArray)

        return trafficJson
    }

    private fun updateTrafficUI() {
        handler.post {
            lastFilteredTopApps?.let { apps ->
                if (apps.isNotEmpty()) {
                    val totalBytes = apps.sumOf { it.totalBytes } // если нужно общее, можно взять из lastTrafficData
                    val totalMB = totalBytes / (1024.0 * 1024.0)
                    val rxMB = (lastTrafficData?.totalRxBytes ?: 0) / (1024.0 * 1024.0)
                    val txMB = (lastTrafficData?.totalTxBytes ?: 0) / (1024.0 * 1024.0)

                    tvTotalTraffic.text = String.format(
                        "Всего: %.2f MB (RX: %.2f MB, TX: %.2f MB)",
                        totalMB, rxMB, txMB
                    )

                    val topAppsText = StringBuilder("ТОП приложения (2-сигма):\n")
                    for (app in apps.take(3)) {
                        val appMB = app.totalBytes / (1024.0 * 1024.0)
                        topAppsText.append(String.format(
                            "  • %s: %.2f MB (%.1f%%)\n",
                            app.appName, appMB, app.percentage
                        ))
                    }
                    tvTopApps.text = topAppsText.toString()
                } else {
                    tvTopApps.text = "Нет данных о приложениях"
                }
            } ?: run {
                tvTotalTraffic.text = "Всего передано: --"
                tvTopApps.text = "ТОП приложения: --"
            }
        }
    }
*/
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

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}
