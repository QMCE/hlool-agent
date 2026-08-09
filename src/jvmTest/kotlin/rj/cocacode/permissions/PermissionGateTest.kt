package rj.cocacode.permissions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import rj.cocacode.state.AppStateManager
import rj.cocacode.state.PermissionMode

class PermissionGateTest {

    @Test
    fun readIsAutoAllowed() {
        AppStateManager.setPermissionMode(PermissionMode.DEFAULT)
        assertFalse(PermissionGate.shouldAsk("Read"))
        assertTrue(PermissionGate.isAutoAllowed("Read"))
    }

    @Test
    fun bashAsksInDefault() {
        AppStateManager.setPermissionMode(PermissionMode.DEFAULT)
        PermissionGate.resetSessionAllows()
        assertTrue(PermissionGate.shouldAsk("Bash"))
    }

    @Test
    fun acceptEditsSkipsEditPrompt() {
        AppStateManager.setPermissionMode(PermissionMode.ACCEPT_EDITS)
        PermissionGate.resetSessionAllows()
        assertFalse(PermissionGate.shouldAsk("Edit"))
        assertTrue(PermissionGate.shouldAsk("Bash"))
    }

    @Test
    fun sessionAllowRemembers() {
        AppStateManager.setPermissionMode(PermissionMode.DEFAULT)
        PermissionGate.resetSessionAllows()
        PermissionGate.rememberAllow("Bash")
        assertFalse(PermissionGate.shouldAsk("Bash"))
    }
}
