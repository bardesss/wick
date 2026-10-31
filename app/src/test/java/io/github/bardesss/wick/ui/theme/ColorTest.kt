package io.github.bardesss.wick.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {
    @Test
    fun tokens_match_spec() {
        assertEquals(Color(0xFF12161B), WickColors.Night)
        assertEquals(Color(0xFF1B2129), WickColors.Raised)
        assertEquals(Color(0xFF20262E), WickColors.Line)
        assertEquals(Color(0xFFECE8E1), WickColors.Paper)
        assertEquals(Color(0xFF4B545E), WickColors.Faded)
        assertEquals(Color(0xFF7F8A96), WickColors.Meta)
        assertEquals(Color(0xFFE3A857), WickColors.Amber)
        assertEquals(Color(0xFF5A4628), WickColors.Dusk)
        assertEquals(Color(0xFF3D5C4E), WickColors.Sage)
        assertEquals(Color(0xFFFFE2A8), WickColors.Core)
    }
}
