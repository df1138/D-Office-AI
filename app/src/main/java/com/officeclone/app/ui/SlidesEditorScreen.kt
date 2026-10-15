package com.officeclone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.officeclone.editor.slides.SlidesViewModel

@Composable
fun SlidesEditorScreen(vm: SlidesViewModel = hiltViewModel()) {
    val pres by vm.pres.collectAsState()
    val idx by vm.idx.collectAsState()
    val slide = pres.slides.getOrNull(idx)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
            pres.slides.forEachIndexed { i, _ ->
                Card(
                    modifier = Modifier.size(100.dp, 70.dp).padding(4.dp).clickable { vm.selectSlide(i) },
                    colors = CardDefaults.cardColors(containerColor = if (i == idx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Слайд ${i + 1}") }
                }
            }
            IconButton(onClick = { vm.addSlide() }) { Icon(Icons.Default.Add, contentDescription = "Добавить слайд") }
        }
        slide?.let { current ->
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White).padding(16.dp)) {
                current.elements.forEach { element ->
                    when (element.type) {
                        "text" -> Text(element.text, modifier = Modifier.offset(element.x.dp, element.y.dp).size(element.width.dp, element.height.dp), color = Color.Black)
                        "shape" -> Box(Modifier.offset(element.x.dp, element.y.dp).size(element.width.dp, element.height.dp).background(Color(element.backgroundColor)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
            Button(onClick = { vm.addText() }) { Text("Текст") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addShape() }) { Text("Фигура") }
        }
    }
}
