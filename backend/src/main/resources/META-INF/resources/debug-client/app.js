const state = {
  socket: null,
  sessionToken: null,
  sessionExpiresAt: null,
  starterChoices: [],
  latestPlayerStateMessage: null,
  latestCombatStateMessage: null,
  recentEvents: [],
  latestInventory: [],
  latestTeams: [],
  latestPlayer: null,
  ownedCharacterKeys: [],
  ownedCharactersByKey: {},
};

const ids = {
  baseUrl: document.getElementById("baseUrl"),
  playerId: document.getElementById("playerId"),
  teamId: document.getElementById("teamId"),
  teamSlotPosition: document.getElementById("teamSlotPosition"),
  ownedCharacterKey: document.getElementById("ownedCharacterKey"),
  watchPlayerId: document.getElementById("watchPlayerId"),
  inventoryItemPicker: document.getElementById("inventoryItemPicker"),
  stateOutput: document.getElementById("stateOutput"),
  eventOutput: document.getElementById("eventOutput"),
  logOutput: document.getElementById("logOutput"),
  wsStatus: document.getElementById("wsStatus"),
};

ids.stateOutput.textContent = "Connect WebSocket to watch player-scoped state snapshots.";
ids.eventOutput.textContent = "Offline progression and gameplay notifications will appear here.";
ids.logOutput.textContent = "Use the forms above to issue REST commands.";

document.getElementById("createPlayerForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const response = await request("/auth/signup", "POST", {
    name: document.getElementById("playerName").value.trim(),
    email: null,
    password: null,
  });
  state.sessionToken = response.sessionToken;
  state.sessionExpiresAt = response.sessionExpiresAt;
  state.latestPlayer = response.player;
  setInputValue(ids.playerId, response.player.id);
  setInputValue(ids.watchPlayerId, response.player.id);
  syncOwnedCharacterKeys(response.player.ownedCharacterKeys);
  await fetchTeams(response.player.id);
  appendLog("Signed up guest player", response);
});

document.getElementById("assignCharacterForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const teamId = requireValue(ids.teamId.value.trim(), "Current team ID is required.");
  const position = Number.parseInt(requireValue(ids.teamSlotPosition.value.trim(), "Current team slot is required."), 10);
  const characterKey = requireValue(ids.ownedCharacterKey.value.trim(), "Owned character is required.");
  const response = await saveTeamLoadout(teamId, (slots) => slots.map((slot) => (
    slot.position === position ? { ...slot, characterKey } : slot
  )));
  appendLog("Character assigned to team slot", response);
});

document.getElementById("equipForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const teamId = requireValue(ids.teamId.value.trim(), "Current team ID is required.");
  const position = Number.parseInt(requireValue(ids.teamSlotPosition.value.trim(), "Current team slot is required."), 10);
  const inventoryItemId = requireValue(ids.inventoryItemPicker.value.trim(), "Inventory item is required.");
  const slotType = document.getElementById("equipSlot").value;
  const response = await saveTeamLoadout(teamId, (slots) => slots.map((slot) => {
    if (slot.position !== position) {
      return slot;
    }
    if (slotType === "WEAPON") {
      return { ...slot, weaponItemId: inventoryItemId };
    }
    if (slotType === "ARMOR") {
      return { ...slot, armorItemId: inventoryItemId };
    }
    return { ...slot, accessoryItemId: inventoryItemId };
  }));
  appendLog("Equip command sent", { teamId, position, inventoryItemId, response });
});

document.getElementById("unequipForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const teamId = requireValue(ids.teamId.value.trim(), "Current team ID is required.");
  const position = Number.parseInt(requireValue(ids.teamSlotPosition.value.trim(), "Current team slot is required."), 10);
  const slotType = document.getElementById("unequipSlot").value;
  const response = await saveTeamLoadout(teamId, (slots) => slots.map((slot) => {
    if (slot.position !== position) {
      return slot;
    }
    if (slotType === "WEAPON") {
      return { ...slot, weaponItemId: null };
    }
    if (slotType === "ARMOR") {
      return { ...slot, armorItemId: null };
    }
    return { ...slot, accessoryItemId: null };
  });
  appendLog("Unequip command sent", { teamId, position, response });
});

document.getElementById("fetchPlayer").addEventListener("click", async () => {
  const playerId = requireValue(ids.playerId.value.trim(), "Active player ID is required.");
  const response = await request(`/players/${playerId}`, "GET");
  state.latestPlayer = response;
  syncOwnedCharacterKeys(response.ownedCharacterKeys);
  await fetchTeams(playerId);
  appendLog("Fetched player", response);
});

