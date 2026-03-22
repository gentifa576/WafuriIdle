package com.wafuri.idle.tests.support

import com.wafuri.idle.application.config.GameConfig
import io.mockk.every
import io.mockk.mockk
import java.time.Duration

fun gameConfig(
  tickInterval: Duration = Duration.ofMillis(200),
  publishJitterMax: Duration = Duration.ZERO,
  initialTeamSlots: Int = 3,
  enemyMaxHp: Float = 1000f,
  damageInterval: Duration = Duration.ofSeconds(1),
  respawnDelay: Duration = Duration.ofSeconds(1),
  contentRefreshInterval: Duration = Duration.ofMinutes(1),
): GameConfig {
  val tickConfig = mockk<GameConfig.Tick>()
  val teamConfig = mockk<GameConfig.Team>()
  val combatConfig = mockk<GameConfig.Combat>()
  val contentConfig = mockk<GameConfig.Content>()
  val gameConfig = mockk<GameConfig>()

  every { tickConfig.interval() } returns tickInterval
  every { tickConfig.publishJitterMax() } returns publishJitterMax
  every { teamConfig.initialSlots() } returns initialTeamSlots
  every { combatConfig.enemyMaxHp() } returns enemyMaxHp
  every { combatConfig.damageInterval() } returns damageInterval
  every { combatConfig.respawnDelay() } returns respawnDelay
  every { contentConfig.refreshInterval() } returns contentRefreshInterval
  every { gameConfig.tick() } returns tickConfig
  every { gameConfig.team() } returns teamConfig
  every { gameConfig.combat() } returns combatConfig
  every { gameConfig.content() } returns contentConfig

  return gameConfig
}
