package tv.ororo.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.ui.player.shouldHandleBackAsHideControls

class PlayerBackBehaviorTest {

    @Test
    fun `back hides controls when controller is visible`() {
        assertTrue(shouldHandleBackAsHideControls(isControllerVisible = true))
    }

    @Test
    fun `back navigates out when controller is hidden`() {
        assertFalse(shouldHandleBackAsHideControls(isControllerVisible = false))
    }
}
