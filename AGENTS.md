# AGENTS.md

## Stack
- Kotlin
- Quarkus
- Gradle Kotlin DSL
- PostgreSQL-ready persistence
- REST for request/response APIs
- WebSocket for push updates only

## Architecture
- Use a layered structure.
- Keep the domain layer framework-independent.
- Treat the server as authoritative for all game state and rule enforcement.

## Game Invariants
- Team max size is 3.
- Each slot may hold exactly one item.
- Equipment must come from the owning player's inventory.
- Duplicate equips are forbidden.

## Inventory Rules
- Items belong to exactly one player.
- Equipped items are unavailable to any other inventory or slot usage.
- Unequip returns the item to the owning player's inventory.

## WebSocket Rules
- Channels are player-scoped.
- WebSocket messages push state updates only.
- Authoritative game logic must not depend on WebSocket transport.

## Testing Requirements
- Tests are required for all domain rules.
- Tests are required for service-layer logic.
- Slot validation must be covered by tests.
- Inventory ownership rules must be covered by tests.

## Out of Scope
- combat
- skills/passives
- crafting
- trading
- matchmaking
- microservices
- advanced auth
