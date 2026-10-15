package com.officeclone.app.conversion

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** OOXML PowerPoint text bridge. It creates valid, editable text slides; it does not preserve arbitrary themes/media/animations. */
object PptxBridge {
    fun importText(bytes: ByteArray): String {
        val files = unzip(bytes)
        require(files.containsKey("ppt/presentation.xml")) { "Не найден ppt/presentation.xml — файл не похож на PPTX" }
        val slideNames = files.keys.filter { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }
            .sortedBy { it.substringAfter("slide").substringBefore(".").toIntOrNull() ?: Int.MAX_VALUE }
        require(slideNames.isNotEmpty()) { "В презентации не найдены слайды" }
        return slideNames.joinToString("\n\n--- СЛАЙД ---\n\n") { name ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(ByteArrayInputStream(files.getValue(name)), "UTF-8")
            val out = StringBuilder(); var inText = false; var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t" && parser.namespace == "http://schemas.openxmlformats.org/drawingml/2006/main") inText = true
                    XmlPullParser.TEXT -> if (inText) out.append(parser.text)
                    XmlPullParser.END_TAG -> if (parser.name == "t") { inText = false; out.append('\n') }
                }
                event = parser.next()
            }
            out.toString().trim()
        }
    }

    data class TextStyle(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val fontSizePt: Int = 18, val color: String = "000000")
    data class Shape(val name: String, val preset: String, val x: Long, val y: Long, val width: Long, val height: Long, val fill: String?, val text: String, val style: TextStyle = TextStyle())
    data class StructuredSlide(val shapes: List<Shape>)

    /** Imports editable shapes with geometry and the first text run's basic style. Coordinates are OOXML EMUs. */
    fun importSlides(bytes: ByteArray): List<StructuredSlide> {
        val files = unzip(bytes)
        require(files.containsKey("ppt/presentation.xml")) { "Не найден ppt/presentation.xml" }
        val names = files.keys.filter { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }
            .sortedBy { it.substringAfter("slide").substringBefore(".").toIntOrNull() ?: Int.MAX_VALUE }
        return names.map { name ->
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(ByteArrayInputStream(files.getValue(name)), "UTF-8")
            val shapes = mutableListOf<Shape>(); var current: MutableMap<String, Any?>? = null
            var inShape = false; var inText = false; var text = StringBuilder(); var firstRunStyle = TextStyle()
            var bold = false; var italic = false; var underline = false; var fontSize = 18; var color = "000000"
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = parser.name.substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> when (local) {
                        "sp", "pic", "graphicFrame", "cxnSp" -> { current = mutableMapOf("name" to local, "preset" to "rect", "x" to 0L, "y" to 0L, "width" to 0L, "height" to 0L, "fill" to null); text = StringBuilder(); firstRunStyle = TextStyle(); inShape = true }
                        "cNvPr" -> if (inShape) current?.set("name", parser.getAttributeValue(null, "name") ?: "Shape")
                        "off" -> if (inShape) { current?.set("x", parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L); current?.set("y", parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L) }
                        "ext" -> if (inShape) { current?.set("width", parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L); current?.set("height", parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L) }
                        "prstGeom" -> if (inShape) current?.set("preset", parser.getAttributeValue(null, "prst") ?: "rect")
                        "srgbClr" -> if (inShape && current?.get("fill") == null) current?.set("fill", parser.getAttributeValue(null, "val")) else if (inShape) color = parser.getAttributeValue(null, "val") ?: color
                        "solidFill" -> Unit
                        "t" -> inText = true
                        "rPr" -> { bold = parser.getAttributeValue(null, "b") == "1"; italic = parser.getAttributeValue(null, "i") == "1"; underline = parser.getAttributeValue(null, "u") == "sng"; fontSize = (parser.getAttributeValue(null, "sz")?.toIntOrNull()?.div(100)).orDefault(18) }
                    }
                    XmlPullParser.TEXT -> if (inShape && inText) text.append(parser.text)
                    XmlPullParser.END_TAG -> when (local) {
                        "t" -> { inText = false; text.append("") }
                        "p" -> if (inShape && text.isNotEmpty() && !text.endsWith("\n")) text.append('\n')
                        "sp", "pic", "graphicFrame", "cxnSp" -> {
                            val m = current
                            if (m != null) shapes.add(Shape(m["name"] as? String ?: "Shape", m["preset"] as? String ?: "rect", m["x"] as Long, m["y"] as Long, m["width"] as Long, m["height"] as Long, m["fill"] as? String, text.toString().trimEnd(), TextStyle(bold, italic, underline, fontSize, color)))
                            current = null; inShape = false; inText = false
                        }
                    }
                }
                event = parser.next()
            }
            StructuredSlide(shapes)
        }
    }

    /** Exports geometry, preset shapes, solid fills, and first-run text style to an editable PPTX. */
    fun exportSlides(slides: List<StructuredSlide>): ByteArray {
        require(slides.isNotEmpty()) { "Добавьте хотя бы один слайд" }
        require(slides.size <= 100) { "Максимум 100 слайдов" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/><Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/><Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>${slides.indices.joinToString("") { "<Override PartName=\"/ppt/slides/slide${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" }}</Types>""")
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>""")
            entry(zip, "ppt/presentation.xml", """<?xml version="1.0" encoding="UTF-8"?><p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>${slides.indices.joinToString("") { "<p:sldId id=\"${256+it}\" r:id=\"rId${it+2}\"/>" }}</p:sldIdLst><p:sldSz cx="9144000" cy="5143500" type="wide"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""")
            entry(zip, "ppt/_rels/presentation.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>${slides.indices.joinToString("") { "<Relationship Id=\"rId${it+2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${it+1}.xml\"/>" }}</Relationships>""")
            entry(zip, "ppt/slideMasters/slideMaster1.xml", """<?xml version="1.0" encoding="UTF-8"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" tx1="dk1" tx2="dk2" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="1" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles/></p:sldMaster>""")
            entry(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>""")
            entry(zip, "ppt/slideLayouts/slideLayout1.xml", """<?xml version="1.0" encoding="UTF-8"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>""")
            entry(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>""")
            entry(zip, "ppt/theme/theme1.xml", """<?xml version="1.0" encoding="UTF-8"?><a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="OfficeClone"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F1F1F"/></a:dk2><a:lt2><a:srgbClr val="EEEEEE"/></a:lt2><a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2><a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Arial"/></a:majorFont><a:minorFont><a:latin typeface="Arial"/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>""")
            slides.forEachIndexed { i, slide ->
                val shapes = slide.shapes.mapIndexed { j, sh ->
                    val geom = sh.preset.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9]{0,40}")) } ?: "rect"
                    val fill = sh.fill?.filter { it.isLetterOrDigit() }?.takeIf { it.length == 6 }?.let { "<a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill>" } ?: "<a:noFill/>"
                    val style = sh.style
                    val rpr = "<a:rPr lang=\"ru-RU\" sz=\"${(style.fontSizePt.coerceIn(6,96))*100}\"${if(style.bold) " b=\"1\"" else ""}${if(style.italic) " i=\"1\"" else ""}${if(style.underline) " u=\"sng\"" else ""}><a:solidFill><a:srgbClr val=\"${style.color.filter { it.isLetterOrDigit() }.takeIf { it.length==6 } ?: "000000"}\"/></a:solidFill></a:rPr>"
                    val paras = sh.text.split('\n').joinToString("") { "<a:p><a:r>$rpr<a:t xml:space=\"preserve\">${escape(it)}</a:t></a:r><a:endParaRPr lang=\"ru-RU\"/></a:p>" }
                    """<p:sp><p:nvSpPr><p:cNvPr id="${j+2}" name="${escape(sh.name)}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="${sh.x.coerceAtLeast(0)}" y="${sh.y.coerceAtLeast(0)}"/><a:ext cx="${sh.width.coerceAtLeast(1)}" cy="${sh.height.coerceAtLeast(1)}"/></a:xfrm><a:prstGeom prst="$geom"><a:avLst/></a:prstGeom>$fill</p:spPr><p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>$paras</p:txBody></p:sp>"""
                }.joinToString("")
                entry(zip, "ppt/slides/slide${i+1}.xml", """<?xml version="1.0" encoding="UTF-8"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>${shapes}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""")
                entry(zip, "ppt/slides/_rels/slide${i+1}.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>""")
            }
        }
        return out.toByteArray()
    }
    private fun Int?.orDefault(default: Int) = this ?: default

    fun exportText(text: String): ByteArray {
        val slides = text.split(Regex("\\n\\s*---\\s*СЛАЙД\\s*---\\s*\\n", RegexOption.IGNORE_CASE)).ifEmpty { listOf(text) }.take(100)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/><Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/><Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>${slides.indices.joinToString("") { "<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" }}</Types>""")
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>""")
            entry(zip, "ppt/presentation.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>${slides.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 2}\"/>" }}</p:sldIdLst><p:sldSz cx="9144000" cy="5143500" type="wide"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""")
            entry(zip, "ppt/_rels/presentation.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>${slides.indices.joinToString("") { "<Relationship Id=\"rId${it + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${it + 1}.xml\"/>" }}</Relationships>""")
            entry(zip, "ppt/slideMasters/slideMaster1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" tx1="dk1" tx2="dk2" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="1" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles/></p:sldMaster>""")
            entry(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>""")
            entry(zip, "ppt/slideLayouts/slideLayout1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>""")
            entry(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>""")
            entry(zip, "ppt/theme/theme1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="OfficeClone"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F1F1F"/></a:dk2><a:lt2><a:srgbClr val="EEEEEE"/></a:lt2><a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2><a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Arial"/></a:majorFont><a:minorFont><a:latin typeface="Arial"/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>""")
            slides.forEachIndexed { i, slide ->
                val paragraphs = slide.lines().ifEmpty { listOf("") }.joinToString("") { line -> "<a:p><a:r><a:rPr lang=\"ru-RU\" dirty=\"0\"/><a:t xml:space=\"preserve\">${escape(line)}</a:t></a:r><a:endParaRPr lang=\"ru-RU\"/></a:p>" }
                val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr><p:sp><p:nvSpPr><p:cNvPr id="2" name="Text"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="457200" y="457200"/><a:ext cx="8229600" cy="4236720"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>$paragraphs</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
                entry(zip, "ppt/slides/slide${i + 1}.xml", xml)
                entry(zip, "ppt/slides/_rels/slide${i + 1}.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>""")
            }
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> var e = z.nextEntry; while (e != null) { if (!e.isDirectory) { val out = ByteArrayOutputStream(); val b = ByteArray(8192); var n: Int; while (z.read(b).also { n = it } > 0) { out.write(b, 0, n); require(out.size() <= 25 * 1024 * 1024) { "PPTX слишком велик" } }; map[e.name] = out.toByteArray() }; e = z.nextEntry } }
        return map
    }
    private fun entry(zip: ZipOutputStream, name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
