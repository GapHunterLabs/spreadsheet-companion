package dev.gaphunter.spreadsheetcompanion.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun simpleCommaSeparatedValues() {
        val table = CsvParser.parse("a,b,c\n1,2,3")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), table.rows)
        assertEquals(',', table.delimiter)
    }

    @Test
    fun quotedFieldWithEmbeddedComma() {
        val table = CsvParser.parse("name,notes\n\"Diaz, Joel\",ok")
        assertEquals(listOf("Diaz, Joel", "ok"), table.rows[1])
    }

    @Test
    fun quotedFieldWithEscapedQuote() {
        val table = CsvParser.parse("a\n\"He said \"\"hi\"\"\"")
        assertEquals("He said \"hi\"", table.rows[1][0])
    }

    @Test
    fun quotedFieldWithEmbeddedNewline() {
        val table = CsvParser.parse("a,b\n\"line1\nline2\",x")
        assertEquals(2, table.rows.size)
        assertEquals("line1\nline2", table.rows[1][0])
    }

    @Test
    fun crlfLineEndings() {
        val table = CsvParser.parse("a,b\r\n1,2\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), table.rows)
    }

    @Test
    fun semicolonDelimiterIsSniffed() {
        val table = CsvParser.parse("a;b;c\n1;2;3")
        assertEquals(';', table.delimiter)
        assertEquals(listOf("1", "2", "3"), table.rows[1])
    }

    @Test
    fun tabDelimiterIsSniffed() {
        val table = CsvParser.parse("a\tb\n1\t2")
        assertEquals('\t', table.delimiter)
    }

    @Test
    fun sniffingIgnoresDelimitersInsideQuotes() {
        // Commas only appear inside quotes; semicolon is the real delimiter.
        val text = "\"a,x\";b\n\"c,y\";d"
        assertEquals(';', CsvParser.sniffDelimiter(text))
    }

    @Test
    fun emptyFieldsArePreserved() {
        val table = CsvParser.parse("a,,c\n,,")
        assertEquals(listOf("a", "", "c"), table.rows[0])
        assertEquals(listOf("", "", ""), table.rows[1])
    }

    @Test
    fun serializeRoundTripsWithQuoting() {
        val rows = listOf(
            listOf("plain", "with,comma", "with\"quote", "with\nnewline"),
            listOf("", "x", "", ""),
        )
        val text = CsvParser.serialize(rows, ',')
        assertEquals(rows, CsvParser.parse(text, ',').rows)
    }

    @Test
    fun serializeUsesRequestedLineSeparator() {
        val text = CsvParser.serialize(listOf(listOf("a"), listOf("b")), ',', "\r\n")
        assertEquals("a\r\nb", text)
    }
}
