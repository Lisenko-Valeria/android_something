package com.example.android_something

import android.Manifest
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.RemoteException
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

import java.util.*


// Добавьте в начало класса TrafficStatsHelper
private data class NetworkTotals(
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long
)
data class AppTrafficInfo(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long,
    val percentage: Double
)

data class TrafficStatsData(
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val totalRxPackets: Long,
    val totalTxPackets: Long,
    val topApps: List<AppTrafficInfo>,
    val measurementStartTime: Long,
    val measurementEndTime: Long
)

class TrafficStatsHelper(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager = context.packageManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val tag = "TrafficStatsHelper"

    /**
     * Проверяет наличие разрешения на доступ к статистике использования
     */
    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /**
     * Получает статистику трафика за последние N часов
     */
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getTrafficStats(hours: Int = 24): TrafficStatsData {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (hours * 60 * 60 * 1000L)

        Log.d(tag, "Getting traffic stats from ${Date(startTime)} to ${Date(endTime)}")

        return getTrafficStats(startTime, endTime)
    }

    /**
     * Получает статистику трафика за указанный период
     */
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getTrafficStats(startTime: Long, endTime: Long): TrafficStatsData {
        val networkStatsMap = HashMap<Int, AppTraffic>()
        var totalRxBytes = 0L
        var totalTxBytes = 0L
        var totalRxPackets = 0L
        var totalTxPackets = 0L

        // Проверяем разрешение
        if (!hasUsageStatsPermission()) {
            Log.e(tag, "No usage stats permission")
            return createEmptyResult(startTime, endTime)
        }

        try {
            // Получаем статистику для мобильной сети и WiFi
            val networkTypes = listOf(
                ConnectivityManager.TYPE_MOBILE,
                ConnectivityManager.TYPE_WIFI
            )

            for (networkType in networkTypes) {
                val subscriberId = if (networkType == ConnectivityManager.TYPE_MOBILE) {
                    getSubscriberId()
                } else {
                    null
                }

                try {
                    Log.d(tag, "Querying network type: $networkType")

                    val networkStats = networkStatsManager.querySummary(
                        networkType,
                        subscriberId,
                        startTime,
                        endTime
                    )

                    processNetworkStats(networkStats, networkStatsMap)?.let { totals ->
                        totalRxBytes += totals.rxBytes      // было totals.first
                        totalTxBytes += totals.txBytes      // было totals.second
                        totalRxPackets += totals.rxPackets  // было totals.third
                        totalTxPackets += totals.txPackets  // было totals.four
                    }

                } catch (e: SecurityException) {
                    Log.e(tag, "Security exception for type $networkType: ${e.message}")
                } catch (e: RemoteException) {
                    Log.e(tag, "Remote exception for type $networkType: ${e.message}")
                } catch (e: Exception) {
                    Log.e(tag, "Error processing network type $networkType: ${e.message}")
                }
            }

            Log.d(tag, "Total bytes - RX: $totalRxBytes, TX: $totalTxBytes")

            // Получаем информацию о приложениях
            val appTrafficList = buildAppTrafficList(networkStatsMap, totalRxBytes + totalTxBytes)

            return TrafficStatsData(
                totalRxBytes = totalRxBytes,
                totalTxBytes = totalTxBytes,
                totalRxPackets = totalRxPackets,
                totalTxPackets = totalTxPackets,
                topApps = appTrafficList,
                measurementStartTime = startTime,
                measurementEndTime = endTime
            )

        } catch (e: Exception) {
            Log.e(tag, "Error in getTrafficStats: ${e.message}")
            e.printStackTrace()
            return createEmptyResult(startTime, endTime)
        }
    }

    /**
     * Обрабатывает NetworkStats и возвращает сумму байт и пакетов
     */
    /**
     * Обрабатывает NetworkStats и возвращает сумму байт и пакетов
     */
    private fun processNetworkStats(
        networkStats: NetworkStats?,
        networkStatsMap: HashMap<Int, AppTraffic>
    ): NetworkTotals? {
        if (networkStats == null) return null

        var rxBytes = 0L
        var txBytes = 0L
        var rxPackets = 0L
        var txPackets = 0L

        val bucket = NetworkStats.Bucket()

        networkStats.use { stats ->
            while (stats.hasNextBucket()) {
                try {
                    stats.getNextBucket(bucket)

                    val uid = bucket.uid
                    val bucketRxBytes = bucket.rxBytes
                    val bucketTxBytes = bucket.txBytes
                    val bucketRxPackets = bucket.rxPackets
                    val bucketTxPackets = bucket.txPackets

                    // Пропускаем системные UID (0-10000 обычно системные)
                    if (uid > 10000 && (bucketRxBytes > 0 || bucketTxBytes > 0)) {
                        val appTraffic = networkStatsMap.getOrPut(uid) {
                            AppTraffic(uid, 0L, 0L, 0L, 0L)
                        }
                        appTraffic.rxBytes += bucketRxBytes
                        appTraffic.txBytes += bucketTxBytes
                        appTraffic.rxPackets += bucketRxPackets
                        appTraffic.txPackets += bucketTxPackets

                        rxBytes += bucketRxBytes
                        txBytes += bucketTxBytes
                        rxPackets += bucketRxPackets
                        txPackets += bucketTxPackets
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing bucket: ${e.message}")
                }
            }
        }

        return NetworkTotals(rxBytes, txBytes, rxPackets, txPackets)
    }

    /**
     * Создает список информации о приложениях
     */
    private fun buildAppTrafficList(
        networkStatsMap: HashMap<Int, AppTraffic>,
        totalBytes: Long
    ): List<AppTrafficInfo> {
        val appTrafficList = mutableListOf<AppTrafficInfo>()

        for ((uid, traffic) in networkStatsMap) {
            val appInfo = getAppInfo(uid)

            val appTrafficInfo = AppTrafficInfo(
                uid = uid,
                appName = appInfo.first,
                packageName = appInfo.second,
                rxBytes = traffic.rxBytes,
                txBytes = traffic.txBytes,
                totalBytes = traffic.rxBytes + traffic.txBytes,
                percentage = if (totalBytes > 0)
                    ((traffic.rxBytes + traffic.txBytes).toDouble() / totalBytes) * 100
                else 0.0
            )

            if (appTrafficInfo.totalBytes > 0) {
                appTrafficList.add(appTrafficInfo)
            }
        }

        // Сортируем по убыванию трафика
        return appTrafficList.sortedByDescending { it.totalBytes }
    }

    /**
     * Получает информацию о приложении по UID
     */
    /**
     * Получает информацию о приложении по UID
     */
    /**
     * Получает информацию о приложении по UID
     */
    private fun getAppInfo(uid: Int): Pair<String, String> {
        Log.d(tag, "=== Getting app info for UID: $uid ===")

        return try {
            // Метод 1: Пытаемся получить пакеты через getPackagesForUid
            val packages = packageManager.getPackagesForUid(uid)
            Log.d(tag, "getPackagesForUid returned: ${packages?.joinToString()}")

            if (packages != null && packages.isNotEmpty()) {
                val packageName = packages[0]
                Log.d(tag, "First package: $packageName")

                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    Log.d(tag, "SUCCESS: Found app name '$appName' for package '$packageName'")
                    return Pair(appName, packageName)
                } catch (e: Exception) {
                    Log.e(tag, "Error getting appInfo for $packageName: ${e.message}")
                }
            }
/*
            // Метод 2: Пробуем getNameForUid
            val packageName = packageManager.getNameForUid(uid)
            Log.d(tag, "getNameForUid returned: $packageName")

            if (packageName != null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    Log.d(tag, "SUCCESS (method 2): Found app name '$appName' for package '$packageName'")
                    return Pair(appName, packageName)
                } catch (e: Exception) {
                    Log.e(tag, "Error getting appInfo for $packageName: ${e.message}")
                    return Pair("Unknown ($uid)", packageName)
                }
            }

            // Метод 3: Пробуем найти пакет через queryIntentActivities (для системных приложений)
            try {
                val intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_MAIN
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
                for (resolveInfo in resolveInfoList) {
                    if (resolveInfo.activityInfo.applicationInfo.uid == uid) {
                        val appName = resolveInfo.loadLabel(packageManager).toString()
                        val pkgName = resolveInfo.activityInfo.packageName
                        Log.d(tag, "SUCCESS (method 3): Found via launcher: $appName ($pkgName)")
                        return Pair(appName, pkgName)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in method 3: ${e.message}")
            }*/

            Log.w(tag, "All methods failed for UID $uid")
            Pair("Unknown ($uid)", "unknown")

        } catch (e: Exception) {
            Log.e(tag, "Critical error in getAppInfo for UID $uid: ${e.message}")
            e.printStackTrace()
            Pair("Unknown ($uid)", "unknown")
        }
    }
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    private fun getSubscriberId(): String? {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED) {
                telephonyManager.subscriberId
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Создает пустой результат
     */
    private fun createEmptyResult(startTime: Long, endTime: Long): TrafficStatsData {
        return TrafficStatsData(
            totalRxBytes = 0,
            totalTxBytes = 0,
            totalRxPackets = 0,
            totalTxPackets = 0,
            topApps = emptyList(),
            measurementStartTime = startTime,
            measurementEndTime = endTime
        )
    }

    /**
     * Получает ТОП приложений по 2-сигма
     */
    fun getTopAppsByTwoSigma(trafficData: TrafficStatsData): List<AppTrafficInfo> {
        if (trafficData.topApps.isEmpty()) return emptyList()

        val totalBytesList = trafficData.topApps.map { it.totalBytes.toDouble() }

        // Вычисляем среднее и стандартное отклонение
        val mean = totalBytesList.average()
        val variance = totalBytesList.map { (it - mean) * (it - mean) }.average()
        val stdDev = Math.sqrt(variance)

        // 2-сигма интервал: mean ± 2*stdDev
        val lowerBound = mean - 2 * stdDev
        val upperBound = mean + 2 * stdDev

        Log.d(tag, "Mean: $mean, StdDev: $stdDev, 2-sigma range: [$lowerBound, $upperBound]")

        // Возвращаем приложения в пределах 2-сигма
        return trafficData.topApps.filter {
            it.totalBytes.toDouble() >= lowerBound && it.totalBytes.toDouble() <= upperBound
        }
    }

    /**
     * Внутренний класс для хранения трафика приложения
     */
    private data class AppTraffic(
        val uid: Int,
        var rxBytes: Long,
        var txBytes: Long,
        var rxPackets: Long,
        var txPackets: Long
    )
}