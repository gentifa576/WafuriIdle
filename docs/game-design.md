# Game Design

## Purpose

This document tracks game design intent that should guide implementation.

Use this file for:
- progression design
- combat and loot rules
- tuning direction
- terminology
- planned systems

Do not use this file for transport contracts or repository-specific engineering rules. Those belong in `AGENTS.md`.

## Core Structure

The game has two progression dimensions:

1. Player progression
2. Zone progression

They are related, but separate.

## Player Progression

Player progression is global to the account/player.

Current direction:
- players gain experience from enemy kills
- player experience converts into player level
- player level is independent from any specific zone
- all owned characters share the player's current level

Intended purpose:
- represent overall account or character progression
- gate future content, features, or power scaling
- provide a long-term progression track across all zones

Current planned model:
- `experience`
- `level`
- `gold`
- `essence`

Current implementation:
- players start at level `1`
- players start at `0` EXP
- players start at `0` gold
- players start at `0` essence
- each kill grants base `10` player EXP and base `25` gold before zone reward scaling
- kill rewards currently scale by defeated enemy zone level through a softer reward curve: `rewardMultiplier = zoneMultiplier ^ rewardScalingExponent`
- player level currently advances every `100` EXP
- player level directly scales all owned character combat stats through template stat growth

Open questions:
- exact EXP curve per level

## Zone Progression

Zone progression is tracked per player, per zone.

Examples:
- Player A may have `Zone A = level 1` and `Zone B = level 3`
- Player B may have `Zone A = level 2` and `Zone B = level 1`

Current direction:
- each player has separate progression in each zone
- zone progression increases as that player kills enemies in that zone
- zone progression is not shared between players
- zone progression in one zone does not advance another zone

Intended purpose:
- reward repeated activity in a zone
- support future zone scaling, unlocks, enemy pools, and loot improvements
- create local progression without replacing global player progression

Current planned model:
- `playerId`
- `zoneId`
- `kills`
- `level`

Recommended initial implementation:
- zone level advances through kill thresholds
- do not introduce zone EXP yet unless tuning needs it later

Current implementation:
- zone progression starts at level `1`
- zone progression starts at `0` kills
- each kill in a zone increments only that player’s progress in that zone
- zone level currently advances every `10` kills in that zone
- when a zone level increases, the server pushes a player-scoped WebSocket notification
- future enemy spawns in that combat now scale their HP from the player's current zone level using config-driven smooth growth plus dynamic spike spacing
- EXP and gold rewards for kills in that zone also scale from the defeated enemy zone level, but use a softer exponent than enemy HP so the economy grows more slowly than combat durability

## Kill Rewards

Each enemy kill should currently do three things:

1. grant player EXP
2. grant player gold
3. advance zone progression for the current zone
4. roll item drops

So on kill:
- player EXP increases
- player gold increases
- player level may increase
- zone kill count increases for that player in that zone
- zone level may increase for that player in that zone
- loot may be generated and added to inventory

## Offline Progression

Current direction:
- live combat progression only advances while the player has an active WebSocket session
- disconnecting from WebSocket pauses live combat ticks for that player
- when the player reconnects, the server calculates missed combat progression from the timestamp difference since the last combat simulation
- offline progression currently uses the same 1:1 combat, EXP, zone, and loot rules as live progression
- offline reward replay uses each projected kill's actual enemy zone level, so reconnect summaries reflect the same scaled EXP and gold a live session would have earned

Current implementation:
- combat state records the last authoritative simulation timestamp
- reconnect catch-up analytically projects kills and combat state from elapsed time instead of replaying server ticks
- each projected kill currently grants the same rewards as an active kill:
  - player EXP
  - player gold
  - zone kill progression
  - loot rolls
- if offline progression covers at least `5m`, the server pushes a player-scoped WebSocket summary of gains on reconnect, including earned gold

## Character Acquisition

Current direction:
- starter choice is the initial guaranteed roster entry
- additional characters come from a gold-funded gacha pull
- all loaded character templates currently share equal pull odds
- duplicate pulls do not create another copy of the same character
- duplicate pulls convert into `essence` compensation

Current implementation:
- one character pull currently costs `250` gold
- duplicate pulls currently grant `15` essence
- the pull pool is every loaded character template, including starter characters
- because characters are unique by `characterKey`, pulling an already-owned character leaves the roster unchanged and only grants essence

