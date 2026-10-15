package com.officeclone.app.ui

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.officeclone.editor.word.WordViewModel

class SelectionEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {
    var onSelectionChanged: ((Int, Int) -> Unit)? = null
    var updatingFromModel: Boolean = false

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(vm: WordViewModel = hiltViewModel()) {
    val richText by vm.text.collectAsState()
    val title by vm.title.collectAsState()
    val message by vm.message.collectAsState()
    var selStart by remember { mutableIntStateOf(0) }
    var selEnd by remember { mutableIntStateOf(0) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri -> uri?.let(vm::save) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::load) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(title, maxLines = 1) }, actions = {
            IconButton(onClick = { openLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Открыть DOCX")
            }
            IconButton(onClick = { saveLauncher.launch("$title.docx") }) {
                Icon(Icons.Default.Save, contentDescription = "Сохранить DOCX")
            }
            IconButton(onClick = {
                openLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            }) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Импорт DOCX с Google Диска")
            }
            IconButton(onClick = { saveLauncher.launch("$title.docx") }) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Экспорт DOCX на Google Диск")
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Импорт и экспорт через Google Диск: в системном окне выберите Google Диск. Для этого приложение Google Диск должно быть установлено и подключено к аккаунту.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Text("↶", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) { Text("↷", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = { showFindReplace = true }) { Text("Найти", style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = { vm.applyBold(selStart, selEnd) }) { Icon(Icons.Default.FormatBold, contentDescription = "Полужирный") }
                IconButton(onClick = { vm.applyItalic(selStart, selEnd) }) { Icon(Icons.Default.FormatItalic, contentDescription = "Курсив") }
                IconButton(onClick = { vm.applyUnderline(selStart, selEnd) }) { Icon(Icons.Default.FormatUnderlined, contentDescription = "Подчёркивание") }
            }
            AndroidView(
                factory = { ctx ->
                    SelectionEditText(ctx).apply {
                        hint = "Начните писать..."
                        setTextColor(android.graphics.Color.WHITE)
                        setHintTextColor(android.graphics.Color.LTGRAY)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(32, 32, 32, 32)
                        textSize = 16f
                        gravity = Gravity.TOP or Gravity.START
                        minLines = 8
                        isVerticalScrollBarEnabled = true
                        onSelectionChanged = { start, end ->
                            selStart = start.coerceAtLeast(0)
                            selEnd = end.coerceAtLeast(0)
                        }
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                if (!updatingFromModel) s?.let(vm::updateText)
                            }
                        })
                    }
                },
                update = { view ->
                    if (view.text.toString() != richText.ssb.toString() || !sameSpans(view.text, richText.ssb)) {
                        val start = view.selectionStart.coerceAtLeast(0)
                        val end = view.selectionEnd.coerceAtLeast(0)
                        view.updatingFromModel = true
                        view.setText(richText.ssb)
                        val length = view.text.length
                        view.setSelection(start.coerceAtMost(length), end.coerceAtMost(length))
                        view.updatingFromModel = false
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            val plain = richText.ssb.toString()
            val words = plain.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Слов: $words · Символов: ${plain.length}", style = MaterialTheme.typography.labelSmall)
                if (message != null) {
                    Text("  ·  ${message.orEmpty()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                }
            }
        }
    }

    if (showFindReplace) {
        AlertDialog(
            onDismissRequest = { showFindReplace = false },
            title = { Text("Найти и заменить") },
            text = {
                Column {
                    OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text("Найти") }, singleLine = true)
                    OutlinedTextField(value = replacementText, onValueChange = { replacementText = it }, label = { Text("Заменить на") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { vm.replaceAll(findText, replacementText); showFindReplace = false }) { Text("Заменить всё") }
            },
            dismissButton = { TextButton(onClick = { showFindReplace = false }) { Text("Отмена") } }
        )
    }
}

private fun sameSpans(a: Spanned, b: Spanned): Boolean {
    fun signature(s: Spanned): List<String> {
        val styles = s.getSpans(0, s.length, StyleSpan::class.java).map {
            "S:${s.getSpanStart(it)}:${s.getSpanEnd(it)}:${it.style}"
        }
        val underlines = s.getSpans(0, s.length, UnderlineSpan::class.java).map {
            "U:${s.getSpanStart(it)}:${s.getSpanEnd(it)}"
        }
        return (styles + underlines).sorted()
    }
    return signature(a) == signature(b)
}
