package com.spendwise.app.ui

import com.spendwise.app.ui.auth.validateCredentials
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test fun validLogin() = assertNull(validateCredentials("a@b.co", "x", isRegister = false))
    @Test fun badEmailRejected() {
        listOf("", "plain", "a@b", "a b@c.com", "@x.com").forEach { assertNotNull(it, validateCredentials(it, "password1", false)) }
    }
    @Test fun registerNeedsEightCharPassword() {
        assertNotNull(validateCredentials("a@b.co", "short", isRegister = true))
        assertNull(validateCredentials("a@b.co", "longenough", isRegister = true))
    }
    @Test fun loginNeedsSomePassword() = assertNotNull(validateCredentials("a@b.co", "", isRegister = false))
}