Open questions:
- future uses for essence
- whether different banners or weighted pools should replace the global even-odds pool later

Open questions:
- whether offline progression should eventually be capped
- whether offline progression should always stay 1:1 with live combat
- whether reconnect summaries should include more detail than aggregated rewards

## Combat

Current combat direction:
- combat is server-authoritative
- combat starts from an explicit player command
- combat then progresses on the server
- combat currently uses the active team
- team DPS is derived from living members only

Current stat derivation:
- `attack = strength.base + strength.increment * (playerLevel - 1)`
- `hit = agility.base + agility.increment * (playerLevel - 1)`
- `hp = vitality.base + vitality.increment * (playerLevel - 1)`

Not in use yet:
- intelligence
- wisdom

Current combat-content direction:
- each character embeds one `skill` and one `passive`
- characters also carry free-form `tags` for authored combat conditions
- skills are authored as cooldown-based definitions with ordered effects directly inside the character content
- passives are authored as leader-owned team rules directly inside the character content
- skill auto-use should be a player/runtime setting, not a character content field
- aura passives are the first implemented passive mode
- triggered passives and active skill execution are defined in content shape but not yet active in combat

Authoring reference:
- `docs/combat-content.md`

Current loop direction:
- enemies respawn after a delay
- combat auto-continues after respawn
- zone is the unit that groups combat processing
- when team stat refresh changes a combat member's max HP, current HP preserves the existing current-to-max ratio instead of keeping the old flat HP value
- enemy HP scaling is currently the only implemented enemy-side zone scaling; enemy damage is not authored yet
- zone reward scaling currently reuses the same zone multiplier with a softer exponent so reward growth stays below enemy HP growth

## Zones

Zones currently have static template data and separate player-owned progression.

Static zone template purpose:
- level range
- enemy list
- loot table
- future event references

Dynamic zone progression purpose:
- track how far an individual player has advanced in that zone

Important distinction:
- `ZoneTemplate` is content
- player zone progression is state

## Loot

Current loot direction:
- loot is rolled on kill
- loot is server-generated and added directly to player inventory
- loot config is driven by game configuration
- dropped items inherit an `itemLevel` from the defeated enemy's zone level
- effective item stats scale from template stat values by item level

Current default tuning:
- base item drop rate: `1%` per kill
- rarity distribution:
  - common: `70%`
  - rare: `20%`
  - epic: `8%`
  - legendary: `2%`

Current dev override:
- local dev uses `100%` drop rate for easier testing

## Inventory and Equipment

Current direction:
- generated items belong to exactly one player
- equipment must come from that player’s inventory
- equipped items are unavailable to any other team slot
- unequip returns items to inventory
- each generated item may be assigned to at most one team position at a time

Static item templates define:
- stable name
- display name
- type
- base stat
- substat pool

Generated inventory state defines:
- item level
- rarity
- rolled substats
- upgrade state

## Teams

Current direction:
- players may own multiple teams
- only one team may be active at a time
- combat requires an active team
- teams are positional loadouts with exactly 3 member slots
- each team slot may contain:
  - one character reference
  - one weapon
  - one armor
  - one accessory
- characters are unique by `characterKey`
- the same owned character may be reused across multiple teams
- a character may not appear twice in the same team
- the first team slot is the leader slot for passive activation
- combat stats are derived from the selected team slot's character plus the items equipped to that same team slot
- while combat is running, changes to the active team or its composition should be reflected by the next combat tick

New players currently begin with:
- no owned characters
- one claimable configured starter while the roster is still empty
- a configured number of empty team slots

## Authentication and Sessions

Current player access direction:
- players can sign up or log in
- guest accounts are allowed
- email is nullable
- if email is absent, password may also be absent
- guest players may operate on session-only auth

Current session direction:
- JWT-based auth
- session token duration: `12h`
- if the player is still active and authenticated, the server may mint a fresh token

Security note:
- checked-in JWT keys are for local dev/test only
- production keys must come from external configuration

## Immediate Next Design Targets

Near-term systems to define more precisely:
- player EXP curve
- zone kill thresholds per level
- how far zone level should expand beyond enemy HP, reward scaling, and item level into enemy damage or unlocks
- full active skill execution model
- triggered passive runtime and event counters

## Open Questions

- Should zone progression ever reset, prestige, or cap?
- Should each kill always grant the same EXP, or vary by enemy/zone?
- Should zone progression be linear by kills, or use a separate zone EXP curve later?
