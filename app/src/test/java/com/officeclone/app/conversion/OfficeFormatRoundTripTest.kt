package com.officeclone.app.conversion

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class OfficeFormatRoundTripTest {
    @Test fun odtPreservesParagraphsAndTabularCells() {
        val source = "Заголовок\nИмя\tЦена\nТовар\t125"
        val bytes = OdtBridge.exportText(source)
        val report = OfficeArchiveValidator.validate(bytes, OfficeArchiveValidator.Kind.ODT)
        assertTrue(report.entries.contains("content.xml"))
        val text = OdtBridge.importText(bytes)
        assertTrue(text.contains("Заголовок"))
        assertTrue(text.contains("Имя"))
        assertTrue(text.contains("Цена"))
        assertTrue(text.contains("125"))
    }

    @Test fun pptxPreservesShapeGeometryTextAndBasicStyle() {
        val slide = PptxBridge.StructuredSlide(listOf(
            PptxBridge.Shape("Заголовок", "roundRect", 100L, 200L, 3000000L, 600000L, "4472C4", "OfficeClone", PptxBridge.TextStyle(bold = true, fontSizePt = 24, color = "FFFFFF")),
            PptxBridge.Shape("Прямоугольник", "rect", 400L, 500L, 700000L, 800000L, "FF0000", "Текст")
        ))
        val bytes = PptxBridge.exportSlides(listOf(slide))
        val report = OfficeArchiveValidator.validate(bytes, OfficeArchiveValidator.Kind.PPTX)
        assertTrue(report.entries.contains("ppt/slides/slide1.xml"))
        val imported = PptxBridge.importSlides(bytes)
        assertEquals(1, imported.size)
        assertEquals(2, imported[0].shapes.size)
        assertEquals(100L, imported[0].shapes[0].x)
        assertEquals(3000000L, imported[0].shapes[0].width)
        assertEquals("OfficeClone", imported[0].shapes[0].text)
        assertEquals("4472C4", imported[0].shapes[0].fill)
        assertTrue(imported[0].shapes[0].style.bold)
        assertEquals(24, imported[0].shapes[0].style.fontSizePt)
    }

    @Test fun archiveValidatorRejectsNonZip() {
        try {
            OfficeArchiveValidator.validate("not a zip".toByteArray(), OfficeArchiveValidator.Kind.ODT)
            fail("Expected malformed archive rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun pptxPackageContainsWellFormedXmlParts() {
        val bytes = PptxBridge.exportSlides(listOf(PptxBridge.StructuredSlide(listOf(PptxBridge.Shape("shape", "rect", 1, 2, 3, 4, null, "A & B < C")))))
        val xmlEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val e = zip.nextEntry ?: break; if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) xmlEntries.add(zip.readBytes().toString(Charsets.UTF_8)); zip.closeEntry() } }
        assertTrue(xmlEntries.isNotEmpty())
        assertTrue(xmlEntries.any { it.contains("A &amp; B &lt; C") })
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        xmlEntries.forEach { xml -> factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml))) }
        assertFalse(xmlEntries.any { it.contains("<" + "\$shapes>") })
    }
}
