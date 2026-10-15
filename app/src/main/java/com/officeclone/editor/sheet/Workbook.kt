package com.officeclone.editor.sheet

/** An editable workbook with stable, user-visible worksheet names. */
data class WorkbookSheet(val name: String, val sheet: Sheet)
data class Workbook(val sheets: List<WorkbookSheet> = listOf(WorkbookSheet("Лист1", Sheet()))) {
    init { require(sheets.isNotEmpty()) { "Книга должна содержать хотя бы один лист" } }
}
