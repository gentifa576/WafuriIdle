# Combat Content Authoring

This document defines the JSON shape for combat content that designers edit by hand.

Current status:
- characters embed exactly one `skill` and one `passive`
- leader aura passives currently affect combat stat resolution
- triggered passives and active skills are defined and validated as content, but are not yet executed in combat

## File Layout

- `backend/src/main/resources/characters/characters.json`

## Character JSON

Each character now carries:
- `tags`: free-form labels used by passive conditions
- `skill`: embedded active skill definition
- `passive`: embedded passive definition

Example:

```json
{
  "id": "vagner",
  "name": "Vagner",
  "strength": [13.1, 2.2],
  "agility": [11.6, 2.0],
  "intelligence": [4.1, 0.7],
  "wisdom": [4.8, 0.8],
  "vitality": [9.9, 1.7],
  "tags": ["fire", "bow", "attacker", "male", "dragon"],
  "skill": { "...": "..." },
  "passive": { "...": "..." }
}
```

## Skill JSON

Skills are cooldown-based active abilities.

Fields:
- `key`: local skill id
- `name`: display name
- `cooldownMillis`: cooldown in milliseconds
- `effects`: ordered list of effects; order matters

Example:

```json
{
  "key": "prominence-blaze",
  "name": "Prominence Blaze",
  "cooldownMillis": 5500,
  "effects": [
    {
      "type": "DAMAGE",
      "target": "ENEMY",
      "amount": {
        "type": "STAT_SCALING",
        "stat": "ATTACK",
        "multiplier": 2.6,
        "flatBonus": 6.5
      }
    }
  ]
}
```

This models a direct damage skill that:
- scales from the character's derived attack stat
- applies its authored damage formula to the current enemy target

Auto-use behavior is intentionally not part of authored skill content.
It should come from player-controlled runtime settings later.

## Passive JSON

Passives are leader-owned team rules.

Fields:
- `leaderOnly`: only the team leader activates the passive
- `trigger`: passive mode
- `condition`: team-state or combat-state condition
- `modifiers`: live aura modifiers
- `effects`: future event-driven or timed passive outputs

There are currently two passive modes:
- `AURA`: always-on conditional team filter
- `EVENT_COUNTER`: future triggered passive based on event counts

Example aura passive:

```json
{
  "key": "sisterhood_banner",
  "name": "Sisterhood Banner",
  "leaderOnly": true,
  "trigger": "AURA",
  "condition": {
    "type": "ALIVE_ALLIES_WITH_TAG_AT_LEAST",
    "tag": "female",
    "minimumCount": 2
  },
  "modifiers": [
    {
      "type": "ATTACK_MULTIPLIER",
      "value": 0.5
    }
  ]
}
```

This means:
- only the first team member may supply the passive
- if at least two alive allies have the `female` tag
- the team attack stat is increased by 50%

Example triggered passive definition:

```json
{
  "key": "battle_rhythm",
  "name": "Battle Rhythm",
  "leaderOnly": true,
  "trigger": "EVENT_COUNTER",
  "triggerEvent": "HIT_DONE",
  "triggerEveryCount": 5,
  "condition": {
    "type": "ALWAYS"
  },
  "effects": [
    {
      "type": "APPLY_EFFECT",
      "target": "TEAM",
      "durationMillis": 4000,
      "modifiers": [
        {
          "type": "ATTACK_MULTIPLIER",
          "value": 0.2
        }
      ]
    }
  ]
}
```

This is not executed yet, but it documents the intended shape for:
- every 5 hits
- apply a temporary team attack buff

## Condition Types

Supported condition shape:

```json
{
  "type": "ALWAYS"
}
```

Current types:
- `ALWAYS`
- `SELF_HP_BELOW_PERCENT`
- `ANY_ALLY_HP_BELOW_PERCENT`
- `ALIVE_ALLIES_WITH_TAG_AT_LEAST`

Optional fields by type:
- `percent`
- `minimumCount`
- `tag`

## Effect Types

Current effect types:
- `DAMAGE`
- `HEAL`
- `APPLY_EFFECT`
- `APPLY_DAMAGE_REDIRECT`

Common fields:
- `target`
- `amount`
- `durationMillis`
- `modifiers`

`APPLY_DAMAGE_REDIRECT` is the sequential effect form for redirect behavior.
For example, a skill can first apply redirect to `SELF`, then apply a second timed effect that reduces incoming damage.

## Targets

Current targets:
- `SELF`
- `ENEMY`
- `TEAM`
- `LOWEST_HP_ALLY`

## Value Formulas

Value formulas define effect magnitude.

Current types:
- `FLAT`
- `STAT_SCALING`
- `PERCENT_OF_SELF_CURRENT_HP`
- `PERCENT_OF_TARGET_MAX_HP`

Example:

```json
{
  "type": "STAT_SCALING",
  "stat": "ATTACK",
  "multiplier": 1.7,
  "flatBonus": 2.0
}
```

## Modifiers

Current modifier types:
- `ATTACK_MULTIPLIER`
- `OUTGOING_DAMAGE_MULTIPLIER`
- `INCOMING_DAMAGE_MULTIPLIER`

`value` is expressed as a decimal multiplier delta:
- `0.5` = +50%
- `-0.3` = -30%

Only `ATTACK_MULTIPLIER` on `AURA` passives is currently applied by combat code.

## Authoring Rules

- Keep embedded skill/passive keys stable once referenced by external systems or docs.
- Prefer simple tags such as `female`, `frontline`, `support`, `ranged`.
- Use one passive for persistent conditional rules and one skill for active cooldown behavior.
- For multi-step skills, keep effects in the exact execution order you want; effects are the only authored execution sequence.
- Do not assume a defined field is implemented in combat yet; check the "Current status" section first.
