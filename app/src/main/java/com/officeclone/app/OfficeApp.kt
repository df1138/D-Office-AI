package com.officeclone.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

@HiltAndroidApp
class OfficeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
