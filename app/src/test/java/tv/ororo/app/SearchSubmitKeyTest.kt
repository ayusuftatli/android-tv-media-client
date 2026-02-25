package tv.ororo.app

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.ui.search.isSearchSubmitKey

class SearchSubmitKeyTest {

    @Test
    fun `enter and search keys are treated as submit keys`() {
        assertTrue(isSearchSubmitKey(KeyEvent.KEYCODE_ENTER))
        assertTrue(isSearchSubmitKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(isSearchSubmitKey(KeyEvent.KEYCODE_SEARCH))
    }

    @Test
    fun `non-submit key is ignored`() {
        assertFalse(isSearchSubmitKey(KeyEvent.KEYCODE_DPAD_LEFT))
    }
}
