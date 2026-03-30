# AGENTS.md

## Stack
| Area | Requirement |
| --- | --- |
| Language | Kotlin |
| Framework | Quarkus |
| Build | Gradle Kotlin DSL |
| Persistence | PostgreSQL-ready persistence |
| HTTP transport | REST for request/response APIs |
| Realtime transport | WebSocket for push updates and selected player-intent commands |
| Test stack | Kotest with MockK plus Quarkus test harness for backend integration tests; Vitest with Testing Library for frontend unit/component tests; Playwright for frontend browser smoke tests |
| Linting | Ktlint enforced through Gradle `check` |
| Local debug UI | Static browser client served by Quarkus with no extra frontend dependency |
| Product frontend | Separate browser client in `frontend/` using React, TypeScript, Vite, and PixiJS |
| Local server control | Use `backend/gradlew runServer` and `backend/gradlew stopServer` on Unix-like shells, `backend\gradlew.bat runServer` and `backend\gradlew.bat stopServer` on Windows shells, and the matching `scripts/start-local-playtest.sh` or clickable `scripts\start-local-playtest.bat` plus `scripts/stop-local-playtest.sh` or `scripts\stop-local-playtest.bat` helpers for full local playtest startup/shutdown. |
| Auth | Quarkus JWT auth; dev/test may use resource keypairs, but prod signing and verify keys must come from external config, not checked-in secrets |
| Character content | Static character templates loaded from resource in non-prod and from DB cache in prod |
| Zone content | Static zone templates loaded from resource in non-prod and from DB cache in prod |
| Item content | Static item templates loaded from resource in non-prod and from DB cache in prod |
| Combat content | Character combat kits are embedded in character JSON as one `skill` and one `passive` definition per character |

## Maintenance
| Trigger | Required AGENTS.md update |
| --- | --- |
| Tech stack changes | Update the `Stack` section in the same change. |
| New, changed, or removed endpoint | Update `Current Capabilities` in the same change. |
| WebSocket contract changes | Update `WebSocket Rules` and `WebSocket Capability` in the same change. |
| Documentation drift found | Update the affected `.md` files in the same change; do not leave repo docs stale relative to code. |
| Rule changes affecting domain, inventory, or testing | Update the matching rules table in the same change. |
| Tooling changes such as linting or formatting | Update `Stack` and any affected workflow/testing rules in the same change. |
| Any capability change | Do not leave `AGENTS.md` stale relative to code. |

## Design Doc Maintenance
| Trigger | Required `docs/game-design.md` update |
| --- | --- |
| New gameplay system or mechanic | Update `docs/game-design.md` in the same change. |
| Progression changes such as player, zone, loot, or combat scaling | Update `docs/game-design.md` in the same change. |
| Rule or tuning changes that affect implemented gameplay behavior | Update `docs/game-design.md` in the same change. |
| Any feature that changes game design intent | Do not leave `docs/game-design.md` stale relative to code. |

## Documentation Rules
| Area | Rule |
| --- | --- |
| Markdown links | When writing repo `.md` files, use relative links instead of absolute filesystem paths. |
| Drift handling | When implementation changes or existing doc drift is discovered, update the affected repo docs in the same change instead of deferring it. |

## Collaboration Rules
| Area | Rule |
| --- | --- |
| Change confirmation | Before making code or file changes, confirm the requested change with the user first. |
| Clarification | If any requirement or expected behavior is ambiguous, ask follow-up questions until the scope is fully clear before editing files. |

