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
- player level currently uses a nonlinear threshold curve with three progression bands:
  - early `1-20` is fast
  - mid `21-50` steepens
  - post-`50` continues scaling without a hard cap
- the configured `experiencePerLevel` value anchors the level-2 threshold, while later thresholds come from the authoritative scaling rule
- player level scales all owned character combat stats through a shared soft-capped progression factor instead of raw linear growth
- the current playtest tuning anchors level `2` at `85` total EXP and targets roughly `1-20` in `~2h` and level `100` in `~28d`

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
- zone level uses a nonlinear threshold curve with the same three bands as player progression, normalized so the configured `killsPerLevel` value remains the level-2 threshold
- the current playtest tuning anchors zone level `2` at `9` local progress points
- zone progression gain is now decoupled slightly from player EXP gain:
  - each kill still awards one kill for reward and loot purposes
  - but zone level progression may gain more than one local progress point per kill through config
  - the current playtest tuning uses `progressMultiplier = 16.0`
  - this is intended to let local zone pressure outrun global player leveling instead of lagging behind it
- when a zone level increases, the server pushes a player-scoped WebSocket notification
- future enemy spawns in that combat now scale their HP from the player's current zone level using config-driven smooth growth plus dynamic spike spacing
- enemy retaliation attack now also scales from current zone level through the authoritative zone-scaling rule
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
- the same gacha action may currently resolve either `1` pull or `10` pulls at once
- all loaded character templates currently share equal pull odds
- duplicate pulls do not create another copy of the same character
- duplicate pulls convert into `essence` compensation

Current implementation:
- one character pull currently costs `250` gold
- a ten-pull currently costs `2500` gold
- duplicate pulls currently grant `15` essence
- the pull pool is every loaded character template, including starter characters
- because characters are unique by `characterKey`, pulling an already-owned character leaves the roster unchanged and only grants essence
- batch pulls resolve in order against the updated roster, so a duplicate can occur later in the same ten-pull after that character was unlocked earlier in the batch

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
- combat can also be stopped explicitly by the player
- combat then progresses on the server
- combat currently uses the active team
- team DPS is derived from living members only
- each spawned enemy resolves from static enemy template content

Current stat derivation:
- `attack`, `hit`, and `hp` still come from `strength`, `agility`, and `vitality`
- player-level stat growth is no longer raw linear growth
- the authoritative scaling rule now applies a shared soft-capped player factor across combat-relevant stats
- scaled `strength` and `vitality` are then converted into combat-space `attack` and `hp`, so the live combat numbers are not the raw authored template stat values
- agility currently converts into hit with `sqrt(agility)` and a minimum of `1`
- items are added on top of those scaled character stats and use the validated nonlinear `softcap_surge` item curve, which stays modest early and ramps much harder after about item level `24`

Not in use yet:
- intelligence
- wisdom

Current combat-content direction:
- each character embeds one `skill` and one `passive`
- characters also carry free-form `tags` for authored combat conditions
- enemies are authored separately from zones with stable `id`, `name`, optional `image`, `baseHp`, and `attack`
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
- each 1s combat damage step applies team damage first and then immediate enemy retaliation in the same resolution only if the enemy survives that step
- current enemy retaliation deals the selected enemy template's zone-scaled `attack` damage with no mitigation, but a killed enemy does not retaliate on its death step
- retaliation currently consumes team HP across living members in team order
- if every combat member reaches `0 HP`, the team enters a downed state for `30s`
- while the team is downed, combat-relevant team edits are blocked; players cannot swap characters, activate another team, or equip and unequip items until combat leaves `DOWN`
- after that down timer, the same team revives at `50%` HP and combat resumes against a fresh full-HP enemy unless the player explicitly stops combat first
- enemy HP and retaliation attack are both scaled by current zone level; respawns reuse the active enemy template's `baseHp` and `attack` and then apply the authoritative zone scaling rule
- zone reward scaling currently reuses the same zone multiplier with reward exponent `0.37` so reward growth stays below enemy HP growth

## Zones

Zones currently have static template data and separate player-owned progression.

Static zone template purpose:
- level range
- enemy ID list
- loot table
- future event references

## Enemies

Enemies are static template content referenced by zones.

Current enemy template purpose:
- define the authored enemy identity used by a zone
- own the base HP before zone scaling
- own the flat retaliation attack used in combat

Current implementation:
- zones reference enemies by stable `enemyId`
- combat starts in the default zone and randomly selects an enemy from that zone's enemy ID pool whenever combat enters `FIGHTING`, including initial start, post-win respawns, and post-`DOWN` revives
- enemy display name and optional combat portrait path come from the enemy template, not directly from zone content

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
- effective item stats scale from template stat values by item level through the nonlinear `softcap_surge` item curve so items start weaker early and take over more of the power budget later

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
