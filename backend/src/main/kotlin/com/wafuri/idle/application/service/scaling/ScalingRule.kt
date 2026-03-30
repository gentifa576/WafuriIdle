package com.wafuri.idle.application.service.scaling

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Stat
import jakarta.enterprise.context.ApplicationScoped
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@ApplicationScoped
class ScalingRule(
  private val gameConfig: GameConfig,
) {
  fun enemyHpFor(
    zoneLevel: Int,
    baseEnemyHp: Float,
  ): Float = baseEnemyHp * zoneMultiplier(zoneLevel) * gameConfig.combat().zoneScaling().hpScalingConstant()

  fun rewardMultiplier(zoneLevel: Int): Float =
    zoneMultiplier(zoneLevel)
      .toDouble()
      .pow(
        gameConfig
          .combat()
          .zoneScaling()
          .rewardScalingExponent()
          .toDouble(),
      ).toFloat()

  fun scaledBaseStat(inventoryItem: InventoryItem): Stat = scaleStat(inventoryItem.item.baseStat, inventoryItem.itemLevel)

  fun scaledSubStats(inventoryItem: InventoryItem): List<Stat> = inventoryItem.subStats.map { scaleStat(it, inventoryItem.itemLevel) }

  private fun scaleStat(
    stat: Stat,
    itemLevel: Int,
  ): Stat {
    val perLevelMultiplier =
      gameConfig
        .combat()
        .loot()
        .itemLevel()
        .statMultiplierPerLevel()
    val multiplier = 1f + (perLevelMultiplier * (itemLevel - 1).coerceAtLeast(0))
    return stat.copy(value = stat.value * multiplier)
  }

  /**
   Formula reference and tuning playground:
   https://www.desmos.com/calculator/bvrblgspsh

   Desmos names:
   u = tutorialEndLevel
   q = tutorialGrowthRate
   n = normalGrowthRate
   t = spikeStartLevel
   p = spikeGrowthFactor
   spikeSpacingStart = early W(x)
   spikeSpacingMax = late W(x)
   spikeSpacingRamp = widening ramp

   Implemented formula:

   tutorialLevels = min(zoneLevel - 1, tutorialEndLevel - 1)
   postTutorialLevels = max(zoneLevel - tutorialEndLevel, 0)

   smoothMultiplier =
   (1 + tutorialGrowthRate) ^ tutorialLevels *
   (1 + normalGrowthRate) ^ postTutorialLevels

   levelSinceSpikeStart = max(zoneLevel - spikeStartLevel, 0)

   wideningFactor =
   if spikeSpacingRamp <= 0:
   1
   else:
   1 - e ^ (-levelSinceSpikeStart / spikeSpacingRamp)

   spikeSpacing =
   spikeSpacingStart +
   (spikeSpacingMax - spikeSpacingStart) * wideningFactor

   spikeCount =
   0, when zoneLevel < spikeStartLevel
   floor((zoneLevel - spikeStartLevel) / spikeSpacing) + 1, otherwise

   zoneMultiplier =
   smoothMultiplier * (1 + spikeGrowthFactor) ^ spikeCount

   enemyHp =
   enemyBaseHp * zoneMultiplier * hpScalingConstant

   rewardMultiplier =
   zoneMultiplier ^ rewardScalingExponent

   itemStatValue =
   templateStatValue * (1 + statMultiplierPerLevel * (itemLevel - 1))
   **/
  fun zoneMultiplier(zoneLevel: Int): Float {
    require(zoneLevel >= 1) { "Zone level must be at least 1." }
    val config = gameConfig.combat().zoneScaling()
    val tutorialLevels = min(zoneLevel - 1, config.tutorialEndLevel() - 1).coerceAtLeast(0)
    val postTutorialLevels = max(zoneLevel - config.tutorialEndLevel(), 0)
    val smoothMultiplier =
      (1f + config.tutorialGrowthRate()).pow(tutorialLevels) *
        (1f + config.normalGrowthRate()).pow(postTutorialLevels)
    val spikeMultiplier = (1f + config.spikeGrowthFactor()).pow(spikeCount(zoneLevel))
    return smoothMultiplier * spikeMultiplier
  }

  private fun spikeCount(zoneLevel: Int): Int {
    require(zoneLevel >= 1) { "Zone level must be at least 1." }
    val config = gameConfig.combat().zoneScaling()
    if (zoneLevel < config.spikeStartLevel()) {
      return 0
    }
    val spacing = spikeSpacing(zoneLevel).coerceAtLeast(1f)
    return floor((zoneLevel - config.spikeStartLevel()) / spacing).toInt() + 1
  }

  private fun spikeSpacing(zoneLevel: Int): Float {
    require(zoneLevel >= 1) { "Zone level must be at least 1." }
    val config = gameConfig.combat().zoneScaling()
    val levelSinceSpikeStart = max(zoneLevel - config.spikeStartLevel(), 0)
    val wideningFactor =
      if (config.spikeSpacingRamp() <= 0f) {
        1.0
      } else {
        1.0 - exp(-levelSinceSpikeStart / config.spikeSpacingRamp().toDouble())
      }
    val spacing =
      config.spikeSpacingStart() +
        ((config.spikeSpacingMax() - config.spikeSpacingStart()) * wideningFactor.toFloat())
    return spacing.coerceAtLeast(config.spikeSpacingStart())
  }
}
