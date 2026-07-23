package dev.gaphunter.spreadsheetcompanion.xlsx

import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

data class XlsxSheet(
    val name: String,
    val rows: List<List<String>>,
)

data class XlsxWorkbook(
    val sheets: List<XlsxSheet>,
)

class XlsxFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal read-only .xlsx reader on top of the JDK alone (ZipInputStream +
 * SAX). An .xlsx file is a ZIP: workbook.xml names the sheets,
 * sharedStrings.xml holds deduplicated cell text, and each
 * worksheets/sheetN.xml holds the cell grid. No Apache POI: a viewer only
 * needs values, and the dependency would be bigger than the whole plugin.
 */
object XlsxReader {

    fun read(bytes: ByteArray): XlsxWorkbook {
        val entries = readZipEntries(bytes)
        if (!entries.containsKey("xl/workbook.xml")) {
            throw XlsxFormatException("Not an .xlsx workbook: missing xl/workbook.xml")
        }
        val sheetNames = parseSheetNames(entries.getValue("xl/workbook.xml"))
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val sheets = mutableListOf<XlsxSheet>()
        for ((index, name) in sheetNames.withIndex()) {
            val entryName = "xl/worksheets/sheet${index + 1}.xml"
            val sheetXml = entries[entryName] ?: continue
            sheets.add(XlsxSheet(name, parseSheet(sheetXml, sharedStrings)))
        }
        if (sheets.isEmpty()) {
            throw XlsxFormatException("Workbook contains no readable sheets")
        }
        return XlsxWorkbook(sheets)
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/workbook.xml" || name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                ) {
                    result[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun newSaxFactory(): SAXParserFactory {
        val factory = SAXParserFactory.newInstance()
        // XML from untrusted files: no DTDs, no external entities.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory
    }

    private fun parseXml(xml: ByteArray, handler: DefaultHandler) {
        try {
            newSaxFactory().newSAXParser().parse(InputSource(ByteArrayInputStream(xml)), handler)
        } catch (e: Exception) {
            throw XlsxFormatException("Malformed XML inside .xlsx", e)
        }
    }

    private fun parseSheetNames(xml: ByteArray): List<String> {
        val names = mutableListOf<String>()
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (qName == "sheet" || qName.endsWith(":sheet")) {
                    names.add(attributes.getValue("name") ?: "Sheet${names.size + 1}")
                }
            }
        })
        return names
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        parseXml(xml, object : DefaultHandler() {
            val current = StringBuilder()
            var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when {
                    qName == "si" || qName.endsWith(":si") -> current.setLength(0)
                    qName == "t" || qName.endsWith(":t") -> inText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when {
                    qName == "t" || qName.endsWith(":t") -> inText = false
                    qName == "si" || qName.endsWith(":si") -> strings.add(current.toString())
                }
            }
        })
        return strings
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        // rowIndex -> (columnIndex -> value); the grid is sparse in the file.
        val cells = mutableMapOf<Int, MutableMap<Int, String>>()
        var maxRow = -1
        var maxCol = -1

        parseXml(xml, object : DefaultHandler() {
            var currentRow = -1
            var currentCol = -1
            var currentType = ""
            var inValue = false
            var inInlineText = false
            val value = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when {
                    qName == "row" || qName.endsWith(":row") -> {
                        currentRow = (attributes.getValue("r")?.toIntOrNull() ?: (currentRow + 2)) - 1
                    }
                    qName == "c" || qName.endsWith(":c") -> {
                        val ref = attributes.getValue("r")
                        currentCol = if (ref != null) columnIndexOf(ref) else currentCol + 1
                        currentType = attributes.getValue("t") ?: ""
                        value.setLength(0)
                    }
                    qName == "v" || qName.endsWith(":v") -> inValue = true
                    // inline strings: <c t="inlineStr"><is><t>text</t></is></c>
                    qName == "t" || qName.endsWith(":t") -> inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) value.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when {
                    qName == "v" || qName.endsWith(":v") -> inValue = false
                    qName == "t" || qName.endsWith(":t") -> inInlineText = false
                    qName == "c" || qName.endsWith(":c") -> {
                        if (value.isNotEmpty() && currentRow >= 0 && currentCol >= 0) {
                            val text = when (currentType) {
                                "s" -> sharedStrings.getOrNull(value.toString().toIntOrNull() ?: -1) ?: ""
                                "b" -> if (value.toString() == "1") "TRUE" else "FALSE"
                                else -> value.toString()
                            }
                            cells.getOrPut(currentRow) { mutableMapOf() }[currentCol] = text
                            if (currentRow > maxRow) maxRow = currentRow
                            if (currentCol > maxCol) maxCol = currentCol
                        }
                    }
                }
            }
        })

        if (maxRow < 0) return emptyList()
        return (0..maxRow).map { r ->
            val rowCells = cells[r]
            (0..maxCol).map { c -> rowCells?.get(c) ?: "" }
        }
    }

    /** "B7" -> 1, "AA3" -> 26. The digits (row part) are ignored. */
    internal fun columnIndexOf(cellRef: String): Int {
        var col = 0
        for (c in cellRef) {
            if (!c.isLetter()) break
            col = col * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return col - 1
    }
}
