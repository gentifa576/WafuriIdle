import type {
  CombatSnapshot,
  PlayerStateSnapshot,
} from '../../../core/types/api'
import type { ClientCombat, ClientInventoryItem, ClientOwnedCharacter, ClientPlayer, ClientZoneProgress, ClientStat } from '../model/clientModels'
import { mapCombat, mapPlayerSnapshot } from '../model/clientModels'

export interface PlayerSyncResult {
  player: ClientPlayer | null
  inventory: ClientInventoryItem[]
  ownedCharacters: ClientOwnedCharacter[]
  zoneProgress: ClientZoneProgress[]
}

export function applyPlayerStateSnapshot(
  currentPlayer: ClientPlayer | null,
  currentInventory: ClientInventoryItem[],
  currentOwnedCharacters: ClientOwnedCharacter[],
  currentZoneProgress: ClientZoneProgress[],
  snapshot: PlayerStateSnapshot,
): PlayerSyncResult {
  const mapped = mapPlayerSnapshot(snapshot)
  return {
    player: currentPlayer == null ? null : updatePlayerFromSnapshot(currentPlayer, mapped.player),
    inventory: reuseIfEqual(currentInventory, mapped.inventory, inventoryItemEquals),
    ownedCharacters: reuseIfEqual(currentOwnedCharacters, mapped.ownedCharacters, ownedCharacterEquals),
    zoneProgress: reuseIfEqual(currentZoneProgress, mapped.zoneProgress, zoneProgressEntryEquals),
  }
}

export function applyCombatStateSnapshot(currentCombat: ClientCombat | null, snapshot: CombatSnapshot | null) {
  const mapped = mapCombat(snapshot)
  return combatEquals(currentCombat, mapped) ? currentCombat : mapped
}

function updatePlayerFromSnapshot(player: ClientPlayer, snapshot: ClientPlayer) {
  const nextOwnedCharacterKeys = snapshot.ownedCharacterKeys
  if (
    playerEquals(player, snapshot) &&
    arrayEquals(player.ownedCharacterKeys, nextOwnedCharacterKeys, primitiveEquals)
  ) {
    return player
  }

  return {
    ...player,
    id: snapshot.id,
    name: snapshot.name,
    ownedCharacterKeys: nextOwnedCharacterKeys,
    experience: snapshot.experience,
    level: snapshot.level,
    gold: snapshot.gold,
    essence: snapshot.essence,
    hasStarterChoice: snapshot.hasStarterChoice,
  }
}

function playerEquals(left: ClientPlayer, right: ClientPlayer) {
  return (
    left.id === right.id &&
    left.name === right.name &&
    left.experience === right.experience &&
    left.level === right.level &&
    left.gold === right.gold &&
    left.essence === right.essence &&
    left.hasStarterChoice === right.hasStarterChoice
  )
}

function ownedCharacterEquals(left: ClientOwnedCharacter, right: ClientOwnedCharacter) {
  return left.key === right.key && left.name === right.name && left.level === right.level && left.label === right.label
}

function zoneProgressEntryEquals(left: ClientZoneProgress, right: ClientZoneProgress) {
  return (
    left.zoneId === right.zoneId &&
    left.killCount === right.killCount &&
    left.level === right.level &&
    left.label === right.label
  )
}

function inventoryItemEquals(left: ClientInventoryItem, right: ClientInventoryItem) {
  return (
    left.id === right.id &&
    left.itemName === right.itemName &&
    left.itemDisplayName === right.itemDisplayName &&
    left.itemType === right.itemType &&
    statEquals(left.itemBaseStat, right.itemBaseStat) &&
    arrayEquals(left.itemSubStatPool, right.itemSubStatPool, primitiveEquals) &&
    arrayEquals(left.subStats, right.subStats, statEquals) &&
    left.rarity === right.rarity &&
    left.upgrade === right.upgrade &&
    left.equippedTeamId === right.equippedTeamId &&
    left.equippedPosition === right.equippedPosition &&
    left.assignmentLabel === right.assignmentLabel
  )
}

function combatEquals(left: ClientCombat | null, right: ClientCombat | null) {
  if (left === right) {
    return true
  }
  if (left == null || right == null) {
    return false
  }

  return (
    left.playerId === right.playerId &&
    left.status === right.status &&
    left.zoneId === right.zoneId &&
    left.activeTeamId === right.activeTeamId &&
    left.enemyName === right.enemyName &&
    left.enemyImage === right.enemyImage &&
    left.enemyAttack === right.enemyAttack &&
    left.enemyHp === right.enemyHp &&
    left.enemyMaxHp === right.enemyMaxHp &&
    left.teamDps === right.teamDps &&
    left.pendingReviveMillis === right.pendingReviveMillis &&
    left.teamCurrentHp === right.teamCurrentHp &&
    left.teamMaxHp === right.teamMaxHp &&
    arrayEquals(left.members, right.members, combatMemberEquals)
  )
}

function combatMemberEquals(left: ClientCombat['members'][number], right: ClientCombat['members'][number]) {
  return (
    left.characterKey === right.characterKey &&
    left.attack === right.attack &&
    left.hit === right.hit &&
    left.currentHp === right.currentHp &&
    left.maxHp === right.maxHp &&
    left.alive === right.alive &&
    left.hpLabel === right.hpLabel &&
    left.skillCooldownRemainingMillis === right.skillCooldownRemainingMillis
  )
}

function arrayEquals<T>(left: readonly T[], right: readonly T[], equals: (left: T, right: T) => boolean) {
  return left.length === right.length && left.every((value, index) => equals(value, right[index] as T))
}

function reuseIfEqual<T>(current: T[], next: T[], equals: (left: T, right: T) => boolean) {
  return arrayEquals(current, next, equals) ? current : next
}

function primitiveEquals<T>(left: T, right: T) {
  return left === right
}

function statEquals(left: ClientStat, right: ClientStat) {
  return left.type === right.type && left.value === right.value
}
