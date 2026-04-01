package com.wafuri.idle.tests.support

import com.wafuri.idle.application.exception.ValidationException
import com.wafuri.idle.application.model.CharacterCombatStats
import com.wafuri.idle.application.model.CharacterPull
import com.wafuri.idle.application.model.CharacterPullResult
import com.wafuri.idle.application.model.CombatMemberSnapshot
import com.wafuri.idle.application.model.CombatSnapshot
import com.wafuri.idle.application.model.EventType
import com.wafuri.idle.application.model.InventoryItemSnapshot
import com.wafuri.idle.application.model.OfflineProgressionMessage
import com.wafuri.idle.application.model.OfflineRewardSummary
import com.wafuri.idle.application.model.OwnedCharacterSnapshot
import com.wafuri.idle.application.model.PlayerStateMessage
import com.wafuri.idle.application.model.PlayerStateSnapshot
import com.wafuri.idle.application.model.TeamCombatStats
import com.wafuri.idle.application.model.ZoneProgressSnapshot
import com.wafuri.idle.application.service.player.OfflineProgressionResult
import com.wafuri.idle.domain.model.CombatMemberState
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import com.wafuri.idle.domain.model.InventoryItem
import com.wafuri.idle.domain.model.Player
import com.wafuri.idle.domain.model.PlayerZoneProgress
import com.wafuri.idle.domain.model.Team
import com.wafuri.idle.domain.model.TeamMemberSlot
import com.wafuri.idle.transport.rest.dto.AuthResponse
import com.wafuri.idle.transport.rest.dto.ErrorResponse
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun ValidationException.shouldMatchExpected(expected: ValidationException) {
  this.message shouldBe expected.message
}

fun expectedPlayer(
  id: UUID,
  name: String,
  ownedCharacterKeys: Set<String> = emptySet(),
  activeTeamId: UUID? = null,
  experience: Int = 0,
  level: Int = 1,
  gold: Int = 0,
  essence: Int = 0,
): Player = Player(id, name, ownedCharacterKeys, activeTeamId, experience, level, gold, essence)

fun expectedZoneProgress(
  playerId: UUID,
  zoneId: String,
  killCount: Int = 0,
  level: Int = 1,
): PlayerZoneProgress = PlayerZoneProgress(playerId, zoneId, killCount, level)

fun expectedTeam(
  id: UUID,
  playerId: UUID,
  slots: List<TeamMemberSlot> = Team.defaultSlots(),
): Team = Team(id, playerId, slots)

fun expectedCharacterPullResult(
  player: Player,
  count: Int,
  pulls: List<CharacterPull>,
  totalEssenceGranted: Int,
): CharacterPullResult = CharacterPullResult(player, count, pulls, totalEssenceGranted)

fun expectedCharacterPull(
  pulledCharacterKey: String,
  grantedCharacterKey: String?,
  essenceGranted: Int,
): CharacterPull = CharacterPull(pulledCharacterKey, grantedCharacterKey, essenceGranted)

fun expectedOfflineProgressionResult(
  playerId: UUID,
  offlineDuration: Duration,
  kills: Int,
  zoneId: String,
  rewards: List<OfflineRewardSummary>,
  experiencePerKill: Int = 10,
  goldPerKill: Int = 25,
  killsPerLevel: Int = 10,
): OfflineProgressionResult =
  OfflineProgressionResult(
    playerId = playerId,
    offlineDuration = offlineDuration,
    kills = kills,
    experienceGained = kills * experiencePerKill,
    goldGained = kills * goldPerKill,
    playerLevel = 1 + (kills / killsPerLevel),
    playerLevelsGained = kills / killsPerLevel,
    zoneId = zoneId,
    zoneLevel = 1 + (kills / killsPerLevel),
    zoneLevelsGained = kills / killsPerLevel,
    rewards = rewards,
  )

fun expectedOfflineProgressionMessage(
  playerId: UUID,
  offlineDuration: Duration,
  kills: Int,
  zoneId: String,
  rewards: List<OfflineRewardSummary>,
  experiencePerKill: Int = 10,
  goldPerKill: Int = 25,
  killsPerLevel: Int = 10,
): OfflineProgressionMessage =
  OfflineProgressionMessage(
    playerId = playerId,
    offlineDurationMillis = offlineDuration.toMillis(),
    kills = kills,
    experienceGained = kills * experiencePerKill,
    goldGained = kills * goldPerKill,
    playerLevel = 1 + (kills / killsPerLevel),
    playerLevelsGained = kills / killsPerLevel,
    zoneId = zoneId,
    zoneLevel = 1 + (kills / killsPerLevel),
    zoneLevelsGained = kills / killsPerLevel,
    rewards = rewards,
  )

