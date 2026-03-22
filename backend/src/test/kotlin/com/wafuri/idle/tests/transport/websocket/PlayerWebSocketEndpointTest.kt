package com.wafuri.idle.tests.transport.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.port.out.PlayerStateChangeTracker
import com.wafuri.idle.application.port.out.PlayerStateWorkQueue
import com.wafuri.idle.application.service.combat.CombatService
import com.wafuri.idle.application.service.player.OfflineProgressionService
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.transport.websocket.PlayerSocketCommand
import com.wafuri.idle.transport.websocket.PlayerSocketCommandType
import com.wafuri.idle.transport.websocket.PlayerWebSocketEndpoint
import com.wafuri.idle.transport.websocket.PlayerWebSocketRegistry
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifyOrder
import jakarta.websocket.RemoteEndpoint
import jakarta.websocket.Session
import org.eclipse.microprofile.context.ManagedExecutor
import java.util.UUID

class PlayerWebSocketEndpointTest :
  StringSpec({
    "onOpen registers first, marks dirty, and applies offline progression off the io thread" {
      val playerId = UUID.randomUUID()
      val session = mockk<Session>()
      val registry = mockk<PlayerWebSocketRegistry>()
      val playerStateChangeTracker = mockk<PlayerStateChangeTracker>()
      val playerStateWorkQueue = mockk<PlayerStateWorkQueue>()
      val offlineProgressionService = mockk<OfflineProgressionService>()
      val backgroundExecutor = mockk<ManagedExecutor>()
      val combatService = mockk<CombatService>()
      val endpoint =
        PlayerWebSocketEndpoint().apply {
          this.registry = registry
          this.playerStateChangeTracker = playerStateChangeTracker
          this.playerStateWorkQueue = playerStateWorkQueue
          this.offlineProgressionService = offlineProgressionService
          this.backgroundExecutor = backgroundExecutor
          this.combatService = combatService
          this.objectMapper = jacksonObjectMapper()
        }

      every { offlineProgressionService.applyIfNeeded(playerId) } returns null
      every { playerStateChangeTracker.invalidate(playerId) } just runs
      every { registry.register(playerId, session) } just runs
      every { playerStateWorkQueue.markDirty(playerId) } just runs
      every { backgroundExecutor.execute(any()) } answers {
        firstArg<Runnable>().run()
      }

      endpoint.onOpen(session, playerId.toString())

      verifyOrder {
        registry.register(playerId, session)
        playerStateChangeTracker.invalidate(playerId)
        playerStateWorkQueue.markDirty(playerId)
        offlineProgressionService.applyIfNeeded(playerId)
        playerStateChangeTracker.invalidate(playerId)
        playerStateWorkQueue.markDirty(playerId)
      }
    }

    "onMessage starts combat from websocket command" {
      val playerId = UUID.randomUUID()
      val objectMapper = mockk<ObjectMapper>()
      val endpoint =
        PlayerWebSocketEndpoint().apply {
          registry = mockk()
          playerStateChangeTracker = mockk()
          playerStateWorkQueue = mockk()
          offlineProgressionService = mockk()
          backgroundExecutor = mockk()
          combatService = mockk()
          this.objectMapper = objectMapper
        }

      every { endpoint.combatService.start(playerId) } returns
        CombatSnapshot(
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
      val session = mockk<Session>()
      val basicRemote = mockk<RemoteEndpoint.Basic>()
      every { session.pathParameters } returns mapOf("playerId" to playerId.toString())
      every { session.basicRemote } returns basicRemote
      every { basicRemote.sendText(any()) } just runs
      every {
        objectMapper.readValue(
          """{"type":"START_COMBAT"}""",
          any<Class<PlayerSocketCommand>>(),
        )
      } returns PlayerSocketCommand(PlayerSocketCommandType.START_COMBAT)
      every { objectMapper.writeValueAsString(any()) } returns "{}"
      every { endpoint.playerStateChangeTracker.invalidate(playerId) } just runs
      every { endpoint.playerStateWorkQueue.markDirty(playerId) } just runs
      every { endpoint.backgroundExecutor.execute(any()) } answers {
        firstArg<Runnable>().run()
      }

      endpoint.onMessage("""{"type":"START_COMBAT"}""", session)

      verifyOrder {
        endpoint.backgroundExecutor.execute(any())
        endpoint.combatService.start(playerId)
        endpoint.playerStateChangeTracker.invalidate(playerId)
        endpoint.playerStateWorkQueue.markDirty(playerId)
      }
    }
  })
