package com.example.android_something

import android.Manifest
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.tasks.await

class BackgroundService : Service() {

    private val TAG = "BackgroundService"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    // Telephony
    private lateinit var telephonyManager: TelephonyManager

    // ZeroMQ - делаем изменяемыми и потокобезопасными
    @Volatile
    private var zmqContext: ZContext? = null
    @Volatile
    private var zmqSocket: ZMQ.Socket? = null

    // Tracking state
    private val isTracking = AtomicBoolean(false)
    private var trackingJob: Job? = null

    // Broadcast actions
    companion object {
        const val ACTION_STATUS = "BG_SERVICE_STATUS"
        const val ACTION_LOCATION = "BG_SERVICE_LOCATION"
        const val ACTION_CELL_INFO = "BG_SERVICE_CELL_INFO"
        const val ACTION_LOG = "BG_SERVICE_LOG"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_LOCATION = "EXTRA_LOCATION"
        const val EXTRA_CELL_INFO = "EXTRA_CELL_INFO"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TRACKING" -> {
                val serverIp = intent.getStringExtra("SERVER_IP") ?: "10.121.42.134"
                val interval = intent.getLongExtra("INTERVAL", 5000)
                startTracking(serverIp, interval)
            }
            "STOP_TRACKING" -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startTracking(serverIp: String, intervalMs: Long) {
        if (isTracking.getAndSet(true)) return

        sendBroadcastStatus("Трекинг запущен. Сервер: $serverIp, интервал: ${intervalMs / 1000} сек")

        // Отменяем предыдущую корутину если есть
        trackingJob?.cancel()

        trackingJob = serviceScope.launch {
            try {
                runTrackingLoop(serverIp, intervalMs)
            } catch (e: CancellationException) {
                Log.d(TAG, "Tracking loop cancelled normally")
            } catch (e: Exception) {
                Log.e(TAG, "Error in tracking loop: ${e.message}")
                sendBroadcastLog("Критическая ошибка: ${e.message}")
            } finally {
                isTracking.set(false)
                cleanupZmq()
            }
        }
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking")
        isTracking.set(false)
        trackingJob?.cancel()
        trackingJob = null
        cleanupZmq()
        sendBroadcastStatus("Трекинг остановлен")
    }

    private fun cleanupZmq() {
        try {
            zmqSocket?.close()
            zmqContext?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ZMQ: ${e.message}")
        } finally {
            zmqSocket = null
            zmqContext = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun runTrackingLoop(serverIp: String, intervalMs: Long) {
        var packetCounter = 1
        val maxReconnectionAttempts = 10

        while (isTracking.get()) {
            try {
                val location = getCurrentLocation() ?: lastKnownLocation
                val cellInfoJson = getCellInfoJson()
                val combinedData = createCombinedJson(location, cellInfoJson)


                if (location != null) {
                    withContext(Dispatchers.Main) {
                        sendLocationBroadcast(location)
                    }
                }
                withContext(Dispatchers.Main) {
                    sendCellInfoBroadcast(cellInfoJson)
                }

                saveToFile(combinedData)

                var attempts = 0
                var success = false

                while (!success && attempts < maxReconnectionAttempts && isTracking.get()) {
                    try {
                        val response = sendToServer(serverIp, combinedData.toString())
                        if (response != null) {
                            sendBroadcastLog("[$packetCounter] Локация отправлена на сервер")
                            sendBroadcastLog("Ответ сервера: $response")
                            packetCounter++
                            success = true
                        } else {
                            attempts++
                            sendBroadcastLog("Ошибка: нет ответа от сервера (попытка $attempts)")
                            if (attempts < maxReconnectionAttempts && isTracking.get()) {
                                delay(5000)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isTracking.get()) break
                        attempts++
                        sendBroadcastLog("Ошибка подключения: ${e.message} (попытка $attempts)")
                        if (attempts < maxReconnectionAttempts && isTracking.get()) {
                            delay(5000)
                        }
                    }
                }

                if (!success && isTracking.get()) {
                    sendBroadcastLog("Достигнут максимум попыток переподключения. Остановка.")
                    stopTracking()
                    break
                }

                if (isTracking.get()) {
                    delay(intervalMs)
                }

            } catch (e: Exception) {
                if (!isTracking.get()) break
                sendBroadcastLog("Ошибка в сборе данных: ${e.message}")
                e.printStackTrace()

                if (isTracking.get()) {
                    delay(5000)
                }
            }
        }
    }

    private var lastSuccessfulLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var locationRequestInProgress = AtomicBoolean(false)

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendBroadcastLog("Нет разрешения на определение местоположения")
            return null
        }

        // Предотвращаем множественные одновременные запросы
        if (locationRequestInProgress.get()) {
            sendBroadcastLog("Запрос локации уже выполняется, использую последнюю известную")
            return lastSuccessfulLocation
        }

        return withContext(Dispatchers.IO) {
            locationRequestInProgress.set(true)
            var location: Location? = null

            try {
                sendBroadcastLog("🔍 Запрашиваю НОВУЮ локацию...")

                val locationResult = CompletableDeferred<Location?>()

                // Создаем запрос с высоким приоритетом
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000L // Минимальный интервал обновления
                ).setMaxUpdates(1) // Только одно обновление
                    .setDurationMillis(8000) // Максимальная длительность 8 секунд
                    .setMinUpdateIntervalMillis(500)
                    .build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val newLocation = result.lastLocation
                        if (newLocation != null) {
                            sendBroadcastLog("📍 Получена НОВАЯ локация: ${newLocation.latitude}, ${newLocation.longitude}")
                            locationResult.complete(newLocation)
                        }
                    }
                }

                try {
                    // Запрашиваем обновление
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    ).addOnFailureListener { exception ->
                        sendBroadcastLog("❌ Ошибка запроса локации: ${exception.message}")
                        locationResult.completeExceptionally(exception)
                    }

                    // Ждем новую локацию с таймаутом
                    location = withTimeoutOrNull(10000L) {
                        locationResult.await()
                    }

                    if (location != null) {
                        // Сохраняем новую локацию
                        lastSuccessfulLocation = location
                        lastLocationTime = System.currentTimeMillis()
                        sendBroadcastLog("✅ Новая локация сохранена")
                    } else {
                        sendBroadcastLog("⚠️ Таймаут получения новой локации")

                        // Если не получили новую, но есть свежая (до 30 секунд)
                        if (lastSuccessfulLocation != null &&
                            System.currentTimeMillis() - lastLocationTime < 30000) {
                            sendBroadcastLog("🔄 Использую свежую локацию от ${(System.currentTimeMillis() - lastLocationTime) / 1000} сек назад")
                            location = lastSuccessfulLocation
                        }
                    }

                } finally {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error getting location: ${e.message}")
                e.printStackTrace()

                // В случае ошибки не используем старую локацию, если она старая
                if (lastSuccessfulLocation != null &&
                    System.currentTimeMillis() - lastLocationTime < 60000) { // 1 минута
                    sendBroadcastLog("⚠️ Ошибка, использую недавнюю локацию")
                    location = lastSuccessfulLocation
                }
            } finally {
                locationRequestInProgress.set(false)
            }

