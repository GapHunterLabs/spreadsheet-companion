package dev.gaphunter.spreadsheetcompanion.ui

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.spreadsheetcompanion.csv.CsvParser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableModel

class CsvTableEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private var delimiter: Char = ','
    private val model = object : DefaultTableModel() {
        override fun getColumnName(column: Int) = spreadsheetColumnName(column)
    }
    private val table = JTable(model)
    private val panel = JPanel(BorderLayout())
    private val statusLabel = JLabel()

    /** True while we are the ones mutating either side, to break sync loops. */
    private var syncing = false

    init {
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.cellSelectionEnabled = true

        panel.add(buildToolbar(), BorderLayout.NORTH)
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)

        loadFromDocument()

        model.addTableModelListener { e ->
            if (!syncing && e.type != TableModelEvent.HEADER_ROW) {
                writeToDocument()
            }
        }
        document?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!syncing) loadFromDocument()
            }
        }, this)
    }

    private fun buildToolbar(): JComponent {
        val bar = JPanel()
        bar.add(button("Add Row") {
            model.addRow(arrayOfNulls<Any>(model.columnCount.coerceAtLeast(1)))
        })
        bar.add(button("Delete Row") {
            table.selectedRows.sortedDescending().forEach { model.removeRow(it) }
        })
        bar.add(button("Add Column") {
            model.addColumn(null)
        })
        bar.add(button("Delete Column") {
            val selected = table.selectedColumns.sortedDescending()
            if (selected.isEmpty()) return@button
            syncing = true
            try {
                val data = currentRows().map { row ->
                    row.filterIndexed { i, _ -> i !in selected }
                }
                setModelData(data, (model.columnCount - selected.size).coerceAtLeast(1))
            } finally {
                syncing = false
            }
            writeToDocument()
        })
        return bar
    }

    private fun button(text: String, action: () -> Unit): JButton {
        val b = JButton(text)
        b.addActionListener {
            if (table.isEditing) table.cellEditor.stopCellEditing()
            action()
        }
        return b
    }

    private fun currentRows(): List<List<String>> =
        (0 until model.rowCount).map { r ->
            (0 until model.columnCount).map { c -> model.getValueAt(r, c)?.toString() ?: "" }
        }

    private fun setModelData(rows: List<List<String>>, columnCount: Int) {
        model.setDataVector(
            rows.map { row -> Array(columnCount) { i -> row.getOrElse(i) { "" } } }.toTypedArray(),
            Array(columnCount) { "" },
        )
    }

    private fun loadFromDocument() {
        val text = document?.text ?: return
        syncing = true
        try {
            val parsed = CsvParser.parse(text)
            delimiter = parsed.delimiter
            setModelData(parsed.rows, parsed.columnCount.coerceAtLeast(1))
            statusLabel.text = " ${parsed.rows.size} rows x ${parsed.columnCount} columns" +
                "  |  delimiter: ${if (delimiter == '\t') "tab" else delimiter.toString()}"
        } finally {
            syncing = false
        }
    }

    private fun writeToDocument() {
        val doc = document ?: return
        val text = CsvParser.serialize(currentRows(), delimiter)
        syncing = true
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                doc.setText(text)
            } finally {
                syncing = false
            }
        }
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = table
    override fun getName(): String = "Table"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {}

    companion object {
        fun spreadsheetColumnName(index: Int): String {
            var i = index
            val sb = StringBuilder()
            while (i >= 0) {
                sb.insert(0, ('A' + i % 26))
                i = i / 26 - 1
            }
            return sb.toString()
        }
    }
}
