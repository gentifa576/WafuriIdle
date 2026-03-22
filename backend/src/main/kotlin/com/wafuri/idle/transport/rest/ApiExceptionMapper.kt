package com.wafuri.idle.transport.rest

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.transport.rest.dto.ErrorResponse
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Request
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory

@Provider
class ApiExceptionMapper : ExceptionMapper<RuntimeException> {
  @Context
  lateinit var request: Request

  @Context
  lateinit var uriInfo: UriInfo

  override fun toResponse(exception: RuntimeException): Response {
    val status =
      when (exception) {
        is ResourceNotFoundException -> Response.Status.NOT_FOUND
        is ValidationException, is IllegalArgumentException -> Response.Status.BAD_REQUEST
        else -> Response.Status.INTERNAL_SERVER_ERROR
      }

    if (status == Response.Status.INTERNAL_SERVER_ERROR) {
      logger
        .atError()
        .setCause(exception)
        .addKeyValue("method", request.method)
        .addKeyValue("path", "/${uriInfo.path}")
        .log("Unhandled REST error.")
    }

    return Response
      .status(status)
      .entity(ErrorResponse(exception.message ?: "Unexpected error."))
      .build()
  }

  companion object {
    private val logger = LoggerFactory.getLogger(ApiExceptionMapper::class.java)
  }
}
