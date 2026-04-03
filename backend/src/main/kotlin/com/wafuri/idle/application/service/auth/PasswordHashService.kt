package com.wafuri.idle.application.service.auth

import jakarta.enterprise.context.ApplicationScoped
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@ApplicationScoped
class PasswordHashService {
  fun hash(password: String): HashedPassword {
    require(password.isNotBlank()) { "Password must not be blank." }
    val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
    val hash = hash(password, salt)
    return HashedPassword(
      Base64.getEncoder().encodeToString(hash),
      Base64.getEncoder().encodeToString(salt),
    )
  }

  fun verify(
    password: String,
    storedHash: String,
    storedSalt: String,
  ): Boolean {
    if (password.isBlank()) {
      return false
    }
    val salt = Base64.getDecoder().decode(storedSalt)
    val expected = Base64.getDecoder().decode(storedHash)
    val actual = hash(password, salt)
    return actual.contentEquals(expected)
  }

  private fun hash(
    password: String,
    salt: ByteArray,
  ): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
    return SecretKeyFactory
      .getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(spec)
      .encoded
  }

  companion object {
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private val secureRandom = SecureRandom()
  }
}

data class HashedPassword(
  val hash: String,
  val salt: String,
)
