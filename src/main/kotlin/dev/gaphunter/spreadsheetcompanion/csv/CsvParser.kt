package dev.gaphunter.spreadsheetcompanion.csv

data class CsvTable(
    val rows: List<List<String>>,
    val delimiter: Char,
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

object CsvParser {

    private val CANDIDATE_DELIMITERS = charArrayOf(',', ';', '\t', '|')

    fun sniffDelimiter(text: String): Char {
        val sample = text.lineSequence().take(20).joinToString("\n")
        var best = ','
        var bestScore = -1
        for (candidate in CANDIDATE_DELIMITERS) {
            val counts = sample.lineSequence()
                .filter { it.isNotBlank() }
                .map { line -> countOutsideQuotes(line, candidate) }
                .toList()
            if (counts.isEmpty()) continue
            val first = counts.first()
            if (first == 0) continue
            // Consistency wins: every sampled line having the same count is
            // a stronger signal than a higher count on one line.
            val consistent = counts.all { it == first }
            val score = (if (consistent) 1000 else 0) + first
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    fun parse(text: String, delimiter: Char = sniffDelimiter(text)): CsvTable {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    row.add(field.toString()); field.setLength(0)
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                c == '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return CsvTable(rows, delimiter)
    }

    fun serialize(rows: List<List<String>>, delimiter: Char, lineSeparator: String = "\n"): String {
        return rows.joinToString(lineSeparator) { row ->
            row.joinToString(delimiter.toString()) { cell -> escape(cell, delimiter) }
        }
    }

    private fun escape(cell: String, delimiter: Char): String {
        val needsQuoting = cell.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return cell
        return "\"" + cell.replace("\"", "\"\"") + "\""
    }
}
