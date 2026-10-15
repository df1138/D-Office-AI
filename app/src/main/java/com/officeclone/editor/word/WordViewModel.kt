package com.officeclone.editor.word

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.officeclone.app.LocalDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import javax.inject.Inject

@HiltViewModel
class WordViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {
    private val _text = MutableStateFlow(RichText())
    val text: StateFlow<RichText> = _text
    private val _title = MutableStateFlow("Без названия")
    val title: StateFlow<String> = _title
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val undoStack = ArrayDeque<RichText>()
    private val redoStack = ArrayDeque<RichText>()

    init {
        val recovered = LocalDraftStore.loadText(app)
        if (!recovered.isNullOrEmpty()) {
            _text.value = RichText(SpannableStringBuilder(recovered))
            _title.value = "Восстановленный документ"
            _message.value = "Восстановлен локальный черновик"
        }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun updateText(s: CharSequence) {
        if (_text.value.ssb.toString() == s.toString()) return
        rememberUndoState()
        _text.value = RichText(SpannableStringBuilder(s))
        LocalDraftStore.saveText(getApplication(), _text.value.ssb.toString())
        redoStack.clear()
    }

    fun applyBold(start: Int, end: Int) = updateFormatting { it.toggleBold(start, end) }
    fun applyItalic(start: Int, end: Int) = updateFormatting { it.toggleItalic(start, end) }
    fun applyUnderline(start: Int, end: Int) = updateFormatting { it.toggleUnderline(start, end) }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(copyOf(_text.value))
        _text.value = undoStack.removeLast()
        LocalDraftStore.saveText(getApplication(), _text.value.ssb.toString())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(copyOf(_text.value))
        _text.value = redoStack.removeLast()
        LocalDraftStore.saveText(getApplication(), _text.value.ssb.toString())
    }

    fun replaceAll(find: String, replacement: String): Int {
        if (find.isEmpty()) {
            _message.value = "Введите текст для поиска"
            return 0
        }
        val source = _text.value.ssb.toString()
        val matches = Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(source).count()
        if (matches == 0) {
            _message.value = "Совпадений не найдено"
            return 0
        }
        rememberUndoState()
        val builder = SpannableStringBuilder(_text.value.ssb)
        var index = builder.toString().indexOf(find, ignoreCase = true)
        while (index >= 0) {
            builder.replace(index, index + find.length, replacement)
            index = builder.toString().indexOf(find, index + replacement.length, ignoreCase = true)
        }
        _text.value = RichText(builder)
        redoStack.clear()
        _message.value = "Заменено: $matches"
        return matches
    }

    fun newDocument() {
        rememberUndoState()
        _text.value = RichText()
        _title.value = "Без названия"
        redoStack.clear()
        LocalDraftStore.saveText(getApplication(), "")
        _message.value = "Создан новый документ"
    }

    private fun updateFormatting(action: (RichText) -> Unit) {
        val copy = copyOf(_text.value)
        val before = copyOf(_text.value)
        action(copy)
        if (formatSignature(before) != formatSignature(copy)) {
            rememberUndoState()
            _text.value = copy
            LocalDraftStore.saveText(getApplication(), _text.value.ssb.toString())
            redoStack.clear()
        }
    }

    private fun rememberUndoState() {
        undoStack.addLast(copyOf(_text.value))
        while (undoStack.size > 100) undoStack.removeFirst()
    }

    private fun copyOf(value: RichText) = RichText(SpannableStringBuilder(value.ssb))

    private fun formatSignature(value: RichText): String {
        val text = value.ssb
        val styles = text.getSpans(0, text.length, Any::class.java).mapNotNull { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end < 0) null else "${span.javaClass.name}:$start:$end:${span.hashCode()}"
        }.sorted()
        return text.toString() + styles.joinToString()
    }

    fun load(uri: Uri) {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(uri)?.use(DocxBridge::load) ?: error("Не удалось открыть файл")
                }
                undoStack.clear(); redoStack.clear()
                _text.value = loaded
                _title.value = queryFileName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Документ"
                LocalDraftStore.saveText(getApplication(), loaded.ssb.toString())
                LocalDraftStore.addRecent(getApplication(), _title.value, "DOCX")
                _message.value = "Документ открыт"
            } catch (e: Exception) {
                _message.value = "Ошибка открытия: ${e.localizedMessage ?: "неизвестная ошибка"}"
            }
        }
    }

    fun save(uri: Uri) {
        viewModelScope.launch {
            try {
                val snapshot = copyOf(_text.value)
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.use { DocxBridge.save(snapshot, it) } ?: error("Не удалось сохранить файл")
                }
                queryFileName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }?.let { _title.value = it }
                LocalDraftStore.saveText(getApplication(), snapshot.ssb.toString())
                LocalDraftStore.addRecent(getApplication(), _title.value, "DOCX")
                _message.value = "Документ сохранён"
            } catch (e: Exception) {
                _message.value = "Ошибка сохранения: ${e.localizedMessage ?: "неизвестная ошибка"}"
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: uri.lastPathSegment
    }
}
