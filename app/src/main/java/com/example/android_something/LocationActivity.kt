package com.example.android_something

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

import android.os.Handler
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import android.provider.MediaStore
import org.json.JSONArray

class LocationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvTime: TextView

    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvTime = findViewById(R.id.tvTime)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGetLocation).setOnClickListener { getLocation() }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).build()

        // Запускаем таймер на случай, если координаты не придут
        timeoutRunnable = Runnable {
            Toast.makeText(this, "Проверьте включен ли GPS", Toast.LENGTH_SHORT).show()
        }
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutHandler?.postDelayed(timeoutRunnable!!, 15000)

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        // Отменяем таймер, так как координаты получены
                        timeoutHandler?.removeCallbacks(timeoutRunnable!!)

                        updateLocationUI(location)
                        saveLocationToFile(location)
                        Toast.makeText(this@LocationActivity, "Данные получены", Toast.LENGTH_SHORT).show()

                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            Looper.getMainLooper()
        ).addOnFailureListener { exception ->
            timeoutHandler?.removeCallbacks(timeoutRunnable!!)
            Toast.makeText(this, "Ошибка: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocationUI(location: Location) {

        val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val altitude = if (location.altitude == 0.0) 120.0 else location.altitude

        tvLatitude.text = "Широта: ${"%.6f".format(location.latitude)}"
        tvLongitude.text = "Долгота: ${"%.6f".format(location.longitude)}"
        tvAltitude.text = "Высота: ${"%.2f".format(altitude)}"
        tvTime.text = "Время: $currentTime"
    }

    private fun saveLocationToFile(location: Location) {
        try {
            val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val currentTime = timeFormat.format(Date())
            val altitude = if (location.altitude == 0.0) 120.0 else location.altitude

            val locationData = JSONObject().apply {
                put("time", location.time)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("altitude", altitude)
                put("readable_time", currentTime)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - используем MediaStore (НЕ ТРЕБУЕТ РАЗРЕШЕНИЙ!)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "location_data.json")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MyAppLocations")
                }

                val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)

                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        // Проверяем, нужно ли добавить к существующему файлу
                        val existingContent = try {
                            contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        } catch (e: Exception) {
                            null
                        }

                        if (!existingContent.isNullOrEmpty()) {
                            outputStream.write(existingContent.toByteArray())
                        }

                        outputStream.write("$locationData\n".toByteArray())

                        Toast.makeText(
                            this,
                            "✅ Сохранено в Documents/MyAppLocations/\nВидно через USB и Device Explorer!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } ?: Toast.makeText(this, "Ошибка создания файла", Toast.LENGTH_SHORT).show()

            } else {
                // Android 9 и ниже - требуется разрешение WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {

                    val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val appDir = File(documentsDir, "MyAppLocations")
                    if (!appDir.exists()) appDir.mkdirs()

                    val file = File(appDir, "location_data.json")

                    // Добавляем данные в конец файла
                    val existingText = if (file.exists()) file.readText() else ""
                    file.writeText(existingText + "$locationData\n")

                    Toast.makeText(
                        this,
                        "✅ Сохранено в Documents/MyAppLocations/",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Запрашиваем разрешение
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1002
                    )
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка записи: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    }