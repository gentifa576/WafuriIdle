package com.wafuri.idle.domain.model

data class StatGrowth(
  val base: Float,
  val increment: Float,
) {
  fun atLevel(level: Int): Float {
    require(level >= 1) { "Stat growth level must be at least 1." }
    return base + (increment * (level - 1))
  }
}
