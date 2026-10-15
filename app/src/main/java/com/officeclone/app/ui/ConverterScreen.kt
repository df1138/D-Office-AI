package com.officeclone.app.ui

import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.officeclone.app.conversion.FileConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceFormat by remember { mutableStateOf<FileConverter.Format?>(null) }
    var targetFormat by remember { mutableStateOf(FileConverter.Format.DOCX) }
    var message by remember { mutableStateOf("Выберите файл для преобразования.") }
    var busy by remember { mutableStateOf(false) }
    var ocrImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrText by remember { mutableStateOf("") }
    var ocrMessage by remember { mutableStateOf("Выберите изображение, чтобы распознать текст на устройстве.") }

    val openImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ocrImageUri = uri
            ocrText = ""
            ocrMessage = "Изображение выбрано. Нажмите «Распознать текст»."
        }
    }
    val saveOcrText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(ocrText.toByteArray(Charsets.UTF_8)) }
                    ?: error("Не удалось создать файл")
                ocrMessage = "Распознанный текст сохранён."
            } catch (e: Exception) {
                ocrMessage = "Не удалось сохранить текст: ${e.message ?: "ошибка"}"
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "файл"
                } ?: "файл"
                val format = FileConverter.Format.fromName(name)
                if (format == null) {
                    message = "Формат не распознан. Поддерживаются TXT, HTML, RTF, DOCX, ODT, PPTX, PDF, CSV и XLSX."
                } else {
                    sourceUri = uri; sourceName = name; sourceFormat = format
                    targetFormat = when (format) {
                        FileConverter.Format.TXT, FileConverter.Format.HTML, FileConverter.Format.RTF -> FileConverter.Format.DOCX
                        FileConverter.Format.CSV -> FileConverter.Format.XLSX
                        FileConverter.Format.DOCX -> FileConverter.Format.PDF
                        FileConverter.Format.XLSX -> FileConverter.Format.CSV
                        FileConverter.Format.PDF -> FileConverter.Format.DOCX
                        FileConverter.Format.ODT -> FileConverter.Format.DOCX
                        FileConverter.Format.PPTX -> FileConverter.Format.DOCX
                    }
                    message = "Файл выбран: $name"
                }
            } catch (e: Exception) { message = "Не удалось прочитать сведения о файле: ${e.message}" }
        }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        val src = sourceUri; val srcFormat = sourceFormat
        if (destination != null && src != null && srcFormat != null) {
            busy = true
            try {
                val bytes = context.contentResolver.openInputStream(src)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                    while (true) { val count = stream.read(buffer); if (count < 0) break; total += count; require(total <= 25 * 1024 * 1024) { "Файл больше 25 МБ" }; out.write(buffer, 0, count) }
                    out.toByteArray()
                } ?: error("Не удалось открыть исходный файл")
                val converted = FileConverter.convert(bytes, srcFormat, targetFormat)
                context.contentResolver.openOutputStream(destination, "wt")?.use { it.write(converted.bytes) } ?: error("Не удалось создать файл")
                message = "Готово: файл сохранён в формате ${targetFormat.extension.uppercase()}"
            } catch (e: Exception) {
                message = "Ошибка преобразования: ${e.message ?: "неизвестная ошибка"}"
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Конвертер файлов", style = MaterialTheme.typography.headlineMedium)
        Text("Преобразование распространённых офисных форматов. Исходный файл не изменяется.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OCR — распознавание текста на изображении", style = MaterialTheme.typography.titleMedium)
                Text("Обработка выполняется на устройстве с помощью ML Kit; изображение не отправляется в облако этим модулем.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = { openImage.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, contentDescription = null); Text("  Выбрать изображение")
                }
                Button(onClick = {
                    val uri = ocrImageUri
                    if (uri == null) {
                        ocrMessage = "Сначала выберите изображение."
                    } else {
                        busy = true
                        ocrMessage = "Распознавание…"
                        try {
                            val image = InputImage.fromFilePath(context, uri)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            recognizer.process(image)
                                .addOnSuccessListener { result ->
                                    ocrText = result.text
                                    ocrMessage = if (result.text.isBlank()) "Текст не обнаружен. Попробуйте более чёткое изображение." else "Распознавание завершено. Проверьте текст и сохраните его."
                                    busy = false
                                    recognizer.close()
                                }
                                .addOnFailureListener { error ->
                                    ocrMessage = "Ошибка OCR: ${error.localizedMessage ?: "не удалось распознать изображение"}"
                                    busy = false
                                    recognizer.close()
                                }
                        } catch (e: Exception) {
                            ocrMessage = "Не удалось открыть изображение: ${e.localizedMessage ?: "ошибка"}"
                            busy = false
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Обработка…" else "Распознать текст") }
                OutlinedTextField(value = ocrText, onValueChange = { ocrText = it }, label = { Text("Результат OCR (можно исправить)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
                OutlinedButton(onClick = { if (ocrText.isBlank()) ocrMessage = "Сначала распознайте или введите текст." else saveOcrText.launch("ocr-result.txt") }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить результат в TXT") }
                Text(ocrMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, contentDescription = null); Text("  Выбрать исходный файл")
                }
                Text(if (sourceName.isBlank()) "Файл не выбран" else "Источник: $sourceName (${sourceFormat?.extension?.uppercase()})")
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = "${targetFormat.label} (.${targetFormat.extension})", onValueChange = {}, readOnly = true,
                        label = { Text("Формат результата") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        FileConverter.Format.entries.forEach { format ->
                            DropdownMenuItem(text = { Text("${format.label} (.${format.extension})") }, onClick = { targetFormat = format; expanded = false })
                        }
                    }
                }
                Button(onClick = {
                    val src = sourceFormat
                    if (sourceUri == null || src == null) message = "Сначала выберите исходный файл."
                    else if (src == targetFormat) message = "Выберите формат, отличный от исходного."
                    else saveFile.launch("${sourceName.substringBeforeLast('.', sourceName)}.${targetFormat.extension}")
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null); Text(if (busy) "Преобразование…" else "Преобразовать и сохранить")
                }
            }
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Text("Поддерживаются PDF (извлечение текста), DOCX, ODT, PPTX (текст слайдов), TXT, HTML, RTF, CSV, XLSX и экспорт в PDF. Сложная вёрстка PDF и мультимедиа/анимации PPTX не сохраняются. OCR распознаёт текст с изображений и сохраняет результат в TXT.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
