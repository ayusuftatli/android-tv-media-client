package tv.ororo.app

import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.ui.components.SortOption

class SortOptionTest {

    @Test
    fun `SortOption contains all planned options`() {
        val labels = SortOption.entries.map { it.label }
        assertTrue("Title" in labels)
        assertTrue("Added" in labels)
        assertTrue("Year" in labels)
        assertTrue("Rating" in labels)
    }

    @Test
    fun `SortOption has exactly 4 entries`() {
        assertEquals(4, SortOption.entries.size)
    }

    @Test
    fun `ADDED sort label is correct`() {
        assertEquals("Added", SortOption.ADDED.label)
    }
}
