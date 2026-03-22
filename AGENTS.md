# AGENTS.md

## Stack
| Area | Requirement |
| --- | --- |
| Language | Kotlin |
| Framework | Quarkus |
| Build | Gradle Kotlin DSL |
| Persistence | PostgreSQL-ready persistence |
| HTTP transport | REST for request/response APIs |
| Realtime transport | WebSocket for push updates only |
| Test stack | Kotest with MockK; Quarkus test harness for integration tests |
| Linting | Ktlint enforced through Gradle `check` |
| Local debug UI | Static browser client served by Quarkus with no extra frontend dependency |
| Product frontend | Separate browser client in `frontend/` using React, TypeScript, Vite, and PixiJS |
| Local server control | Use `backend/gradlew runServer` and `backend/gradlew stopServer` for background local server management |
| Character content | Static character templates loaded from resource in non-prod and from DB cache in prod |
| Zone content | Static zone templates loaded from resource in non-prod and from DB cache in prod |
| Item content | Static item templates loaded from resource in non-prod and from DB cache in prod |

## Maintenance
| Trigger | Required AGENTS.md update |
| --- | --- |
| Tech stack changes | Update the `Stack` section in the same change. |
| New, changed, or removed endpoint | Update `Current Capabilities` in the same change. |
| WebSocket contract changes | Update `WebSocket Rules` and `WebSocket Capability` in the same change. |
| Rule changes affecting domain, inventory, or testing | Update the matching rules table in the same change. |
| Tooling changes such as linting or formatting | Update `Stack` and any affected workflow/testing rules in the same change. |
| Any capability change | Do not leave `AGENTS.md` stale relative to code. |

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
| Combat ownership | Combat simulation is zone-oriented; the tick processes active combats grouped by `zoneId`, not as a flat player list. |
| Zone job lifecycle | Combat zone jobs should exist only while a zone has active players/combat state and must stop when the zone becomes empty. |
| Combat damage cadence | Combat damage resolves on a separate 1s server-side cadence using elapsed-time correction, not on every 200ms tick. |
| Combat continuation | After an enemy dies, combat auto-continues after a configured respawn delay. |
| Team model | Players may own multiple teams, but only one team may be active at a time. |
| Repository design | Repository interfaces should share a generic base contract; do not create specialized repository interfaces unless they add aggregate-specific queries. |
| Character sourcing | Load static character templates through sequential fetchers, resource first when available, then DB fallback. |
| Zone sourcing | Load static zone templates through sequential fetchers, resource first when available, then DB fallback. |
| Item sourcing | Load static item templates through sequential fetchers, resource first when available, then DB fallback. |

## Game Invariants
| Invariant | Rule |
| --- | --- |
| Team size | Team max size is 3. |
| Slot occupancy | Each slot may hold exactly one item. |
| Equipment source | Equipment must come from the owning player's inventory. |
| Duplicate equip | Duplicate equips are forbidden. |

## Inventory Rules
| Rule | Requirement |
| --- | --- |
| Ownership | Items belong to exactly one player. |
| Availability | Equipped items are unavailable to any other inventory or slot usage. |
| Unequip | Unequip returns the item to the owning player's inventory. |
| Template split | Static item content belongs to the item template catalog; generated item state belongs to `inventory_items`. |

## WebSocket Rules
| Rule | Requirement |
| --- | --- |
| Scope | Channels are player-scoped. |
| Usage | WebSocket messages push state updates only. |
| Authority | Authoritative game logic must not depend on WebSocket transport. |
| Tick source | WebSocket state updates are emitted from the server tick loop, not directly from REST commands. |
| Loop separation | WebSocket state sync must not be responsible for advancing combat simulation. |
| Publish timing | Player and combat publishes should each include small jitter to avoid synchronized fan-out spikes. |
| Change gating | Player state should not be published when the authoritative state content is unchanged. |

