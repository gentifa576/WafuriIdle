const state = {
  socket: null,
  latestPlayerStateMessage: null,
  latestCombatStateMessage: null,
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
  ownedCharacterKey: document.getElementById("ownedCharacterKey"),
  characterKey: document.getElementById("characterKey"),
  equipCharacterKey: document.getElementById("equipCharacterKey"),
  unequipCharacterKey: document.getElementById("unequipCharacterKey"),
  watchPlayerId: document.getElementById("watchPlayerId"),
  inventoryItemPicker: document.getElementById("inventoryItemPicker"),
  stateOutput: document.getElementById("stateOutput"),
  logOutput: document.getElementById("logOutput"),
  wsStatus: document.getElementById("wsStatus"),
};

ids.stateOutput.textContent = "Connect WebSocket to watch player-scoped state snapshots.";
ids.logOutput.textContent = "Use the forms above to issue REST commands.";

document.getElementById("createPlayerForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const response = await request("/players", "POST", {
    name: document.getElementById("playerName").value.trim(),
  });
  state.latestPlayer = response;
  setInputValue(ids.playerId, response.id);
  setInputValue(ids.watchPlayerId, response.id);
  setSelectValue(ids.teamId, response.activeTeamId);
  syncOwnedCharacterKeys(response.ownedCharacterKeys);
  await fetchTeams(response.id);
  appendLog("Created player", response);
});

document.getElementById("assignCharacterForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const teamId = requireValue(ids.teamId.value.trim(), "Current team ID is required.");
  const characterKey = requireValue(ids.ownedCharacterKey.value.trim(), "Owned character is required.");
  const response = await request(`/teams/${teamId}/characters/${characterKey}`, "POST");
  updateTeam(response);
  syncCharacterSelection(characterKey);
  appendLog("Character assigned to team", response);
});

document.getElementById("equipForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const characterKey = resolveValue("equipCharacterKey", ids.characterKey);
  await request(`/characters/${characterKey}/equip`, "POST", {
    inventoryItemId: document.getElementById("inventoryItemId").value.trim(),
    slot: document.getElementById("equipSlot").value,
  });
  syncCharacterSelection(characterKey);
  appendLog("Equip command sent", { characterKey });
});

document.getElementById("unequipForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const characterKey = resolveValue("unequipCharacterKey", ids.characterKey);
  await request(`/characters/${characterKey}/unequip`, "POST", {
    slot: document.getElementById("unequipSlot").value,
  });
  syncCharacterSelection(characterKey);
  appendLog("Unequip command sent", { characterKey });
});

