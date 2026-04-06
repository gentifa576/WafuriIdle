package com.wafuri.idle.domain.model

import java.util.UUID

data class PlayerZoneProgress(
  val playerId: UUID,
  val zoneId: String,
  val killCount: Int = 0,
  val level: Int = 1,
) {
  init {
    require(zoneId.isNotBlank()) { "Zone id must not be blank." }
    require(killCount >= 0) { "Zone kill count must not be negative." }
    require(level >= 1) { "Zone level must be at least 1." }
  }

  fun recordKill(killsPerLevel: Int): PlayerZoneProgress {
    require(killsPerLevel > 0) { "Kills per level must be positive." }
    val nextKillCount = killCount + 1
    val nextLevel = (nextKillCount / killsPerLevel) + 1
    return copy(killCount = nextKillCount, level = nextLevel)
  }

  fun recordKill(levelForKillCount: (Int) -> Int): PlayerZoneProgress {
    val nextKillCount = killCount + 1
    val nextLevel = levelForKillCount(nextKillCount)
    require(nextLevel >= 1) { "Resolved zone level must be at least 1." }
    return copy(killCount = nextKillCount, level = nextLevel)
  }

  fun recordKills(
    killPoints: Int,
    levelForKillCount: (Int) -> Int,
  ): PlayerZoneProgress {
    require(killPoints >= 1) { "Zone progress gain must be at least 1." }
    val nextKillCount = killCount + killPoints
    val nextLevel = levelForKillCount(nextKillCount)
    require(nextLevel >= 1) { "Resolved zone level must be at least 1." }
    return copy(killCount = nextKillCount, level = nextLevel)
  }
}
