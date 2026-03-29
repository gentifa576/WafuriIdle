# WafuriIdle Frontend

Browser product client for the WafuriIdle backend.

## Stack
- React
- TypeScript
- Vite
- PixiJS

## Role In The Monorepo
- `frontend/` is the product client.
- `backend/` is the authoritative game server.
- `backend` also serves `/debug-client/` as a separate local QA harness.

## Run
Install dependencies once:

```bash
npm install
```

Start the backend in a separate shell:

```bash
cd ../backend
./gradlew runServer
```

Start the frontend dev server from `frontend/`:

```bash
npm run dev
```

Open:

```text
http://localhost:5173
```

## Local Transport
- The frontend expects the backend on `http://localhost:8080` by default.
- Vite proxies `/auth`, `/players`, `/teams`, `/characters`, and `/ws` to the backend in local development.
- REST uses the backend-issued session token as a bearer token.
- WebSocket connects to `/ws/player/{playerId}` and passes the current session token as the `token` query parameter.

If you need a different backend origin, set `VITE_API_BASE_URL`.

## Current Capability
- Create a guest player.
- Sign up with email and password.
- Log in with name or email plus password.
- Load player, roster, teams, inventory, and zone progress.
- Claim a starter character.
- Pull characters from the gacha endpoint.
- Assign characters to team slots.
- Activate a team.
- Equip and unequip inventory items on team positions.
- Open the player WebSocket channel.
- Start combat over WebSocket with `START_COMBAT`.
- Observe player sync, combat sync, zone level-up, and offline progression notifications.
- Render the combat scene in the frontend while backend combat remains authoritative.

## Current Limitations
- The frontend currently relies on in-memory session state; refreshing the browser loses the active session token.
- The only automated frontend gate today is `npm run build`.
- `/debug-client/` is still useful for backend-oriented QA and transport inspection.

## Build
```bash
npm run build
```

## Layout
- `src/app`: app shell and top-level composition.
- `src/core`: environment config, HTTP/WebSocket clients, and shared API types.
- `src/features/combat`: combat rendering and combat HUD mapping.
- `src/features/session`: authenticated game-client state and actions.
- `src/screens`: page-level composition.
- `src/shared/styles`: global styling.
