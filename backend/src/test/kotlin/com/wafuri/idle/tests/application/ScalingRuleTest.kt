package com.wafuri.idle.tests.application

import com.wafuri.idle.application.service.scaling.ScalingRule
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Rarity
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatType
import com.wafuri.idle.tests.support.gameConfig
import com.wafuri.idle.tests.support.swordItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class ScalingRuleTest :
  StringSpec({
    val scalingRule = ScalingRule(gameConfig())
    val checkpoints =
      listOf(
        ScalingCheckpoint(
          zoneLevel = 1,
          zoneMultiplier = "1.000",
          rewardMultiplier = "1.000",
          enemyHpForBase100 = "100.000",
          scaledBaseStat = "10.800",
          scaledSubStat = "3.500",
        ),
        ScalingCheckpoint(
          zoneLevel = 15,
          zoneMultiplier = "1.072",
          rewardMultiplier = "1.036",
          enemyHpForBase100 = "107.232",
          scaledBaseStat = "22.896",
          scaledSubStat = "7.420",
        ),
        ScalingCheckpoint(
          zoneLevel = 50,
          zoneMultiplier = "3.074",
          rewardMultiplier = "1.753",
          enemyHpForBase100 = "307.388",
          scaledBaseStat = "53.136",
          scaledSubStat = "17.220",
        ),
        ScalingCheckpoint(
          zoneLevel = 100,
          zoneMultiplier = "10.942",
          rewardMultiplier = "3.308",
          enemyHpForBase100 = "1094.187",
          scaledBaseStat = "96.336",
          scaledSubStat = "31.220",
        ),
        ScalingCheckpoint(
          zoneLevel = 1000,
          zoneMultiplier = "5628647424.000",
          rewardMultiplier = "75024.313",
          enemyHpForBase100 = "562864717824.000",
          scaledBaseStat = "873.936",
          scaledSubStat = "283.220",
        ),
      )

    "zone multiplier matches the documented checkpoint values" {
      checkpoints.forEach { checkpoint ->
        rounded3(scalingRule.zoneMultiplier(checkpoint.zoneLevel)) shouldBe checkpoint.zoneMultiplier
      }
    }

    "reward multiplier matches the documented checkpoint values" {
      checkpoints.forEach { checkpoint ->
        rounded3(scalingRule.rewardMultiplier(checkpoint.zoneLevel)) shouldBe checkpoint.rewardMultiplier
      }
    }

    "enemy HP scaling matches the documented checkpoint values for base HP 100" {
      checkpoints.forEach { checkpoint ->
        rounded3(scalingRule.enemyHpFor(zoneLevel = checkpoint.zoneLevel, baseEnemyHp = 100f)) shouldBe
          checkpoint.enemyHpForBase100
      }
    }

    "item stat scaling matches the documented checkpoint values" {
      checkpoints.forEach { checkpoint ->
        val inventoryItem =
          InventoryItem(
            id = UUID.randomUUID(),
            playerId = UUID.randomUUID(),
            item = swordItem(),
            itemLevel = checkpoint.zoneLevel,
            subStats = listOf(Stat(StatType.AGILITY, 3.5f)),
            rarity = Rarity.COMMON,
          )

        rounded3(scalingRule.scaledBaseStat(inventoryItem).value) shouldBe checkpoint.scaledBaseStat
        rounded3(scalingRule.scaledSubStats(inventoryItem).single().value) shouldBe checkpoint.scaledSubStat
      }
    }
  })

private data class ScalingCheckpoint(
  val zoneLevel: Int,
  val zoneMultiplier: String,
  val rewardMultiplier: String,
  val enemyHpForBase100: String,
  val scaledBaseStat: String,
  val scaledSubStat: String,
)

private fun rounded3(value: Float): String = BigDecimal.valueOf(value.toDouble()).setScale(3, RoundingMode.HALF_UP).toPlainString()