            if (location == null) {
                sendBroadcastLog("❌ Не удалось получить локацию")
            }

            location
        }
    }

    private fun isLocationRecent(location: Location): Boolean {
        // Считаем локацию свежей, если ей меньше 2 минут
        val age = System.currentTimeMillis() - location.time
        return age < 2 * 60 * 1000
    }

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

        try {
            val allCellInfo = telephonyManager.allCellInfo
            val cellsArray = JSONArray()

            if (allCellInfo != null && allCellInfo.isNotEmpty()) {
                for (cellInfo in allCellInfo) {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr -> {
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
        } catch (e: SecurityException) {
            cellInfoJson.put("error", "Security exception: ${e.message}")
        } catch (e: Exception) {
            cellInfoJson.put("error", "Error getting cell info: ${e.message}")
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
        return combined
    }

    private fun saveToFile(data: JSONObject) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
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
                Log.d(TAG, "Data saved to file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file: ${e.message}")
        }
    }

    private fun sendToServer(serverIp: String, data: String): String? {
        var localSocket: ZMQ.Socket? = null
        var localContext: ZContext? = null

        try {
            val address = "tcp://$serverIp:4789"
            Log.d(TAG, "Connecting to $address")

            // Создаем новый контекст и сокет для каждой попытки
            localContext = ZContext()
            localSocket = localContext.createSocket(ZMQ.REQ)
            localSocket.receiveTimeOut = 5000
            localSocket.sendTimeOut = 5000

            localSocket.connect(address)

            // Отправляем данные
            val sent = localSocket.send(data.toByteArray(ZMQ.CHARSET), 0)
            if (!sent) {
                Log.e(TAG, "Failed to send data")
                return null
            }

            // Получаем ответ
            val reply = localSocket.recv(0)
            if (reply == null) {
                Log.e(TAG, "No response from server")
                return null
            }

            val response = String(reply, ZMQ.CHARSET)
            Log.d(TAG, "Received response: $response")

            // Сохраняем сокет и контекст для последующего использования
            synchronized(this) {
                // Закрываем старые если есть
                try {
                    zmqSocket?.close()
                    zmqContext?.close()
                } catch (e: Exception) {
                    // Игнорируем
                }

                // Сохраняем новые
                zmqSocket = localSocket
                zmqContext = localContext
            }

            return response

        } catch (e: Exception) {
            Log.e(TAG, "Socket error: ${e.message}")
            e.printStackTrace()

            // Закрываем временные ресурсы в случае ошибки
            try {
                localSocket?.close()
                localContext?.close()
            } catch (ex: Exception) {
                // Игнорируем
            }

            return null
        }
    }

    // Broadcast helpers
    private fun sendBroadcastStatus(message: String) {
        val intent = Intent(ACTION_STATUS).putExtra(EXTRA_MESSAGE, message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendLocationBroadcast(location: Location) {
        val intent = Intent(ACTION_LOCATION)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendCellInfoBroadcast(cellInfo: JSONObject) {
        val intent = Intent(ACTION_CELL_INFO)
        intent.putExtra(EXTRA_CELL_INFO, cellInfo.toString())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendBroadcastLog(message: String) {
        Log.d(TAG, message)
        val intent = Intent(ACTION_LOG).putExtra(EXTRA_MESSAGE, message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }
}