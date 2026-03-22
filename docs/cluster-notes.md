# Multi-Instance Notes

## Purpose

This document records the current multi-instance direction for the backend so implementation can proceed without reopening the same design decisions.

These notes are engineering/runtime rules, not gameplay rules.

## Current Decision

The initial multi-instance approach avoids adding extra infrastructure such as Redis or a broker.

The cluster will use:
- node discovery persisted in the existing database
- internal REST between nodes
- internal node JWTs reusing the existing JWT structure with the `InternalNode` role
- local WebSocket ownership per node
- local combat ticking per node for the players that node owns

## Node Discovery

Each node should register itself on startup with:
- `instanceId`
- `internalBaseUrl`
- `lastHeartbeatAt`

Each node should refresh its heartbeat periodically.

Stale nodes should be treated as unavailable once their heartbeat expires.

## Cross-Node Player Propagation

Player-scoped publish propagation should use immediate async broadcast to all other live nodes.

Typical flow:
1. Node A mutates player state.
2. Node A marks the player dirty locally.
3. Node A asynchronously calls the internal dirty-player endpoint on every other live node.
4. Each receiving node marks that player dirty locally.
5. On the next local player sync tick, only the node that owns the player's WebSocket session will actually publish.

Notes:
- This broadcast is fire-and-forget.
- Failed broadcasts should be retried later from a local retry queue.
- This path is for player-scoped state and notifications, not cluster-wide zone state.

## Zone Aggregate Broadcast

Zone-level aggregate presence should use a separate periodic broadcast, not per-tick updates.

Initial direction:
- every `10s`, each node broadcasts its current connected-player counts by zone
- other nodes cache the latest aggregate payload from each instance
- cluster-wide zone counts are derived by summing the latest non-stale payloads

This data is advisory only.

Current intended uses:
- UI or observability
- future cluster-wide zone notifications
- future informational zone events such as boss discovery

This data should not be used as the authoritative source for combat correctness.

## Combat Ownership

Combat is currently interpreted as:
- player-scoped state
- grouped by `zoneId` within a server instance
- not globally single-owned per zone across the entire cluster

That means:
- multiple instances may process combats for the same `zoneId`
- this is acceptable because there is currently no shared global zone combat state

The real ownership invariant is:
- one active owner node per active player combat

## Combat Start

Combat start should move from REST to WebSocket-originated command flow in multi-instance mode.

Reason:
- the command should land on the node that already owns the player's active WebSocket session
- that node becomes the initial owner of the player's active combat

## Reconnect Ownership Rule

The current direction assumes:
- one active login/session per player
- one active WebSocket owner per player

Therefore:
- reconnecting to another node implies the previous connection is gone or revoked first
- live combat ownership follows the currently connected WebSocket owner node
- if the player has no active WebSocket session, live combat does not tick for that player
- disconnected players rely on offline progression instead
- when the player reconnects on another node, live combat ownership transfers automatically to that node

No separate explicit combat resume command is required for this model.

## Future Zone Events

The periodic zone broadcast mechanism is expected to evolve into a cluster-wide zone event channel.

Example future use:
- node B detects a boss or other notable zone event
- node B immediately broadcasts the zone event to the other nodes
- each node surfaces that event locally to its connected players

## Known Limits Of This First Version

This first version intentionally accepts:
- broadcast fanout to all nodes instead of targeted per-player routing
- eventual consistency for cluster-wide zone aggregate counts
- local retry instead of durable broker-backed delivery

If cluster size grows, the next likely improvement is:
- write `playerId -> instanceId` ownership into shared storage and switch from broadcast to targeted routing
