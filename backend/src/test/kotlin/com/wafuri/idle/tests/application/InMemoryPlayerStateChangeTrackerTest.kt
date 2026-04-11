package com.wafuri.idle.tests.application

import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.persistence.runtime.InMemoryPlayerStateChangeTracker
import com.wafuri.idle.tests.support.expectedCombatMemberSnapshot
import com.wafuri.idle.tests.support.expectedCombatSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class InMemoryPlayerStateChangeTrackerTest :
  StringSpec({
    "combat publish suppresses repeated down snapshots until combat leaves down" {
      val tracker = InMemoryPlayerStateChangeTracker()
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val downSnapshot =
        expectedCombatSnapshot(
          playerId,
          CombatStatus.DOWN,
          "starter-plains",
          teamId,
          "Training Slime",
          1f,
          19f,
          20f,
          0f,
          pendingReviveMillis = 12_000,
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 0f, 24f, false)),
        )
      val laterDownSnapshot =
        downSnapshot.copy(pendingReviveMillis = 13_000)
      val fightingSnapshot =
        downSnapshot.copy(
          status = CombatStatus.FIGHTING,
          enemyHp = 20f,
          pendingReviveMillis = 0,
          teamDps = 5.5f,
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 12f, 24f, true)),
        )

      tracker.shouldPublishCombatState(playerId, downSnapshot) shouldBe true
      tracker.shouldPublishCombatState(playerId, laterDownSnapshot) shouldBe false
      tracker.shouldPublishCombatState(playerId, fightingSnapshot) shouldBe true
    }

    "combat publish emits once when combat clears to idle" {
      val tracker = InMemoryPlayerStateChangeTracker()
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val fightingSnapshot =
        expectedCombatSnapshot(
          playerId,
          CombatStatus.FIGHTING,
          "starter-plains",
          teamId,
          "Training Slime",
          1f,
          20f,
          20f,
          5.5f,
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 24f, 24f, true)),
        )

      tracker.shouldPublishCombatState(playerId, fightingSnapshot) shouldBe true
      tracker.shouldPublishCombatState(playerId, null) shouldBe true
      tracker.shouldPublishCombatState(playerId, null) shouldBe false
    }

    "combat publish suppresses cooldown-only drift while cooldown remains above zero" {
      val tracker = InMemoryPlayerStateChangeTracker()
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val firstSnapshot =
        expectedCombatSnapshot(
          playerId,
          CombatStatus.FIGHTING,
          "starter-plains",
          teamId,
          "Training Slime",
          1f,
          20f,
          20f,
          5.5f,
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 24f, 24f, true, skillCooldownRemainingMillis = 2_100L)),
        )
      val cooldownOnlySnapshot =
        firstSnapshot.copy(
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 24f, 24f, true, skillCooldownRemainingMillis = 1_900L)),
        )

      tracker.shouldPublishCombatState(playerId, firstSnapshot) shouldBe true
      tracker.shouldPublishCombatState(playerId, cooldownOnlySnapshot) shouldBe false
    }

    "combat publish emits when cooldown becomes ready without other combat changes" {
      val tracker = InMemoryPlayerStateChangeTracker()
      val playerId = UUID.randomUUID()
      val teamId = UUID.randomUUID()
      val coolingSnapshot =
        expectedCombatSnapshot(
          playerId,
          CombatStatus.FIGHTING,
          "starter-plains",
          teamId,
          "Training Slime",
          1f,
          20f,
          20f,
          5.5f,
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 24f, 24f, true, skillCooldownRemainingMillis = 300L)),
        )
      val readySnapshot =
        coolingSnapshot.copy(
          members = listOf(expectedCombatMemberSnapshot("hero", 10f, 0.55f, 24f, 24f, true, skillCooldownRemainingMillis = null)),
        )

      tracker.shouldPublishCombatState(playerId, coolingSnapshot) shouldBe true
      tracker.shouldPublishCombatState(playerId, readySnapshot) shouldBe true
    }
  })
