package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroCredentialPatternTest {
    @Test
    fun rejectsAwsSecretAccessKeyAssignmentsButAllowsOrdinaryCommands() {
        assertTrue(MacroEngine.containsCredentialLikeText("AWS_SECRET_ACCESS_KEY=example-secret"))
        assertTrue(MacroEngine.containsCredentialLikeText("export aws-secret-access-key = example-secret"))
        assertFalse(MacroEngine.containsCredentialLikeText("aws s3 ls"))
    }
}
