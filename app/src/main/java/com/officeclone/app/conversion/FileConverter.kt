package com.officeclone.app.conversion

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Spanned
import android.text.SpannableStringBuilder
import com.officeclone.editor.sheet.Cell
import com.officeclone.editor.sheet.Sheet
import com.officeclone.editor.sheet.Workbook
import com.officeclone.editor.sheet.WorkbookSheet
import com.officeclone.editor.sheet.XlsxBridge
import com.officeclone.editor.word.DocxBridge
import com.officeclone.editor.word.RichText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Converts common document and spreadsheet formats. This is intentionally not advertised as universal conversion. */
object FileConverter {
    enum class Format(val extension: String, val label: String, val mime: String) {
        TXT("txt", "Текст TXT", "text/plain"),
        HTML("html", "Веб-страница HTML", "text/html"),
        DOCX("docx", "Документ Word DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        CSV("csv", "Таблица CSV", "text/csv"),
        XLSX("xlsx", "Книга Excel XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "Документ PDF (экспорт)", "application/pdf"),
        RTF("rtf", "Rich Text Format (базовый текст)", "application/rtf"),
        ODT("odt", "OpenDocument Text", "application/vnd.oasis.opendocument.text"),
        PPTX("pptx", "PowerPoint PPTX (текстовые слайды)", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        companion object {
            fun fromName(name: String): Format? = entries.firstOrNull { name.substringAfterLast('.', "").equals(it.extension, true) }
        }
    }

    data class Converted(val bytes: ByteArray, val format: Format)

    fun convert(input: ByteArray, source: Format, target: Format): Converted {
        require(input.size <= 25 * 1024 * 1024) { "Файл больше допустимых 25 МБ" }
        require(target != Format.PDF || source !in setOf(Format.PDF, Format.XLSX, Format.CSV))
        if (source == target) {
            if (source == Format.PPTX) {
                val structured = PptxBridge.importSlides(input)
                val normalized = PptxBridge.exportSlides(structured)
                OfficeArchiveValidator.validate(normalized, OfficeArchiveValidator.Kind.PPTX)
                return Converted(normalized, target)
            }
            return Converted(input, target)
        }

        val text = when (source) {
            Format.TXT, Format.CSV -> input.toString(Charset.forName("UTF-8")).removePrefix("\uFEFF")
            Format.RTF -> rtfToText(input.toString(Charsets.UTF_8))
            Format.HTML -> Html.fromHtml(input.toString(Charsets.UTF_8), Html.FROM_HTML_MODE_COMPACT).toString()
            Format.DOCX -> DocxBridge.load(ByteArrayInputStream(input)).ssb.toString()
            Format.XLSX -> sheetToCsv(XlsxBridge.import(input))
            Format.PDF -> PdfBridge.extractText(input)
            Format.ODT -> OdtBridge.importText(input)
            Format.PPTX -> PptxBridge.importText(input)
        }

        val output = when (target) {
            Format.TXT -> text.toByteArray(Charsets.UTF_8)
            Format.HTML -> textToHtml(text).toByteArray(Charsets.UTF_8)
            Format.RTF -> textToRtf(text).toByteArray(Charsets.UTF_8)
            Format.ODT -> OdtBridge.exportText(text)
            Format.PPTX -> PptxBridge.exportText(text)
            Format.DOCX -> ByteArrayOutputStream().also { DocxBridge.save(RichText(SpannableStringBuilder(text)), it) }.toByteArray()
            Format.CSV -> if (source == Format.XLSX) text.toByteArray(Charsets.UTF_8) else text.toByteArray(Charsets.UTF_8)
            Format.XLSX -> {
                val csvText = if (source == Format.HTML || source == Format.DOCX || source == Format.TXT) text else text
                XlsxBridge.export(csvToSheet(csvText))
            }
            Format.PDF -> textToPdf(text)
        }
        when (target) {
            Format.DOCX -> OfficeArchiveValidator.validate(output, OfficeArchiveValidator.Kind.DOCX)
            Format.ODT -> OfficeArchiveValidator.validate(output, OfficeArchiveValidator.Kind.ODT)
            Format.PPTX -> OfficeArchiveValidator.validate(output, OfficeArchiveValidator.Kind.PPTX)
            Format.XLSX -> OfficeArchiveValidator.validate(output, OfficeArchiveValidator.Kind.XLSX)
            else -> Unit
        }
        return Converted(output, target)
    }

    private fun rtfToText(rtf: String): String {
        require(rtf.trimStart().startsWith("{\\rtf")) { "Файл не является корректным RTF" }
        val out = StringBuilder(); var i = 0; var depth = 0
        while (i < rtf.length) {
            val c = rtf[i]
            when {
                c == '{' -> { depth++; i++ }
                c == '}' -> { depth--; i++ }
                c == '\\' && i + 1 < rtf.length -> {
                    val n = rtf[i + 1]
                    when {
                        n == '\\' || n == '{' || n == '}' -> { if (depth > 1) out.append(n); i += 2 }
                        n == '\'' && i + 3 < rtf.length -> { i += 4 }
                        n.isLetter() -> {
                            var j = i + 1; while (j < rtf.length && rtf[j].isLetter()) j++
                            val word = rtf.substring(i + 1, j)
                            if (j < rtf.length && rtf[j] == '-') j++
                            while (j < rtf.length && rtf[j].isDigit()) j++
                            if (j < rtf.length && rtf[j] == ' ') j++
                            if (depth > 1) when (word) { "par", "line" -> out.append('\n'); "tab" -> out.append('\t') }
                            i = j
                        }
                        else -> i += 2
                    }
                }
                depth > 1 -> { out.append(c); i++ }
                else -> i++
            }
        }
        return out.toString().trim()
    }

    private fun textToRtf(text: String): String {
        val escaped = buildString {
            text.forEach { c -> when (c) {
                '\\' -> append("\\\\")
                '{' -> append("\\{")
                '}' -> append("\\}")
                '\n' -> append("\\par\n")
                else -> if (c.code in 32..126) append(c) else append("\\u${c.code}?")
            } }
        }
        return "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Arial;}}\\f0\\fs22 $escaped}"
    }

    private fun textToHtml(text: String): String = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head><body><pre style="white-space:pre-wrap;font-family:sans-serif">${escapeHtml(text)}</pre></body></html>"""

    private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun csvToSheet(csv: String): Sheet {
        val lines = parseCsv(csv).take(200)
        val width = (lines.maxOfOrNull { it.size } ?: 1).coerceIn(1, 30)
        val sheet = Sheet(rows = maxOf(50, lines.size), cols = maxOf(10, width))
        lines.forEachIndexed { r, row -> row.take(30).forEachIndexed { c, value -> if (value.isNotEmpty()) sheet.set(r, c, Cell(value)) } }
        return sheet
    }

    private fun sheetToCsv(sheet: Sheet): String {
        val maxRow = (sheet.data.keys.maxOfOrNull { it.first } ?: 0).coerceAtMost(199)
        val maxCol = (sheet.data.keys.maxOfOrNull { it.second } ?: 0).coerceAtMost(29)
        return (0..maxRow).joinToString("\r\n") { r ->
            (0..maxCol).joinToString(",") { c -> csvEscape(sheet.get(r, c).value) }
        }
    }

    private fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                (ch == ',' || ch == ';' || ch == '\t') && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.setLength(0); rows.add(row); row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun textToPdf(text: String): ByteArray {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val pageWidth = 595; val pageHeight = 842; val margin = 40f; val lineHeight = 16f
        val lines = text.lines().flatMap { line ->
            if (line.isEmpty()) listOf("") else {
                val out = mutableListOf<String>(); var rest = line
                while (paint.measureText(rest) > pageWidth - margin * 2 && rest.isNotEmpty()) {
                    var cut = rest.length
                    while (cut > 1 && paint.measureText(rest.substring(0, cut)) > pageWidth - margin * 2) cut--
                    out.add(rest.substring(0, cut)); rest = rest.substring(cut)
                }
                out.add(rest); out
            }
        }
        var pageNumber = 1; var index = 0
        while (index < lines.size || (lines.isEmpty() && pageNumber == 1)) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var y = margin + lineHeight
            while (index < lines.size && y < pageHeight - margin) {
                page.canvas.drawText(lines[index].take(500), margin, y, paint); y += lineHeight; index++
            }
            doc.finishPage(page); pageNumber++
            if (lines.isEmpty()) break
        }
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }
}
