package com.wafuri.idle.application.exception

class AuthenticationException(
  message: String,
) : RuntimeException(message)

class AuthorizationException(
  message: String,
) : RuntimeException(message)
