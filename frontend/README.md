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
Install these once on any platform:
- Git
- Java 25
- Node.js 20 or newer

Recommended shell by platform:
- Windows: PowerShell
- macOS: Terminal
- Linux: your normal shell

Clone the repo with HTTPS:

```bash
git clone https://github.com/gentifa576/WafuriIdle.git
cd WafuriIdle
```

Or, if your machine already has GitHub SSH access configured:

```bash
git clone git@github.com:gentifa576/WafuriIdle.git
cd WafuriIdle
```

Install frontend dependencies once:

```bash
cd frontend
npm install
cd ..
```

For the simplest local playtest flow from the repo root:

```bash
./scripts/start-local-playtest.sh
```

On Windows, double-click `scripts\start-local-playtest.bat` in Explorer or run:

```bat
.\scripts\start-local-playtest.bat
```

When the tester is done, stop both backend and frontend with:

```bash
./scripts/stop-local-playtest.sh
```

On Windows, double-click `scripts\stop-local-playtest.bat` in Explorer or run:

```bat
.\scripts\stop-local-playtest.bat
```

Then open:

```text
http://127.0.0.1:5173
```

What a tester should do:
- choose `Guest` for the fastest path
- create a player name
- claim a starter character when prompted
- assign that character to a team slot
- activate the team
- open the combat view and start combat

What a tester should look for:
- the page loads without a blank screen or obvious errors
- socket status changes to `connected`
- the player panel shows a name, level, gold, and essence
- combat can be started after a team is activated
- combat and player state continue updating after combat starts
- notifications appear for progression events such as zone level-up or offline progression

Install dependencies once:

```bash
npm install
```

Start the backend in a separate shell:

```bash
cd ../backend
./gradlew runServer
```

On Windows `cmd.exe` or PowerShell:

```powershell
cd ..\backend
.\gradlew.bat runServer
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
- WebSocket connects to `/ws/player/{playerId}` and carries the bearer token through the `bearer-token-carrier` subprotocol/header bridge so the backend can authenticate the upgrade.

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
- Start combat over WebSocket with `START_COMBAT`; rejected commands surface a `COMMAND_ERROR` message with the gameplay validation reason.
- Observe player sync, combat sync, zone level-up, and offline progression notifications.
- Render the combat scene in the frontend while backend combat remains authoritative.

## Current Limitations
- The browser smoke test uses mocked backend REST and WebSocket transport, so it guards frontend regressions without replacing live backend QA.
- `/debug-client/` is still useful for backend-oriented QA and transport inspection.

## Build
```bash
npm run build
```

## Regression Gates
Run the full frontend gate with:

```bash
npm run check
```

Run the fast unit/component suite only:

```bash
npm run test:unit
```

Run the browser smoke suite only:

```bash
npm run test:e2e
```

The browser smoke test starts a local Vite server, mocks backend REST and WebSocket traffic in the browser, and verifies the guest-to-combat path.

## Layout
- `src/app`: app shell and top-level composition.
- `src/core`: environment config, HTTP/WebSocket clients, and shared API types.
- `src/features/combat`: combat rendering and combat HUD mapping.
- `src/features/session`: authenticated game-client state and actions.
- `src/screens`: page-level composition.
- `src/shared/styles`: global styling.
