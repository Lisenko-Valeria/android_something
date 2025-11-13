package com.example.android_something

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.TimeUnit

class MediaPlayerActivity : AppCompatActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button
    private lateinit var btnBack: Button
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvSongTitle: TextView
    private lateinit var listView: ListView
    private lateinit var volumeSeekBar: SeekBar

    private var musicFiles = ArrayList<String>()
    private var musicPaths = ArrayList<String>()
    private var currentPosition = 0
    private var isPlaying = false
    private var handler = Handler(Looper.getMainLooper())
    private val PERMISSION_REQUEST_CODE = 123

    private var updateSeekBar: Runnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                val currentDuration = mediaPlayer.currentPosition
                seekBar.progress = currentDuration
                tvCurrentTime.text = formatTime(currentDuration.toLong())
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)

        initializeViews()
        initializeMediaPlayer()

        if (checkPermission()) {
            loadMusicFiles()
        } else {
            requestPermission()
        }
        setupListeners()
    }

    private fun initializeViews() {
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnBack = findViewById(R.id.btnBack)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvSongTitle = findViewById(R.id.tvSongTitle)
        listView = findViewById(R.id.listView)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer()
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFiles()
            } else {
                Toast.makeText(this, "Разрешение необходимо для доступа к музыке", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupListeners() {
        btnPlay.setOnClickListener { playMusic() }
        btnPause.setOnClickListener { pauseMusic() }
        btnNext.setOnClickListener { nextMusic() }
        btnPrevious.setOnClickListener { previousMusic() }
        btnBack.setOnClickListener { finish() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::mediaPlayer.isInitialized) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100.0f
                    setVolume(volume)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadMusicFiles() {
        musicFiles.clear()
        musicPaths.clear()

        val storageDir = Environment.getExternalStorageDirectory()
        findMusicFiles(storageDir)

        if (musicFiles.isEmpty()) {
            Toast.makeText(this, "Музыкальные файлы не найдены", Toast.LENGTH_LONG).show()
            musicFiles.add("Музыка не найдена")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, musicFiles)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (musicPaths.isNotEmpty()) {
                playMusic(position)
            }
        }
    }

    private fun findMusicFiles(directory: File) {
        if (directory.isDirectory) {
            val files = directory.listFiles()
            files?.forEach { file ->
                if (file.isDirectory) {
                    findMusicFiles(file)
                } else {
                    if (isAudioFile(file)) {
                        musicFiles.add(file.name)
                        musicPaths.add(file.absolutePath)
                    }
                }
            }
        }
    }

    private fun isAudioFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".mp3") ||
                name.endsWith(".wav") ||
                name.endsWith(".ogg") ||
                name.endsWith(".m4a") ||
                name.endsWith(".flac")
    }

    private fun playMusic(position: Int = currentPosition) {
        if (musicPaths.isEmpty()) return

        try {
            if (::mediaPlayer.isInitialized) {
                mediaPlayer.reset()
            }

            currentPosition = position
            mediaPlayer.setDataSource(musicPaths[position])
            mediaPlayer.prepare()
            mediaPlayer.start()

            seekBar.max = mediaPlayer.duration
            tvTotalTime.text = formatTime(mediaPlayer.duration.toLong())
            tvSongTitle.text = getFileNameFromPath(musicPaths[position])

            isPlaying = true
            handler.postDelayed(updateSeekBar, 0)


            mediaPlayer.setOnCompletionListener {
                nextMusic()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка воспроизведения: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun playMusic() {
        if (::mediaPlayer.isInitialized && !isPlaying && musicPaths.isNotEmpty()) {
            mediaPlayer.start()
            isPlaying = true
            handler.postDelayed(updateSeekBar, 0)
        }
    }

    private fun pauseMusic() {
        if (::mediaPlayer.isInitialized && isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            handler.removeCallbacks(updateSeekBar)
        }
    }

    private fun nextMusic() {
        if (musicPaths.isEmpty()) return

        currentPosition = (currentPosition + 1) % musicPaths.size
        playMusic(currentPosition)
    }

    private fun previousMusic() {
        if (musicPaths.isEmpty()) return

        currentPosition = if (currentPosition - 1 < 0) {
            musicPaths.size - 1
        } else {
            currentPosition - 1
        }
        playMusic(currentPosition)
    }

    private fun setVolume(volume: Float) {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.setVolume(volume, volume)
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getFileNameFromPath(path: String): String {
        return path.substringAfterLast("/")
    }

    override fun onPause() {
        super.onPause()
        if (::mediaPlayer.isInitialized && isPlaying) {
            mediaPlayer.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mediaPlayer.isInitialized && isPlaying) {
            mediaPlayer.start()
            handler.postDelayed(updateSeekBar, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        handler.removeCallbacks(updateSeekBar)
    }
}
