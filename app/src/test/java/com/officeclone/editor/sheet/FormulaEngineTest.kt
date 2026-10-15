package com.officeclone.editor.sheet

import org.junit.Assert.assertEquals
import org.junit.Test

class FormulaEngineTest {
    @Test fun evaluatesArithmeticAndCellReferences() {
        val sheet = Sheet(rows = 4, cols = 3)
        sheet.set(0, 0, Cell("2"))
        sheet.set(0, 1, Cell("3"))
        sheet.set(0, 2, Cell("=A1+B1*2"))
        assertEquals("8", FormulaEngine.evaluateAll(sheet)[0 to 2])
    }

    @Test fun evaluatesRangesAndAggregateFunctions() {
        val sheet = Sheet(rows = 5, cols = 2)
        sheet.set(0, 0, Cell("2"))
        sheet.set(1, 0, Cell("3"))
        sheet.set(2, 0, Cell("5"))
        sheet.set(0, 1, Cell("=SUM(A1:A3)"))
        sheet.set(1, 1, Cell("=AVERAGE(A1:A3)"))
        sheet.set(2, 1, Cell("=MIN(A1:A3)"))
        sheet.set(3, 1, Cell("=MAX(A1:A3)"))
        assertEquals("10", FormulaEngine.evaluateAll(sheet)[0 to 1])
        assertEquals("3.333333", FormulaEngine.evaluateAll(sheet)[1 to 1])
        assertEquals("2", FormulaEngine.evaluateAll(sheet)[2 to 1])
        assertEquals("5", FormulaEngine.evaluateAll(sheet)[3 to 1])
    }

    @Test fun reportsDivisionByZeroAndCircularReferences() {
        val sheet = Sheet(rows = 2, cols = 2)
        sheet.set(0, 0, Cell("=1/0"))
        sheet.set(0, 1, Cell("=B2"))
        sheet.set(1, 1, Cell("=B1"))
        val values = FormulaEngine.evaluateAll(sheet)
        assertEquals("#DIV/0!", values[0 to 0])
        assertEquals("#CYCLE!", values[0 to 1])
        assertEquals("#CYCLE!", values[1 to 1])
    }
}
