package com.wafuri.idle.tests.support

import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.ContainerProvider
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.Session
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

fun connectPlayerWebSocket(
  wsBaseUri: URI,
  playerId: String,
  token: String?,
  collector: MessageCollector,
): Session =
  ContainerProvider
    .getWebSocketContainer()
    .connectToServer(
      playerWebSocketClientEndpoint(collector),
      playerWebSocketClientConfig(token),
      playerWebSocketUri(wsBaseUri, playerId),
    )

fun playerWebSocketUri(
  wsBaseUri: URI,
  playerId: String,
): URI =
  URI.create(
    wsBaseUri
      .toString()
      .replaceFirst("http", "ws")
      .trimEnd('/') + "/$playerId",
  )

fun playerWebSocketClientConfig(token: String?): ClientEndpointConfig {
  val builder = ClientEndpointConfig.Builder.create()
  if (token != null) {
    builder.preferredSubprotocols(
      listOf(
        "bearer-token-carrier",
        "quarkus-http-upgrade#Authorization#Bearer%20$token",
      ),
    )
  }
  return builder.build()
}

fun playerWebSocketClientEndpoint(collector: MessageCollector): Endpoint =
  object : Endpoint() {
    override fun onOpen(
      session: Session,
      config: EndpointConfig,
    ) {
      collector.onOpen()
      session.addMessageHandler(String::class.java) { message -> collector.onMessage(message) }
    }
  }

class MessageCollector {
  val opened = CountDownLatch(1)
  val messages = LinkedBlockingQueue<String>()

  fun onOpen() {
    opened.countDown()
  }

  fun onMessage(message: String) {
    messages.offer(message)
  }
}
