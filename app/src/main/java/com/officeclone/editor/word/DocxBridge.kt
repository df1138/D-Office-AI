package com.officeclone.editor.word

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Lightweight DOCX import/export bridge. Preserves paragraphs and bold/italic/underline runs. */
object DocxBridge {
    fun save(rich: RichText, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""")
            put(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")
            put(zip, "word/document.xml", documentXml(rich))
        }
    }

    fun load(input: InputStream): RichText {
        var documentXml: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readBytes()
                    break
                }
                zip.closeEntry()
            }
        }
        val bytes = documentXml ?: error("В DOCX отсутствует word/document.xml")
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val result = RichText()
        var inParagraph = false
        var inRun = false
        var runStart = 0
        var bold = false
        var italic = false
        var underline = false
        var paragraphCount = 0
        var inTable = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "tbl" -> { inTable = true; if (result.ssb.isNotEmpty() && result.ssb.last() != '\n') result.ssb.append('\n') }
                    "p" -> {
                        if (!inTable && paragraphCount > 0) result.ssb.append('\n')
                        if (!inTable) paragraphCount++
                        inParagraph = true
                    }
                    "r" -> {
                        inRun = true
                        runStart = result.ssb.length
                        bold = false; italic = false; underline = false
                    }
                    "b" -> if (inRun) bold = parser.attributeByLocalName("val") !in setOf("0", "false", "off")
                    "i" -> if (inRun) italic = parser.attributeByLocalName("val") !in setOf("0", "false", "off")
                    "u" -> if (inRun) underline = parser.attributeByLocalName("val") !in setOf("none", "0", "false")
                    "tab" -> if (inParagraph && inRun) result.ssb.append('\t')
                    "br" -> if (inParagraph && inRun) result.ssb.append('\n')
                }
                XmlPullParser.TEXT -> if (inParagraph && inRun) result.ssb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "r" -> {
                        val end = result.ssb.length
                        if (end > runStart) {
                            if (bold) result.ssb.setSpan(StyleSpan(Typeface.BOLD), runStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (italic) result.ssb.setSpan(StyleSpan(Typeface.ITALIC), runStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (underline) result.ssb.setSpan(UnderlineSpan(), runStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        inRun = false
                    }
                    "p" -> inParagraph = false
                    "tc" -> if (inTable && result.ssb.isNotEmpty()) result.ssb.append('\t')
                    "tr" -> if (inTable && result.ssb.isNotEmpty()) { if (result.ssb.last() == '\t') result.ssb.setLength(result.ssb.length - 1); result.ssb.append('\n') }
                    "tbl" -> { inTable = false; if (result.ssb.isNotEmpty() && result.ssb.last() != '\n') result.ssb.append('\n') }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun documentXml(rich: RichText): String {
        val text = rich.ssb
        val fullText = text.toString()
        val xml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        var paragraphStart = 0
        val lines = fullText.split('\n')
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            if (line.contains('\t')) {
                val rows = mutableListOf<List<String>>()
                while (lineIndex < lines.size && lines[lineIndex].contains('\t')) { rows.add(lines[lineIndex].split('\t')); lineIndex++ }
                xml.append("<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\"/><w:left w:val=\"single\"/><w:bottom w:val=\"single\"/><w:right w:val=\"single\"/><w:insideH w:val=\"single\"/><w:insideV w:val=\"single\"/></w:tblBorders></w:tblPr>")
                rows.forEach { row -> xml.append("<w:tr>"); row.forEach { cell -> xml.append("<w:tc><w:tcPr/><w:p><w:r><w:t xml:space=\"preserve\">").append(escapeXml(cell)).append("</w:t></w:r></w:p></w:tc>") }; xml.append("</w:tr>") }
                xml.append("</w:tbl>"); paragraphStart = lines.take(lineIndex).sumOf { it.length + 1 }; continue
            }
            xml.append("<w:p>")
            val start = paragraphStart
            val end = start + line.length
            if (line.isNotEmpty()) {
                var runStart = start
                var runBold = isBoldAt(text, start); var runItalic = isItalicAt(text, start); var runUnderline = isUnderlineAt(text, start)
                var i = start + 1
                while (i <= end) {
                    val nextBold = if (i < end) isBoldAt(text, i) else !runBold
                    val nextItalic = if (i < end) isItalicAt(text, i) else !runItalic
                    val nextUnderline = if (i < end) isUnderlineAt(text, i) else !runUnderline
                    if (i == end || nextBold != runBold || nextItalic != runItalic || nextUnderline != runUnderline) {
                        xml.append("<w:r><w:rPr>"); if (runBold) xml.append("<w:b/>"); if (runItalic) xml.append("<w:i/>"); if (runUnderline) xml.append("<w:u w:val=\"single\"/>")
                        xml.append("</w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(fullText.substring(runStart, i))).append("</w:t></w:r>")
                        if (i < end) { runStart = i; runBold = nextBold; runItalic = nextItalic; runUnderline = nextUnderline }
                    }
                    i++
                }
            }
            xml.append("</w:p>"); paragraphStart = end + 1; lineIndex++
        }
        xml.append("<w:sectPr/></w:body></w:document>")
        return xml.toString()
    }

    private fun isBoldAt(s: Spanned, i: Int) = s.getSpans(i, i + 1, StyleSpan::class.java).any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
    private fun isItalicAt(s: Spanned, i: Int) = s.getSpans(i, i + 1, StyleSpan::class.java).any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
    private fun isUnderlineAt(s: Spanned, i: Int) = s.getSpans(i, i + 1, UnderlineSpan::class.java).isNotEmpty()

    private fun XmlPullParser.attributeByLocalName(localName: String): String? {
        for (i in 0 until attributeCount) if (getAttributeName(i).substringAfter(':') == localName) return getAttributeValue(i)
        return null
    }

    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun put(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
