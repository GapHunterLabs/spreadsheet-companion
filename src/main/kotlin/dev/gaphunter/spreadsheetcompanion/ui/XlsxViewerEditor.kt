package dev.gaphunter.spreadsheetcompanion.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxReader
import dev.gaphunter.spreadsheetcompanion.xlsx.XlsxWorkbook
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

class XlsxViewerEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())

    init {
        panel.add(JLabel("Loading ${file.name}...", SwingConstants.CENTER), BorderLayout.CENTER)
        // Parsing happens off the EDT; a big workbook must never freeze the IDE.
        val bytes = file.contentsToByteArray()
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
        if (workbook.sheets.size == 1) return buildSheetView(workbook.sheets[0].rows)
        val tabs = JTabbedPane()
        for (sheet in workbook.sheets) {
            tabs.addTab(sheet.name, buildSheetView(sheet.rows))
        }
        return tabs
    }

    private fun buildSheetView(rows: List<List<String>>): JComponent {
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        val model = object : DefaultTableModel(
            rows.map { row -> Array(columnCount) { i -> row.getOrElse(i) { "" } } }.toTypedArray(),
            Array(columnCount) { "" },
        ) {
            override fun getColumnName(column: Int) = CsvTableEditor.spreadsheetColumnName(column)
            override fun isCellEditable(row: Int, column: Int) = false
        }
        val table = JTable(model)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.cellSelectionEnabled = true
        return JScrollPane(table)
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
