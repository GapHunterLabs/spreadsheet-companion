package dev.gaphunter.spreadsheetcompanion.xlsx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Surgical .xlsx cell writer on top of the JDK alone (ZIP + DOM), mirroring
 * XlsxReader's "no Apache POI" call. Only the one worksheet XML entry that
 * changed is re-parsed and re-serialized; every other ZIP entry (styles,
 * theme, sharedStrings, merged-cell ranges, etc.) is copied byte for byte,
 * untouched. That is the direct fix for "corrupted my file on save",
 * the real complaint against the paid incumbent this plugin answers -- the
 * usual way that bug happens is regenerating the whole workbook on write.
 */
object XlsxWriter {

    /**
     * Returns a new .xlsx byte array with a single cell overwritten.
     * [row]/[col] are 0-indexed, matching XlsxReader/XlsxSheet.
     *
     * Never call this on a cell listed in XlsxSheet.formulaCells -- the
     * check below is a last-resort defense, not the primary guard (that is
     * the caller not making the cell editable in the first place).
     */
    fun writeCellValue(originalBytes: ByteArray, sheetIndex: Int, row: Int, col: Int, newValue: String): ByteArray {
        val sheetEntryName = "xl/worksheets/sheet${sheetIndex + 1}.xml"
        var touchedSheet = false

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zos ->
            ZipInputStream(ByteArrayInputStream(originalBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val bytes = zis.readBytes()
                    // A fresh ZipEntry (not the one read from the stream) avoids
                    // carrying over compressed-size/CRC metadata that no longer
                    // matches once we've rewritten the content for the target entry.
                    zos.putNextEntry(ZipEntry(name))
                    if (name == sheetEntryName) {
                        zos.write(rewriteSheetXml(bytes, row, col, newValue))
                        touchedSheet = true
                    } else {
                        zos.write(bytes)
                    }
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (!touchedSheet) {
            throw XlsxFormatException("Workbook has no entry $sheetEntryName")
        }
        return output.toByteArray()
    }

    private fun rewriteSheetXml(xml: ByteArray, row: Int, col: Int, newValue: String): ByteArray {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        } catch (e: Exception) {
            throw XlsxFormatException("Malformed worksheet XML", e)
        }

        val sheetData = firstChildElement(doc.documentElement, "sheetData")
            ?: throw XlsxFormatException("Worksheet XML has no <sheetData>")

        val rowEl = findOrCreateRow(doc, sheetData, row)
        val cellRef = cellReference(row, col)
        val cellEl = findOrCreateCell(doc, rowEl, col, cellRef)

        if (firstChildElement(cellEl, "f") != null) {
            throw XlsxFormatException("Refusing to overwrite formula cell $cellRef")
        }

        val numeric = newValue.toDoubleOrNull()
        while (cellEl.firstChild != null) cellEl.removeChild(cellEl.firstChild)
        if (numeric != null) {
            // No "t" attribute at all is the numeric-cell convention -- keeps
            // whatever number/date display format styles.xml already assigns.
            cellEl.removeAttribute("t")
            val v = doc.createElement("v")
            v.textContent = newValue
            cellEl.appendChild(v)
        } else {
            // Inline string, never sharedStrings.xml: other cells reference
            // shared strings by index, so touching that table risks shifting
            // an index some other, untouched cell still points to.
            cellEl.setAttribute("t", "inlineStr")
            val isEl = doc.createElement("is")
            val t = doc.createElement("t")
            t.textContent = newValue
            isEl.appendChild(t)
            cellEl.appendChild(isEl)
        }

        val out = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    /** Matches both "sheetData" and a namespace-prefixed "x:sheetData", same dual check as XlsxReader's SAX handler. */
    private fun firstChildElement(parent: Node, localName: String): Element? =
        childElements(parent, localName).firstOrNull()

    private fun childElements(parent: Node, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element && (n.tagName == localName || n.tagName.endsWith(":$localName"))) result.add(n)
        }
        return result
    }

    private fun findOrCreateRow(doc: Document, sheetData: Element, row: Int): Element {
        val rowNumber = row + 1 // 1-indexed in the file
        for (r in childElements(sheetData, "row")) {
            val rNum = r.getAttribute("r").toIntOrNull() ?: continue
            if (rNum == rowNumber) return r
            if (rNum > rowNumber) {
                val newRow = doc.createElement("row")
                newRow.setAttribute("r", rowNumber.toString())
                sheetData.insertBefore(newRow, r)
                return newRow
            }
        }
        val newRow = doc.createElement("row")
        newRow.setAttribute("r", rowNumber.toString())
        sheetData.appendChild(newRow)
        return newRow
    }

    private fun findOrCreateCell(doc: Document, rowEl: Element, col: Int, cellRef: String): Element {
        for (c in childElements(rowEl, "c")) {
            val ref = c.getAttribute("r")
            if (ref == cellRef) return c
            val existingCol = if (ref.isNotEmpty()) XlsxReader.columnIndexOf(ref) else null
            if (existingCol != null && existingCol > col) {
                val newCell = doc.createElement("c")
                newCell.setAttribute("r", cellRef)
                rowEl.insertBefore(newCell, c)
                return newCell
            }
        }
        val newCell = doc.createElement("c")
        newCell.setAttribute("r", cellRef)
        rowEl.appendChild(newCell)
        return newCell
    }

    private fun columnLetters(col: Int): String {
        var i = col
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun cellReference(row: Int, col: Int) = "${columnLetters(col)}${row + 1}"
}
