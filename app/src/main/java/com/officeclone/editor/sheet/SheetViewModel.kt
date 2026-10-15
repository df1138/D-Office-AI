package com.officeclone.editor.sheet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.officeclone.app.LocalDraftStore
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SheetViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {
    private val _workbook = MutableStateFlow(Workbook())
    val workbook: StateFlow<Workbook> = _workbook
    private val _activeSheetIndex = MutableStateFlow(0)
    val activeSheetIndex: StateFlow<Int> = _activeSheetIndex
    private val _sheet = MutableStateFlow(_workbook.value.sheets[0].sheet)
    val sheet: StateFlow<Sheet> = _sheet
    private val _selected = MutableStateFlow<Pair<Int, Int>?>(null)
    val selected: StateFlow<Pair<Int, Int>?> = _selected
    private val _calculated = MutableStateFlow<Map<Pair<Int, Int>, String>>(emptyMap())
    val calculated: StateFlow<Map<Pair<Int, Int>, String>> = _calculated

    init {
        val saved = LocalDraftStore.loadWorkbook(app)
        if (saved != null) runCatching { replaceWorkbook(fromJson(saved)) }
        recalculate(_sheet.value)
    }

    fun replaceSheet(value: Sheet) = replaceWorkbook(Workbook(listOf(WorkbookSheet("Лист1", value))))

    fun replaceWorkbook(value: Workbook) {
        val safeSheets = value.sheets.take(MAX_SHEETS).mapIndexed { i, item ->
            WorkbookSheet(cleanSheetName(item.name, i, value.sheets.take(MAX_SHEETS).map { it.name }), item.sheet.copy(data = item.sheet.data.toMutableMap()))
        }.ifEmpty { listOf(WorkbookSheet("Лист1", Sheet())) }
        _workbook.value = Workbook(safeSheets)
        _activeSheetIndex.value = 0
        activateSheet(0)
        saveDraft()
    }

    fun selectSheet(index: Int) {
        if (index !in _workbook.value.sheets.indices) return
        persistActiveSheet()
        _activeSheetIndex.value = index
        activateSheet(index)
    }

    fun addSheet() {
        val current = _workbook.value
        if (current.sheets.size >= MAX_SHEETS) return
        persistActiveSheet()
        var n = current.sheets.size + 1
        var name = "Лист$n"
        while (current.sheets.any { it.name.equals(name, true) }) { n++; name = "Лист$n" }
        _workbook.value = Workbook(current.sheets + WorkbookSheet(name, Sheet()))
        _activeSheetIndex.value = _workbook.value.sheets.lastIndex
        activateSheet(_activeSheetIndex.value)
        saveDraft()
    }

    fun renameActiveSheet(name: String): Boolean {
        val trimmed = name.trim().take(31)
        if (trimmed.isBlank() || trimmed.any { it in "[]:*?/\\" }) return false
        val list = _workbook.value.sheets.toMutableList()
        val index = _activeSheetIndex.value
        if (list.indices.any { it != index && list[it].name.equals(trimmed, true) }) return false
        list[index] = list[index].copy(name = trimmed)
        _workbook.value = Workbook(list)
        saveDraft()
        return true
    }

    fun setCellValue(r: Int, c: Int, value: String) {
        val current = _sheet.value
        if (r !in 0 until current.rows || c !in 0 until current.cols) return
        val data = current.data.toMutableMap()
        if (value.isEmpty()) data.remove(r to c) else data[r to c] = Cell(value, value.replace(',', '.').toDoubleOrNull())
        val updated = current.copy(data = data)
        _sheet.value = updated
        persistActiveSheet()
        saveDraft()
        recalculate(updated)
    }

    fun sortRowsByColumn(column: Int, ascending: Boolean = true) {
        val current = _sheet.value
        if (column !in 0 until current.cols || current.rows < 2) return
        val calculatedValues = _calculated.value
        val rows = (0 until current.rows).map { r ->
            val rowData = current.data.filterKeys { it.first == r }.mapKeys { it.key.second }
            Triple(r, calculatedValues[r to column] ?: rowData[column]?.value.orEmpty(), rowData)
        }
        val header = rows.first()
        val sorted = rows.drop(1).sortedWith { a, b ->
            val aBlank = a.second.isBlank(); val bBlank = b.second.isBlank()
            when {
                aBlank && bBlank -> 0
                aBlank -> 1
                bBlank -> -1
                else -> compareCellValues(a.second, b.second) * (if (ascending) 1 else -1)
            }
        }
        val reordered = (listOf(header) + sorted)
        val newData = mutableMapOf<Pair<Int, Int>, Cell>()
        reordered.forEachIndexed { newRow, item -> item.third.forEach { (col, cell) -> newData[newRow to col] = cell } }
        val updated = current.copy(data = newData)
        _sheet.value = updated
        persistActiveSheet()
        saveDraft()
        recalculate(updated)
    }

    fun displayValue(r: Int, c: Int): String = _calculated.value[r to c] ?: _sheet.value.get(r, c).value

    private fun compareCellValues(a: String, b: String): Int {
        val an = a.replace(',', '.').toDoubleOrNull(); val bn = b.replace(',', '.').toDoubleOrNull()
        return if (an != null && bn != null) an.compareTo(bn) else a.compareTo(b, ignoreCase = true)
    }

    private fun persistActiveSheet() {
        val list = _workbook.value.sheets.toMutableList()
        val index = _activeSheetIndex.value
        if (index in list.indices) { list[index] = list[index].copy(sheet = _sheet.value); _workbook.value = Workbook(list); saveDraft() }
    }

    private fun activateSheet(index: Int) {
        _sheet.value = _workbook.value.sheets[index].sheet.copy(data = _workbook.value.sheets[index].sheet.data.toMutableMap())
        _selected.value = null
        recalculate(_sheet.value)
    }

    fun noteImportedOrExported(format: String = "XLSX") {
        LocalDraftStore.addRecent(getApplication(), "Таблица", format)
    }

    private fun saveDraft() {
        runCatching {
            val root = JSONObject().put("sheets", JSONArray())
            val sheetsArray = root.getJSONArray("sheets")
            _workbook.value.sheets.forEach { ws ->
                val item = JSONObject().put("name", ws.name).put("rows", ws.sheet.rows).put("cols", ws.sheet.cols).put("cells", JSONArray())
                val cells = item.getJSONArray("cells")
                ws.sheet.data.forEach { (key, cell) -> cells.put(JSONObject().put("r", key.first).put("c", key.second).put("v", cell.value)) }
                sheetsArray.put(item)
            }
            LocalDraftStore.saveWorkbook(getApplication(), root)
        }
    }

    private fun fromJson(root: JSONObject): Workbook {
        val array = root.optJSONArray("sheets") ?: JSONArray()
        val sheets = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val rows = item.optInt("rows", 50).coerceIn(1, 200)
            val cols = item.optInt("cols", 10).coerceIn(1, 30)
            val data = mutableMapOf<Pair<Int, Int>, Cell>()
            val cells = item.optJSONArray("cells") ?: JSONArray()
            for (j in 0 until minOf(cells.length(), 6000)) {
                val cell = cells.optJSONObject(j) ?: continue
                val r = cell.optInt("r", -1); val c = cell.optInt("c", -1)
                if (r in 0 until rows && c in 0 until cols) data[r to c] = Cell(cell.optString("v"), cell.optString("v").replace(',', '.').toDoubleOrNull())
            }
            WorkbookSheet(item.optString("name", "Лист${i + 1}"), Sheet(rows, cols, data))
        }.take(MAX_SHEETS)
        return Workbook(sheets.ifEmpty { listOf(WorkbookSheet("Лист1", Sheet())) })
    }

    private fun cleanSheetName(name: String, index: Int, names: List<String>): String {
        val base = name.trim().take(31).ifBlank { "Лист${index + 1}" }.replace(Regex("[\\[\\]:*?/\\\\]"), "_")
        var result = base; var suffix = 2
        while (names.take(index).any { it.equals(result, true) }) { result = (base.take(27) + "_$suffix").take(31); suffix++ }
        return result
    }

    private fun select(r: Int, c: Int) { val current = _sheet.value; if (r in 0 until current.rows && c in 0 until current.cols) _selected.value = r to c }
    fun selectCell(r: Int, c: Int) = select(r, c)

    private fun recalculate(value: Sheet) { _calculated.value = FormulaEngine.evaluateAll(value) }
    companion object { private const val MAX_SHEETS = 20 }
}
