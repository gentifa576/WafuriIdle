package com.wafuri.idle.application.exception

class ResourceNotFoundException(
  message: String,
) : RuntimeException(message)

class ValidationException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