document.getElementById("fetchPlayer").addEventListener("click", async () => {
  const playerId = requireValue(ids.playerId.value.trim(), "Active player ID is required.");
  const response = await request(`/players/${playerId}`, "GET");
  state.latestPlayer = response;
  setSelectValue(ids.teamId, response.activeTeamId);
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
  const playerId = requireValue(ids.playerId.value.trim(), "Active player ID is required.");
  const response = await request(`/players/${playerId}/combat/start`, "POST");
  state.latestCombatStateMessage = {
    type: "COMBAT_STATE_SYNC",
    playerId,
    snapshot: response,
    serverTime: new Date().toISOString(),
  };
  renderStateOutput();
  appendLog("Combat started", response);
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

ids.inventoryItemPicker.addEventListener("change", () => {
  if (ids.inventoryItemPicker.value) {
    setInputValue(ids.inventoryItemId, ids.inventoryItemPicker.value);
  }
});

ids.teamId.addEventListener("change", () => {
  syncCharacterOptions();
});

ids.characterKey.addEventListener("change", () => {
  const value = ids.characterKey.value;
  setSelectValue(ids.equipCharacterKey, "");
  setSelectValue(ids.unequipCharacterKey, "");
  if (value) {
    syncCharacterSelection(value);
  }
});

document.getElementById("connectWs").addEventListener("click", () => {
  connectWebSocket();
});

document.getElementById("disconnectWs").addEventListener("click", () => {
  disconnectWebSocket();
});

document.getElementById("clearLog").addEventListener("click", () => {
  ids.logOutput.textContent = "";
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
  const socket = new WebSocket(wsUrl);
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

  socket.addEventListener("close", () => {
    ids.wsStatus.textContent = "Disconnected";
    ids.wsStatus.className = "status disconnected";
    appendLog("WebSocket disconnected");
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

function handleSocketMessage(message) {
  if (message?.type === "PLAYER_STATE_SYNC") {
    state.latestPlayerStateMessage = message;
    syncInventoryOptions(message?.snapshot?.inventory);
    syncOwnedCharacters(message?.snapshot?.ownedCharacters);
  } else if (message?.type === "COMBAT_STATE_SYNC") {
    state.latestCombatStateMessage = message;
  }
  renderStateOutput();
}

async function fetchTeams(playerId) {
  const response = await request(`/players/${playerId}/teams`, "GET");
  state.latestTeams = Array.isArray(response) ? response : [];
  syncTeamOptions();
  syncCharacterOptions();
  return response;
}

async function request(path, method, body) {
  const baseUrl = normalizedBaseUrl();
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  const payload = text ? parseJson(text) : null;
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
      playerState: state.latestPlayerStateMessage,
      combatState: state.latestCombatStateMessage,
    },
    null,
    2,
  );
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
    const equippedSuffix = item.equippedCharacterKey ? ` | equipped:${item.itemType}` : "";
    option.textContent = `${item.itemDisplayName} [${item.itemName}] (${item.itemType}) | ${item.id}${equippedSuffix}`;
    ids.inventoryItemPicker.appendChild(option);
  });

  const stillExists = state.latestInventory.some((item) => item.id === currentValue);
  if (stillExists) {
    ids.inventoryItemPicker.value = currentValue;
  } else if (inventory.length === 1) {
    ids.inventoryItemPicker.value = inventory[0].id;
    setInputValue(ids.inventoryItemId, inventory[0].id);
  }
}

function syncCharacterOptions() {
  const teamId = ids.teamId.value.trim();
  const selectedTeam = state.latestTeams.find((team) => team.id === teamId);
  const characterKeys = selectedTeam?.characterKeys || [];

  populateCharacterSelect(ids.characterKey, characterKeys, "Fetch teams to list characters for the selected team");
  populateCharacterSelect(ids.equipCharacterKey, characterKeys, "Use current character");
  populateCharacterSelect(ids.unequipCharacterKey, characterKeys, "Use current character");

  if (characterKeys.length === 1) {
    syncCharacterSelection(characterKeys[0]);
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

function populateCharacterSelect(select, characterKeys, placeholderText) {
  const currentValue = select.value;
  select.innerHTML = "";

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = characterKeys.length === 0 ? placeholderText : "Select a character";
  select.appendChild(placeholder);

  characterKeys.forEach((characterKey) => {
    const option = document.createElement("option");
    option.value = characterKey;
    option.textContent = state.ownedCharactersByKey[characterKey] || characterKey;
    select.appendChild(option);
  });

  if (characterKeys.includes(currentValue)) {
    select.value = currentValue;
  }
}

function syncCharacterSelection(characterKey) {
  setSelectValue(ids.characterKey, characterKey);
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
  syncCharacterOptions();
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
  syncCharacterOptions();
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

function buildWsUrl(path) {
  return normalizedBaseUrl().replace(/^http/, "ws") + path;
}

function setInputValue(input, value) {
  input.value = value || "";
}

function setSelectValue(select, value) {
  const nextValue = value || "";
  const optionExists = Array.from(select.options).some((option) => option.value === nextValue);
  select.value = optionExists ? nextValue : "";
}

function resolveValue(sourceId, fallbackInput) {
  const directValue = document.getElementById(sourceId).value.trim();
  if (directValue) {
    return directValue;
  }
  return requireValue(fallbackInput.value.trim(), `${fallbackInput.previousElementSibling?.textContent || "Value"} is required.`);
}

function requireValue(value, message) {
  if (!value) {
    throw new Error(message);
  }
  return value;
}

window.addEventListener("error", (event) => {
  appendLog("Client error", { message: event.error?.message || event.message });
});

window.addEventListener("unhandledrejection", (event) => {
  appendLog("Unhandled rejection", { message: String(event.reason?.message || event.reason) });
});
