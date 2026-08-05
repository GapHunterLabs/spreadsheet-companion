package dev.gaphunter.spreadsheetcompanion.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class XlsxViewerEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.extension?.lowercase() == "xlsx"

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        XlsxViewerEditor(project, file)

    override fun getEditorTypeId(): String = "spreadsheet-companion-xlsx-viewer"

    // xlsx is a binary format: the platform's text editor is useless for it,
    // so showing the workbook view first is helping, not hijacking.
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