document.getElementById("fetchTeams").addEventListener("click", async () => {
  const playerId = requireValue(ids.playerId.value.trim(), "Active player ID is required.");
  const response = await fetchTeams(playerId);
  appendLog("Fetched teams", response);
});

document.getElementById("fetchInventory").addEventListener("click", async () => {
  const playerId = requireValue(ids.playerId.value.trim(), "Active player ID is required.");
  const response = await request(`/players/${playerId}/inventory`, "GET");
  syncInventoryOptions(response);
  appendLog("Fetched inventory", response);
});

document.getElementById("startCombat").addEventListener("click", async () => {
  requireConnectedSocket();
  state.socket.send(JSON.stringify({ type: "START_COMBAT" }));
  appendLog("Combat start command sent over WebSocket");
});

document.getElementById("activateTeam").addEventListener("click", async () => {
  const teamId = requireValue(ids.teamId.value.trim(), "Current team ID is required.");
  const response = await request(`/teams/${teamId}/activate`, "POST");
  if (state.latestPlayer) {
    state.latestPlayer = {
      ...state.latestPlayer,
      activeTeamId: teamId,
    };
  }
  updateTeam(response);
  appendLog("Team activated", response);
});

document.getElementById("connectWs").addEventListener("click", () => {
  connectWebSocket();
});

document.getElementById("disconnectWs").addEventListener("click", () => {
  disconnectWebSocket();
});

document.getElementById("clearLog").addEventListener("click", () => {
  ids.logOutput.textContent = "";
  state.recentEvents = [];
  renderEventOutput();
});

document.getElementById("copyState").addEventListener("click", () => {
  navigator.clipboard.writeText(ids.stateOutput.textContent || "");
});

document.getElementById("copyLog").addEventListener("click", () => {
  navigator.clipboard.writeText(ids.logOutput.textContent || "");
});

function connectWebSocket() {
  disconnectWebSocket();
  const playerId = requireValue(
    (ids.watchPlayerId.value || ids.playerId.value).trim(),
    "Player ID is required to open a WebSocket session.",
  );
  const wsUrl = buildWsUrl(`/ws/player/${playerId}`);
  const socket = new WebSocket(wsUrl, [
    "bearer-token-carrier",
    encodeURIComponent(`quarkus-http-upgrade#Authorization#Bearer ${requireSessionToken()}`),
  ]);
  state.socket = socket;

  socket.addEventListener("open", () => {
    ids.wsStatus.textContent = `Connected to ${wsUrl}`;
    ids.wsStatus.className = "status connected";
    appendLog("WebSocket connected", { playerId, wsUrl });
  });

  socket.addEventListener("message", (event) => {
    const parsed = parseJson(event.data);
    handleSocketMessage(parsed);
    appendLog("WebSocket state sync", parsed);
  });

  socket.addEventListener("close", (event) => {
    ids.wsStatus.textContent = "Disconnected";
    ids.wsStatus.className = "status disconnected";
    appendLog("WebSocket disconnected", { code: event.code, reason: event.reason || null });
    if (state.socket === socket) {
      state.socket = null;
    }
  });

  socket.addEventListener("error", () => {
    appendLog("WebSocket error");
  });
}

function disconnectWebSocket() {
  if (state.socket) {
    state.socket.close();
    state.socket = null;
  }
}

function requireConnectedSocket() {
  if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
    throw new Error("Connect the player WebSocket before sending combat commands.");
  }
  return state.socket;
}

function handleSocketMessage(message) {
  if (message?.type === "PLAYER_STATE_SYNC") {
    state.latestPlayerStateMessage = message;
    syncInventoryOptions(message?.snapshot?.inventory);
    syncOwnedCharacters(message?.snapshot?.ownedCharacters);
  } else if (message?.type === "COMBAT_STATE_SYNC") {
    state.latestCombatStateMessage = message;
  } else if (message?.type === "ZONE_LEVEL_UP" || message?.type === "OFFLINE_PROGRESSION") {
    pushRecentEvent(message);
  }
  renderStateOutput();
}

async function fetchTeams(playerId) {
  const response = await request(`/players/${playerId}/teams`, "GET");
  state.latestTeams = Array.isArray(response) ? response : [];
  syncTeamOptions();
  return response;
}

