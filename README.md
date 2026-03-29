# WafuriIdle

Monorepo for the WafuriIdle game project.

## Layout
- `backend/`: Kotlin + Quarkus server, static debug client, persistence, and server-side simulation.
- `frontend/`: product client workspace for the real browser/mobile/desktop-facing UI.
- `AGENTS.md`: repo contract and backend capability rules.
- `ARCHITECTURE.md`: architectural constraints, storage contracts, and modeling rules.

## Getting Started

### Setup
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

Get the latest changes in your local copy before starting:

```bash
git pull
```

### Local Playtest
After the one-time frontend install above, run from the repo root:

```bash
./scripts/start-local-playtest.sh
```

On Windows, double-click `scripts\start-local-playtest.bat` in Explorer or run:

```bat
.\scripts\start-local-playtest.bat
```

When finished, stop the local playtest services with:

```bash
./scripts/stop-local-playtest.sh
```

On Windows, double-click `scripts\stop-local-playtest.bat` in Explorer or run:

```bat
.\scripts\stop-local-playtest.bat
```

If the script finishes successfully, the tester should:
- open the printed client URL
- choose `Guest`
- enter a name and create a guest player
- claim a starter character
- assign that character to a team slot
- activate the team
- start combat

What success looks like:
- the page opens without a blank screen
- socket status becomes `connected`
- player stats appear at the top of the page
- combat starts after the team is activated
- the combat view and notifications keep updating

### Backend
Run all backend commands from `backend/`:

```bash
cd backend
./gradlew runServer
```

On Windows `cmd.exe` or PowerShell, use:

```powershell
cd backend
.\gradlew.bat runServer
```

Stop the local backend:

```bash
cd backend
./gradlew stopServer
```

On Windows `cmd.exe` or PowerShell, use:

```powershell
cd backend
.\gradlew.bat stopServer
```

The backend debug harness is served at:

```text
http://localhost:8080/debug-client/
```

Backend details live in [backend/README.md](backend/README.md).

### Frontend
The product frontend lives in `frontend/`. The bundled `/debug-client/` remains backend-only tooling and is not the product frontend.

Run the frontend from `frontend/`:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts at:

```text
http://localhost:5173
```

Local development expects the backend on `http://localhost:8080`. By default, Vite proxies REST routes and `/ws/player/{playerId}` to the backend, so the browser client can use the same origin during development.

Frontend details live in [frontend/README.md](frontend/README.md).
