# Architecture

## Purpose
This file defines implementation constraints for the backend architecture so changes stay consistent with the current game model and do not introduce unnecessary layers or identities.

## Layering
| Layer | Responsibility | Must Not Do |
| --- | --- | --- |
| `transport/` | HTTP and WebSocket transport, request parsing, response wiring | Hold business rules or become the source of truth |
| `application/` | Use cases, orchestration, server tick flow, projections when truly required | Invent persistence-shaped concepts without domain need |
| `domain/` | Core rules, invariants, state transitions, value objects | Depend on Quarkus, JPA, REST, WebSocket, or transport concerns |
| `persistence/` | Database entities, repository adapters, mapping | Define business rules or create extra domain identities |

## Modeling Rules
| Rule | Requirement |
| --- | --- |
| Domain first | Model game concepts from gameplay rules, not from table structure. |
| No random wrappers | Do not add new response, view, or projection objects unless the existing domain/application model cannot represent the contract cleanly. |
| No duplicate shapes | If two layers need the same shape, prefer one shared model instead of parallel copies. |
| Stable identity only | Introduce a separate ID only when the game truly needs separate identity. Do not add instance IDs when uniqueness is already guaranteed by a key. |
| Character identity | Player-owned characters are unique by `characterKey`, not by per-player character instance ID. |
| Team membership | Teams store `characterKey` values, not character instance IDs. |
| Equipment ownership | Equipped inventory points to `characterKey` and slot, not a separate character record. |
| Derived data | Derived or resolved display data should stay out of core domain state unless it is itself authoritative game state. |

## API Rules
| Rule | Requirement |
| --- | --- |
| Thin controllers | Controllers should delegate immediately to services. |
| Request DTOs only when needed | Use request DTOs only when the incoming shape differs from an existing model. |
| Reuse response models | Prefer returning existing domain or application models directly when they already express the API contract. |
| No REST-only abstractions | Do not create transport objects just to mirror an existing model with the same fields. |
| Explicit divergence only | If transport shape differs for a real reason, document that reason in code or the PR. |

## Service Rules
| Rule | Requirement |
| --- | --- |
| Services own rules | Validation and business rules belong in services and domain methods, not controllers. |
| Tick authority | Ongoing progression is server-driven from the tick loop, not request-driven. |
| Command separation | REST commands express player intent or configuration changes; they do not become the main simulation loop. |
| Zone ownership | Combat simulation should be organized around zones. Tick execution groups active combats by `zoneId` before advancing player combat state. |
| Loop separation | Player-facing sync and combat simulation run on separate loops. Combat loops must be able to stop when a zone has no active combat participants. |
| Item content split | Static item template data lives in the item catalog. Generated inventory state lives on the player-owned inventory item. |
| Loot config | Drop rates and rarity weights belong in grouped game config, not hardcoded service constants. |

## Persistence Rules
| Rule | Requirement |
| --- | --- |
| Repositories are ports | Application code depends on repository interfaces, not JPA details. |
| Persistence follows domain | Entities and schema should support the domain model; they must not dictate it. |
| No persistence leakage | Avoid exposing JPA-specific entities or persistence concerns outside `persistence/`. |

## Database Model
| Table | Key columns / properties | Purpose | Notes |
| --- | --- | --- | --- |
| `players` | `id`, `name`, `active_team_id` | Player root state | `id` is the player identity. `active_team_id` references the currently active team when one exists. |
| `player_owned_character_keys` | `player_id`, `character_key` | Player-owned roster keys | `character_key` is authoritative owned-roster state. A player may own a key at most once. |
| `teams` | `id`, `player_id` | Player-owned team slots | Teams are provisioned for players and hold no combat runtime state. |
| `team_characters` | `team_id`, `character_key` | Team membership by `characterKey` | Stores `character_key`, not per-player character instance IDs. |
| `items` | `name`, `display_name`, `type`, `base_stat`, `sub_stat_pool` | Static item template records | `name` is the stable primary key and loot-table reference. `display_name` is user-facing content and may diverge from the key. In non-prod, resource-loaded item templates are authoritative even if this table is empty. |
| `inventory_items` | `id`, `player_id`, `item_name`, `sub_stats`, `rarity`, `upgrade`, `equipped_character_key` | Player-owned generated item state and equip state | Static item content is resolved by `item_name`. Equip state stores only `equipped_character_key`. Slot is derived from the resolved item template `type`. |
| `character_templates` | `key`, `name`, `strength_base`, `strength_increment`, `agility_base`, `agility_increment`, `intelligence_base`, `intelligence_increment`, `wisdom_base`, `wisdom_increment`, `vitality_base`, `vitality_increment`, `image`, `skill_refs`, `passive_ref` | Static character content loaded from resource or DB | Source of truth for template stats and presentation data. |
| `zone_templates` | `zone_id`, `name`, `min_level`, `max_level`, `event_refs`, `loot_table`, `enemies` | Static zone content loaded from resource or DB | Source of truth for combat zone metadata, loot definitions, and enemy names. |

## Runtime-Only State
| State | Storage | Notes |
| --- | --- | --- |
| Active combat state | In-memory repository | Current combat status, zone selection, enemy HP, and combat members are runtime-only for now and must not be persisted to SQL. |
| Combat members | Derived in service and retained in runtime state only | Per-member combat data should be recalculated or kept in memory, not written to the database. |

## Storage Naming
| Storage kind | Convention |
| --- | --- |
| SQL table names | Use plural snake_case nouns. |
| Join/collection tables | Name them by relationship purpose, e.g. `team_characters`. |
| Primary runtime identity | Prefer gameplay identity like `playerId`, `teamId`, `characterKey` over generated storage-only identity when possible. |
| Columns | Use descriptive names that reflect game meaning, not transport naming. |

## Future Key-Value Storage
| Rule | Requirement |
| --- | --- |
| Key scheme first | If Redis or another key-value store is introduced, document key format in this file before adding production usage. |
| Stable prefixes | Use stable namespaced prefixes such as `player:`, `combat:`, `ws:`, `cache:`. |
| Identity alignment | Redis keys must reuse the same gameplay identities already used by the domain, such as `playerId` and `characterKey`. |
| Purpose clarity | Each key pattern must define whether it stores cache, lock, queue, session, or authoritative runtime state. |
| Expiry clarity | Any TTL-based key must document expected expiry and invalidation behavior. |

## Change Discipline
| Situation | Required action |
| --- | --- |
| New model introduced | Justify why an existing model cannot be reused. |
| New identifier introduced | Justify why existing identity is insufficient. |
| New DTO/view added | Justify why it is not redundant with an existing domain or application model. |
| Core rule changed | Update this file and `AGENTS.md` in the same change. |