## Architecture
| Area | Rule |
| --- | --- |
| Structure | Use a layered structure. |
| Domain | Keep the domain layer framework-independent. |
| Authority | Treat the server as authoritative for all game state and rule enforcement. |
| Simulation | Ongoing game progression must run on a server-side tick loop, not on request timing. |
| Tick cadence | The authoritative server tick runs every 200ms. |
| Player sync loop | Player-facing state sync runs on its own server loop. |
| Combat loop | Auto-combat runs on a separate server loop after an explicit player start command. |
| Online combat gating | Live combat ticks apply only to players with an active WebSocket session; disconnected players catch up through offline progression instead of the live combat loop. |
| Combat ownership | Combat simulation is zone-oriented; the tick processes active combats grouped by `zoneId`, not as a flat player list. |
| Cluster combat ownership | Zone-oriented combat grouping is per server instance only; multiple instances may process combats for the same `zoneId` because combat state is currently player-scoped, not globally zone-scoped. |
| Zone job lifecycle | Combat zone jobs should exist only while a zone has active players/combat state and must stop when the zone becomes empty. |
| Combat damage cadence | Combat damage resolves on a separate 1s server-side cadence using elapsed-time correction, not on every 200ms tick. |
| Combat continuation | After an enemy dies, combat auto-continues after a configured respawn delay. |
| Team model | Players may own multiple teams, but only one team may be active at a time. |
| Team loadout model | Teams are positional loadouts with exactly 3 member slots; each slot owns its character reference and weapon, armor, and accessory assignment. |
| Repository design | Repository interfaces should share a generic base contract; do not create specialized repository interfaces unless they add aggregate-specific queries. |
| Model duplication | Before adding a new object, DTO, event, or message type, verify an existing type cannot cover the same role with a small extension; do not create parallel types that differ only by minor metadata or transport timing. |
| Character sourcing | Load static character templates through sequential fetchers, resource first when available, then DB fallback. |
| Zone sourcing | Load static zone templates through sequential fetchers, resource first when available, then DB fallback. |
| Item sourcing | Load static item templates through sequential fetchers, resource first when available, then DB fallback. |

## Game Invariants
| Invariant | Rule |
| --- | --- |
| Team size | Team max size is 3. |
| Slot occupancy | Each team position may hold at most one weapon, one armor, and one accessory. |
| Equipment source | Equipment must come from the owning player's inventory. |
| Duplicate equip | Duplicate equips are forbidden. |

## Inventory Rules
| Rule | Requirement |
| --- | --- |
| Ownership | Items belong to exactly one player. |
| Availability | Equipped items are unavailable to any other inventory or team slot usage. |
| Unequip | Unequip returns the item to the owning player's inventory. |
| Team assignment | An inventory item may be assigned to at most one team position at a time. |
| Template split | Static item content belongs to the item template catalog; generated item state belongs to `inventory_items`. |

## WebSocket Rules
| Rule | Requirement |
| --- | --- |
| Scope | Channels are player-scoped. |
| Usage | WebSocket messages push player-scoped state sync and gameplay notifications only. |
| Command input | WebSocket may also accept selected player-scoped commands when the command must execute on the socket-owning node. |
| Authentication | Player WebSocket sessions must present a valid session JWT through the WebSocket bearer-token carrier flow, and the authenticated JWT subject must match the `{playerId}` path before the session is treated as active or commands are accepted. |
| Authority | Authoritative game logic must not depend on WebSocket transport. |
| Tick source | Ongoing WebSocket state updates are emitted from the server tick loop; explicit WebSocket commands may return an immediate command-specific acknowledgement payload. |
| Command failure shape | WebSocket command validation failures should return a player-scoped command error payload instead of failing silently or surfacing only as transport ambiguity. |
| Loop separation | WebSocket state sync must not be responsible for advancing combat simulation. |
| Publish timing | Player and combat publishes should each include small jitter to avoid synchronized fan-out spikes. |
| Change gating | Player state should not be published when the authoritative state content is unchanged. |
| Offline summary dedupe | When an offline progression summary is published for a zone, redundant zone level-up notifications for that same zone should be omitted from the same publish cycle. |

