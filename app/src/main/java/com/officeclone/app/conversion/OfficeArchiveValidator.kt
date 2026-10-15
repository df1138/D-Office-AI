package com.officeclone.app.conversion

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Lightweight safety/structure validation for generated OpenDocument and OOXML packages. */
object OfficeArchiveValidator {
    enum class Kind { DOCX, ODT, PPTX, XLSX }
    data class Report(val entries: Set<String>, val uncompressedBytes: Long)

    fun validate(bytes: ByteArray, kind: Kind, maxBytes: Long = 50L * 1024 * 1024): Report {
        require(bytes.isNotEmpty()) { "Пустой файл" }
        val entries = linkedSetOf<String>()
        var total = 0L
        val xmlParts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!name.startsWith("/") && !name.split('/').contains("..")) { "Небезопасный путь в архиве: $name" }
                require(entries.add(name)) { "Повторяющаяся запись в архиве: $name" }
                if (!entry.isDirectory) {
                    val buffer = ByteArray(8192)
                    val content = java.io.ByteArrayOutputStream()
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= maxBytes) { "Распакованное содержимое превышает лимит $maxBytes байт" }
                        content.write(buffer, 0, n)
                    }
                    if (name.endsWith(".xml", true) || name.endsWith(".rels", true)) xmlParts[name] = content.toByteArray()
                }
                zip.closeEntry()
            }
        }
        xmlParts.forEach { (name, content) ->
            try {
                val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
                parser.setInput(java.io.ByteArrayInputStream(content), "UTF-8")
                while (parser.next() != XmlPullParser.END_DOCUMENT) { /* force well-formedness check */ }
            } catch (e: Exception) {
                throw IllegalArgumentException("Некорректный XML в офисном архиве: $name", e)
            }
        }
        val required = when (kind) {
            Kind.DOCX -> setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")
            Kind.ODT -> setOf("mimetype", "content.xml", "META-INF/manifest.xml")
            Kind.PPTX -> setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/slides/slide1.xml")
            Kind.XLSX -> setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml")
        }
        require(entries.containsAll(required)) { "Некорректный ${kind.name}: отсутствуют ${required - entries}" }
        return Report(entries, total)
    }
}
