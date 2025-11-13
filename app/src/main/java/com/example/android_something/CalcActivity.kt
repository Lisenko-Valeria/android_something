package com.example.android_something

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class CalculatorActivity : AppCompatActivity() {

    private lateinit var tvDisplay: TextView
    private var currentExpression = StringBuilder()
    private var isResultDisplayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calc)

        tvDisplay = findViewById(R.id.tvDisplay)
        setupNumberButtons()
        setupOperatorButtons()

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            currentExpression.clear()
            currentExpression.append("0")
            isResultDisplayed = false
            updateDisplay()
        }

        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            calculateResult()
        }

        findViewById<Button>(R.id.btnBackToMain).setOnClickListener {
            finish()
        }
    }

    private fun setupNumberButtons() {
        listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9).forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                if (isResultDisplayed) {
                    currentExpression.clear()
                    isResultDisplayed = false
                }
                if (currentExpression.toString() == "0") {
                    currentExpression.clear()
                }
                currentExpression.append((it as Button).text)
                updateDisplay()
            }
        }
    }

    private fun setupOperatorButtons() {
        findViewById<Button>(R.id.btnAdd).setOnClickListener { appendOperator("+") }
        findViewById<Button>(R.id.btnSubtract).setOnClickListener { appendOperator("-") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { appendOperator("*") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendOperator("/") }
    }

    private fun appendOperator(op: String) {
        if (currentExpression.isNotEmpty()) {
            val lastChar = currentExpression.last().toString()
            if (lastChar.matches("[+\\-*/]".toRegex())) {
                currentExpression.deleteCharAt(currentExpression.length - 1)
            }
            currentExpression.append(op)
            isResultDisplayed = false
            updateDisplay()
        }
    }

    private fun calculateResult() {
        if (currentExpression.isNotEmpty()) {
            try {
                val expression = currentExpression.toString()

                val operatorIndex = findOperatorIndex(expression)
                if (operatorIndex != -1) {
                    val num1 = expression.substring(0, operatorIndex).toDouble()
                    val operator = expression[operatorIndex].toString()
                    val num2 = expression.substring(operatorIndex + 1).toDouble()

                    val result = when (operator) {
                        "+" -> num1 + num2
                        "-" -> num1 - num2
                        "*" -> num1 * num2
                        "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                        else -> throw IllegalArgumentException("Неизвестный оператор")
                    }

                    currentExpression.clear()
                    currentExpression.append(
                        if (result.isNaN()) "Ошибка"
                        else if (result % 1 == 0.0) result.toInt().toString()
                        else result.toString()
                    )
                    isResultDisplayed = true
                    updateDisplay()
                }
            } catch (e: Exception) {
                currentExpression.clear()
                currentExpression.append("Ошибка")
                isResultDisplayed = true
                updateDisplay()
            }
        }
    }

    private fun findOperatorIndex(expression: String): Int {
        for (i in expression.indices) {
            if (expression[i].toString().matches("[+\\-*/]".toRegex())) {
                return i
            }
        }
        return -1
    }

    private fun updateDisplay() {
        tvDisplay.text = currentExpression.toString()
    }
}