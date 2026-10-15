package com.officeclone.editor.sheet

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Lightweight XLSX bridge with multi-worksheet import/export, formulas, shared/inline strings,
 * numeric and boolean values, plus archive size limits for safer processing on mobile. */
object XlsxBridge {
    fun export(sheet: Sheet): ByteArray = export(Workbook(listOf(WorkbookSheet("Лист1", sheet))))

    fun export(workbook: Workbook): ByteArray {
        val safeSheets = workbook.sheets.take(MAX_SHEETS).ifEmpty { listOf(WorkbookSheet("Лист1", Sheet())) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val overrides = safeSheets.indices.joinToString("\n") { i ->
                "<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            }
            put(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  $overrides
                </Types>
            """.trimIndent())
            put(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent())
            val sheetTags = safeSheets.mapIndexed { i, item ->
                "<sheet name=\"${escape(item.name.take(31))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
            }.joinToString("")
            put(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>$sheetTags</sheets>
                </workbook>
            """.trimIndent())
            val relTags = safeSheets.indices.joinToString("") { i ->
                "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>"
            }
            put(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$relTags</Relationships>
            """.trimIndent())
            safeSheets.forEachIndexed { index, item ->
                val sheet = item.sheet
                val maxRow = maxOf(sheet.rows, (sheet.data.keys.maxOfOrNull { it.first + 1 } ?: 1)).coerceAtMost(MAX_ROWS)
                val maxCol = maxOf(sheet.cols, (sheet.data.keys.maxOfOrNull { it.second + 1 } ?: 1)).coerceAtMost(MAX_COLS)
                val calculated = FormulaEngine.evaluateAll(sheet)
                val xml = StringBuilder()
                xml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                for (r in 0 until maxRow) {
                    val rowCells = (0 until maxCol).mapNotNull { c -> sheet.data[r to c]?.takeIf { it.value.isNotEmpty() }?.let { c to it } }
                    if (rowCells.isEmpty()) continue
                    xml.append("<row r=\"").append(r + 1).append("\">")
                    for ((c, cell) in rowCells) {
                        val ref = sheet.cellRef(r, c)
                        val raw = cell.value
                        if (raw.startsWith("=")) {
                            xml.append("<c r=\"").append(ref).append("\"><f>").append(escape(raw.drop(1))).append("</f>")
                            calculated[r to c]?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { xml.append("<v>").append(it).append("</v>") }
                            xml.append("</c>")
                        } else {
                            val numeric = raw.replace(',', '.').toDoubleOrNull()
                            if (numeric != null && numeric.isFinite()) {
                                xml.append("<c r=\"").append(ref).append("\"><v>").append(numeric).append("</v></c>")
                            } else {
                                xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                                    .append(escape(raw)).append("</t></is></c>")
                            }
                        }
                    }
                    xml.append("</row>")
                }
                xml.append("</sheetData></worksheet>")
                put(zip, "xl/worksheets/sheet${index + 1}.xml", xml.toString())
            }
        }
        return output.toByteArray()
    }

    fun import(bytes: ByteArray): Sheet = importWorkbook(bytes).sheets.first().sheet

    fun importWorkbook(bytes: ByteArray): Workbook {
        require(bytes.size <= MAX_FILE_BYTES) { "Файл слишком большой (максимум 25 МБ)" }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    count++
                    require(count <= MAX_ZIP_ENTRIES) { "Слишком много элементов в XLSX" }
                    val data = zip.readBytes()
                    total += data.size
                    require(total <= MAX_UNCOMPRESSED_BYTES) { "Распакованный XLSX слишком большой" }
                    entries[entry.name] = data
                }
                zip.closeEntry()
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val descriptions = resolveWorksheets(entries)
        val sheets = descriptions.take(MAX_SHEETS).mapIndexed { index, description ->
            val data = entries[description.second] ?: throw IllegalArgumentException("Не найден лист ${description.first}")
            val cells = parseWorksheet(data, shared)
            val maxRow = maxOf(50, (cells.keys.maxOfOrNull { it.first + 1 } ?: 1)).coerceAtMost(MAX_ROWS)
            val maxCol = maxOf(10, (cells.keys.maxOfOrNull { it.second + 1 } ?: 1)).coerceAtMost(MAX_COLS)
            WorkbookSheet(description.first.ifBlank { "Лист${index + 1}" }.take(31), Sheet(rows = maxRow, cols = maxCol, data = cells))
        }
        return Workbook(sheets.ifEmpty { listOf(WorkbookSheet("Лист1", Sheet())) })
    }

    private fun resolveWorksheets(entries: Map<String, ByteArray>): List<Pair<String, String>> {
        val workbook = entries["xl/workbook.xml"] ?: return listOf("Лист1" to resolveFirstWorksheet(entries))
        val relationships = mutableMapOf<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { rels ->
            val p = parser(rels)
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (id != null && target != null) relationships[id] = normalizeWorkbookTarget(target)
                }
                p.next()
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        val p = parser(workbook)
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name") ?: "Лист${result.size + 1}"
                val id = p.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: p.getAttributeValue(null, "r:id")
                val target = id?.let { relationships[it] }
                if (target != null && target.startsWith("xl/worksheets/") && !target.contains("..")) result += name to target
            }
            p.next()
        }
        return result.ifEmpty { listOf("Лист1" to resolveFirstWorksheet(entries)) }
    }

    private fun normalizeWorkbookTarget(target: String): String {
        val path = target.removePrefix("/")
        return if (path.startsWith("xl/")) path else "xl/$path"
    }

    private fun resolveFirstWorksheet(entries: Map<String, ByteArray>): String {
        val rels = entries["xl/_rels/workbook.xml.rels"] ?: return "xl/worksheets/sheet1.xml"
        val workbook = entries["xl/workbook.xml"]
        val firstId = workbook?.let { xml ->
            var id: String? = null
            parser(xml).apply {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && name == "sheet") {
                        id = getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            ?: getAttributeValue(null, "r:id")
                        break
                    }
                    next()
                }
            }
            id
        } ?: "rId1"
        var target: String? = null
        parser(rels).apply {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && name == "Relationship" && getAttributeValue(null, "Id") == firstId) {
                    target = getAttributeValue(null, "Target")
                    break
                }
                next()
            }
        }
        val path = target ?: "worksheets/sheet1.xml"
        return if (path.startsWith("/")) path.removePrefix("/") else if (path.startsWith("xl/")) path else "xl/$path"
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val p = parser(bytes)
        var inItem = false
        var value = StringBuilder()
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inItem = true; value = StringBuilder() }
                    "t" -> if (inItem) value.append(readText(p))
                }
                XmlPullParser.END_TAG -> if (p.name == "si" && inItem) { result += value.toString(); inItem = false }
            }
            p.next()
        }
        return result
    }

    private fun parseWorksheet(bytes: ByteArray, shared: List<String>): MutableMap<Pair<Int, Int>, Cell> {
        val cells = mutableMapOf<Pair<Int, Int>, Cell>()
        val p = parser(bytes)
        var ref: String? = null
        var type: String? = null
        var formula: String? = null
        var value: String? = null
        var inline = StringBuilder()
        var inCell = false
        var inInline = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "c" -> { inCell = true; ref = p.getAttributeValue(null, "r"); type = p.getAttributeValue(null, "t"); formula = null; value = null; inline = StringBuilder() }
                    "f" -> if (inCell) formula = readText(p)
                    "v" -> if (inCell) value = readText(p)
                    "is" -> if (inCell) inInline = true
                    "t" -> if (inCell && inInline) inline.append(readText(p))
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "is" -> inInline = false
                    "c" -> {
                        val coord = ref?.let(::parseCellRef)
                        if (coord != null && coord.first in 0 until MAX_ROWS && coord.second in 0 until MAX_COLS) {
                            val raw = when {
                                formula != null -> "=$formula"
                                type == "s" -> value?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                                type == "inlineStr" -> inline.toString()
                                type == "b" -> if (value == "1") "TRUE" else "FALSE"
                                else -> value.orEmpty()
                            }
                            cells[coord] = Cell(raw, raw.toDoubleOrNull())
                        }
                        inCell = false
                    }
                }
            }
            p.next()
        }
        return cells
    }

    private fun readText(parser: XmlPullParser): String {
        val text = if (parser.next() == XmlPullParser.TEXT) parser.text else ""
        return text
    }

    private fun parseCellRef(ref: String): Pair<Int, Int>? {
        val match = Regex("^\\$?([A-Za-z]+)\\$?([0-9]+)$").matchEntire(ref) ?: return null
        var col = 0
        for (ch in match.groupValues[1].uppercase()) col = col * 26 + (ch - 'A' + 1)
        val row = match.groupValues[2].toIntOrNull() ?: return null
        if (row < 1 || col < 1) return null
        return row - 1 to col - 1
    }

    private fun parser(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name())
    }

    private fun put(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private const val MAX_ROWS = 200
    private const val MAX_COLS = 30
    private const val MAX_SHEETS = 20
    private const val MAX_FILE_BYTES = 25 * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 500
}
