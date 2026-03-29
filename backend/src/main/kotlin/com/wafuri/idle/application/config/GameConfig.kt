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
    fun enemyMaxHp(): Float

    fun damageInterval(): Duration

    fun respawnDelay(): Duration

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

    fun rarity(): Rarity
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
  }

  interface Offline {
    fun notifyThreshold(): Duration
  }
}