fun expectedOfflineRewardSummary(
  itemName: String,
  count: Int,
): OfflineRewardSummary = OfflineRewardSummary(itemName, count)

fun expectedAuthResponse(
  player: Player,
  guestAccount: Boolean,
  sessionToken: String = "",
  sessionExpiresAt: String = "",
): AuthResponse = AuthResponse(player, sessionToken, sessionExpiresAt, guestAccount)

fun expectedErrorResponse(message: String): ErrorResponse = ErrorResponse(message)

fun expectedValidationException(message: String): ValidationException = ValidationException(message)

fun expectedOwnedCharacterSnapshot(
  key: String,
  name: String,
  level: Int,
): OwnedCharacterSnapshot = OwnedCharacterSnapshot(key, name, level)

fun expectedZoneProgressSnapshot(
  zoneId: String,
  killCount: Int,
  level: Int,
): ZoneProgressSnapshot = ZoneProgressSnapshot(zoneId, killCount, level)

fun expectedPlayerStateSnapshot(
  playerId: UUID,
  playerName: String,
  playerExperience: Int = 0,
  playerLevel: Int = 1,
  playerGold: Int = 0,
  playerEssence: Int = 0,
  ownedCharacters: List<OwnedCharacterSnapshot> = emptyList(),
  zoneProgress: List<ZoneProgressSnapshot> = emptyList(),
  inventory: List<InventoryItemSnapshot> = emptyList(),
  serverTime: Instant = Instant.EPOCH,
): PlayerStateSnapshot =
  PlayerStateSnapshot(
    playerId,
    playerName,
    playerExperience,
    playerLevel,
    playerGold,
    playerEssence,
    ownedCharacters,
    zoneProgress,
    inventory,
    serverTime,
  )

fun expectedBasicPlayerStateSnapshot(
  playerId: UUID,
  playerName: String = "Alice",
  playerExperience: Int = 0,
  playerLevel: Int = 1,
  playerGold: Int = 0,
  playerEssence: Int = 0,
  serverTime: Instant,
): PlayerStateSnapshot =
  expectedPlayerStateSnapshot(
    playerId = playerId,
    playerName = playerName,
    playerExperience = playerExperience,
    playerLevel = playerLevel,
    playerGold = playerGold,
    playerEssence = playerEssence,
    ownedCharacters = emptyList(),
    zoneProgress = emptyList(),
    inventory = emptyList(),
    serverTime = serverTime,
  )

fun expectedPlayerStateMessage(
  playerId: UUID,
  snapshot: PlayerStateSnapshot,
  type: EventType = EventType.PLAYER_STATE_SYNC,
): PlayerStateMessage = PlayerStateMessage(type, playerId, snapshot)

fun expectedCombatMemberState(
  characterKey: String,
  attack: Float,
  hit: Float,
  currentHp: Float,
  maxHp: Float,
): CombatMemberState = CombatMemberState(characterKey, attack, hit, currentHp, maxHp)

fun expectedCombatState(
  playerId: UUID,
  status: CombatStatus = CombatStatus.IDLE,
  zoneId: String? = null,
  activeTeamId: UUID? = null,
  enemyName: String? = null,
  enemyLevel: Int = 0,
  enemyBaseHp: Float = 0f,
  enemyAttack: Float = 0f,
  enemyHp: Float = 0f,
  enemyMaxHp: Float = 0f,
  members: List<CombatMemberState> = emptyList(),
  pendingDamageMillis: Long = 0,
  pendingRespawnMillis: Long = 0,
  pendingReviveMillis: Long = 0,
  lastSimulatedAt: Instant? = null,
): CombatState =
  CombatState(
    playerId = playerId,
    status = status,
    zoneId = zoneId,
    activeTeamId = activeTeamId,
    enemyName = enemyName,
    enemyLevel = if (status == CombatStatus.IDLE) 0 else enemyLevel.coerceAtLeast(1),
    enemyBaseHp =
      if (status == CombatStatus.IDLE) {
        0f
      } else if (enemyBaseHp > 0f) {
        enemyBaseHp
      } else {
        enemyMaxHp
      },
    enemyAttack = if (status == CombatStatus.IDLE) 0f else enemyAttack.coerceAtLeast(1f),
    enemyHp = enemyHp,
    enemyMaxHp = enemyMaxHp,
    members = members,
    pendingDamageMillis = pendingDamageMillis,
    pendingRespawnMillis = pendingRespawnMillis,
    pendingReviveMillis = pendingReviveMillis,
    lastSimulatedAt = lastSimulatedAt,
  )

