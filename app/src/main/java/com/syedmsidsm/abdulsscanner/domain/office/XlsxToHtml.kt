package com.syedmsidsm.abdulsscanner.domain.office

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class XlsxResult(
    val html: String,
    val isLandscape: Boolean
)

object XlsxToHtml {

    data class SheetInfo(
        val name: String,
        val relId: String,
        var path: String = ""
    )

    data class CellData(
        val colIndex: Int,
        val value: String
    )

    data class RowData(
        val rowIndex: Int,
        val cells: List<CellData>
    )

    fun convert(xlsxInputStream: InputStream): XlsxResult {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(xlsxInputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipEntries[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val sharedStrings = parseSharedStrings(zipEntries["xl/sharedStrings.xml"])
        val sheets = parseWorkbook(zipEntries["xl/workbook.xml"])
        val rels = parseWorkbookRels(zipEntries["xl/_rels/workbook.xml.rels"])

        for (sheet in sheets) {
            val target = rels[sheet.relId] ?: "worksheets/sheet1.xml"
            sheet.path = if (target.startsWith("xl/")) target else "xl/$target"
        }

        var maxColsOverall = 0
        val sheetsHtml = StringBuilder()

        for ((idx, sheet) in sheets.withIndex()) {
            val sheetBytes = zipEntries[sheet.path] ?: continue
            val (sheetContent, maxColsInSheet) = parseWorksheet(sheetBytes, sheet.name, sharedStrings)
            if (maxColsInSheet > maxColsOverall) maxColsOverall = maxColsInSheet

            if (idx > 0) {
                sheetsHtml.append("<div style=\"page-break-before: always;\"></div>\n")
            }
            sheetsHtml.append(sheetContent)
        }

        val isLandscape = maxColsOverall > 6
        val css = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <style>
              @page { size: ${if (isLandscape) "A4 landscape" else "A4"}; margin: 15mm; }
              body { font-family: sans-serif; font-size: 10pt; color: #111; line-height: 1.4; }
              h2 { font-size: 16pt; margin: 10pt 0 6pt 0; color: #222; }
              table { border-collapse: collapse; width: 100%; margin-bottom: 16pt; }
              td, th { border: 1px solid #aaa; padding: 4pt 6pt; font-size: 9pt; text-align: left; }
              th { background-color: #f2f2f2; font-weight: bold; }
              tr:nth-child(even) { background-color: #fafafa; }
            </style>
            </head>
            <body>
        """.trimIndent()

        val fullHtml = "$css\n$sheetsHtml\n</body>\n</html>"
        return XlsxResult(html = fullHtml, isLandscape = isLandscape)
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val strings = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val siList = doc.getElementsByTagName("si")
            for (i in 0 until siList.length) {
                val si = siList.item(i) as? Element ?: continue
                val tList = si.getElementsByTagName("t")
                val sb = StringBuilder()
                for (j in 0 until tList.length) {
                    sb.append(tList.item(j).textContent)
                }
                strings.add(sb.toString())
            }
        } catch (e: Exception) {
            // ignore
        }
        return strings
    }

    private fun parseWorkbook(bytes: ByteArray?): List<SheetInfo> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<SheetInfo>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val sheetList = doc.getElementsByTagName("sheet")
            for (i in 0 until sheetList.length) {
                val sheet = sheetList.item(i) as? Element ?: continue
                val name = sheet.getAttribute("name").ifEmpty { "Sheet" }
                val relId = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    .ifEmpty { sheet.getAttribute("r:id") }
                    .ifEmpty { sheet.getAttribute("id") }
                list.add(SheetInfo(name = name, relId = relId))
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun parseWorkbookRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val relList = doc.getElementsByTagName("Relationship")
            for (i in 0 until relList.length) {
                val rel = relList.item(i) as? Element ?: continue
                val id = rel.getAttribute("Id")
                val target = rel.getAttribute("Target")
                if (id.isNotEmpty() && target.isNotEmpty()) {
                    map[id] = target
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return map
    }

    private fun parseWorksheet(bytes: ByteArray, sheetName: String, sharedStrings: List<String>): Pair<String, Int> {
        val rows = mutableListOf<RowData>()
        var maxColIndex = 0

        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val rowList = doc.getElementsByTagName("row")

            for (i in 0 until rowList.length) {
                val rowElem = rowList.item(i) as? Element ?: continue
                val rowIdx = rowElem.getAttribute("r").toIntOrNull() ?: (i + 1)
                val currentCells = mutableListOf<CellData>()

                var cChild = rowElem.firstChild
                while (cChild != null) {
                    if (cChild.nodeType == Node.ELEMENT_NODE) {
                        val cElem = cChild as Element
                        val localName = cElem.localName ?: cElem.nodeName.substringAfterLast(':')
                        if (localName == "c") {
                            val ref = cElem.getAttribute("r")
                            val type = cElem.getAttribute("t")

                            val vList = cElem.getElementsByTagName("v")
                            val rawValue = if (vList.length > 0) {
                                vList.item(0).textContent
                            } else {
                                val isList = cElem.getElementsByTagName("is")
                                if (isList.length > 0) isList.item(0).textContent else ""
                            }

                            val colIdx = getColIndexFromRef(ref)
                            if (colIdx > maxColIndex) maxColIndex = colIdx

                            val resolved = formatCellValue(rawValue, type, sharedStrings)
                            currentCells.add(CellData(colIdx, resolved))
                        }
                    }
                    cChild = cChild.nextSibling
                }

                if (currentCells.isNotEmpty()) {
                    rows.add(RowData(rowIdx, currentCells))
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        val totalCols = maxColIndex + 1
        val sb = StringBuilder()
        sb.append("<h2>").append(escapeHtml(sheetName)).append("</h2>\n")
        sb.append("<table>\n")

        for (row in rows) {
            sb.append("<tr>")
            val cellsByCol = row.cells.associateBy { it.colIndex }
            for (col in 0 until totalCols) {
                val value = cellsByCol[col]?.value.orEmpty()
                sb.append("<td>").append(escapeHtml(value)).append("</td>")
            }
            sb.append("</tr>\n")
        }

        sb.append("</table>\n")
        return Pair(sb.toString(), totalCols)
    }

    private fun formatCellValue(raw: String, type: String, sharedStrings: List<String>): String {
        return when (type) {
            "s" -> {
                val idx = raw.toIntOrNull() ?: return raw
                sharedStrings.getOrElse(idx) { raw }
            }
            "b" -> if (raw == "1") "TRUE" else "FALSE"
            "str", "inlineStr" -> raw
            else -> {
                if (raw.endsWith(".0")) raw.removeSuffix(".0") else raw
            }
        }
    }

    private fun getColIndexFromRef(ref: String): Int {
        val colLetters = ref.takeWhile { it.isLetter() }.uppercase(Locale.US)
        if (colLetters.isEmpty()) return 0
        var idx = 0
        for (char in colLetters) {
            idx = idx * 26 + (char - 'A' + 1)
        }
        return (idx - 1).coerceAtLeast(0)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
