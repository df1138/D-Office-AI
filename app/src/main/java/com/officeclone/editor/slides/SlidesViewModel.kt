package com.officeclone.editor.slides

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SlidesViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {
    private val _pres = MutableStateFlow(Presentation().apply { addSlide() })
    val pres: StateFlow<Presentation> = _pres
    private val _idx = MutableStateFlow(0)
    val idx: StateFlow<Int> = _idx

    fun selectSlide(i: Int) { if (i in _pres.value.slides.indices) _idx.value = i }

    fun addSlide() {
        val current = _pres.value
        val updated = Presentation(current.slides.map { it.copy(elements = it.elements.toMutableList()) }.toMutableList())
        updated.addSlide()
        _pres.value = updated
        _idx.value = updated.slides.lastIndex
    }

    fun addText() {
        val updated = mutablePresentationCopy() ?: return
        updated.slides[_idx.value].elements.add(SlideElement(type = "text", x = 50f, y = 50f, width = 200f, height = 50f, text = "Текст"))
        _pres.value = updated
    }

    fun addShape() {
        val updated = mutablePresentationCopy() ?: return
        updated.slides[_idx.value].elements.add(SlideElement(type = "shape", x = 100f, y = 100f, width = 100f, height = 100f))
        _pres.value = updated
    }

    private fun mutablePresentationCopy(): Presentation? {
        val current = _pres.value
        if (_idx.value !in current.slides.indices) return null
        return Presentation(current.slides.map { slide -> slide.copy(elements = slide.elements.toMutableList()) }.toMutableList())
    }
}
