# WafuriIdle Backend

Kotlin + Quarkus backend for a server-authoritative idle game backend with a layered structure.

Requires Java 25.

## Stack
- Kotlin
- Quarkus
- Gradle Kotlin DSL
- PostgreSQL-ready persistence
- REST + WebSocket transport

## Structure
- `src/main/kotlin/com/wafuri/idle/domain`: framework-independent domain model and invariants
- `src/main/kotlin/com/wafuri/idle/application`: service layer and ports
- `src/main/kotlin/com/wafuri/idle/persistence`: JPA entities and repository implementations
- `src/main/kotlin/com/wafuri/idle/transport`: REST and WebSocket adapters
- `src/test/kotlin/com/wafuri/idle/tests`: domain, service, persistence, REST, and WebSocket tests

## Run
Start the server in the background:

```bash
./gradlew runServer
```

Stop the background server:

```bash
./gradlew stopServer
```

The server log is written to `build/server/server.log`, and the PID file is stored at `build/server/server.pid`.

Dev and test use H2 in PostgreSQL compatibility mode. Production is configured for PostgreSQL.

The app starts on `http://localhost:8080`.

## Character Templates
Characters are static templates, not freeform player-authored definitions.

- Non-prod startup loads templates from [characters.json](src/main/resources/characters/characters.json).
- Prod startup skips the resource fetcher and loads templates from the database into an in-memory catalog.
- Prod also refreshes the DB-backed character template cache periodically.
- Character creation uses a stable `characterKey`, and REST/WS output resolves the template name from the cached catalog.
- Templates define stat growth as `base + increment` pairs for `strength`, `agility`, `intelligence`, `wisdom`, and `vitality`.
- Character level is not part of the template. Level/progression is handled separately and can currently be derived from player state.
- `skillRefs` and `passiveRef` are content placeholders only until skills/passives are implemented.

## Zone Templates
Zones are static content, not runtime combat state.

- Non-prod startup loads zones from [zones.json](src/main/resources/zones/zones.json).
- Prod startup skips the resource fetcher and loads zones from the database into an in-memory catalog.
- Prod also refreshes the DB-backed zone cache periodically.
- Zones currently define:
  - `id`
  - `name`
  - `levelRange`
  - future `eventRefs`
  - `lootTable`
  - `enemies`
- Combat currently uses the default zone from the cached zone catalog as its source of enemy data.

## Item Templates
Items are static template content plus generated inventory state.

- Non-prod startup loads item templates from [items.json](src/main/resources/items/items.json).
- Prod startup skips the resource fetcher and loads item templates from the database into an in-memory catalog.
- Prod also refreshes the DB-backed item cache periodically.
- Static item template data currently includes:
  - stable `name`
  - user-facing `displayName`
  - `type`
  - `baseStat` as `{ type, value }`
  - `subStatPool` of rollable stat types
- Player-owned inventory state currently includes:
  - generated float-valued `subStats`
  - `rarity`
  - `upgrade`
  - equip ownership via `equippedCharacterKey`

## Starting Roster
- A newly created player starts with no owned characters.
- The player may later claim one configured starter while their roster is still empty.
- The allowed starter choices come from `game.team.starter-choices`.
- A newly created player also starts with `3` empty team slots by default.
- The initial team-slot count is configured by `game.team.initial-slots`.

## Combat Loop
- Combat is server-authoritative and progresses on the existing `200ms` game tick.
- Players start combat with `POST /players/{id}/combat/start`.
- The server uses the player's active team from persistence for combat.
- If the player has no active team, combat start returns `400 Bad Request`.
- Team combat output is currently derived from character template base stats only:
  - `attack = strength.base`
  - `hit = agility.base`
  - `hp = vitality.base`
  - `teamDps = sum(attack * hit)` across living combat members only
- Combat starts in the default zone and uses that zone's first enemy name.
- Enemy max HP is globally configured at `1000` for now.
- Damage resolves on a separate `1s` cadence using real elapsed tick time.
- After an enemy dies, combat auto-continues after the configured respawn delay (`1s` by default).
- Loot drop settings are grouped under `game.combat.loot`:
  - base item drop rate: `1%` per kill
  - rarity roll weights: `common 70%`, `rare 20%`, `epic 8%`, `legendary 2%`

## Debug Client
The repository includes a dependency-free browser test client served by Quarkus at:

```text
http://localhost:8080/debug-client/
```

It uses native browser `fetch` and `WebSocket` APIs only, so it adds no frontend build step and no extra runtime dependency.

## Quality
```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew check
```

## Docker
```bash
docker compose up --build
```
