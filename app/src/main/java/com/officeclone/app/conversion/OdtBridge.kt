package com.officeclone.app.conversion

import android.text.Html
import android.text.Spanned
import android.text.SpannableStringBuilder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Basic OpenDocument Text bridge. Text and paragraph boundaries are preserved; advanced styles/layout are not. */
object OdtBridge {
    fun importText(bytes: ByteArray): String {
        val entries = unzip(bytes)
        val xml = entries["content.xml"] ?: error("В ODT отсутствует content.xml")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val out = StringBuilder(); var inText = false; var inTable = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "table" -> { inTable = true; if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
                    "p", "h" -> { if (!inTable && out.isNotEmpty() && out.last() != '\n') out.append('\n'); inText = true }
                    "tab" -> if (inText) out.append('\t')
                    "line-break" -> if (inText) out.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) out.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "p", "h" -> inText = false
                    "table-cell" -> if (inTable && out.isNotEmpty()) out.append('\t')
                    "table-row" -> if (inTable && out.isNotEmpty()) { if (out.last() == '\t') out.setLength(out.length - 1); out.append('\n') }
                    "table" -> { inTable = false; if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
                }
            }
            event = parser.next()
        }
        return out.toString().trim()
    }

    fun exportText(text: String): ByteArray {
        val lines = text.split('\n')
        val blocks = StringBuilder(); var i = 0
        while (i < lines.size) {
            if (lines[i].contains('\t')) {
                blocks.append("<table:table table:name=\"Table${i + 1}\">")
                while (i < lines.size && lines[i].contains('\t')) {
                    blocks.append("<table:table-row>")
                    lines[i].split('\t').forEach { cell -> blocks.append("<table:table-cell office:value-type=\"string\"><text:p>").append(xmlEscape(cell)).append("</text:p></table:table-cell>") }
                    blocks.append("</table:table-row>"); i++
                }
                blocks.append("</table:table>")
            } else { blocks.append("<text:p>").append(xmlEscape(lines[i])).append("</text:p>"); i++ }
        }
        val content = """<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" office:version="1.2"><office:automatic-styles/><office:body><office:text>${blocks}</office:text></office:body></office:document-content>"""
        val manifest = """<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/></manifest:manifest>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mime = ODT_MIME.toByteArray(); zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = java.util.zip.CRC32().apply { update(mime) }.value }); zip.write(mime); zip.closeEntry()
            zip.putNextEntry(ZipEntry("content.xml")); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/manifest.xml")); zip.write(manifest.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        return out.toByteArray()
    }
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> var e = zip.nextEntry; while (e != null) { if (!e.isDirectory) { val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var n: Int; while (zip.read(buf).also { n = it } > 0) { out.write(buf, 0, n); require(out.size() <= 25 * 1024 * 1024) { "ODT-содержимое слишком велико" } }; result[e.name] = out.toByteArray() }; e = zip.nextEntry } }
        return result
    }
    private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private const val ODT_MIME = "application/vnd.oasis.opendocument.text"
}
