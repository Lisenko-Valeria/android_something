package com.example.android_something

import android.content.Intent
import android.os.Bundle
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
import org.json.JSONObject
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
        "/storage/emulated/0/Android/data/com.example.android_something/files/Documents/location_data.json"

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

        etServerIp.setText("10.101.209.134")

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
        logMessage("=== Подключение к серверу ($serverIp) ===")

        clientThread = Thread {
            startExternalClient(serverIp)
        }.apply { start() }
    }

    fun stopAll() {
        isRunning = false
        clientThread?.interrupt()
        logMessage("=== Все соединения остановлены ===")
    }

    private fun readLastLocationLineFromFine(): String? {
        try {
            val file = File(locationFilePath)

            if (!file.exists()) {
                logMessage("Файл локаций не найден: $locationFilePath")
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
                logMessage("Файл пустой или содержит только пустые строки")
                return null
            }

            return lastLine

        } catch (e: Exception) {
            logMessage("Ошибка чтения файла локаций: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun startExternalClient(serverIp: String) {
        val context = ZMQ.context(1)
        val socket = ZContext().createSocket(org.zeromq.SocketType.REQ)

        try {
            val address = "tcp://$serverIp:1234"
            socket.connect(address)
            logMessage("[EXTERNAL CLIENT] Подключение к $address")

            var packetCounter = 1

            while (isRunning) {
                try {
                    val lastLocation = readLastLocationLineFromFine()

                    if (lastLocation != null) {
                        val request = lastLocation

                        socket.send(request.toByteArray(ZMQ.CHARSET), 0)
                        logMessage(" Отправлен ответ серверу с локацией")

                        val reply = socket.recv(0)
                        val response = String(reply, ZMQ.CHARSET)
                        logMessage(" Ответ сервера: $response")

                        packetCounter++
                    } else {
                        logMessage("[EXTERNAL CLIENT] Не найдена последняя локация в файле")
                    }

                    Thread.sleep(10000)

                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logMessage("[EXTERNAL CLIENT] Ошибка в цикле отправки: ${e.message}")
                    Thread.sleep(5000)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                logMessage("[EXTERNAL CLIENT] Критическая ошибка: ${e.message}")
                logMessage("Убедитесь, что:")
                logMessage("1. Компьютер и телефон в одной сети Wi-Fi")
                logMessage("2. Python сервер запущен на компьютере")
                logMessage("3. Правильный IP адрес: $serverIp")
                e.printStackTrace()
            }
        } finally {
            socket.close()
            context.close()
            isRunning = false
            logMessage("[EXTERNAL CLIENT] Отключен")
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