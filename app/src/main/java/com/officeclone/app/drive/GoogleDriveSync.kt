package com.officeclone.app.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.net.URLEncoder

/** Direct Google Drive REST API access. OAuth consent/configuration is required in Google Cloud Console. */
class GoogleDriveSync(private val context: Context) {
    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
        const val WEB_CLIENT_ID_RESOURCE = "default_web_client_id"
        private const val CHANGE_TOKEN_PREFS = "drive_sync"
    }

    fun signInIntent(): Intent {
        val webClientId = context.resources.getIdentifier(WEB_CLIENT_ID_RESOURCE, "string", context.packageName)
            .takeIf { it != 0 }?.let(context::getString).orEmpty()
        require(webClientId.isNotBlank() && !webClientId.contains("REPLACE_ME")) {
            "Настройте OAuth Web Client ID в Google Cloud Console и добавьте строку default_web_client_id в ресурсы приложения."
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestIdToken(webClientId).requestScopes(Scope(DRIVE_FILE_SCOPE)).build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    suspend fun listFiles(account: GoogleSignInAccount): List<DriveFile> = withContext(Dispatchers.IO) {
        val token = accessToken(account)
        val json = request("$DRIVE_API/files?fields=files(id,name,mimeType,size,modifiedTime,webViewLink,headRevisionId)&pageSize=100&orderBy=modifiedTime%20desc", "GET", token)
        val files = json.optJSONArray("files") ?: org.json.JSONArray()
        (0 until files.length()).map { i -> files.getJSONObject(i).let { DriveFile(it.optString("id"), it.optString("name"), it.optString("mimeType"), it.optString("size"), it.optString("modifiedTime"), it.optString("webViewLink"), it.optString("headRevisionId")) } }
    }

    suspend fun upload(account: GoogleSignInAccount, name: String, mimeType: String, bytes: ByteArray): DriveFile = withContext(Dispatchers.IO) {
        require(bytes.size <= 25 * 1024 * 1024) { "Для синхронизации ограничение этого клиента — 25 МБ" }
        val token = accessToken(account); val boundary = "officeclone_${UUID.randomUUID()}"
        val meta = JSONObject().put("name", name).put("mimeType", mimeType).toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray()); write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val result = request("$UPLOAD_API?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime,webViewLink,headRevisionId", "POST", token, body, "multipart/related; boundary=$boundary")
        DriveFile(result.optString("id"), result.optString("name"), result.optString("mimeType"), result.optString("size"), result.optString("modifiedTime"), result.optString("webViewLink"), result.optString("headRevisionId"))
    }

    /** Update an existing Drive file. If expectedRevisionId is supplied, refuse to overwrite a newer remote revision. */
    suspend fun update(account: GoogleSignInAccount, fileId: String, name: String, mimeType: String, bytes: ByteArray, expectedRevisionId: String? = null): DriveFile = withContext(Dispatchers.IO) {
        require(fileId.matches(Regex("[A-Za-z0-9_-]{5,200}"))) { "Некорректный ID файла Drive" }
        require(bytes.size <= 25 * 1024 * 1024) { "Для синхронизации ограничение этого клиента — 25 МБ" }
        val token = accessToken(account)
        val remote = request("$DRIVE_API/files/$fileId?fields=id,name,mimeType,size,modifiedTime,webViewLink,headRevisionId", "GET", token)
        val remoteRevision = remote.optString("headRevisionId").takeIf { it.isNotBlank() }
        if (expectedRevisionId != null && remoteRevision != null && expectedRevisionId != remoteRevision) {
            throw DriveConflictException("Файл изменён в Google Drive после последней загрузки. Сначала скачайте удалённую версию или сохраните локальную копию.")
        }
        val boundary = "officeclone_${UUID.randomUUID()}"
        val metadata = JSONObject().put("name", name).put("mimeType", mimeType).toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray()); write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val result = request("$UPLOAD_API/$fileId?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime,webViewLink,headRevisionId", "PATCH", token, body, "multipart/related; boundary=$boundary")
        DriveFile(result.optString("id"), result.optString("name"), result.optString("mimeType"), result.optString("size"), result.optString("modifiedTime"), result.optString("webViewLink"), result.optString("headRevisionId"))
    }

    /** Persisted Drive change cursor: call once to initialize, then poll for server-side changes. */
    suspend fun getStartPageToken(account: GoogleSignInAccount): String = withContext(Dispatchers.IO) {
        val token = accessToken(account)
        request("$DRIVE_API/changes/startPageToken", "GET", token).optString("startPageToken").also {
            require(it.isNotBlank()) { "Google Drive не вернул startPageToken" }
            context.getSharedPreferences(CHANGE_TOKEN_PREFS, Context.MODE_PRIVATE).edit().putString("pageToken", it).apply()
        }
    }

    suspend fun pollChanges(account: GoogleSignInAccount): ChangeBatch = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(CHANGE_TOKEN_PREFS, Context.MODE_PRIVATE)
        val pageToken = prefs.getString("pageToken", null) ?: getStartPageToken(account)
        val token = accessToken(account)
        val encoded = URLEncoder.encode(pageToken, "UTF-8")
        val json = request("$DRIVE_API/changes?pageToken=$encoded&includeRemoved=true&fields=nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,modifiedTime,headRevisionId))", "GET", token)
        val arr = json.optJSONArray("changes") ?: org.json.JSONArray()
        val changes = (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i); val f = item.optJSONObject("file")
            DriveChange(item.optString("fileId"), item.optBoolean("removed"), f?.let { DriveFile(it.optString("id"), it.optString("name"), it.optString("mimeType"), "", it.optString("modifiedTime"), "", it.optString("headRevisionId")) })
        }
        val next = json.optString("newStartPageToken").takeIf { it.isNotBlank() } ?: json.optString("nextPageToken").takeIf { it.isNotBlank() }
        if (next != null) prefs.edit().putString("pageToken", next).apply()
        ChangeBatch(changes, next, changes.any { !it.removed })
    }

    data class DriveChange(val fileId: String, val removed: Boolean, val file: DriveFile?)
    data class ChangeBatch(val changes: List<DriveChange>, val nextPageToken: String?, val hasUpdates: Boolean)
    class DriveConflictException(message: String) : IllegalStateException(message)

    suspend fun download(account: GoogleSignInAccount, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        require(fileId.matches(Regex("[A-Za-z0-9_-]{5,200}"))) { "Некорректный ID файла Drive" }
        val conn = (URL("$DRIVE_API/files/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; setRequestProperty("Authorization", "Bearer ${accessToken(account)}"); connectTimeout = 20000; readTimeout = 30000
        }
        try {
            if (conn.responseCode !in 200..299) error("Google Drive API вернул HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(500)}")
            conn.inputStream.use { input -> val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 25 * 1024 * 1024) { "Файл больше 25 МБ" }; out.write(buffer, 0, n) }; out.toByteArray() }
        } finally { conn.disconnect() }
    }

    private fun accessToken(account: GoogleSignInAccount): String {
        val email = account.email ?: error("Google-аккаунт не содержит email")
        return try {
            GoogleAuthUtil.getToken(context, Account(email, "com.google"), "oauth2:$DRIVE_FILE_SCOPE")
        } catch (e: UserRecoverableAuthException) {
            throw IllegalStateException("Требуется дополнительное разрешение Google для Drive: ${e.localizedMessage}", e)
        }
    }

    private fun request(url: String, method: String, token: String, body: ByteArray? = null, contentType: String = "application/json"): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Accept", "application/json")
            connectTimeout = 20000; readTimeout = 30000
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", contentType); setFixedLengthStreamingMode(body.size) }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Google Drive API HTTP $code: ${response.take(500)}")
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { conn.disconnect() }
    }

    data class DriveFile(val id: String, val name: String, val mimeType: String, val size: String, val modifiedTime: String, val webViewLink: String, val headRevisionId: String = "")
}
