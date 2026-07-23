package dev.gaphunter.spreadsheetcompanion.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CsvTableEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "csv" || ext == "tsv"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        CsvTableEditor(project, file)

    override fun getEditorTypeId(): String = "spreadsheet-companion-csv-table"

    // The text editor stays the default; the table is an extra tab the user
    // opts into. Never take over how a file opens.
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
