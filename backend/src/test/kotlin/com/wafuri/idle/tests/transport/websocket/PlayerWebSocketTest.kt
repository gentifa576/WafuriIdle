package com.wafuri.idle.tests.transport.websocket

import com.wafuri.idle.application.service.inventory.InventoryService
import com.wafuri.idle.domain.model.ItemType
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@QuarkusTest
class PlayerWebSocketTest {
  @Inject
  lateinit var inventoryService: InventoryService

  @field:TestHTTPResource("/ws/player")
  lateinit var wsBaseUri: URI

  @Test
  fun `player scoped websocket receives inventory change events`() {
    val playerId =
      given()
        .contentType("application/json")
        .body("""{"name":"SocketUser"}""")
        .post("/players")
        .then()
        .statusCode(201)
        .extract()
        .path<String>("id")

    val collector = MessageCollector()
    val client = ContainerProvider.getWebSocketContainer()
    val endpointUri =
      URI.create(
        wsBaseUri
          .toString()
          .replaceFirst("http", "ws")
          .trimEnd('/') + "/$playerId",
      )
    val session: Session = client.connectToServer(collector, endpointUri)
    try {
      inventoryService.addItem(UUID.fromString(playerId), "Sword", ItemType.WEAPON)

      val message = collector.messages.poll(5, TimeUnit.SECONDS)
      message.shouldNotBeNull()
      message.contains("PLAYER_STATE_SYNC") shouldBe true
    } finally {
      session.close()
    }
  }

  @ClientEndpoint
  class MessageCollector {
    val messages = LinkedBlockingQueue<String>()

    @OnMessage
    fun onMessage(message: String) {
      messages.offer(message)
    }
  }
}
