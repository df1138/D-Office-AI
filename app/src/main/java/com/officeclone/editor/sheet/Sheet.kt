package com.officeclone.editor.sheet

data class Cell(val value: String = "", val cachedNumber: Double? = null)

data class Sheet(
    val rows: Int = 50,
    val cols: Int = 10,
    val data: MutableMap<Pair<Int, Int>, Cell> = mutableMapOf()
) {
    fun get(r: Int, c: Int): Cell = data[r to c] ?: Cell()
    fun set(r: Int, c: Int, cell: Cell) { data[r to c] = cell }
    fun cellRef(r: Int, c: Int): String {
        var n = c + 1
        val sb = StringBuilder()
        while (n > 0) {
            sb.insert(0, ('A'.code + (n - 1) % 26).toChar())
            n = (n - 1) / 26
        }
        return sb.toString() + (r + 1)
    }
}