## Current Capabilities
| Endpoint | Request | Response | Notes |
| --- | --- | --- | --- |
| `GET /health` | none | `204 No Content` | Public health check for local/dev/prod reachability checks. |
| `POST /auth/signup` | `{ name, email?, password? }` | `{ player, sessionToken, sessionExpiresAt, guestAccount }` | Creates an account or guest player session. |
| `POST /auth/login` | `{ name?, email?, password }` | `{ player, sessionToken, sessionExpiresAt, guestAccount }` | Authenticates by `name + password` or `email + password`. |
| `POST /auth/logout` | none | `204 No Content` | Revokes the authenticated session family and disconnects active player WebSocket sessions owned by that player. |
| `POST /players/{id}/starter` | `{ characterKey }` | `204 No Content` | Grants one configured starter to a player that currently owns no characters. |
| `POST /players/{id}/gacha/characters/pull` | `{ count? }` | `{ player, count, pulls: [{ pulledCharacterKey, grantedCharacterKey?, essenceGranted }], totalEssenceGranted }` | Spends configured gold for `1` or `10` even-odds character pulls across all loaded character templates; duplicates convert into essence instead of granting another copy. |
| `POST /internal/players/{id}/dirty` | none | `202 Accepted` | Internal node-to-node endpoint only; requires the `InternalNode` JWT role and marks a player dirty on the receiving node. |
| `POST /teams/{id}/slots/{position}/characters/{characterKey}` | none | `Team` | Assigns an owned character to the selected team position. |
| `POST /teams/{id}/activate` | none | `Team` | Activates a non-empty team for its owning player. |
| `POST /teams/{id}/slots/{position}/equip` | `{ inventoryItemId, slot }` | `204 No Content` | Equips an owned inventory item into the selected team position and equipment slot. |
| `POST /teams/{id}/slots/{position}/unequip` | `{ slot }` | `204 No Content` | Unequips the selected team position equipment slot. |
| `GET /characters/starters` | none | `[{ key, name, strength, agility, intelligence, wisdom, vitality, image, tags, skill, passive }]` | Returns the configured starter character choices in config order. |
| `GET /characters/templates` | none | `[{ key, name, strength, agility, intelligence, wisdom, vitality, image, tags, skill, passive }]` | Returns the currently loaded character templates. |
| `GET /players/{id}` | none | `Player` | Returns the player domain model directly, including player EXP, level, gold, and essence. Owned character level is implicitly the same as player level. |
| `GET /players/{id}/teams` | none | `[Team]` | Returns all player teams; active team is inferred from `Player.activeTeamId`. |
| `GET /players/{id}/inventory` | none | `[InventoryItem]` | Returns inventory state for the player with nested static `item` data, generated `itemLevel`, and team-slot assignment state. |
| `GET /players/{id}/zone-progress` | none | `[PlayerZoneProgress]` | Returns per-player per-zone kill and level progression. |
| `GET /debug-client/` | none | Static HTML/JS/CSS page | Local-only manual test harness for REST commands and player-scoped WebSocket observation. |

| Transport rule | Requirement |
| --- | --- |
| Controller behavior | REST controllers must remain thin; use request DTOs only when the input shape differs, and otherwise return existing domain or application models directly. |
| Error shape | Validation and not-found failures return an error DTO `{ message }`. |
| Command handling | REST commands mutate configuration or player intent only. |
| Item ownership | Public REST must not allow clients to mint or inject inventory items directly. |
| Character creation | Public REST must not allow clients to create characters directly. |
| Character template scope | Character templates define static growth and presentation data only; progression state such as level must stay separate. |
| Character combat refs | Each character may embed at most one `skill` and one `passive`; use character `tags` for authored combat conditions. |
| Skill auto use | Skill auto-use behavior must not be authored in static character content; treat it as player/runtime configuration. |
| Character identity | Player-owned characters are unique by `characterKey`; do not introduce per-player character instance IDs unless duplicate ownership becomes a real requirement. |
| Starting roster | New players begin with no owned characters and may claim exactly one configured starter while their roster is empty. |
| Character acquisition | Public REST may spend configured gold for `1` or `10` character pulls across all loaded character templates with equal odds. If a pulled character is already owned, grant configured essence compensation instead of a duplicate character. Multi-pull batches must apply ownership updates within the same batch so later rolls can become duplicates of earlier unlocks. |
| Team slots | New players begin with a configured number of empty team slots. |
| Team assignment | Public REST may assign an owned character to a player-owned team position by `characterKey`. The same owned character may appear across multiple teams, but not twice in the same team. |
| Equipment assignment | Public REST equips generated inventory items to team positions, not directly to characters. |
| Team activation | Combat requires an active team, and a team cannot be activated unless it has at least one character. |
| Combat start | Player combat start must come from the player WebSocket as a player-scoped command; combat progression itself remains server-driven after start. |
| Cluster combat start | In multi-instance mode, combat start should originate from the player's active WebSocket owner node so a single node becomes the initial owner of that player's combat state. |
| Combat stat derivation | Character combat stats scale from player level using static growth: `attack = strength.base + strength.increment * (player.level - 1)`, `hit = agility.base + agility.increment * (player.level - 1)`, `hp = vitality.base + vitality.increment * (player.level - 1)`; do not use intelligence or wisdom in combat yet, and derive team DPS from living members only. |
| Passive execution | Only the team leader's passive may activate; aura passives may modify team stat derivation when their authored condition matches. |
| Combat team refresh | Ongoing combat must refresh its active team id and member snapshot from the player's currently active team each combat tick so team edits and active-team switches are reflected in combat sync. |
| Combat timing | Use real elapsed server tick time to correct drift; do not assume every loop executes exactly on schedule. |
| Offline progression | When a player reconnects after combat continued while they were disconnected, progression catch-up must be calculated from timestamp difference, not by replaying server ticks. |
| Player progression | Players gain EXP and gold from kills and level globally based on configured thresholds; kill rewards currently scale by defeated enemy zone level through a softer config-driven reward exponent layered on top of the main zone multiplier. |
| Zone progression | Zone progression is tracked per player and per zone through kill counts and zone level thresholds, and current zone level drives config-based enemy HP scaling plus softer EXP and gold reward scaling for future kills in that combat zone. |
| Offline summary threshold | If offline progression covers at least `5m`, enqueue a player-scoped summary notification of gained rewards and progression. |
| Loot config | Combat loot settings are config-driven. Base item drop rate is `1%` per kill, rarity rolls use `common 70%`, `rare 20%`, `epic 8%`, `legendary 2%`, and dropped item stats scale from generated `itemLevel` derived from the defeated enemy's zone level. |
| Zone model | Zones define level range, loot table, enemy list, and future event references; combat currently uses the default zone as its source of enemy data and executes grouped by zone. |
| Item model | Items define stable `name`, user-facing `displayName`, `type`, `baseStat`, and `subStatPool` as static template data. Inventory state owns generated `itemLevel`, `subStats`, `rarity`, and `upgrade`; effective item stats scale from template values by `itemLevel`. |
| Rule enforcement | `GET` and `POST` handlers enforce rules through application services, not transport code. |
| Tick handoff | Command-side mutations must mark player state for server tick processing instead of pushing direct WebSocket events. |

