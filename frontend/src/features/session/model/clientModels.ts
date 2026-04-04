import type {
  CharacterPull,
  CharacterTemplate,
  CombatSnapshot,
  InventoryItemSnapshot,
  OwnedCharacterSnapshot,
  Player,
  PlayerStateSnapshot,
  Stat,
  Team,
  TeamMemberSlot,
  ZoneProgressSnapshot,
} from '../../../core/types/api'

export interface ClientPlayer {
  id: string
  name: string
  ownedCharacterKeys: string[]
  activeTeamId: string | null
  experience: number
  level: number
  gold: number
  essence: number
  hasStarterChoice: boolean
}

export interface ClientTeamSlot {
  position: number
  characterKey: string | null
  weaponItemId: string | null
  armorItemId: string | null
  accessoryItemId: string | null
}

export interface ClientTeam {
  id: string
  playerId: string
  slots: ClientTeamSlot[]
  characterKeys: string[]
  occupiedSlots: number
  shortLabel: string
}

export interface ClientCharacterTemplate {
  key: string
  name: string
  image?: string | null
  tags?: string[]
}

export interface ClientOwnedCharacter {
  key: string
  name: string
  level: number
  label: string
}

export interface ClientZoneProgress {
  zoneId: string
  killCount: number
  level: number
  label: string
}

export interface ClientStat {
  type: string
  value: number
}

export interface ClientInventoryItem {
  id: string
  itemName: string
  itemDisplayName: string
  itemType: string
  itemBaseStat: ClientStat
  itemSubStatPool: string[]
  subStats: ClientStat[]
  rarity: string
  upgrade: number
  equippedTeamId: string | null
  equippedPosition: number | null
  assignmentLabel: string
}

export interface ClientCombatMember {
  characterKey: string
  attack: number
  hit: number
  currentHp: number
  maxHp: number
  alive: boolean
  hpLabel: string
}

export interface ClientCombat {
  playerId: string
  status: string
  zoneId: string | null
  activeTeamId: string | null
  enemyName: string | null
  enemyAttack: number
  enemyHp: number
  enemyMaxHp: number
  teamDps: number
  pendingReviveMillis: number
  members: ClientCombatMember[]
  teamCurrentHp: number
  teamMaxHp: number
}

export interface ClientPullResult {
  count: number
  pulls: CharacterPull[]
  totalEssenceGranted: number
  unlockedCount: number
  duplicateCount: number
  pulledCharacterKeys: string[]
}

export function mapPlayer(player: Player): ClientPlayer {
  return {
    ...player,
    hasStarterChoice: player.ownedCharacterKeys.length === 0,
  }
}

export function mapTeam(team: Team): ClientTeam {
  return {
    id: team.id,
    playerId: team.playerId,
    slots: team.slots.map(mapTeamSlot),
    characterKeys: [...team.characterKeys],
    occupiedSlots: team.slots.filter((slot) => slot.characterKey != null).length,
    shortLabel: team.id.slice(0, 8),
  }
}

export function mapTeams(teams: Team[]): ClientTeam[] {
  return teams.map(mapTeam)
}

export function mapCharacterTemplate(template: CharacterTemplate): ClientCharacterTemplate {
  return {
    key: template.key,
    name: template.name,
    image: template.image,
    tags: template.tags,
  }
}

export function mapCharacterTemplates(templates: CharacterTemplate[]): ClientCharacterTemplate[] {
  return templates.map(mapCharacterTemplate)
}

export function mapOwnedCharacters(characters: OwnedCharacterSnapshot[]): ClientOwnedCharacter[] {
  return characters.map((character) => ({
    key: character.key,
    name: character.name,
    level: character.level,
    label: `${character.name} · Lv.${character.level}`,
  }))
}

export function mapZoneProgress(entries: ZoneProgressSnapshot[]): ClientZoneProgress[] {
  return entries.map((entry) => ({
    zoneId: entry.zoneId,
    killCount: entry.killCount,
    level: entry.level,
    label: `${entry.zoneId} Lv.${entry.level}`,
  }))
}

export function mapInventory(items: InventoryItemSnapshot[]): ClientInventoryItem[] {
  return items.map((item) => ({
    id: item.id,
    itemName: item.itemName,
    itemDisplayName: item.itemDisplayName,
    itemType: item.itemType,
    itemBaseStat: mapStat(item.itemBaseStat),
    itemSubStatPool: [...item.itemSubStatPool],
    subStats: item.subStats.map(mapStat),
    rarity: item.rarity,
    upgrade: item.upgrade,
    equippedTeamId: item.equippedTeamId,
    equippedPosition: item.equippedPosition,
    assignmentLabel:
      item.equippedTeamId && item.equippedPosition != null
        ? `Equipped on ${item.equippedTeamId.slice(0, 8)} · Slot ${item.equippedPosition}`
        : 'In backpack',
  }))
}

export function mapCombat(snapshot: CombatSnapshot | null): ClientCombat | null {
  if (snapshot == null) {
    return null
  }

  const members = snapshot.members.map((member) => ({
    characterKey: member.characterKey,
    attack: member.attack,
    hit: member.hit,
    currentHp: member.currentHp,
    maxHp: member.maxHp,
    alive: member.alive,
    hpLabel: `${member.currentHp} / ${member.maxHp}`,
  }))

  return {
    playerId: snapshot.playerId,
    status: snapshot.status,
    zoneId: snapshot.zoneId,
    activeTeamId: snapshot.activeTeamId,
    enemyName: snapshot.enemyName,
    enemyAttack: snapshot.enemyAttack,
    enemyHp: snapshot.enemyHp,
    enemyMaxHp: snapshot.enemyMaxHp,
    teamDps: snapshot.teamDps,
    pendingReviveMillis: snapshot.pendingReviveMillis,
    members,
    teamCurrentHp: members.reduce((total, member) => total + member.currentHp, 0),
    teamMaxHp: members.reduce((total, member) => total + member.maxHp, 0),
  }
}

export function mapPlayerSnapshot(snapshot: PlayerStateSnapshot) {
  return {
    player: mapPlayer({
      id: snapshot.playerId,
      name: snapshot.playerName,
      ownedCharacterKeys: snapshot.ownedCharacters.map((character) => character.key),
      activeTeamId: null,
      experience: snapshot.playerExperience,
      level: snapshot.playerLevel,
      gold: snapshot.playerGold,
      essence: snapshot.playerEssence,
    }),
    ownedCharacters: mapOwnedCharacters(snapshot.ownedCharacters),
    zoneProgress: mapZoneProgress(snapshot.zoneProgress),
    inventory: mapInventory(snapshot.inventory),
  }
}

export function mapPullResult(result: { count: number; pulls: CharacterPull[]; totalEssenceGranted: number }): ClientPullResult {
  const unlockedCount = result.pulls.filter((pull) => pull.grantedCharacterKey != null).length
  return {
    count: result.count,
    pulls: result.pulls,
    totalEssenceGranted: result.totalEssenceGranted,
    unlockedCount,
    duplicateCount: result.pulls.length - unlockedCount,
    pulledCharacterKeys: result.pulls.map((pull) => pull.pulledCharacterKey),
  }
}

function mapTeamSlot(slot: TeamMemberSlot): ClientTeamSlot {
  return {
    position: slot.position,
    characterKey: slot.characterKey,
    weaponItemId: slot.weaponItemId,
    armorItemId: slot.armorItemId,
    accessoryItemId: slot.accessoryItemId,
  }
}

function mapStat(stat: Stat): ClientStat {
  return {
    type: stat.type,
    value: stat.value,
  }
}
