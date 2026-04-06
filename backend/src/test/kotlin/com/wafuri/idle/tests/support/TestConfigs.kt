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
  characterPullGoldCost: Int = 250,
  duplicateEssence: Int = 15,
  damageInterval: Duration = Duration.ofSeconds(1),
  respawnDelay: Duration = Duration.ofSeconds(1),
  reviveDelay: Duration = Duration.ofSeconds(30),
  reviveHpRatio: Float = 0.5f,
  zoneHpScalingConstant: Float = 1f,
  enemyAttackScalingExponent: Float = 1.26f,
  rewardScalingExponent: Float = 0.37f,
  tutorialEndLevel: Int = 19,
  tutorialGrowthRate: Float = 0.005f,
  normalGrowthRate: Float = 0.02f,
  spikeStartLevel: Int = 20,
  spikeGrowthFactor: Float = 0.15f,
  spikeSpacingStart: Float = 10f,
  spikeSpacingMax: Float = 50f,
  spikeSpacingRamp: Float = 300f,
  baseItemDropRate: Float = 0.01f,
  itemStatMultiplierPerLevel: Float = 0.08f,
  commonRarityWeight: Float = 80f,
  rareRarityWeight: Float = 15f,
  epicRarityWeight: Float = 4f,
  legendaryRarityWeight: Float = 1f,
  sessionDuration: Duration = Duration.ofHours(12),
  authIssuer: String = "wafuri-idle-test",
  killExperience: Int = 10,
  experiencePerLevel: Int = 85,
  killGold: Int = 25,
  zoneKillsPerLevel: Int = 9,
  zoneProgressMultiplier: Float = 16f,
  offlineNotifyThreshold: Duration = Duration.ofMinutes(5),
  contentRefreshInterval: Duration = Duration.ofMinutes(1),
): GameConfig {
  val tickConfig = mockk<GameConfig.Tick>()
  val teamConfig = mockk<GameConfig.Team>()
  val combatConfig = mockk<GameConfig.Combat>()
  val gachaConfig = mockk<GameConfig.Gacha>()
  val characterPullConfig = mockk<GameConfig.CharacterPull>()
  val lootConfig = mockk<GameConfig.Loot>()
  val itemLevelConfig = mockk<GameConfig.ItemLevel>()
  val rarityConfig = mockk<GameConfig.Rarity>()
  val authConfig = mockk<GameConfig.Auth>()
  val progressionConfig = mockk<GameConfig.Progression>()
  val playerProgressionConfig = mockk<GameConfig.Player>()
  val zoneProgressionConfig = mockk<GameConfig.Zone>()
  val zoneScalingConfig = mockk<GameConfig.ZoneScaling>()
  val offlineProgressionConfig = mockk<GameConfig.Offline>()
  val contentConfig = mockk<GameConfig.Content>()
  val gameConfig = mockk<GameConfig>()

  every { tickConfig.interval() } returns tickInterval
  every { tickConfig.publishJitterMax() } returns publishJitterMax
  every { teamConfig.initialSlots() } returns initialTeamSlots
  every { teamConfig.starterChoices() } returns starterChoices
  every { gachaConfig.characterPull() } returns characterPullConfig
  every { characterPullConfig.goldCost() } returns characterPullGoldCost
  every { characterPullConfig.duplicateEssence() } returns duplicateEssence
  every { combatConfig.damageInterval() } returns damageInterval
  every { combatConfig.respawnDelay() } returns respawnDelay
  every { combatConfig.reviveDelay() } returns reviveDelay
  every { combatConfig.reviveHpRatio() } returns reviveHpRatio
  every { combatConfig.zoneScaling() } returns zoneScalingConfig
  every { combatConfig.loot() } returns lootConfig
  every { lootConfig.baseItemDropRate() } returns baseItemDropRate
  every { lootConfig.itemLevel() } returns itemLevelConfig
  every { lootConfig.rarity() } returns rarityConfig
  every { itemLevelConfig.statMultiplierPerLevel() } returns itemStatMultiplierPerLevel
  every { rarityConfig.common() } returns commonRarityWeight
  every { rarityConfig.rare() } returns rareRarityWeight
  every { rarityConfig.epic() } returns epicRarityWeight
  every { rarityConfig.legendary() } returns legendaryRarityWeight
  every { authConfig.sessionDuration() } returns sessionDuration
  every { authConfig.issuer() } returns authIssuer
  every { progressionConfig.player() } returns playerProgressionConfig
  every { progressionConfig.zone() } returns zoneProgressionConfig
  every { progressionConfig.offline() } returns offlineProgressionConfig
  every { zoneScalingConfig.hpScalingConstant() } returns zoneHpScalingConstant
  every { zoneScalingConfig.enemyAttackScalingExponent() } returns enemyAttackScalingExponent
  every { zoneScalingConfig.rewardScalingExponent() } returns rewardScalingExponent
  every { zoneScalingConfig.tutorialEndLevel() } returns tutorialEndLevel
  every { zoneScalingConfig.tutorialGrowthRate() } returns tutorialGrowthRate
  every { zoneScalingConfig.normalGrowthRate() } returns normalGrowthRate
  every { zoneScalingConfig.spikeStartLevel() } returns spikeStartLevel
  every { zoneScalingConfig.spikeGrowthFactor() } returns spikeGrowthFactor
  every { zoneScalingConfig.spikeSpacingStart() } returns spikeSpacingStart
  every { zoneScalingConfig.spikeSpacingMax() } returns spikeSpacingMax
  every { zoneScalingConfig.spikeSpacingRamp() } returns spikeSpacingRamp
  every { playerProgressionConfig.killExperience() } returns killExperience
  every { playerProgressionConfig.experiencePerLevel() } returns experiencePerLevel
  every { playerProgressionConfig.killGold() } returns killGold
  every { zoneProgressionConfig.killsPerLevel() } returns zoneKillsPerLevel
  every { zoneProgressionConfig.progressMultiplier() } returns zoneProgressMultiplier
  every { offlineProgressionConfig.notifyThreshold() } returns offlineNotifyThreshold
  every { contentConfig.refreshInterval() } returns contentRefreshInterval
  every { gameConfig.tick() } returns tickConfig
  every { gameConfig.team() } returns teamConfig
  every { gameConfig.gacha() } returns gachaConfig
  every { gameConfig.combat() } returns combatConfig
  every { gameConfig.auth() } returns authConfig
  every { gameConfig.progression() } returns progressionConfig
  every { gameConfig.content() } returns contentConfig

  return gameConfig
}
