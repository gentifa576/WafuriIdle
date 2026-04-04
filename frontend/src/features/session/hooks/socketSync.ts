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
    inventory: inventoryEquals(currentInventory, mapped.inventory) ? currentInventory : mapped.inventory,
    ownedCharacters: ownedCharactersEquals(currentOwnedCharacters, mapped.ownedCharacters) ? currentOwnedCharacters : mapped.ownedCharacters,
    zoneProgress: zoneProgressEquals(currentZoneProgress, mapped.zoneProgress) ? currentZoneProgress : mapped.zoneProgress,
  }
}

export function applyCombatStateSnapshot(currentCombat: ClientCombat | null, snapshot: CombatSnapshot | null) {
  const mapped = mapCombat(snapshot)
  return combatEquals(currentCombat, mapped) ? currentCombat : mapped
}

function updatePlayerFromSnapshot(player: ClientPlayer, snapshot: ClientPlayer) {
  const nextOwnedCharacterKeys = snapshot.ownedCharacterKeys
  if (
    player.id === snapshot.id &&
    player.name === snapshot.name &&
    player.experience === snapshot.experience &&
    player.level === snapshot.level &&
    player.gold === snapshot.gold &&
    player.essence === snapshot.essence &&
    player.hasStarterChoice === snapshot.hasStarterChoice &&
    stringArrayEquals(player.ownedCharacterKeys, nextOwnedCharacterKeys)
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

function stringArrayEquals(left: string[], right: string[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function ownedCharactersEquals(left: ClientOwnedCharacter[], right: ClientOwnedCharacter[]) {
  return (
    left.length === right.length &&
    left.every(
      (character, index) =>
        character.key === right[index]?.key &&
        character.name === right[index]?.name &&
        character.level === right[index]?.level &&
        character.label === right[index]?.label,
    )
  )
}

function zoneProgressEquals(left: ClientZoneProgress[], right: ClientZoneProgress[]) {
  return (
    left.length === right.length &&
    left.every(
      (zone, index) =>
        zone.zoneId === right[index]?.zoneId &&
        zone.killCount === right[index]?.killCount &&
        zone.level === right[index]?.level &&
        zone.label === right[index]?.label,
    )
  )
}

function inventoryEquals(left: ClientInventoryItem[], right: ClientInventoryItem[]) {
  return (
    left.length === right.length &&
    left.every((item, index) => {
      const other = right[index]
      return (
        item.id === other?.id &&
        item.itemName === other?.itemName &&
        item.itemDisplayName === other?.itemDisplayName &&
        item.itemType === other?.itemType &&
        statEquals(item.itemBaseStat, other?.itemBaseStat) &&
        stringArrayEquals(item.itemSubStatPool, other?.itemSubStatPool ?? []) &&
        statsEquals(item.subStats, other?.subStats ?? []) &&
        item.rarity === other?.rarity &&
        item.upgrade === other?.upgrade &&
        item.equippedTeamId === other?.equippedTeamId &&
        item.equippedPosition === other?.equippedPosition &&
        item.assignmentLabel === other?.assignmentLabel
      )
    })
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
    left.enemyAttack === right.enemyAttack &&
    left.enemyHp === right.enemyHp &&
    left.enemyMaxHp === right.enemyMaxHp &&
    left.teamDps === right.teamDps &&
    left.pendingReviveMillis === right.pendingReviveMillis &&
    left.teamCurrentHp === right.teamCurrentHp &&
    left.teamMaxHp === right.teamMaxHp &&
    combatMembersEquals(left.members, right.members)
  )
}

function combatMembersEquals(left: ClientCombat['members'], right: ClientCombat['members']) {
  return (
    left.length === right.length &&
    left.every(
      (member, index) =>
        member.characterKey === right[index]?.characterKey &&
        member.attack === right[index]?.attack &&
        member.hit === right[index]?.hit &&
        member.currentHp === right[index]?.currentHp &&
        member.maxHp === right[index]?.maxHp &&
        member.alive === right[index]?.alive &&
        member.hpLabel === right[index]?.hpLabel,
    )
  )
}

function statsEquals(left: ClientStat[], right: ClientStat[]) {
  return left.length === right.length && left.every((stat, index) => statEquals(stat, right[index]))
}

function statEquals(left: ClientStat, right: ClientStat | undefined) {
  return left.type === right?.type && left.value === right?.value
}
