package com.syedmsidsm.abdulsscanner.domain.office

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object DocxToHtml {

    private const val CSS_HEADER = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <style>
          @page { size: A4; margin: 20mm; }
          body { font-family: sans-serif; font-size: 11pt; line-height: 1.5; color: #111; }
          p { margin: 0 0 8pt 0; }
          h1 { font-size: 22pt; margin: 12pt 0 6pt 0; }
          h2 { font-size: 18pt; margin: 10pt 0 5pt 0; }
          h3 { font-size: 15pt; margin: 8pt 0 4pt 0; }
          h4 { font-size: 13pt; margin: 6pt 0 3pt 0; }
          h5 { font-size: 12pt; margin: 4pt 0 2pt 0; }
          h6 { font-size: 11pt; margin: 4pt 0 2pt 0; font-weight: bold; }
          table { border-collapse: collapse; width: 100%; margin: 8pt 0; }
          td, th { border: 1px solid #777; padding: 6pt 8pt; vertical-align: top; }
          ul, ol { margin: 0 0 8pt 20pt; padding: 0; }
          li { margin-bottom: 4pt; }
          img { max-width: 100%; height: auto; display: block; margin: 8pt auto; }
        </style>
        </head>
        <body>
    """

    private const val CSS_FOOTER = """
        </body>
        </html>
    """

    fun convert(docxInputStream: InputStream): String {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(docxInputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipEntries[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val relsMap = parseRelationships(zipEntries["word/_rels/document.xml.rels"])
        val documentBytes = zipEntries["word/document.xml"] ?: return fallbackHtml("No document body found")
        val bodyHtml = parseDocumentXml(documentBytes, relsMap, zipEntries)

        return "$CSS_HEADER$bodyHtml$CSS_FOOTER"
    }

    private fun parseRelationships(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val rels = mutableMapOf<String, String>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(relsBytes))
            val list = doc.getElementsByTagName("Relationship")
            for (i in 0 until list.length) {
                val elem = list.item(i) as? Element ?: continue
                val id = elem.getAttribute("Id")
                val target = elem.getAttribute("Target")
                if (id.isNotEmpty() && target.isNotEmpty()) {
                    rels[id] = target
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return rels
    }

    private fun parseDocumentXml(
        docBytes: ByteArray,
        relsMap: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): String {
        val out = StringBuilder()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(docBytes))
            val bodyList = doc.getElementsByTagNameNS("*", "body")
            val body = if (bodyList.length > 0) bodyList.item(0) as? Element else doc.documentElement
            if (body == null) return ""

            var inList = false

            fun closeListIfOpen() {
                if (inList) {
                    out.append("</ul>\n")
                    inList = false
                }
            }

            var child = body.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val elem = child as Element
                    val localName = elem.localName ?: elem.nodeName.substringAfterLast(':')

                    when (localName) {
                        "p" -> {
                            val paragraphHtml = parseParagraph(elem, relsMap, zipEntries)
                            if (paragraphHtml != null) {
                                if (paragraphHtml.isListItem) {
                                    val listDir = if (paragraphHtml.isRtl) " dir=\"rtl\"" else ""
                                    if (!inList) {
                                        out.append("<ul$listDir>\n")
                                        inList = true
                                    }
                                    out.append("  <li$listDir>${paragraphHtml.content}</li>\n")
                                } else {
                                    closeListIfOpen()
                                    if (paragraphHtml.headingLevel in 1..6) {
                                        val hDir = if (paragraphHtml.isRtl) " dir=\"rtl\"" else ""
                                        out.append("<h${paragraphHtml.headingLevel}$hDir>${paragraphHtml.content}</h${paragraphHtml.headingLevel}>\n")
                                    } else {
                                        val pDir = if (paragraphHtml.isRtl) " dir=\"rtl\"" else " dir=\"auto\""
                                        out.append("<p$pDir>${paragraphHtml.content}</p>\n")
                                    }
                                }
                            }
                        }
                        "tbl" -> {
                            closeListIfOpen()
                            out.append(parseTable(elem, relsMap, zipEntries))
                        }
                    }
                }
                child = child.nextSibling
            }
            closeListIfOpen()
        } catch (e: Exception) {
            out.append("<p>Error parsing document: ${escapeHtml(e.message ?: "")}</p>")
        }
        return out.toString()
    }

    private data class ParagraphResult(
        val content: String,
        val isListItem: Boolean,
        val headingLevel: Int,
        val isRtl: Boolean
    )

    private fun parseParagraph(
        pElem: Element,
        relsMap: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): ParagraphResult? {
        var isListItem = false
        var headingLevel = 0
        var isRtl = false

        // Check pPr
        var pChild = pElem.firstChild
        while (pChild != null) {
            if (pChild.nodeType == Node.ELEMENT_NODE) {
                val elem = pChild as Element
                val name = elem.localName ?: elem.nodeName.substringAfterLast(':')
                when (name) {
                    "pPr" -> {
                        var prChild = elem.firstChild
                        while (prChild != null) {
                            if (prChild.nodeType == Node.ELEMENT_NODE) {
                                val prElem = prChild as Element
                                val prName = prElem.localName ?: prElem.nodeName.substringAfterLast(':')
                                when (prName) {
                                    "pStyle" -> {
                                        val styleVal = prElem.getAttributeNS("*", "val").ifEmpty { prElem.getAttribute("w:val") }.ifEmpty { prElem.getAttribute("val") }
                                        if (styleVal.equals("Title", ignoreCase = true) || styleVal.equals("Heading1", ignoreCase = true)) headingLevel = 1
                                        else if (styleVal.equals("Heading2", ignoreCase = true)) headingLevel = 2
                                        else if (styleVal.equals("Heading3", ignoreCase = true)) headingLevel = 3
                                        else if (styleVal.equals("Heading4", ignoreCase = true)) headingLevel = 4
                                        else if (styleVal.equals("Heading5", ignoreCase = true)) headingLevel = 5
                                        else if (styleVal.equals("Heading6", ignoreCase = true)) headingLevel = 6
                                    }
                                    "numPr" -> isListItem = true
                                    "bidi", "rtl" -> isRtl = true
                                }
                            }
                            prChild = prChild.nextSibling
                        }
                    }
                }
            }
            pChild = pChild.nextSibling
        }

        val content = StringBuilder()

        pChild = pElem.firstChild
        while (pChild != null) {
            if (pChild.nodeType == Node.ELEMENT_NODE) {
                val elem = pChild as Element
                val name = elem.localName ?: elem.nodeName.substringAfterLast(':')
                when (name) {
                    "r" -> {
                        content.append(parseRun(elem, relsMap, zipEntries))
                    }
                    "drawing" -> {
                        content.append(parseDrawing(elem, relsMap, zipEntries))
                    }
                }
            }
            pChild = pChild.nextSibling
        }

        val trimmed = content.toString().trim()
        if (trimmed.isEmpty()) return null

        return ParagraphResult(
            content = trimmed,
            isListItem = isListItem,
            headingLevel = headingLevel,
            isRtl = isRtl
        )
    }

    private fun parseRun(
        rElem: Element,
        relsMap: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): String {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var fontSizePt: Float? = null
        var isRtl = false

        val sb = StringBuilder()
        var child = rElem.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val elem = child as Element
                val name = elem.localName ?: elem.nodeName.substringAfterLast(':')
                when (name) {
                    "rPr" -> {
                        var prChild = elem.firstChild
                        while (prChild != null) {
                            if (prChild.nodeType == Node.ELEMENT_NODE) {
                                val prElem = prChild as Element
                                val prName = prElem.localName ?: prElem.nodeName.substringAfterLast(':')
                                when (prName) {
                                    "b" -> {
                                        val v = prElem.getAttributeNS("*", "val").ifEmpty { prElem.getAttribute("w:val") }.ifEmpty { prElem.getAttribute("val") }
                                        isBold = v.isEmpty() || v != "0" && v != "false"
                                    }
                                    "i" -> {
                                        val v = prElem.getAttributeNS("*", "val").ifEmpty { prElem.getAttribute("w:val") }.ifEmpty { prElem.getAttribute("val") }
                                        isItalic = v.isEmpty() || v != "0" && v != "false"
                                    }
                                    "u" -> {
                                        val v = prElem.getAttributeNS("*", "val").ifEmpty { prElem.getAttribute("w:val") }.ifEmpty { prElem.getAttribute("val") }
                                        isUnderline = v.isEmpty() || v != "none"
                                    }
                                    "sz" -> {
                                        val v = prElem.getAttributeNS("*", "val").ifEmpty { prElem.getAttribute("w:val") }.ifEmpty { prElem.getAttribute("val") }
                                        val halfPts = v.toIntOrNull()
                                        if (halfPts != null) fontSizePt = halfPts / 2f
                                    }
                                    "rtl", "bidi" -> isRtl = true
                                }
                            }
                            prChild = prChild.nextSibling
                        }
                    }
                    "t" -> {
                        val text = elem.textContent
                        if (text.isNotEmpty()) {
                            var formatted = escapeHtml(text)
                            if (fontSizePt != null) formatted = "<span style=\"font-size:${fontSizePt}pt\">$formatted</span>"
                            if (isUnderline) formatted = "<u>$formatted</u>"
                            if (isItalic) formatted = "<i>$formatted</i>"
                            if (isBold) formatted = "<b>$formatted</b>"
                            if (isRtl) formatted = "<span dir=\"rtl\">$formatted</span>"
                            sb.append(formatted)
                        }
                    }
                    "tab" -> sb.append("&emsp;")
                    "br" -> sb.append("<br>")
                    "drawing" -> sb.append(parseDrawing(elem, relsMap, zipEntries))
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun parseDrawing(
        drawingElem: Element,
        relsMap: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): String {
        val blipList = drawingElem.getElementsByTagNameNS("*", "blip")
        if (blipList.length == 0) return ""
        val blip = blipList.item(0) as? Element ?: return ""

        val embedId = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
            .ifEmpty { blip.getAttribute("r:embed") }
            .ifEmpty { blip.getAttribute("embed") }

        if (embedId.isEmpty()) return ""
        val target = relsMap[embedId] ?: return ""

        val imagePath = if (target.startsWith("media/")) "word/$target"
        else if (target.startsWith("/")) target.removePrefix("/")
        else "word/$target"

        val bytes = zipEntries[imagePath] ?: return ""
        val mime = if (imagePath.endsWith(".png", true)) "image/png"
        else if (imagePath.endsWith(".gif", true)) "image/gif"
        else "image/jpeg"

        val b64 = Base64.getEncoder().encodeToString(bytes)
        return "<img src=\"data:$mime;base64,$b64\">"
    }

    private fun parseTable(
        tblElem: Element,
        relsMap: Map<String, String>,
        zipEntries: Map<String, ByteArray>
    ): String {
        val sb = StringBuilder()
        sb.append("<table>\n")

        var tr = tblElem.firstChild
        while (tr != null) {
            if (tr.nodeType == Node.ELEMENT_NODE) {
                val trElem = tr as Element
                val trName = trElem.localName ?: trElem.nodeName.substringAfterLast(':')
                if (trName == "tr") {
                    sb.append("<tr>\n")
                    var tc = trElem.firstChild
                    while (tc != null) {
                        if (tc.nodeType == Node.ELEMENT_NODE) {
                            val tcElem = tc as Element
                            val tcName = tcElem.localName ?: tcElem.nodeName.substringAfterLast(':')
                            if (tcName == "tc") {
                                sb.append("<td>")
                                var p = tcElem.firstChild
                                while (p != null) {
                                    if (p.nodeType == Node.ELEMENT_NODE) {
                                        val pElem = p as Element
                                        val pName = pElem.localName ?: pElem.nodeName.substringAfterLast(':')
                                        if (pName == "p") {
                                            val pr = parseParagraph(pElem, relsMap, zipEntries)
                                            if (pr != null) sb.append(pr.content)
                                        }
                                    }
                                    p = p.nextSibling
                                }
                                sb.append("</td>\n")
                            }
                        }
                        tc = tc.nextSibling
                    }
                    sb.append("</tr>\n")
                }
            }
            tr = tr.nextSibling
        }
        sb.append("</table>\n")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun fallbackHtml(message: String): String {
        return "$CSS_HEADER<p>$message</p>$CSS_FOOTER"
    }
}