async function request(path, method, body) {
  const baseUrl = normalizedBaseUrl();
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(state.sessionToken ? { Authorization: `Bearer ${state.sessionToken}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const refreshedToken = response.headers.get("X-Session-Token");
  if (refreshedToken) {
    state.sessionToken = refreshedToken;
    state.sessionExpiresAt = response.headers.get("X-Session-Expires-At");
  }

  const text = await response.text();
  const payload = text ? parseJson(text) : null;
  if (payload?.sessionToken) {
    state.sessionToken = payload.sessionToken;
    state.sessionExpiresAt = payload.sessionExpiresAt;
  }
  appendLog(`${method} ${path}`, {
    status: response.status,
    body: payload,
  });

  if (!response.ok) {
    const message = payload?.message || `Request failed with status ${response.status}`;
    throw new Error(message);
  }

  return payload;
}

function appendLog(message, payload) {
  const timestamp = new Date().toLocaleTimeString();
  const line = `[${timestamp}] ${message}`;
  const body = payload ? `\n${JSON.stringify(payload, null, 2)}` : "";
  ids.logOutput.textContent = `${line}${body}\n\n${ids.logOutput.textContent}`.trim();
}

function renderStateOutput() {
  ids.stateOutput.textContent = JSON.stringify(
    {
      session: {
        expiresAt: state.sessionExpiresAt,
      },
      playerState: state.latestPlayerStateMessage,
      combatState: state.latestCombatStateMessage,
    },
    null,
    2,
  );
}

function renderEventOutput() {
  if (state.recentEvents.length === 0) {
    ids.eventOutput.textContent = "Offline progression and gameplay notifications will appear here.";
    return;
  }

  ids.eventOutput.textContent = state.recentEvents
    .map((message) => formatEventMessage(message))
    .join("\n\n");
}

function pushRecentEvent(message) {
  state.recentEvents = [message, ...state.recentEvents].slice(0, 12);
  renderEventOutput();
}

function formatEventMessage(message) {
  const serverTime = formatTimestamp(message.serverTime);
  if (message?.type === "OFFLINE_PROGRESSION") {
    const rewards = Array.isArray(message.rewards) && message.rewards.length > 0
      ? message.rewards.map((reward) => `${reward.itemName} x${reward.count}`).join(", ")
      : "none";
    return [
      `[${serverTime}] Offline progression`,
      `Player: ${message.playerId}`,
      `Offline duration: ${formatDuration(message.offlineDurationMillis)}`,
      `Kills: ${message.kills} | EXP: ${message.experienceGained}`,
      `Player level: ${message.playerLevel} (+${message.playerLevelsGained})`,
      `Zone: ${message.zoneId} level ${message.zoneLevel} (+${message.zoneLevelsGained})`,
      `Rewards: ${rewards}`,
    ].join("\n");
  }
  if (message?.type === "ZONE_LEVEL_UP") {
    return [
      `[${serverTime}] Zone level up`,
      `Player: ${message.playerId}`,
      `Zone: ${message.zoneId}`,
      `New level: ${message.level}`,
    ].join("\n");
  }
  return JSON.stringify(message, null, 2);
}

function formatTimestamp(value) {
  return value ? new Date(value).toLocaleTimeString() : new Date().toLocaleTimeString();
}

function formatDuration(durationMillis) {
  if (!Number.isFinite(durationMillis) || durationMillis < 0) {
    return "0s";
  }

  const totalSeconds = Math.floor(durationMillis / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const parts = [];

  if (hours > 0) {
    parts.push(`${hours}h`);
  }
  if (minutes > 0 || hours > 0) {
    parts.push(`${minutes}m`);
  }
  parts.push(`${seconds}s`);
  return parts.join(" ");
}

function syncInventoryOptions(inventory) {
  if (!Array.isArray(inventory)) {
    return;
  }
  state.latestInventory = inventory;
  const currentValue = ids.inventoryItemPicker.value;
  ids.inventoryItemPicker.innerHTML = "";

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = inventory.length === 0
    ? "No inventory items available"
    : "Select an inventory item";
  ids.inventoryItemPicker.appendChild(placeholder);

  inventory.forEach((item) => {
    const option = document.createElement("option");
    option.value = item.id;
    const equippedSuffix = item.equippedTeamId
      ? ` | equipped:team ${item.equippedTeamId} slot ${item.equippedPosition}`
      : "";
    option.textContent = `${item.itemDisplayName} [${item.itemName}] (${item.itemType}) | ${item.id}${equippedSuffix}`;
    ids.inventoryItemPicker.appendChild(option);
  });

  if (state.latestInventory.some((item) => item.id === currentValue)) {
    ids.inventoryItemPicker.value = currentValue;
  }
}

function syncTeamOptions() {
  const currentValue = ids.teamId.value;
  ids.teamId.innerHTML = "";

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = state.latestTeams.length === 0 ? "Fetch teams to select a team" : "Select a team";
  ids.teamId.appendChild(placeholder);

  state.latestTeams.forEach((team, index) => {
    const option = document.createElement("option");
    option.value = team.id;
    option.textContent = `Team ${index + 1} (${team.id})`;
    ids.teamId.appendChild(option);
  });

  const preferredTeamId = state.latestPlayer?.activeTeamId || currentValue;
  if (preferredTeamId && state.latestTeams.some((team) => team.id === preferredTeamId)) {
    ids.teamId.value = preferredTeamId;
  } else if (state.latestTeams.length === 1) {
    ids.teamId.value = state.latestTeams[0].id;
  }
}

function syncOwnedCharacters(ownedCharacters) {
  if (!Array.isArray(ownedCharacters)) {
    return;
  }
  state.ownedCharacterKeys = ownedCharacters.map((character) => character.key);
  state.ownedCharactersByKey = Object.fromEntries(
    ownedCharacters.map((character) => [character.key, `${character.name} (${character.key})`]),
  );
  syncOwnedCharacterOptions();
}

function syncOwnedCharacterKeys(ownedCharacterKeys) {
  if (!Array.isArray(ownedCharacterKeys)) {
    return;
  }
  state.ownedCharacterKeys = ownedCharacterKeys;
  syncOwnedCharacterOptions();
}

function syncOwnedCharacterOptions() {
  const currentValue = ids.ownedCharacterKey.value;
  ids.ownedCharacterKey.innerHTML = "";

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = state.ownedCharacterKeys.length === 0
    ? "Connect WebSocket or fetch player to list owned characters"
    : "Select an owned character";
  ids.ownedCharacterKey.appendChild(placeholder);

  state.ownedCharacterKeys.forEach((characterKey) => {
    const option = document.createElement("option");
    option.value = characterKey;
    option.textContent = state.ownedCharactersByKey[characterKey] || characterKey;
    ids.ownedCharacterKey.appendChild(option);
  });

  if (state.ownedCharacterKeys.includes(currentValue)) {
    ids.ownedCharacterKey.value = currentValue;
  } else if (state.ownedCharacterKeys.length === 1) {
    ids.ownedCharacterKey.value = state.ownedCharacterKeys[0];
  }
}

function updateTeam(updatedTeam) {
  if (!updatedTeam?.id) {
    return;
  }
  const index = state.latestTeams.findIndex((team) => team.id === updatedTeam.id);
  if (index >= 0) {
    state.latestTeams[index] = updatedTeam;
  } else {
    state.latestTeams.push(updatedTeam);
  }
  syncTeamOptions();
}

async function saveTeamLoadout(teamId, mutateSlots) {
  const team = state.latestTeams.find((entry) => entry.id === teamId);
  if (!team || !Array.isArray(team.slots)) {
    throw new Error("Fetch teams first so loadout updates have a baseline.");
  }
  const nextSlots = mutateSlots(team.slots.map((slot) => ({ ...slot })));
  const response = await request(`/teams/${teamId}/loadout`, "POST", { slots: nextSlots });
  updateTeam(response);
  return response;
}

function parseJson(value) {
  try {
    return JSON.parse(value);
  } catch (_error) {
    return value;
  }
}

function normalizedBaseUrl() {
  return ids.baseUrl.value.trim().replace(/\/+$/, "");
}

function buildWsUrl(path, query = {}) {
  const url = new URL(normalizedBaseUrl().replace(/^http/, "ws") + path);
  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      url.searchParams.set(key, value);
    }
  });
  return url.toString();
}

function setInputValue(input, value) {
  input.value = value || "";
}

function requireValue(value, message) {
  if (!value) {
    throw new Error(message);
  }
  return value;
}

function requireSessionToken() {
  return requireValue(state.sessionToken, "Sign up or log in first so the WebSocket can authenticate.");
}

window.addEventListener("error", (event) => {
  appendLog("Client error", { message: event.error?.message || event.message });
});

window.addEventListener("unhandledrejection", (event) => {
  appendLog("Unhandled rejection", { message: String(event.reason?.message || event.reason) });
});
