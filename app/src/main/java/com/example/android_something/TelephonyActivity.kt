package com.example.android_something

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class TelephonyActivity : AppCompatActivity() {

    private lateinit var tvNetworkInfo: TextView
    private lateinit var telephonyManager: TelephonyManager

    private companion object {
        const val PHONE_PERMISSION_REQUEST_CODE = 1002
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telephony)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnGetNetworkInfo).setOnClickListener {
            checkAllPermissions()
        }
        showBasicInfo()
    }

    private fun showBasicInfo() {
        val stringBuilder = StringBuilder()
        stringBuilder.append("Нажмите 'Получить данные сетей'\n")
        tvNetworkInfo.text = stringBuilder.toString()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkAllPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PHONE_PERMISSION_REQUEST_CODE
            )
        } else {
            fetchNetworkInfo()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun fetchNetworkInfo() {
        try {
            val stringBuilder = StringBuilder()

            stringBuilder.append("Основная информация\n")

            stringBuilder.append("Оператор: ${telephonyManager.networkOperatorName ?: "Неизвестно"}\n")

            stringBuilder.append("SIM оператор: ${telephonyManager.simOperatorName ?: "Неизвестно"}\n")

            stringBuilder.append("\n")

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val allCellInfo = telephonyManager.allCellInfo
                    if (allCellInfo != null && allCellInfo.isNotEmpty()) {
                        stringBuilder.append("------Информация о сотах-----\n")
                        for ((index, cellInfo) in allCellInfo.withIndex()) {
                            stringBuilder.append("------- Сота ${index + 1}: ------\n")

                            when {
                                cellInfo is CellInfoNr -> {
                                    stringBuilder.append("--- NR (5G) ---\n")
                                    stringBuilder.append(getNrInfo(cellInfo))
                                }
                                cellInfo is CellInfoLte -> {
                                    stringBuilder.append("--- LTE (4G) ---\n")
                                    stringBuilder.append(getLteInfo(cellInfo))
                                }
                                cellInfo is CellInfoGsm -> {
                                    stringBuilder.append("--- GSM (2G/3G) ---\n")
                                    stringBuilder.append(getGsmInfo(cellInfo))
                                }
                                else -> {
                                    stringBuilder.append("--- Неизвестный тип (${cellInfo.javaClass.simpleName}) ---\n")
                                }
                            }
                            stringBuilder.append("\n")
                        }
                    } else {
                        stringBuilder.append("Информация о сотах недоступна\n")
                    }
                } catch (e: SecurityException) {
                    stringBuilder.append("Ошибка доступа к информации о сотах: ${e.message}\n")
                }
            } else {
                stringBuilder.append("Требуется разрешение на доступ к местоположению\n")
            }

            tvNetworkInfo.text = stringBuilder.toString()

        } catch (e: Exception) {
            tvNetworkInfo.text = String.format("Ошибка при получении данных: %s", e.message ?: "Неизвестная ошибка")
            Toast.makeText(this, "Ошибка: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private fun getLteInfo(cellInfo: CellInfoLte): String {
        val stringBuilder = StringBuilder()
        val cellIdentity = cellInfo.cellIdentity
        val cellSignal = cellInfo.cellSignalStrength

        stringBuilder.append("CellInfoLte:\n")
        stringBuilder.append("\tCellIdentityLte: \n")
        stringBuilder.append("\t\tBand: ${cellIdentity.bands.joinToString()}\n")

        stringBuilder.append("\t\tCellIdentity: ${cellIdentity.ci}\n")
        stringBuilder.append("\t\tEARFCN: ${cellIdentity.earfcn}\n")
        stringBuilder.append("\t\tMCC: ${cellIdentity.mccString ?: "N/A"}\n")
        stringBuilder.append("\t\tMNC: ${cellIdentity.mncString ?: "N/A"}\n")
        stringBuilder.append("\t\tPCI: ${cellIdentity.pci}\n")
        stringBuilder.append("\t\tTAC: ${cellIdentity.tac}\n")

        stringBuilder.append("\tCellSignalStrengthLte: \n")
        stringBuilder.append("\t\tASU Level: ${cellSignal.asuLevel}\n")
        stringBuilder.append("\t\tCQI: ${cellSignal.cqi}\n")
        stringBuilder.append("\t\tRSRP: ${cellSignal.rsrp} dBm\n")
        stringBuilder.append("\t\tRSRQ: ${cellSignal.rsrq} dB\n")
        stringBuilder.append("\t\tRSSI: ${cellSignal.rssi}\n")
        stringBuilder.append("\t\tRSSNR: ${cellSignal.rssnr} dB\n")
        stringBuilder.append("\t\tTiming Advance: ${cellSignal.timingAdvance}\n")

        return stringBuilder.toString()
    }

    @RequiresApi(Build.VERSION_CODES.R)

    private fun getGsmInfo(cellInfo: CellInfoGsm): String {
        val stringBuilder = StringBuilder()
        val cellIdentity = cellInfo.cellIdentity
        val cellSignal = cellInfo.cellSignalStrength

        stringBuilder.append("CellInfoGsm: \n")
        stringBuilder.append("\tCellIdentityGSM: \n")
        stringBuilder.append("\t\tCellIdentity: ${cellIdentity.cid}\n")
        stringBuilder.append("\t\tBSIC: ${cellIdentity.bsic}\n")
        stringBuilder.append("\t\tARFCN: ${cellIdentity.arfcn}\n")
        stringBuilder.append("\t\tLAC: ${cellIdentity.lac}\n")
        stringBuilder.append("\t\tMCC: ${cellIdentity.mccString ?: "N/A"}\n")
        stringBuilder.append("\t\tMNC: ${cellIdentity.mncString ?: "N/A"}\n")
        stringBuilder.append("\t\tPSC: ${cellIdentity.psc}\n")

        stringBuilder.append("\tCellSignalStrengthGsm: \n")
        stringBuilder.append("\t\tDbm: ${cellSignal.dbm} dBm\n")
        stringBuilder.append("\t\tRSSI: ${cellSignal.rssi}\n")
        stringBuilder.append("\t\tTiming Advance: ${cellSignal.timingAdvance}\n")

        return stringBuilder.toString()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getNrInfo(cellInfo: CellInfoNr): String {
        val stringBuilder = StringBuilder()
        val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
        val cellSignal = cellInfo.cellSignalStrength as CellSignalStrengthNr

        stringBuilder.append("CellInfoNr: \n")
        stringBuilder.append("\tCellIdentityNr: \n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stringBuilder.append("\t\tBand: ${cellIdentity.bands.joinToString()}\n")
        } else {
            stringBuilder.append("\t\tBand: N/A\n")
        }
        stringBuilder.append("\t\tNCI: ${cellIdentity.nci}\n")
        stringBuilder.append("\t\tPCI: ${cellIdentity.pci}\n")
        stringBuilder.append("\t\tNrargcn: ${cellIdentity.nrarfcn}\n")
        stringBuilder.append("\t\tTAC: ${cellIdentity.tac}\n")
        stringBuilder.append("\t\tMCC: ${cellIdentity.mccString ?: "N/A"}\n")
        stringBuilder.append("\t\tMNC: ${cellIdentity.mncString ?: "N/A"}\n")

        stringBuilder.append("\tCellSignalStrengthNr: \n")
        stringBuilder.append("\t\tSS-RSRP: ${cellSignal.ssRsrp} dBm\n")
        stringBuilder.append("\t\tSS-RSRQ: ${cellSignal.ssRsrq} dB\n")
        stringBuilder.append("\t\tSS-SINR: ${cellSignal.ssSinr} dB\n")
        // Timing Advance недоступен для NR
        stringBuilder.append("\t\tTiming Advance: N/A\n")

        return stringBuilder.toString()
    }

    private fun getNetworkTypeName(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
            else -> "Неизвестно ($networkType)"
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PHONE_PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Toast.makeText(this, "Разрешения предоставлены", Toast.LENGTH_SHORT).show()
                    fetchNetworkInfo()
                } else {
                    Toast.makeText(this,
                        "Некоторые разрешения не предоставлены. Функциональность будет ограничена.",
                        Toast.LENGTH_LONG
                    ).show()
                    fetchNetworkInfo()
                }
            }
        }
    }
}