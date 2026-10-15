package com.officeclone.app.conversion

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

/** Text extraction from PDF. Layout, columns, forms and scanned-page OCR are not reconstructed. */
object PdfBridge {
    fun extractText(bytes: ByteArray): String {
        require(bytes.size <= 25 * 1024 * 1024) { "PDF больше 25 МБ" }
        require(bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-") { "Файл не похож на PDF" }
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
            require(!doc.isEncrypted || doc.currentAccessPermission.canExtractContent()) { "PDF защищён от извлечения текста" }
            return PDFTextStripper().getText(doc).trim()
        }
    }
}
