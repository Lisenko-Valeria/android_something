package com.example.android_something

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.android_something.R
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SocketsActivity : AppCompatActivity() {
    private var log_tag: String = "MY_LOG_TAG"
    private lateinit var tvSockets: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnStartInternal: Button
    private lateinit var btnStartExternal: Button
    private lateinit var btnStop: Button
    private lateinit var etServerIp: EditText
    private lateinit var handler: Handler
    private var isRunning = false
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sockets)

        // Инициализация UI элементов
        tvSockets = findViewById(R.id.tvSockets)
        tvLog = findViewById(R.id.tvLog)
        btnStartInternal = findViewById(R.id.btnStartInternal)
        btnStartExternal = findViewById(R.id.btnStartExternal)
        btnStop = findViewById(R.id.btnStop)
        etServerIp = findViewById(R.id.etServerIp)

        handler = Handler(Looper.getMainLooper())

        // Установка IP компьютера по умолчанию
        etServerIp.setText("10.101.209.134") // Замените на реальный IP вашего ПК

        // Кнопка запуска внутреннего теста
        btnStartInternal.setOnClickListener {
            if (!isRunning) {
                startInternalTest()
            }
        }

        // Кнопка подключения к компьютеру
        btnStartExternal.setOnClickListener {
            if (!isRunning) {
                val serverIp = etServerIp.text.toString()
                if (serverIp.isNotEmpty()) {
                    connectToComputer(serverIp)
                } else {
                    logMessage("Введите IP адрес сервера")
                }
            }
        }

        // Кнопка остановки
        btnStop.setOnClickListener {
            stopAll()
        }
    }

    private fun startInternalTest() {
        isRunning = true
        logMessage("=== Запуск внутреннего теста ===")

        // Запуск сервера
        serverThread = Thread {
            startInternalServer()
        }.apply { start() }

        // Даем серверу время на запуск
        Thread.sleep(1000)

        // Запуск клиента
        clientThread = Thread {
            startInternalClient()
        }.apply { start() }
    }

    private fun connectToComputer(serverIp: String) {
        isRunning = true
        logMessage("=== Подключение к компьютеру ($serverIp) ===")

        clientThread = Thread {
            startExternalClient(serverIp)
        }.apply { start() }
    }

    private fun stopAll() {
        isRunning = false
        serverThread?.interrupt()
        clientThread?.interrupt()
        logMessage("=== Все соединения остановлены ===")
    }

    private fun startInternalServer() {
        val context = ZMQ.context(1)
        val socket = ZContext().createSocket(SocketType.REP)

        try {
            socket.bind("tcp://*:2222")
            logMessage("[SERVER] Запущен на порту 2222")
            var counter = 0

            while (isRunning && !Thread.currentThread().isInterrupted) {
                counter++

                // Получаем данные от клиента
                val requestBytes = socket.recv(0)
                val request = String(requestBytes, ZMQ.CHARSET)
                logMessage("[SERVER] Получено: $request (пакет №$counter)")

                // Обновляем UI
                handler.post {
                    tvSockets.text = "Получено пакетов: $counter"
                }

                // Имитация обработки
                Thread.sleep(500)

                // Отправляем ответ
                val response = "Hello from Android Server! Packet: $counter"
                socket.send(response.toByteArray(ZMQ.CHARSET), 0)
                logMessage("[SERVER] Отправлено: $response")
            }
        } catch (e: Exception) {
            if (isRunning) {
                logMessage("[SERVER] Ошибка: ${e.message}")
            }
        } finally {
            socket.close()
            context.close()
            logMessage("[SERVER] Остановлен")
        }
    }

    private fun startInternalClient() {
        val context = ZMQ.context(1)
        val socket = ZContext().createSocket(SocketType.REQ)

        try {
            socket.connect("tcp://localhost:2222")
            logMessage("[CLIENT] Подключен к локальному серверу")

            for (i in 1..10) {
                if (!isRunning) break

                val request = "Hello from Android Client! #$i"
                socket.send(request.toByteArray(ZMQ.CHARSET), 0)
                logMessage("[CLIENT] Отправлено: $request")

                val reply = socket.recv(0)
                val response = String(reply, ZMQ.CHARSET)
                logMessage("[CLIENT] Получено: $response")

                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            if (isRunning) {
                logMessage("[CLIENT] Ошибка: ${e.message}")
            }
        } finally {
            socket.close()
            context.close()
            logMessage("[CLIENT] Остановлен")
        }
    }

    private fun startExternalClient(serverIp: String) {
        val context = ZMQ.context(1)
        val socket = ZContext().createSocket(SocketType.REQ)

        try {
            val address = "tcp://$serverIp:5555"
            socket.connect(address)
            logMessage("[EXTERNAL CLIENT] Подключение к $address")

            for (i in 1..20) {
                if (!isRunning) break

                val request = "Hello from Android! #$i"
                socket.send(request.toByteArray(ZMQ.CHARSET), 0)
                logMessage("[EXTERNAL CLIENT] Отправлено на ПК: $request")

                val reply = socket.recv(0)
                val response = String(reply, ZMQ.CHARSET)
                logMessage("[EXTERNAL CLIENT] Получено с ПК: $response")

                // Обновляем UI
                handler.post {
                    tvSockets.text = "Отправлено на ПК: $i"
                }

                Thread.sleep(2000)
            }
        } catch (e: Exception) {
            if (isRunning) {
                logMessage("[EXTERNAL CLIENT] Ошибка подключения: ${e.message}")
                logMessage("Убедитесь, что:")
                logMessage("1. Компьютер и телефон в одной сети Wi-Fi")
                logMessage("2. Python сервер запущен на компьютере")
                logMessage("3. Правильный IP адрес: $serverIp")
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

            // Автопрокрутка
            val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}