package com.officeclone.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.officeclone.app.LocalDraftStore
import com.officeclone.app.R

@Composable
fun HomeScreen(onOpenWord: () -> Unit, onOpenSheet: () -> Unit, onOpenSlides: () -> Unit, onOpenConverter: () -> Unit, onOpenDrive: () -> Unit) {
    val context = LocalContext.current
    val recent = remember { LocalDraftStore.recent(context) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.officeclone_icon_source), contentDescription = "OfficeClone", modifier = Modifier.size(76.dp))
            Column {
                Text("OfficeClone", style = MaterialTheme.typography.headlineMedium)
                Text("Документы, таблицы и презентации", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text("Создать или открыть", style = MaterialTheme.typography.titleLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenWord, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Description, contentDescription = null)
                    Text("  Новый документ")
                }
                OutlinedButton(onClick = onOpenSheet, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.GridOn, contentDescription = null)
                    Text("  Новая таблица")
                }
                OutlinedButton(onClick = onOpenConverter, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Text("  Конвертировать файл")
                }
                OutlinedButton(onClick = onOpenDrive, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Cloud, contentDescription = null)
                    Text("  Google Drive — синхронизация")
                }
                OutlinedButton(onClick = onOpenSlides, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Slideshow, contentDescription = null)
                    Text("  Презентации")
                }
            }
        }
        Text("Недавние файлы", style = MaterialTheme.typography.titleLarge)
        if (recent.isEmpty()) {
            Text("Здесь появятся файлы, которые вы открывали или сохраняли.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(recent) { _, item ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.first, style = MaterialTheme.typography.titleMedium)
                                Text(item.second, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text("Локальная история", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Черновики автоматически сохраняются на этом устройстве.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}