fun expectedSingleMemberCombatState(
  playerId: UUID,
  teamId: UUID,
  characterKey: String = "warrior",
  attack: Float,
  hit: Float,
  currentHp: Float,
  maxHp: Float,
  status: CombatStatus = CombatStatus.FIGHTING,
  zoneId: String = "starter-plains",
  enemyName: String = "Training Dummy",
  enemyLevel: Int = 1,
  enemyBaseHp: Float? = null,
  enemyAttack: Float = 1f,
  enemyHp: Float,
  enemyMaxHp: Float,
  pendingDamageMillis: Long = 0,
  pendingRespawnMillis: Long = 0,
  pendingReviveMillis: Long = 0,
  lastSimulatedAt: Instant? = null,
): CombatState =
  expectedCombatState(
    playerId = playerId,
    status = status,
    zoneId = zoneId,
    activeTeamId = teamId,
    enemyName = enemyName,
    enemyLevel = enemyLevel,
    enemyBaseHp = enemyBaseHp ?: enemyMaxHp,
    enemyAttack = enemyAttack,
    enemyHp = enemyHp,
    enemyMaxHp = enemyMaxHp,
    members = listOf(expectedCombatMemberState(characterKey, attack, hit, currentHp, maxHp)),
    pendingDamageMillis = pendingDamageMillis,
    pendingRespawnMillis = pendingRespawnMillis,
    pendingReviveMillis = pendingReviveMillis,
    lastSimulatedAt = lastSimulatedAt,
  )

fun expectedCombatMemberSnapshot(
  characterKey: String,
  attack: Float,
  hit: Float,
  currentHp: Float,
  maxHp: Float,
  alive: Boolean,
): CombatMemberSnapshot = CombatMemberSnapshot(characterKey, attack, hit, currentHp, maxHp, alive)

fun expectedCombatSnapshot(
  playerId: UUID,
  status: CombatStatus,
  zoneId: String?,
  activeTeamId: UUID?,
  enemyName: String?,
  enemyAttack: Float,
  enemyHp: Float,
  enemyMaxHp: Float,
  teamDps: Float,
  pendingReviveMillis: Long = 0,
  members: List<CombatMemberSnapshot>,
): CombatSnapshot =
  CombatSnapshot(
    playerId,
    status,
    zoneId,
    activeTeamId,
    enemyName,
    enemyAttack,
    enemyHp,
    enemyMaxHp,
    teamDps,
    pendingReviveMillis,
    members,
  )

fun expectedCharacterCombatStats(
  characterKey: String,
  attack: Float,
  hit: Float,
  maxHp: Float,
): CharacterCombatStats = CharacterCombatStats(characterKey, attack, hit, maxHp)

fun expectedTeamCombatStats(
  teamId: UUID,
  characterStats: List<CharacterCombatStats>,
): TeamCombatStats = TeamCombatStats(teamId, characterStats)

fun expectedSingleMemberTeamCombatStats(
  teamId: UUID,
  characterKey: String = "warrior",
  attack: Float,
  hit: Float,
  maxHp: Float,
): TeamCombatStats =
  expectedTeamCombatStats(
    teamId = teamId,
    characterStats = listOf(expectedCharacterCombatStats(characterKey, attack, hit, maxHp)),
  )

fun expectedInventoryItem(
  id: UUID,
  playerId: UUID,
  item: com.wafuri.idle.domain.model.Item,
  itemLevel: Int = 1,
  equippedTeamId: UUID? = null,
  equippedPosition: Int? = null,
): InventoryItem = InventoryItem(id, playerId, item, itemLevel, equippedTeamId = equippedTeamId, equippedPosition = equippedPosition)