## Current Capabilities
| Endpoint | Request | Response | Notes |
| --- | --- | --- | --- |
| `POST /players` | `{ name }` | `Player` | Creates a player. |
| `POST /teams/{id}/characters/{characterKey}` | none | `Team` | Adds an owned unique character to the selected team. |
| `POST /teams/{id}/activate` | none | `Team` | Activates a non-empty team for its owning player. |
| `POST /players/{id}/combat/start` | none | `{ playerId, status, zoneId, activeTeamId, enemyName, enemyHp, enemyMaxHp, teamDps, members[] }` | Starts deterministic auto-combat for the player's current team in the default zone. |
| `GET /characters/templates` | none | `[{ key, name, strength, agility, intelligence, wisdom, vitality, image, skillRefs, passiveRef }]` | Returns the currently loaded character templates. |
| `GET /players/{id}` | none | `Player` | Returns the player domain model directly. |
| `GET /players/{id}/teams` | none | `[Team]` | Returns all player teams; active team is inferred from `Player.activeTeamId`. |
| `GET /players/{id}/inventory` | none | `[InventoryItem]` | Returns inventory state for the player with nested `item` data. |
| `POST /characters/{key}/equip` | `{ inventoryItemId, slot }` | `204 No Content` | Equips an owned item into a valid slot for the owned character key. |
| `POST /characters/{key}/unequip` | `{ slot }` | `204 No Content` | Unequips the item in the requested slot for the owned character key. |
| `GET /debug-client/` | none | Static HTML/JS/CSS page | Local-only manual test harness for REST commands and player-scoped WebSocket observation. |

| Transport rule | Requirement |
| --- | --- |
| Controller behavior | REST controllers must remain thin; use request DTOs only when the input shape differs, and otherwise return existing domain or application models directly. |
| Error shape | Validation and not-found failures return an error DTO `{ message }`. |
| Command handling | REST commands mutate configuration or player intent only. |
| Item ownership | Public REST must not allow clients to mint or inject inventory items directly. |
| Character creation | Public REST must not allow clients to create characters directly. |
| Character template scope | Character templates define static growth and presentation data only; progression state such as level must stay separate. |
| Character identity | Player-owned characters are unique by `characterKey`; do not introduce per-player character instance IDs unless duplicate ownership becomes a real requirement. |
| Starting roster | New players begin with the static `warrior` character unlocked. |
| Team slots | New players begin with a configured number of empty team slots. |
| Team assignment | Public REST may assign an owned character to a player-owned team by `characterKey`. |
| Team activation | Combat requires an active team, and a team cannot be activated unless it has at least one character. |
| Combat start | Public REST may start combat, but combat progression itself must remain server-driven after start. |
| Combat stat derivation | `attack = strength.base`, `hit = agility.base`, `hp = vitality.base`; do not use intelligence or wisdom in combat yet, and derive team DPS from living members only. |
| Combat timing | Use real elapsed server tick time to correct drift; do not assume every loop executes exactly on schedule. |
| Loot config | Combat loot settings are config-driven. Base item drop rate is `1%` per kill, then rarity rolls use `common 70%`, `rare 20%`, `epic 8%`, `legendary 2%`. |
| Zone model | Zones define level range, loot table, enemy list, and future event references; combat currently uses the default zone as its source of enemy data and executes grouped by zone. |
| Item model | Items define stable `name`, user-facing `displayName`, `type`, `baseStat`, and `subStatPool` as static template data. Inventory state owns generated `subStats`, `rarity`, and `upgrade`. |
| Rule enforcement | `GET` and `POST` handlers enforce rules through application services, not transport code. |
| Tick handoff | Command-side mutations must mark player state for server tick processing instead of pushing direct WebSocket events. |

## WebSocket Capability
| Area | Capability |
| --- | --- |
| Channel | `/ws/player/{playerId}` is the only WebSocket channel. |
| Source | State sync is driven by the server tick loop. |
| Tick rate | The default state-sync tick cadence is 200ms. |
| Jitter | Publish timing includes a small jitter window before sending. |
| Publish rule | Tick processing skips WebSocket publish when the player's state content did not change. |
| Payload | Event payloads are player-scoped messages `{ type, playerId, snapshot }` for player-state sync and `{ type, playerId, snapshot, serverTime }` for combat-state sync. |
| Snapshot | Player-state `snapshot` contains player identity, owned character roster, inventory with static item and generated item data, and server time. Combat state is sent as a separate sync message. |
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
| Build gate | `./gradlew check` must include lint and tests. |

## Out of Scope
| Excluded capability |
| --- |
| skills/passives |
| crafting |
| trading + marketplace |
| party system |
| microservices |
| auth with JWT token |
