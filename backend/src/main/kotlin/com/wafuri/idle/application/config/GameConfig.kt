package com.wafuri.idle.application.config

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "game")
interface GameConfig {
  fun tick(): Tick

  fun team(): Team

  fun combat(): Combat

  fun content(): Content

  interface Tick {
    fun interval(): Duration

    fun publishJitterMax(): Duration
  }

  interface Team {
    fun initialSlots(): Int
  }

  interface Combat {
    fun enemyMaxHp(): Float

    fun damageInterval(): Duration

    fun respawnDelay(): Duration

    fun loot(): Loot
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
}
