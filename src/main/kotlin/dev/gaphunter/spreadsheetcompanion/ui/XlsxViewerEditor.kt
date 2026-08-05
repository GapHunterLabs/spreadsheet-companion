package dev.gaphunter.spreadsheetcompanion.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxReader
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxSheet
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxWorkbook
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxWriter
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableModel

class XlsxViewerEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())

    // The bytes a cell edit is applied against. Updated only after a write
    // succeeds, so a failed write never leaves this pointing at content that
    // was never actually persisted.
    @Volatile
    private var currentBytes: ByteArray = ByteArray(0)

    init {
        panel.add(JLabel("Loading ${file.name}...", SwingConstants.CENTER), BorderLayout.CENTER)
        // Parsing happens off the EDT; a big workbook must never freeze the IDE.
        val bytes = file.contentsToByteArray()
        currentBytes = bytes
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { XlsxReader.read(bytes) }
            ApplicationManager.getApplication().invokeLater {
                panel.removeAll()
                result.fold(
                    onSuccess = { panel.add(buildWorkbookView(it), BorderLayout.CENTER) },
                    onFailure = {
                        panel.add(
                            JLabel("Cannot read ${file.name}: ${it.message}", SwingConstants.CENTER),
                            BorderLayout.CENTER,
                        )
                    },
                )
                panel.revalidate()
                panel.repaint()
            }
        }
    }

    private fun buildWorkbookView(workbook: XlsxWorkbook): JComponent {
        if (workbook.sheets.size == 1) return buildSheetView(0, workbook.sheets[0])
        val tabs = JTabbedPane()
        for ((index, sheet) in workbook.sheets.withIndex()) {
            tabs.addTab(sheet.name, buildSheetView(index, sheet))
        }
        return tabs
    }

    private fun buildSheetView(sheetIndex: Int, sheet: XlsxSheet): JComponent {
        val rows = sheet.rows
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        val formulaCells = sheet.formulaCells
        val model = object : DefaultTableModel(
            rows.map { row -> Array(columnCount) { i -> row.getOrElse(i) { "" } } }.toTypedArray(),
            Array(columnCount) { "" },
        ) {
            override fun getColumnName(column: Int) = CsvTableEditor.spreadsheetColumnName(column)

            // Formulas are not writable in v1 -- XlsxReader doesn't evaluate
            // them, and overwriting one would drop it while leaving a stale
            // cached value behind. Keeping them read-only here is the
            // primary guard; XlsxWriter also refuses them as a backstop.
            override fun isCellEditable(row: Int, column: Int) = (row to column) !in formulaCells
        }
        val table = JTable(model)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.cellSelectionEnabled = true
        model.addTableModelListener { event ->
            if (event.type != TableModelEvent.UPDATE) return@addTableModelListener
            val row = event.firstRow
            val col = event.column
            if (row < 0 || col == TableModelEvent.ALL_COLUMNS) return@addTableModelListener
            writeCell(sheetIndex, row, col, model.getValueAt(row, col)?.toString() ?: "")
        }
        return JScrollPane(table)
    }

    private fun writeCell(sheetIndex: Int, row: Int, col: Int, newValue: String) {
        val bytesSnapshot = currentBytes
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { XlsxWriter.writeCellValue(bytesSnapshot, sheetIndex, row, col, newValue) }
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { newBytes ->
                        WriteCommandAction.runWriteCommandAction(project) {
                            file.setBinaryContent(newBytes)
                        }
                        currentBytes = newBytes
                    },
                    onFailure = {
                        Messages.showErrorDialog(project, it.message ?: "Could not write cell", "Cannot Save Cell")
                    },
                )
            }
        }
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Workbook"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {}
}
