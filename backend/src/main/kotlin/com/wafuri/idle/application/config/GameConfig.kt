package com.wafuri.idle.application.config

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "game")
interface GameConfig {
  fun tick(): Tick

  fun team(): Team

  fun gacha(): Gacha

  fun combat(): Combat

  fun auth(): Auth

  fun progression(): Progression

  fun content(): Content

  interface Tick {
    fun interval(): Duration

    fun publishJitterMax(): Duration
  }

  interface Team {
    fun initialSlots(): Int

    fun starterChoices(): List<String>
  }

  interface Combat {
    fun damageInterval(): Duration

    fun respawnDelay(): Duration

    fun reviveDelay(): Duration

    fun reviveHpRatio(): Float

    fun zoneScaling(): ZoneScaling

    fun loot(): Loot
  }

  interface Gacha {
    fun characterPull(): CharacterPull
  }

  interface CharacterPull {
    fun goldCost(): Int

    fun duplicateEssence(): Int
  }

  interface Loot {
    fun baseItemDropRate(): Float

    fun itemLevel(): ItemLevel

    fun rarity(): Rarity
  }

  interface ItemLevel {
    fun statMultiplierPerLevel(): Float
  }

  interface Rarity {
    fun common(): Float

    fun rare(): Float

    fun epic(): Float

    fun legendary(): Float
  }

  interface Content {
    fun refreshInterval(): Duration
  }

  interface Auth {
    fun sessionDuration(): Duration

    fun issuer(): String
  }

  interface Progression {
    fun player(): Player

    fun zone(): Zone

    fun offline(): Offline
  }

  interface Player {
    fun killExperience(): Int

    fun experiencePerLevel(): Int

    fun killGold(): Int
  }

  interface Zone {
    fun killsPerLevel(): Int

    fun progressMultiplier(): Float
  }

  interface ZoneScaling {
    fun hpScalingConstant(): Float

    fun enemyAttackScalingExponent(): Float

    fun rewardScalingExponent(): Float

    fun tutorialEndLevel(): Int

    fun tutorialGrowthRate(): Float

    fun normalGrowthRate(): Float

    fun spikeStartLevel(): Int

    fun spikeGrowthFactor(): Float

    fun spikeSpacingStart(): Float

    fun spikeSpacingMax(): Float

    fun spikeSpacingRamp(): Float
  }

  interface Offline {
    fun notifyThreshold(): Duration
  }
}
