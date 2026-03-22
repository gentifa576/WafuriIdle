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
`frontend/` is reserved for the real client app. The bundled `/debug-client/` remains backend-only tooling and is not the product frontend.
