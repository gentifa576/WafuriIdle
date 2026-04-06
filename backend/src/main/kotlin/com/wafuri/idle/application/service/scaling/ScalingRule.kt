package com.wafuri.idle.application.service.scaling

import com.wafuri.idle.application.config.GameConfig
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.domain.model.StatType
import jakarta.enterprise.context.ApplicationScoped
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@ApplicationScoped
class ScalingRule(
  private val gameConfig: GameConfig,
) {
  fun enemyHpFor(
    zoneLevel: Int,
    baseEnemyHp: Float,
  ): Float = baseEnemyHp * zoneMultiplier(zoneLevel) * gameConfig.combat().zoneScaling().hpScalingConstant()

  fun enemyAttackFor(
    zoneLevel: Int,
    baseEnemyAttack: Float,
  ): Float =
    baseEnemyAttack *
      zoneMultiplier(zoneLevel)
        .toDouble()
        .pow(
          gameConfig
            .combat()
            .zoneScaling()
            .enemyAttackScalingExponent()
            .toDouble(),
        ).toFloat()

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

  fun scaledStrength(
    growth: StatGrowth,
    playerLevel: Int,
  ): Float = scaledCharacterStat(growth, playerLevel)

  fun scaledAgility(
    growth: StatGrowth,
    playerLevel: Int,
  ): Float = scaledCharacterStat(growth, playerLevel)

  fun scaledVitality(
    growth: StatGrowth,
    playerLevel: Int,
  ): Float = scaledCharacterStat(growth, playerLevel)

  fun hitForAgility(agility: Float): Float {
    require(agility >= 0f) { "Agility must not be negative." }
    return max(1f, sqrt(agility))
  }

  fun attackForStrength(strength: Float): Float {
    require(strength >= 0f) { "Strength must not be negative." }
    return strength * ATTACK_PER_STRENGTH
  }

  fun hpForVitality(vitality: Float): Float {
    require(vitality >= 0f) { "Vitality must not be negative." }
    return vitality * HP_PER_VITALITY
  }

  fun scaledBaseStat(inventoryItem: InventoryItem): Stat = scaleStat(inventoryItem.item.baseStat, inventoryItem.itemLevel)

  fun scaledSubStats(inventoryItem: InventoryItem): List<Stat> = inventoryItem.subStats.map { scaleStat(it, inventoryItem.itemLevel) }

  fun totalExperienceForLevel(level: Int): Int {
    require(level >= 1) { "Player level must be at least 1." }
    if (level == 1) {
      return 0
    }
    val baseThreshold = playerProgressionThreshold(level)
    val normalizedThreshold = baseThreshold * playerThresholdScale()
    return normalizedThreshold.roundToInt()
  }

  fun playerLevelForExperience(experience: Int): Int {
    require(experience >= 0) { "Experience must not be negative." }
    return levelForTotal(experience, ::totalExperienceForLevel)
  }

  fun totalKillsForZoneLevel(level: Int): Int {
    require(level >= 1) { "Zone level must be at least 1." }
    if (level == 1) {
      return 0
    }
    val baseThreshold = zoneProgressionThreshold(level)
    val normalizedThreshold = baseThreshold * zoneThresholdScale()
    return normalizedThreshold.roundToInt()
  }

  fun zoneLevelForKillCount(killCount: Int): Int {
    require(killCount >= 0) { "Zone kill count must not be negative." }
    return levelForTotal(killCount, ::totalKillsForZoneLevel)
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

  private fun scaleStat(
    stat: Stat,
    itemLevel: Int,
  ): Stat {
    require(itemLevel >= 1) { "Item level must be at least 1." }
    val levelOffset = (itemLevel - 1).toFloat()
    val earlyGrowth = levelOffset.toDouble().pow(ITEM_CURVE.earlyExponent).toFloat()
    val midGrowth =
      ITEM_CURVE.midMultiplier *
        max(itemLevel - ITEM_CURVE.midStartLevel, 0).toFloat().pow(ITEM_CURVE.midExponent)
    val lateGrowth =
      ITEM_CURVE.lateMultiplier *
        max(itemLevel - ITEM_CURVE.lateStartLevel, 0).toFloat().pow(ITEM_CURVE.lateExponent)
    val growthFactor = 1f + earlyGrowth + midGrowth + lateGrowth
    val scalingWeight =
      when (stat.type) {
        StatType.STRENGTH -> ITEM_STAT_WEIGHTS.strength
        StatType.AGILITY -> ITEM_STAT_WEIGHTS.agility
        StatType.VITALITY -> ITEM_STAT_WEIGHTS.vitality
        else -> ITEM_STAT_WEIGHTS.default
      }
    val multiplier = 1f + (scalingWeight * (growthFactor - 1f))
    return stat.copy(value = stat.value * multiplier)
  }

  private fun playerProgressionThreshold(level: Int): Float = thresholdForLevel(level, PLAYER_THRESHOLD_CURVE)

  private fun zoneProgressionThreshold(level: Int): Float = thresholdForLevel(level, ZONE_THRESHOLD_CURVE)

  private fun playerThresholdScale(): Float = gameConfig.progression().player().experiencePerLevel() / PLAYER_LEVEL_2_THRESHOLD

  private fun zoneThresholdScale(): Float = gameConfig.progression().zone().killsPerLevel() / ZONE_LEVEL_2_THRESHOLD

  private fun levelForTotal(
    total: Int,
    thresholdForLevel: (Int) -> Int,
  ): Int {
    var low = 1
    var high = 2
    while (thresholdForLevel(high) <= total) {
      low = high
      high = high * 2
    }
    while (low + 1 < high) {
      val mid = (low + high) / 2
      if (thresholdForLevel(mid) <= total) {
        low = mid
      } else {
        high = mid
      }
    }
    return low
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

  private fun scaledCharacterStat(
    growth: StatGrowth,
    playerLevel: Int,
  ): Float {
    require(playerLevel >= 1) { "Player level must be at least 1." }
    return growth.base + (growth.increment * playerProgressionFactor(playerLevel))
  }

  private fun playerProgressionFactor(playerLevel: Int): Float {
    val x = max(playerLevel - 1, 0)
    return 14f * (1f - exp(-(x / 26f)))
  }

  private fun thresholdForLevel(
    level: Int,
    curve: ThresholdCurve,
  ): Float {
    if (level <= 1) {
      return 0f
    }
    val n = (level - 1).toFloat()
    val level20Anchor = (curve.earlyQuadratic * 19f * 19f) + (curve.earlyLinear * 19f)
    if (level <= 20) {
      return (curve.earlyQuadratic * n * n) + (curve.earlyLinear * n)
    }
    if (level <= 50) {
      val m = (level - 20).toFloat()
      return level20Anchor + (curve.midQuadratic * m * m) + (curve.midLinear * m)
    }
    val k = (level - 50).toFloat()
    return level20Anchor +
      (curve.midQuadratic * 30f * 30f) +
      (curve.midLinear * 30f) +
      (curve.lateQuadratic * k * k) +
      (curve.lateLinear * k)
  }

  companion object {
    private val PLAYER_THRESHOLD_CURVE = ThresholdCurve(5f, 80f, 65f, 1050f, 4f, 150f)
    private val ZONE_THRESHOLD_CURVE = ThresholdCurve(1f, 8f, 2f, 45f, 6f, 120f)
    private val ITEM_CURVE =
      ItemCurve(
        earlyExponent = 0.55,
        midStartLevel = 24,
        midMultiplier = 0.39f,
        midExponent = 1.12f,
        lateStartLevel = 55,
        lateMultiplier = 0.10f,
        lateExponent = 1.18f,
      )
    private val ITEM_STAT_WEIGHTS =
      ItemStatWeights(
        strength = 1.4f,
        agility = 1.4f,
        vitality = 0.5f,
        default = 1.0f,
      )
    private const val PLAYER_LEVEL_2_THRESHOLD = 85f
    private const val ZONE_LEVEL_2_THRESHOLD = 9f
    private const val ATTACK_PER_STRENGTH = 1.75f
    private const val HP_PER_VITALITY = 50f
  }

  private data class ThresholdCurve(
    val earlyQuadratic: Float,
    val earlyLinear: Float,
    val midQuadratic: Float,
    val midLinear: Float,
    val lateQuadratic: Float,
    val lateLinear: Float,
  )

  private data class ItemCurve(
    val earlyExponent: Double,
    val midStartLevel: Int,
    val midMultiplier: Float,
    val midExponent: Float,
    val lateStartLevel: Int,
    val lateMultiplier: Float,
    val lateExponent: Float,
  )

  private data class ItemStatWeights(
    val strength: Float,
    val agility: Float,
    val vitality: Float,
    val default: Float,
  )
}
