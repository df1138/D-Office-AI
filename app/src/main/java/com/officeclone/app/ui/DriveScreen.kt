package com.officeclone.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.officeclone.app.drive.GoogleDriveSync
import kotlinx.coroutines.launch

@Composable
fun DriveScreen() {
    val context = LocalContext.current
    val api = remember(context) { GoogleDriveSync(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<GoogleSignInAccount?>(GoogleSignIn.getLastSignedInAccount(context)) }
    var files by remember { mutableStateOf(emptyList<GoogleDriveSync.DriveFile>()) }
    var status by remember { mutableStateOf("Войдите в Google, чтобы работать с файлами через Drive API.") }
    var pendingDownload by remember { mutableStateOf<GoogleDriveSync.DriveFile?>(null) }
    var updateTarget by remember { mutableStateOf<GoogleDriveSync.DriveFile?>(null) }

    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            account = task.getResult(ApiException::class.java)
            status = "Вход выполнен. Загрузите список файлов."
        } catch (e: Exception) { status = "Вход не выполнен: ${e.localizedMessage ?: "проверьте OAuth-настройки"}" }
    }
    val openUpload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val acc = account
        if (uri != null && acc != null) scope.launch {
            try {
                status = "Загрузка файла в Google Drive…"
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else "officeclone-file" } ?: "officeclone-file"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val b = ByteArray(8192); var total = 0
                    while (true) { val n = input.read(b); if (n < 0) break; total += n; require(total <= 25 * 1024 * 1024) { "Файл больше 25 МБ" }; out.write(b, 0, n) }; out.toByteArray()
                } ?: error("Не удалось открыть файл")
                val target = updateTarget
                val uploaded = if (target != null) api.update(acc, target.id, name, mime, bytes, target.headRevisionId.takeIf { it.isNotBlank() }) else api.upload(acc, name, mime, bytes)
                updateTarget = null
                status = if (target != null) "Обновлён существующий файл: ${uploaded.name}" else "Загружено: ${uploaded.name} (ID ${uploaded.id})"
                files = api.listFiles(acc)
            } catch (e: Exception) { status = "Ошибка загрузки: ${e.localizedMessage ?: e.javaClass.simpleName}" }
        } else if (acc == null) status = "Сначала войдите в Google."
    }
    val saveDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val acc = account; val file = pendingDownload
        if (uri != null && acc != null && file != null) scope.launch {
            try {
                status = "Скачивание ${file.name}…"
                val bytes = api.download(acc, file.id)
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Не удалось сохранить файл")
                status = "Файл сохранён: ${file.name}"
            } catch (e: Exception) { status = "Ошибка скачивания: ${e.localizedMessage ?: e.javaClass.simpleName}" }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Google Drive", style = MaterialTheme.typography.headlineMedium)
        Text("Прямая работа через Google Drive REST API: список файлов, загрузка и скачивание. Для входа требуется настроить OAuth Web Client ID в Google Cloud Console.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { try { signIn.launch(api.signInIntent()) } catch (e: Exception) { status = e.message ?: "Не удалось начать вход" } }, modifier = Modifier.fillMaxWidth()) { Text(if (account == null) "Войти в Google" else "Сменить Google-аккаунт") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { val a = account; if (a == null) status = "Сначала войдите в Google." else scope.launch { try { status = "Загрузка списка…"; files = api.listFiles(a); status = "Файлов: ${files.size}" } catch (e: Exception) { status = "Ошибка списка: ${e.localizedMessage ?: "API error"}" } } }, modifier = Modifier.weight(1f)) { Text("Обновить список") }
            OutlinedButton(onClick = { updateTarget = null; openUpload.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Загрузить файл") }
        }
        OutlinedButton(onClick = { val a = account; if (a == null) status = "Сначала войдите в Google." else scope.launch { try { status = "Проверка изменений на сервере…"; val batch = api.pollChanges(a); files = api.listFiles(a); status = "Изменений: ${batch.changes.size}. Список обновлён." } catch (e: Exception) { status = "Ошибка отслеживания: ${e.localizedMessage ?: "API error"}" } } }, modifier = Modifier.fillMaxWidth()) { Text("Проверить изменения Drive") }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.id }) { file ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                        Text("${file.mimeType}${if (file.size.isNotBlank()) " • ${file.size} байт" else ""}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { pendingDownload = file; saveDownload.launch(file.name) }, modifier = Modifier.weight(1f)) { Text("Скачать") }
                            OutlinedButton(onClick = { updateTarget = file; openUpload.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Обновить эту версию") }
                        }
                    }
                }
            }
        }
    }
}
