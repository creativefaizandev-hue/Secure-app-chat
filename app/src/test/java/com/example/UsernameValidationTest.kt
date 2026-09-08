
package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidationTest {
  private val regex = Regex("^[A-Za-z0-9_]{3,20}$")

  @Test fun acceptsValidUsername() {
    assertTrue(regex.matches("cipher_chat"))
    assertTrue(regex.matches("abc123"))
  }

  @Test fun rejectsInvalidUsername() {
    assertFalse(regex.matches("ab"))
    assertFalse(regex.matches("has-dash"))
    assertFalse(regex.matches("has space"))
    assertFalse(regex.matches("a".repeat(21)))
  }
}
