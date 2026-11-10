package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.office.DocxToHtml
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxToHtmlTest {

    @Test
    fun testDocxConversionWithHeadingBoldTableAndRtl() {
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            </Relationships>
        """.trimIndent()

        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Title"/></w:pPr>
                        <w:r><w:t>Letter Title</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:rPr><w:b/></w:rPr>
                            <w:t>Bold Introduction</w:t>
                        </w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr/></w:pPr>
                        <w:r><w:t>First bullet item</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr/></w:pPr>
                        <w:r><w:t>Second bullet item</w:t></w:r>
                    </w:p>
                    <w:tbl>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Col A</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Col B</w:t></w:r></w:p></w:tc>
                        </w:tr>
                    </w:tbl>
                    <w:p>
                        <w:pPr><w:bidi/></w:pPr>
                        <w:r><w:t>نص عربي تجريبي</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:drawing>
                                <a:blip r:embed="rId1"/>
                            </w:drawing>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val dummyPng = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/media/image1.png"))
            zos.write(dummyPng)
            zos.closeEntry()
        }

        val html = DocxToHtml.convert(ByteArrayInputStream(baos.toByteArray()))

        assertTrue("Should contain Title heading h1", html.contains("<h1>Letter Title</h1>"))
        assertTrue("Should contain bold text", html.contains("<b>Bold Introduction</b>"))
        assertTrue("Should contain bullet list", html.contains("<ul>") && html.contains("<li>First bullet item</li>"))
        assertTrue("Should contain table", html.contains("<table>") && html.contains("<td>Col A</td>"))
        assertTrue("Should contain RTL Arabic paragraph", html.contains("<p dir=\"rtl\">نص عربي تجريبي</p>"))
        assertTrue("Should contain base64 image", html.contains("<img src=\"data:image/png;base64,"))
    }
}
