package com.officeclone.editor.slides

import android.graphics.Color
import java.util.UUID

data class SlideElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = "",
    val backgroundColor: Int = Color.LTGRAY
)

data class Slide(
    val id: String = UUID.randomUUID().toString(),
    val elements: MutableList<SlideElement> = mutableListOf()
)

data class Presentation(val slides: MutableList<Slide> = mutableListOf()) {
    fun addSlide(): Slide {
        val slide = Slide()
        slides.add(slide)
        return slide
    }
}
