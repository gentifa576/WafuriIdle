# WafuriIdle

Monorepo for the WafuriIdle game project.

## Layout
- `backend/`: Kotlin + Quarkus server, static debug client, persistence, and server-side simulation.
- `frontend/`: product client workspace for the real browser/mobile/desktop-facing UI.
- `AGENTS.md`: repo contract and backend capability rules.
- `ARCHITECTURE.md`: architectural constraints, storage contracts, and modeling rules.

## Getting Started

### Backend
Run all backend commands from `backend/`:

```bash
cd backend
./gradlew runServer
```

Stop the local backend:

```bash
cd backend
./gradlew stopServer
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
