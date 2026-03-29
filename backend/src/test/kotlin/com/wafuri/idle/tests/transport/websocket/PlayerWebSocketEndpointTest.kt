package com.wafuri.idle.tests.transport.websocket

import com.wafuri.idle.application.exception.AuthorizationException
import com.wafuri.idle.application.model.CombatStateMessage
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.transport.websocket.PlayerSocketCommand
import com.wafuri.idle.transport.websocket.PlayerSocketCommandType
import com.wafuri.idle.transport.websocket.PlayerWebSocketEndpoint
import com.wafuri.idle.transport.websocket.PlayerWebSocketRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifyOrder
import io.quarkus.websockets.next.WebSocketConnection
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.UUID

class PlayerWebSocketEndpointTest :
  StringSpec({
    "onOpen registers first, marks dirty, and applies offline progression off the io thread" {
      val playerId = UUID.randomUUID()
      val connection = mockk<WebSocketConnection>()
      val registry = mockk<PlayerWebSocketRegistry>()
      val playerStateChangeTracker = mockk<PlayerStateChangeTracker>()
      val playerStateWorkQueue = mockk<PlayerStateWorkQueue>()
      val offlineProgressionService = mockk<OfflineProgressionService>()
      val backgroundExecutor = mockk<ManagedExecutor>()
      val combatService = mockk<CombatService>()
      val jwt = mockk<JsonWebToken>()
      val endpoint =
        PlayerWebSocketEndpoint().apply {
          this.registry = registry
          this.playerStateChangeTracker = playerStateChangeTracker
          this.playerStateWorkQueue = playerStateWorkQueue
          this.offlineProgressionService = offlineProgressionService
          this.backgroundExecutor = backgroundExecutor
          this.combatService = combatService
          this.jwt = jwt
        }

      every { connection.pathParam("playerId") } returns playerId.toString()
      every { jwt.subject } returns playerId.toString()
      every { offlineProgressionService.applyIfNeeded(playerId) } returns null
      every { playerStateChangeTracker.invalidate(playerId) } just runs
      every { registry.register(playerId, connection) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { backgroundExecutor.execute(any()) } answers {
        firstArg<Runnable>().run()
      }

      endpoint.onOpen(connection)

      verifyOrder {
        registry.register(playerId, connection)
        playerStateChangeTracker.invalidate(playerId)
        playerStateWorkQueue.markDirty(playerId)
        offlineProgressionService.applyIfNeeded(playerId)
        playerStateChangeTracker.invalidate(playerId)
        playerStateWorkQueue.markDirty(playerId)
      }
    }

    "onMessage starts combat from websocket command and returns an acknowledgement payload" {
      val playerId = UUID.randomUUID()
      val endpoint =
        PlayerWebSocketEndpoint().apply {
          registry = mockk()
          playerStateChangeTracker = mockk()
          playerStateWorkQueue = mockk()
          offlineProgressionService = mockk()
          backgroundExecutor = mockk()
          combatService = mockk()
          jwt = mockk()
        }

      every { endpoint.combatService.start(playerId) } returns
        com.wafuri.idle.application.model.CombatSnapshot(
          playerId = playerId,
          status = CombatStatus.FIGHTING,
          zoneId = "starter-plains",
          activeTeamId = UUID.randomUUID(),
          enemyName = "Training Dummy",
          enemyHp = 100f,
          enemyMaxHp = 100f,
          teamDps = 10f,
          members = emptyList(),
        )
      val connection = mockk<WebSocketConnection>()
      every { connection.pathParam("playerId") } returns playerId.toString()
      every { endpoint.jwt.subject } returns playerId.toString()
      every { endpoint.playerStateChangeTracker.invalidate(playerId) } just runs
      every { endpoint.playerStateWorkQueue.markDirty(playerId) } just runs

      val response = endpoint.onMessage(PlayerSocketCommand(PlayerSocketCommandType.START_COMBAT), connection)
      val combatResponse = response as CombatStateMessage

      combatResponse.playerId shouldBe playerId
      combatResponse.snapshot?.status shouldBe CombatStatus.FIGHTING
      verifyOrder {
        endpoint.combatService.start(playerId)
        endpoint.playerStateChangeTracker.invalidate(playerId)
        endpoint.playerStateWorkQueue.markDirty(playerId)
      }
    }

    "requireAuthorizedPlayer rejects a different player id" {
      val endpoint = PlayerWebSocketEndpoint()
      endpoint.jwt = mockk()
      val authenticatedPlayerId = UUID.randomUUID()
      val requestedPlayerId = UUID.randomUUID()
      every { endpoint.jwt.subject } returns authenticatedPlayerId.toString()

      shouldThrow<AuthorizationException> {
        endpoint.requireAuthorizedPlayer(requestedPlayerId.toString())
      }
    }
  })
