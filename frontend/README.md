# WafuriIdle Frontend

Browser-first product client scaffold.

## Stack
- React
- TypeScript
- Vite
- PixiJS for combat rendering

## Monorepo
- Backend lives in `../backend`
- Frontend lives in `./`
- The frontend is the real product client
- The backend still serves `/debug-client/` as a local test harness

## Run
Install dependencies once:

```bash
npm install
```

Start the backend from the monorepo root in a separate shell:

```bash
cd backend
./gradlew runServer
```

Start the frontend dev server from this directory:

```bash
npm run dev
```

Open:

```text
http://localhost:5173
```

The frontend expects the backend on `http://localhost:8080`.
In dev, Vite proxies REST and WebSocket traffic, so browser requests stay same-origin.

## Dev Transport
- REST requests are proxied to the backend
- WebSocket requests to `/ws/player/{playerId}` are proxied to the backend
- No frontend-side CORS setup is needed for local development

## Current Capability
- Create a player
- Load owned roster, teams, and inventory
- Add owned characters to a team
- Activate a team
- Start combat
- Observe player-scoped WebSocket updates
- Render fake pinball-style combat with PixiJS

## Notes
- The combat scene is preset-driven from `src/features/combat/scene/combatPresets.json`
- Path transitions are selected by matching `end` of the current path to `start` of the next path
- Ball movement is render-only; backend combat remains authoritative

## Combat Presets
The fake combat renderer is driven by:

- [combatPresets.json](src/features/combat/scene/combatPresets.json)

The file shape is:

```json
{
  "paths": [
    {
      "id": "left-outer",
      "flipperSide": "left",
      "flipperBump": 1,
      "start": "left",
      "end": "right",
      "path": [
        { "x": 0.305, "y": 0.82, "vel": 1.05 },
        { "x": 0.34, "y": 0.735, "vel": 1.22 }
      ]
    }
  ]
}
```

### Top-level
- `paths`: list of reusable path segments the ball can travel through

### Path fields
- `id`: unique path name
- `flipperSide`: which flipper should animate for this segment, `left` or `right`
- `flipperBump`: zero-based point index in `path` that represents where the flipper should fire
- `start`: logical entry label for the segment
- `end`: logical exit label for the segment
- `path`: ordered points the ball should travel through for this segment

### Path point fields
- `x`: normalized X position from `0.0` to `1.0`
- `y`: normalized Y position from `0.0` to `1.0`
- `vel`: relative travel speed at that point

`x` and `y` are normalized to the current board size, so:
- `0.0, 0.0` is the top-left of the combat board
- `1.0, 1.0` is the bottom-right of the combat board
- you should keep values inside that range unless you intentionally want off-board motion

`vel` is not raw pixels per second. It is a relative weight used by the renderer:
- higher `vel` means the ball moves faster through that segment
- lower `vel` means the ball lingers longer through that segment

### How path chaining works
When one segment finishes, the renderer looks for the next candidate by matching:

- `current.end == next.start`

If multiple paths match, one is chosen at random.

That means you can create branching motion by adding multiple paths with the same `start`.

Example:
- `left -> right`
- `left -> right`
- `right -> left`

This lets the renderer vary the next ball motion while still keeping transitions valid.

### How to create a new path
1. Pick a unique `id`.
2. Decide which flipper launches this segment with `flipperSide`.
3. Set `start` and `end` labels so it can chain with other paths.
4. Add ordered `path` points from launch point to landing point.
5. Set `flipperBump` to the point index where the paddle should fire.
6. Tune `vel` so the segment still feels good within the 1-second combat cycle.

### Authoring rules
- The first point should be near the tip of the launching flipper, not the hinge.
- The last point should be near the tip of the receiving flipper.
- Keep the enemy-touch point near the top-center of the board.
- Use at least 5-7 points for a path that curves cleanly.
- Keep `flipperBump` within the valid point range.
- If a path ends at `right`, make sure there is at least one other path whose `start` is `right`, or the loop will fall back to the available preset pool.

### Practical example
If you want a new left-launch path:
- set `flipperSide` to `left`
- make the first point near the left flipper tip
- make the last point near the right flipper tip
- set `start` to `left`
- set `end` to `right`

If you want two possible returns from the right side:
- add two different paths with `start: "right"`
- both can end at `left`
- the renderer will pick one randomly

## Current Layout
- `src/app`: app shell and top-level composition
- `src/core`: config, transport clients, and shared API types
- `src/features/combat`: fake combat renderer boundary and client hook
- `src/screens`: page-level composition
- `src/shared/styles`: global styling
