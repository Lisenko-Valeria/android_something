package com.example.android_something

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnToCalculator).setOnClickListener {
            val calculatorIntent = Intent(this, CalculatorActivity::class.java)
            startActivity(calculatorIntent)
        }

        findViewById<Button>(R.id.btnToPlayer).setOnClickListener {
            // Создаем Intent для класса MediaPlayerActivity - это MediaPlayerActivity.kt
            val PlayerIntent = Intent(this, MediaPlayerActivity::class.java)
            startActivity(PlayerIntent)
        }
    }
}