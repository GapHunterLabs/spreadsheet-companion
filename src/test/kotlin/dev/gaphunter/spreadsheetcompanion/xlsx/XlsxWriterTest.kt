package dev.gaphunter.spreadsheetcompanion.xlsx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XlsxWriterTest {

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

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun workbookXml(vararg names: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheets>
            ${names.mapIndexed { i, n -> "<sheet name=\"$n\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" }.joinToString("")}
          </sheets>
        </workbook>
    """.trimIndent()

    // Two entries unrelated to the target sheet, standing in for
    // styles.xml/theme1.xml/etc -- the ones a correct writer must never touch.
    private fun fixture(sheetXml: String): Map<String, String> = mapOf(
        "xl/workbook.xml" to workbookXml("Data"),
        "xl/worksheets/sheet1.xml" to sheetXml,
        "xl/styles.xml" to "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>",
        "xl/theme/theme1.xml" to "<theme xmlns=\"http://schemas.openxmlformats.org/drawingml/2006/main\"/>",
    )

    @Test
    fun overwritesExistingNumericCellAndLeavesOtherEntriesUntouched() {
        val original = zip(fixture("""
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><v>1</v></c><c r="B1"><v>2</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()))

        val updated = XlsxWriter.writeCellValue(original, 0, 0, 1, "99")

        assertEquals(listOf(listOf("1", "99")), XlsxReader.read(updated).sheets[0].rows)

        // Compare decompressed content, not raw ZIP bytes: rewriting the archive
        // can legitimately change an untouched entry's stored timestamp even
        // when its content is exactly the same, which would be a false failure.
        val originalEntries = unzip(original)
        val updatedEntries = unzip(updated)
        for (name in listOf("xl/workbook.xml", "xl/styles.xml", "xl/theme/theme1.xml")) {
            assertArrayEquals(
                "entry $name must stay byte-identical",
                originalEntries.getValue(name),
                updatedEntries.getValue(name),
            )
        }
    }

    @Test
    fun writesNonNumericValueAsInlineString() {
        val original = zip(fixture("""
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><v>1</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()))

        val updated = XlsxWriter.writeCellValue(original, 0, 0, 0, "hello")

        assertEquals(listOf(listOf("hello")), XlsxReader.read(updated).sheets[0].rows)
    }

    @Test
    fun createsMissingRowAndCellInASparseGrid() {
        val original = zip(fixture("""
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><v>1</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()))

        // Row index 2 (file row "3"), column index 1 ("B") does not exist yet.
        val updated = XlsxWriter.writeCellValue(original, 0, 2, 1, "42")

        val rows = XlsxReader.read(updated).sheets[0].rows
        assertEquals(3, rows.size)
        assertEquals(listOf("1", ""), rows[0])
        assertEquals(listOf("", ""), rows[1])
        assertEquals(listOf("", "42"), rows[2])
    }

    @Test
    fun refusesToOverwriteAFormulaCell() {
        val original = zip(fixture("""
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><f>SUM(B1:B2)</f><v>3</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()))

        assertThrows(XlsxFormatException::class.java) {
            XlsxWriter.writeCellValue(original, 0, 0, 0, "99")
        }
    }

    @Test
    fun readerFlagsFormulaCellsSoTheEditorCanKeepThemReadOnly() {
        val original = zip(fixture("""
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1"><f>SUM(B1:B2)</f><v>3</v></c><c r="B1"><v>1</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()))

        assertEquals(setOf(0 to 0), XlsxReader.read(original).sheets[0].formulaCells)
    }
}
