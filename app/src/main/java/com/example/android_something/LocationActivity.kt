package com.example.android_something

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
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

class LocationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvTime: TextView

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

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {

                    val location = locationResult.lastLocation
                    if (location != null) {
                        updateLocationUI(location)
                        saveLocationToFile(location)
                        Toast.makeText(this@LocationActivity, "Данные получены", Toast.LENGTH_SHORT).show()

                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            Looper.getMainLooper()
        ).addOnFailureListener { exception ->
            Toast.makeText(this, "Ошибка: ${exception.message}", Toast.LENGTH_SHORT).show()
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "Проверьте включен ли GPS", Toast.LENGTH_SHORT).show()
        }, 15000)
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
            }

            val docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(docsDir, "location_data.json")

            FileOutputStream(file, true).use { outputStream ->
                val jsonString = locationData.toString()
                outputStream.write("$jsonString\n".toByteArray())
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка записи", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                Toast.makeText(this, "Разрешение необходимо", Toast.LENGTH_SHORT).show()
            }
        }
    }
}