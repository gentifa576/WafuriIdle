package com.wafuri.idle.tests.application

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.application.service.combat.CombatTickService
import com.wafuri.idle.application.service.tick.CombatLoop
import com.wafuri.idle.tests.support.gameConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class CombatLoopTest : StringSpec() {
  private lateinit var combatStateRepository: CombatStateRepository
  private lateinit var combatTickService: CombatTickService
  private lateinit var loop: CombatLoop

  init {
    beforeTest {
      combatStateRepository = mockk()
      combatTickService = mockk()
      loop =
        CombatLoop(
          combatStateRepository,
          combatTickService,
          gameConfig(Duration.ofMillis(10)),
        )
    }

    "combat loop starts zone jobs and stops them when the zone becomes empty" {
      val zoneId = "starter-plains"
      val tickCount = AtomicInteger(0)
      var activeZones: Set<String> = setOf(zoneId)

      every { combatStateRepository.findActiveZoneIds() } answers { activeZones }
      coEvery {
        combatTickService.tickZone(zoneId, any())
      } answers {
        tickCount.incrementAndGet()
        Unit
      }

      loop.start()
      Thread.sleep(50)

      loop.activeZoneJobIds() shouldContain zoneId
      tickCount.get() shouldBeGreaterThan 0

      activeZones = emptySet()
      Thread.sleep(40)

      loop.activeZoneJobIds() shouldNotContain zoneId
      loop.stop()
    }

    "combat loop does not create zone jobs when there are no active zones" {
      every { combatStateRepository.findActiveZoneIds() } returns emptySet()
      coEvery { combatTickService.tickZone(any(), any()) } returns Unit

      loop.start()
      Thread.sleep(30)

      loop.activeZoneJobIds() shouldNotContain "starter-plains"
      loop.stop()
    }
  }
}