## WebSocket Capability
| Area | Capability |
| --- | --- |
| Channel | `/ws/player/{playerId}` is the only WebSocket channel. |
| Auth binding | `/ws/player/{playerId}` requires a valid session token via the WebSocket bearer-token carrier flow and only authorizes the socket-owning player for that same `{playerId}`. |
| Incoming commands | `/ws/player/{playerId}` accepts `{ type: "START_COMBAT" }` to start combat on the socket-owning node. |
| Source | State sync is driven by the server tick loop. |
| Tick rate | The default state-sync tick cadence is 200ms. |
| Jitter | Publish timing includes a small jitter window before sending. |
| Publish rule | Tick processing skips WebSocket publish when the player's state content did not change. |
| Payload | Event payloads are player-scoped messages `{ type, playerId, snapshot }` for player-state sync, `{ type, playerId, snapshot, serverTime }` for combat-state sync, `{ type, playerId, zoneId, level, serverTime }` for zone level-up notifications, `{ type, playerId, offlineDurationMillis, kills, experienceGained, goldGained, playerLevel, playerLevelsGained, zoneId, zoneLevel, zoneLevelsGained, rewards, serverTime }` for offline progression summaries, and `{ type, playerId, commandType, message, serverTime }` for WebSocket command validation failures. `START_COMBAT` may return either an immediate combat-state sync payload or a command error payload on the player channel. |
| Event dedupe | If an offline progression summary for a zone is present in a player publish batch, separate zone level-up notifications for that same zone are suppressed because the summary already carries the final zone level and levels gained. |
| Snapshot | Player-state `snapshot` contains player identity, player EXP/level, gold, essence, owned character roster with shared character level, per-zone progression, inventory with static item metadata plus generated item level and scaled item stat values, and server time. Combat state is sent as a separate sync message. |
| Player snapshot | Player state includes the player's owned character roster so unlock changes can be observed over WebSocket. |

## Testing Requirements
| Area | Requirement |
| --- | --- |
| Domain | Tests are required for all domain rules. |
| Services | Tests are required for service-layer logic. |
| Slot validation | Slot validation must be covered by tests. |
| Inventory ownership | Inventory ownership rules must be covered by tests. |
| Unit test framework | Use Kotest. |
| Mocking | Use MockK instead of manual fake repositories or publishers. |
| Frontend unit tests | Use Vitest with Testing Library for frontend unit and component coverage. |
| Frontend browser tests | Use Playwright for frontend smoke coverage; mock backend transport in-browser when the goal is frontend regression detection rather than backend verification. |
| Kotlin lint fixes | When Kotlin lint/style issues need autofix, run `cd backend && ./gradlew ktlintFormat` instead of manually formatting by hand unless a specific exception is necessary. |
| Build gate | `./gradlew check` must include lint and tests. |

## Out of Scope
| Excluded capability |
| --- |
| crafting |
| trading + marketplace |
| party system |
| splitting the game backend into separately deployed microservices |
| prematurely introducing scale infrastructure such as Redis, SQS, SNS, or similar before the current multi-instance design actually needs it; those remain valid future options if scale justifies them |
| external identity providers or third-party auth |
