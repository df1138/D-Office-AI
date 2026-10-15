package com.officeclone.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.officeclone.editor.sheet.SheetViewModel
import com.officeclone.editor.sheet.XlsxBridge

@Composable
fun SheetEditorScreen(vm: SheetViewModel = hiltViewModel()) {
    val sheet by vm.sheet.collectAsState()
    val workbook by vm.workbook.collectAsState()
    val activeSheetIndex by vm.activeSheetIndex.collectAsState()
    val selected by vm.selected.collectAsState()
    val calculated by vm.calculated.collectAsState()
    var editValue by remember { mutableStateOf("") }
    var fileStatus by remember { mutableStateOf("") }
    var renameDialog by remember { mutableStateOf(false) }
    var newSheetName by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val columnWidths = remember(sheet, calculated) {
        (0 until sheet.cols).associateWith { c ->
            val longest = (0 until sheet.rows).maxOfOrNull { r ->
                (calculated[r to c] ?: sheet.get(r, c).value).length
            } ?: 8
            longest.let { (it * 8 + 28).coerceIn(72, 180) }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Не удалось прочитать файл")
                XlsxBridge.importWorkbook(bytes)
            }.onSuccess {
                vm.replaceWorkbook(it)
                editValue = ""
                vm.noteImportedOrExported("XLSX")
                fileStatus = "XLSX открыт"
            }.onFailure { fileStatus = "Ошибка открытия: ${it.message ?: "неизвестная ошибка"}" }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = XlsxBridge.export(workbook)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Не удалось сохранить файл")
            }.onSuccess { vm.noteImportedOrExported("XLSX"); fileStatus = "XLSX сохранён" }
                .onFailure { fileStatus = "Ошибка сохранения: ${it.message ?: "неизвестная ошибка"}" }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val csv = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Не удалось прочитать CSV")
            parseCsv(csv)
        }.onSuccess { vm.replaceWorkbook(it); vm.noteImportedOrExported("CSV"); fileStatus = "CSV импортирован" }
            .onFailure { fileStatus = "Ошибка CSV: ${it.message ?: "неизвестная ошибка"}" }
    }
    val saveCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) runCatching {
            val csv = workbook.sheets.first().sheet.let { current ->
                (0 until current.rows).joinToString("\r\n") { r ->
                    (0 until current.cols).joinToString(",") { c -> csvEscape(current.get(r, c).value) }
                }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF"); it.write(csv) }
                ?: error("Не удалось сохранить CSV")
        }.onSuccess { vm.noteImportedOrExported("CSV"); fileStatus = "CSV экспортирован" }
            .onFailure { fileStatus = "Ошибка CSV: ${it.message ?: "неизвестная ошибка"}" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                modifier = Modifier.weight(1f)
            ) { Text("Открыть XLSX") }
            Button(onClick = { saveLauncher.launch("Таблица.xlsx") }, modifier = Modifier.weight(1f)) {
                Text("Сохранить XLSX")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("Импорт CSV") }
            OutlinedButton(onClick = { saveCsvLauncher.launch("Таблица.csv") }, modifier = Modifier.weight(1f)) { Text("Экспорт CSV") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    fileStatus = "Выберите Google Диск в боковом меню файлового окна"
                    openLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Импорт с Диска") }
            OutlinedButton(
                onClick = {
                    fileStatus = "В окне сохранения выберите Google Диск как место назначения"
                    saveLauncher.launch("Таблица.xlsx")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Экспорт на Диск") }
        }
        Text(
            "Google Диск открывается через системный выбор файлов Android. Установите приложение Google Диск и войдите в аккаунт, чтобы он появился среди мест хранения.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (fileStatus.isNotBlank()) {
            Text(fileStatus, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.addSheet(); editValue = ""; fileStatus = "Добавлен лист" }, modifier = Modifier.weight(1f)) { Text("+ Лист") }
            OutlinedButton(onClick = { newSheetName = workbook.sheets.getOrNull(activeSheetIndex)?.name.orEmpty(); renameDialog = true }, modifier = Modifier.weight(1f)) { Text("Переименовать") }
            OutlinedButton(onClick = {
                selected?.let { (_, col) ->
                    val ascendingNow = sortAscending
                    vm.sortRowsByColumn(col, ascendingNow)
                    fileStatus = if (ascendingNow) "Сортировка по возрастанию" else "Сортировка по убыванию"
                    sortAscending = !sortAscending
                } ?: run { fileStatus = "Выберите ячейку в столбце для сортировки" }
            }, modifier = Modifier.weight(1f)) { Text(if (sortAscending) "Сорт. ↑" else "Сорт. ↓") }
        }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(workbook.sheets) { index, item ->
                if (index == activeSheetIndex) {
                    Button(onClick = { vm.selectSheet(index); editValue = "" }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text(item.name, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { vm.selectSheet(index); editValue = "" }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text(item.name, maxLines = 1) }
                }
            }
        }
        if (renameDialog) {
            AlertDialog(
                onDismissRequest = { renameDialog = false },
                title = { Text("Название листа") },
                text = { OutlinedTextField(value = newSheetName, onValueChange = { newSheetName = it }, singleLine = true, label = { Text("До 31 символа") }) },
                confirmButton = { TextButton(onClick = {
                    val ok = vm.renameActiveSheet(newSheetName)
                    fileStatus = if (ok) "Лист переименован" else "Недопустимое или занятое имя"
                    renameDialog = false
                }) { Text("Сохранить") } },
                dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Отмена") } }
            )
        }
        Text(
            "Формулы: =A1+B1 · =SUM(A1:A5) · =AVERAGE(A1:A5) · =MIN(A1:A5) · =MAX(A1:A5)",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        OutlinedTextField(
            value = editValue,
            onValueChange = { editValue = it },
            label = { Text(selected?.let { (r, c) -> sheet.cellRef(r, c) } ?: "Выберите ячейку") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true
        )
        Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.height(32.dp)) {
                    Box(Modifier.width(44.dp).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant))
                    for (c in 0 until sheet.cols) {
                        Box(Modifier.width((columnWidths[c] ?: 96).dp).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                            Text(sheet.cellRef(0, c).takeWhile { it.isLetter() })
                        }
                    }
                }
                for (r in 0 until sheet.rows) Row(Modifier.height(32.dp)) {
                    Box(Modifier.width(44.dp).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) { Text("${r + 1}") }
                    for (c in 0 until sheet.cols) {
                        val cell = sheet.get(r, c)
                        val isSelected = selected == (r to c)
                        Box(
                            Modifier.width((columnWidths[c] ?: 96).dp).fillMaxHeight()
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                .clickable { vm.selectCell(r, c); editValue = cell.value },
                            contentAlignment = Alignment.CenterStart
                        ) { Text(calculated[r to c] ?: cell.value, modifier = Modifier.padding(horizontal = 6.dp), maxLines = 1) }
                    }
                }
            }
        }
        Button(onClick = { selected?.let { (r, c) -> vm.setCellValue(r, c, editValue) } }, modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            Text("Применить")
        }
    }
}


private fun csvEscape(value: String): String {
    return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

private fun parseCsv(input: String): com.officeclone.editor.sheet.Workbook {
    val text = input.removePrefix("\uFEFF")
    val rows = mutableListOf<MutableList<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
            (ch == '\n' || ch == '\r') && !quoted -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row.add(cell.toString()); cell.setLength(0)
                rows.add(row); row = mutableListOf()
            }
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
    val safeRows = rows.take(200)
    val cols = (safeRows.maxOfOrNull { it.size } ?: 1).coerceIn(1, 30)
    val data = mutableMapOf<Pair<Int, Int>, com.officeclone.editor.sheet.Cell>()
    safeRows.forEachIndexed { r, values -> values.take(cols).forEachIndexed { c, value -> if (value.isNotEmpty()) data[r to c] = com.officeclone.editor.sheet.Cell(value, value.replace(',', '.').toDoubleOrNull()) } }
    return com.officeclone.editor.sheet.Workbook(listOf(com.officeclone.editor.sheet.WorkbookSheet("Лист1", com.officeclone.editor.sheet.Sheet(maxOf(50, safeRows.size), maxOf(10, cols), data))))
}
