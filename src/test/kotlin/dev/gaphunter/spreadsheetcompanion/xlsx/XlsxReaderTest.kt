package dev.gaphunter.spreadsheetcompanion.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XlsxReaderTest {

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun workbookXml(vararg names: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheets>
            ${names.mapIndexed { i, n -> "<sheet name=\"$n\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" }.joinToString("")}
          </sheets>
        </workbook>
    """.trimIndent()

    @Test
    fun readsSharedStringsAndNumbers() {
        val bytes = zip(mapOf(
            "xl/workbook.xml" to workbookXml("Data"),
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="2" uniqueCount="2">
                  <si><t>Name</t></si><si><t>Joel</t></si>
                </sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>42</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>3.14</v></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
        ))
        val wb = XlsxReader.read(bytes)
        assertEquals(1, wb.sheets.size)
        assertEquals("Data", wb.sheets[0].name)
        assertEquals(listOf(listOf("Name", "42"), listOf("Joel", "3.14")), wb.sheets[0].rows)
    }

    @Test
    fun sparseCellsAreFilledWithEmptyStrings() {
        val bytes = zip(mapOf(
            "xl/workbook.xml" to workbookXml("S"),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="C1"><v>7</v></c></row>
                    <row r="3"><c r="A3"><v>9</v></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
        ))
        val rows = XlsxReader.read(bytes).sheets[0].rows
        assertEquals(3, rows.size)
        assertEquals(listOf("", "", "7"), rows[0])
        assertEquals(listOf("", "", ""), rows[1])
        assertEquals(listOf("9", "", ""), rows[2])
    }

    @Test
    fun inlineStringsAndBooleansAreDecoded() {
        val bytes = zip(mapOf(
            "xl/workbook.xml" to workbookXml("S"),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="inlineStr"><is><t>hello</t></is></c>
                      <c r="B1" t="b"><v>1</v></c>
                      <c r="C1" t="b"><v>0</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
        ))
        assertEquals(listOf(listOf("hello", "TRUE", "FALSE")), XlsxReader.read(bytes).sheets[0].rows)
    }

    @Test
    fun multipleSheetsKeepTheirNames() {
        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData><row r="1"><c r="A1"><v>1</v></c></row></sheetData>
            </worksheet>
        """.trimIndent()
        val bytes = zip(mapOf(
            "xl/workbook.xml" to workbookXml("First", "Second"),
            "xl/worksheets/sheet1.xml" to sheet,
            "xl/worksheets/sheet2.xml" to sheet,
        ))
        val wb = XlsxReader.read(bytes)
        assertEquals(listOf("First", "Second"), wb.sheets.map { it.name })
    }

    @Test
    fun nonXlsxZipIsRejected() {
        val bytes = zip(mapOf("hello.txt" to "not a workbook"))
        assertThrows(XlsxFormatException::class.java) { XlsxReader.read(bytes) }
    }

    @Test
    fun garbageBytesAreRejected() {
        assertThrows(Exception::class.java) { XlsxReader.read(byteArrayOf(1, 2, 3, 4)) }
    }

    @Test
    fun columnReferenceDecoding() {
        assertEquals(0, XlsxReader.columnIndexOf("A1"))
        assertEquals(1, XlsxReader.columnIndexOf("B7"))
        assertEquals(25, XlsxReader.columnIndexOf("Z3"))
        assertEquals(26, XlsxReader.columnIndexOf("AA3"))
        assertEquals(27, XlsxReader.columnIndexOf("AB12"))
    }
}
