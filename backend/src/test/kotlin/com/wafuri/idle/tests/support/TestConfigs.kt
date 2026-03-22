package com.wafuri.idle.tests.support

import com.wafuri.idle.application.config.GameConfig
import io.mockk.every
import io.mockk.mockk
import java.time.Duration

fun gameConfig(
  tickInterval: Duration = Duration.ofMillis(200),
  publishJitterMax: Duration = Duration.ZERO,
  initialTeamSlots: Int = 3,
  starterChoices: List<String> = listOf("nimbus", "inaho", "vyron"),
  enemyMaxHp: Float = 1000f,
  damageInterval: Duration = Duration.ofSeconds(1),
  respawnDelay: Duration = Duration.ofSeconds(1),
  baseItemDropRate: Float = 0.01f,
  commonRarityWeight: Float = 80f,
  rareRarityWeight: Float = 15f,
  epicRarityWeight: Float = 4f,
  legendaryRarityWeight: Float = 1f,
  sessionDuration: Duration = Duration.ofHours(12),
  authIssuer: String = "wafuri-idle-test",
  killExperience: Int = 10,
  experiencePerLevel: Int = 100,
  zoneKillsPerLevel: Int = 10,
  offlineNotifyThreshold: Duration = Duration.ofMinutes(5),
  contentRefreshInterval: Duration = Duration.ofMinutes(1),
): GameConfig {
  val tickConfig = mockk<GameConfig.Tick>()
  val teamConfig = mockk<GameConfig.Team>()
  val combatConfig = mockk<GameConfig.Combat>()
  val lootConfig = mockk<GameConfig.Loot>()
  val rarityConfig = mockk<GameConfig.Rarity>()
  val authConfig = mockk<GameConfig.Auth>()
  val progressionConfig = mockk<GameConfig.Progression>()
  val playerProgressionConfig = mockk<GameConfig.Player>()
  val zoneProgressionConfig = mockk<GameConfig.Zone>()
  val offlineProgressionConfig = mockk<GameConfig.Offline>()
  val contentConfig = mockk<GameConfig.Content>()
  val gameConfig = mockk<GameConfig>()

  every { tickConfig.interval() } returns tickInterval
  every { tickConfig.publishJitterMax() } returns publishJitterMax
  every { teamConfig.initialSlots() } returns initialTeamSlots
  every { teamConfig.starterChoices() } returns starterChoices
  every { combatConfig.enemyMaxHp() } returns enemyMaxHp
  every { combatConfig.damageInterval() } returns damageInterval
  every { combatConfig.respawnDelay() } returns respawnDelay
  every { combatConfig.loot() } returns lootConfig
  every { lootConfig.baseItemDropRate() } returns baseItemDropRate
  every { lootConfig.rarity() } returns rarityConfig
  every { rarityConfig.common() } returns commonRarityWeight
  every { rarityConfig.rare() } returns rareRarityWeight
  every { rarityConfig.epic() } returns epicRarityWeight
  every { rarityConfig.legendary() } returns legendaryRarityWeight
  every { authConfig.sessionDuration() } returns sessionDuration
  every { authConfig.issuer() } returns authIssuer
  every { progressionConfig.player() } returns playerProgressionConfig
  every { progressionConfig.zone() } returns zoneProgressionConfig
  every { progressionConfig.offline() } returns offlineProgressionConfig
  every { playerProgressionConfig.killExperience() } returns killExperience
  every { playerProgressionConfig.experiencePerLevel() } returns experiencePerLevel
  every { zoneProgressionConfig.killsPerLevel() } returns zoneKillsPerLevel
  every { offlineProgressionConfig.notifyThreshold() } returns offlineNotifyThreshold
  every { contentConfig.refreshInterval() } returns contentRefreshInterval
  every { gameConfig.tick() } returns tickConfig
  every { gameConfig.team() } returns teamConfig
  every { gameConfig.combat() } returns combatConfig
  every { gameConfig.auth() } returns authConfig
  every { gameConfig.progression() } returns progressionConfig
  every { gameConfig.content() } returns contentConfig

  return gameConfig
}
