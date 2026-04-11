import type {
  ClientCharacterTemplate,
  ClientCombatConditionDefinition,
  ClientOwnedCharacter,
  ClientPassiveDefinition,
  ClientStatGrowth,
} from './clientModels'

export interface CharacterDisplayModel {
  key: string
  name: string
  level: number
  image?: string | null
  tags: string[]
  strength: ClientStatGrowth
  agility: ClientStatGrowth
  intelligence: ClientStatGrowth
  wisdom: ClientStatGrowth
  vitality: ClientStatGrowth
  skill?: ClientCharacterTemplate['skill']
  passive?: ClientPassiveDefinition | null
}

export function mapCharacterDisplayModels(
  ownedCharacters: ClientOwnedCharacter[],
  templates: ClientCharacterTemplate[],
): CharacterDisplayModel[] {
  const templatesByKey = new Map(templates.map((template) => [template.key, template]))
  return ownedCharacters.map((character) => {
    const template = templatesByKey.get(character.key)
    return {
      key: character.key,
      name: character.name,
      level: character.level,
      image: template?.image,
      tags: template?.tags ?? [],
      strength: template?.strength ?? emptyGrowth(),
      agility: template?.agility ?? emptyGrowth(),
      intelligence: template?.intelligence ?? emptyGrowth(),
      wisdom: template?.wisdom ?? emptyGrowth(),
      vitality: template?.vitality ?? emptyGrowth(),
      skill: template?.skill,
      passive: template?.passive ?? null,
    }
  })
}

export function growthAtLevel(growth: ClientStatGrowth, level: number) {
  return formatGrowth(numericGrowthAtLevel(growth, level))
}

export function numericGrowthAtLevel(growth: ClientStatGrowth, level: number) {
  return growth.base + growth.increment * Math.max(0, level - 1)
}

export function formatGrowth(value: number) {
  return Number.isInteger(value) ? value.toString() : value.toFixed(1)
}

export function describePassive(passive: ClientPassiveDefinition) {
  const scope = passive.leaderOnly ? 'Leader only' : 'Teamwide'
  const trigger = passive.trigger.toLowerCase().replaceAll('_', ' ')
  const condition = describeCondition(passive.condition)
  return `${scope} passive. Trigger: ${trigger}. ${condition}`
}

function describeCondition(condition: ClientCombatConditionDefinition) {
  switch (condition.type) {
    case 'ALIVE_ALLIES_WITH_TAG_AT_LEAST':
      return `Requires at least ${condition.minimumCount ?? 0} ally${condition.minimumCount === 1 ? '' : 'ies'} with ${condition.tag ?? 'a tag'}.`
    case 'ANY_ALLY_HP_BELOW_PERCENT':
    case 'SELF_HP_BELOW_PERCENT':
      return `Activates below ${condition.percent ?? 0}% HP.`
    default:
      return 'Always available.'
  }
}

function emptyGrowth(): ClientStatGrowth {
  return { base: 0, increment: 0 }
}
