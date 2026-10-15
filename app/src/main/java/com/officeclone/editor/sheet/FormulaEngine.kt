package com.officeclone.editor.sheet

import java.util.Locale

/** Small offline formula engine for spreadsheet MVP: arithmetic, cell references and common aggregate functions. */
object FormulaEngine {
    fun evaluateAll(sheet: Sheet): Map<Pair<Int, Int>, String> {
        val result = mutableMapOf<Pair<Int, Int>, String>()
        val resolving = mutableSetOf<Pair<Int, Int>>()

        fun evaluateCell(row: Int, col: Int): Double? {
            if (row !in 0 until sheet.rows || col !in 0 until sheet.cols) return null
            val key = row to col
            if (key in resolving) throw FormulaError("#CYCLE!")
            val raw = sheet.get(row, col).value.trim()
            if (raw.isEmpty()) return 0.0
            if (!raw.startsWith("=")) return raw.replace(',', '.').toDoubleOrNull()
            resolving.add(key)
            return try {
                Parser(raw.drop(1)) { r, c -> evaluateCell(r, c) }.parse()
            } finally {
                resolving.remove(key)
            }
        }

        for (r in 0 until sheet.rows) for (c in 0 until sheet.cols) {
            val raw = sheet.get(r, c).value
            if (raw.startsWith("=")) {
                val key = r to c
                result[key] = try {
                    val value = evaluateCell(r, c) ?: throw FormulaError("#VALUE!")
                    format(value)
                } catch (e: FormulaError) {
                    e.message ?: "#VALUE!"
                } catch (_: ArithmeticException) {
                    "#DIV/0!"
                } catch (_: Exception) {
                    "#VALUE!"
                }
            } else {
                result[r to c] = raw
            }
        }
        return result
    }

    private fun format(value: Double): String {
        if (!value.isFinite()) return "#NUM!"
        return if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }

    private class FormulaError(message: String) : RuntimeException(message)

    private class Parser(
        source: String,
        private val cellValue: (Int, Int) -> Double?
    ) {
        private val tokens = Regex("\\s*([A-Za-z]+[0-9]+|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)|[A-Za-z]+|[()+\\-*/,:]|.)")
            .findAll(source).map { it.groupValues[1] }.toList()
        private var position = 0

        fun parse(): Double {
            val value = expression()
            if (position != tokens.size) throw FormulaError("#VALUE!")
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (peek() == "+" || peek() == "-") {
                val op = take()
                val right = term()
                value = if (op == "+") value + right else value - right
            }
            return value
        }

        private fun term(): Double {
            var value = factor()
            while (peek() == "*" || peek() == "/") {
                val op = take()
                val right = factor()
                if (op == "/" && right == 0.0) throw ArithmeticException("Division by zero")
                value = if (op == "*") value * right else value / right
            }
            return value
        }

        private fun factor(): Double {
            val token = takeOrError()
            return when {
                token == "+" -> factor()
                token == "-" -> -factor()
                token == "(" -> expression().also { expect(")") }
                token.firstOrNull()?.isDigit() == true || token.startsWith(".") -> token.toDoubleOrNull() ?: throw FormulaError("#VALUE!")
                token.firstOrNull()?.isLetter() == true && token.lastOrNull()?.isDigit() == true -> cell(token)
                token.firstOrNull()?.isLetter() == true -> function(token)
                else -> throw FormulaError("#VALUE!")
            }
        }

        private fun function(name: String): Double {
            expect("(")
            val values = mutableListOf<Double>()
            if (peek() != ")") {
                do {
                    val first = takeOrError()
                    if (first.firstOrNull()?.isLetter() == true && first.lastOrNull()?.isDigit() == true && peek() == ":") {
                        take()
                        val second = takeOrError()
                        val a = coordinates(first)
                        val b = coordinates(second)
                        val minRow = minOf(a.first, b.first)
                        val maxRow = maxOf(a.first, b.first)
                        val minCol = minOf(a.second, b.second)
                        val maxCol = maxOf(a.second, b.second)
                        for (r in minRow..maxRow) for (c in minCol..maxCol) values += cellValue(r, c) ?: 0.0
                    } else {
                        position--
                        values += expression()
                    }
                    if (peek() != ",") break
                    take()
                } while (peek() != ")")
            }
            expect(")")
            return when (name.uppercase(Locale.US)) {
                "SUM" -> values.sum()
                "AVERAGE", "AVG" -> if (values.isEmpty()) 0.0 else values.average()
                "MIN" -> values.minOrNull() ?: 0.0
                "MAX" -> values.maxOrNull() ?: 0.0
                "COUNT" -> values.size.toDouble()
                else -> throw FormulaError("#NAME?")
            }
        }

        private fun cell(reference: String): Double {
            val (row, col) = coordinates(reference)
            return cellValue(row, col) ?: throw FormulaError("#VALUE!")
        }

        private fun coordinates(reference: String): Pair<Int, Int> {
            val match = Regex("^([A-Za-z]+)([0-9]+)$").matchEntire(reference) ?: throw FormulaError("#REF!")
            var column = 0
            for (ch in match.groupValues[1].uppercase(Locale.US)) column = column * 26 + (ch - 'A' + 1)
            val row = match.groupValues[2].toIntOrNull() ?: throw FormulaError("#REF!")
            if (row < 1 || column < 1) throw FormulaError("#REF!")
            return (row - 1) to (column - 1)
        }

        private fun peek(): String? = tokens.getOrNull(position)
        private fun take(): String = takeOrError()
        private fun takeOrError(): String = tokens.getOrNull(position++) ?: throw FormulaError("#VALUE!")
        private fun expect(value: String) { if (takeOrError() != value) throw FormulaError("#VALUE!") }
    }
}
