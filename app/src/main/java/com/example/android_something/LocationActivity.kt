package com.example.android_something

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.*

import android.os.Handler
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi


class LocationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvAccuracy: TextView
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
        tvAccuracy = findViewById(R.id.tvAccuracy)
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
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
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
        tvAccuracy.text = "Точность: ${"%.2f".format(location.accuracy)}"
        tvTime.text = "Время: $currentTime"
    }

    @RequiresApi(Build.VERSION_CODES.Q) //Mediastore
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
                put("accuracy", 0 )
                put("time_of_getting_location", currentTime)

            }

            // 1. Ищем или создаем файл
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("location_data.json", Environment.DIRECTORY_DOCUMENTS + "/")

            val uri = contentResolver.query(collection, null, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    Uri.withAppendedPath(collection, id.toString())
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "location_data.json")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/")
                    }
                    contentResolver.insert(collection, values)
                }
            }

            // 2. Добавляем в конец файла (APPEND)
            uri?.let {
                contentResolver.openOutputStream(it, "wa")?.use { outputStream ->
                    outputStream.write("$locationData\n".toByteArray())
                }
                Toast.makeText(this, "Сохранено в Documents/location_data.json", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка записи", Toast.LENGTH_SHORT).show()
        }
    }
    }