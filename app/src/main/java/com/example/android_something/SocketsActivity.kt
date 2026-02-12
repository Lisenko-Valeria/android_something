package com.example.android_something

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SocketsActivity : AppCompatActivity() {
    private var log_tag: String = "MY_LOG_TAG"
    private lateinit var tvSockets: TextView
    private lateinit var tvLog: TextView
    private lateinit var etServerIp: EditText
    private lateinit var handler: Handler
    private var isRunning = false
    private var clientThread: Thread? = null

    private val locationFilePath =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)}/location_data.json"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sockets)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvSockets = findViewById(R.id.tvSockets)
        tvLog = findViewById(R.id.tvLog)
        etServerIp = findViewById(R.id.etServerIp)

        val btnBack: Button = findViewById(R.id.btnBack)
        val btnConnect: Button = findViewById(R.id.btnStartExternal)
        val btnStop: Button = findViewById(R.id.btnStop)

        handler = Handler(Looper.getMainLooper())

        etServerIp.setText("10.81.55.134")

        btnBack.setOnClickListener {
            stopAll()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnConnect.setOnClickListener {
            if (!isRunning) {
                val serverIp = etServerIp.text.toString()
                if (serverIp.isNotEmpty()) {
                    connectToServer(serverIp)
                } else {
                    logMessage("Введите IP адрес сервера")
                }
            }
        }

        btnStop.setOnClickListener {
            stopAll()
        }
    }

    fun connectToServer(serverIp: String) {
        if (isRunning) return

        isRunning = true
        logMessage("Подключение к серверу $serverIp")

        clientThread = Thread {
            startExternalClient(serverIp)
        }.apply { start() }
    }

    fun stopAll() {
        isRunning = false
        clientThread?.interrupt()
        logMessage("Все соединения остановлены")
    }

    private fun readLastLocationLineFromFile(): String? {
        try {
            val file = File(locationFilePath)

            if (!file.exists()) {
                logMessage("Location file not found : $locationFilePath")
                return null
            }

            var lastLine: String? = null

            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim()
                    if (trimmedLine?.isNotEmpty() == true) {
                        lastLine = trimmedLine
                    }
                }
            }

            if (lastLine == null) {
                logMessage("There is no locations in the file")
                return null
            }

            return lastLine

        } catch (e: Exception) {
            logMessage("Reading error: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun startExternalClient(serverIp: String) {
        var context: ZContext? = null
        var socket: org.zeromq.ZMQ.Socket? = null
        var packetCounter = 1
        var reconnectionAttempts = 0
        val maxReconnectionAttempts = 10

        try {
            val address = "tcp://$serverIp:4789"

            while (isRunning && reconnectionAttempts < maxReconnectionAttempts) {
                try {
                    // Создаем новое соединение или переподключаемся
                    if (socket == null) {
                        context = ZContext()
                        socket = context.createSocket(ZMQ.REQ)

                        // Настраиваем таймауты
                        socket?.receiveTimeOut = 5000
                        socket?.sendTimeOut = 5000

                        socket?.connect(address)
                    }

                    val lastLocation = readLastLocationLineFromFile()

                    if (lastLocation != null) {
                        // Отправляем данные
                        val sent = socket?.send(lastLocation.toByteArray(ZMQ.CHARSET), 0)
                        if (sent == true) {
                            logMessage("[$packetCounter] Локация отправлена на сервер")
                        } else {
                            throw Exception("Send failed")
                        }

                        // Ждем ответ (важно для проверки соединения)
                        val reply = socket?.recv(0)
                        if (reply == null) {
                            throw Exception("No response from server")
                        }

                        val response = String(reply, ZMQ.CHARSET)
                        logMessage("Ответ сервера: $response")

                        packetCounter++
                        reconnectionAttempts = 0 // Сброс счетчика переподключений

                    } else {

                        logMessage("Cant find last location")
                    }

                    Thread.sleep(10000)

                } catch (e: Exception) {
                    if (!isRunning) {
                        // Если остановлено пользователем - просто выходим без ошибки
                        break
                    }
                    logMessage("error: ${e.message}")

                    // Закрываем текущее соединение
                    socket?.close()
                    context?.close()
                    socket = null
                    context = null

                    // Увеличиваем счетчик переподключений
                    reconnectionAttempts++

                    if (isRunning && reconnectionAttempts < maxReconnectionAttempts) {
                        logMessage("Попытка переподключения $reconnectionAttempts...")
                        Thread.sleep(5000) // Ждем 5 секунд перед следующей попыткой
                    } else if (reconnectionAttempts >= maxReconnectionAttempts) {
                        logMessage("Достигнут максимум попыток переподключения. Остановка.")
                        break
                    }
                }
            }

        } catch (e: Exception) {
            if (isRunning) {
                logMessage("error ${e.message}")
                e.printStackTrace()
            }
        } finally {
            // Очистка ресурсов
            socket?.close()
            context?.close()
            isRunning = false
            logMessage("Отключен от сервера")
        }
    }

    private fun logMessage(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timeStamp] $message\n"

        handler.post {
            tvLog.append(logMessage)
            Log.d(log_tag, message)

            val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}