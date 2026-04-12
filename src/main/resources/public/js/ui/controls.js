/**
 * Wires the config-panel DOM controls to the store.
 * Changes to inputs update the store; store changes re-render the controls.
 *
 * Key design: structural changes (rover add/remove) rebuild DOM;
 * value changes (typing in commands) update existing DOM in-place,
 * skipping the focused element to prevent cursor loss.
 */

let lastRoverIds = [];
let lastObstacleCount = -1;

export function bindControls(store, el) {
  // Grid size
  el.gridWidth.addEventListener("input", () => {
    const v = el.gridWidth.value === "" ? null : parseInt(el.gridWidth.value, 10);
    updateConfig(store, { width: Number.isFinite(v) ? v : null });
  });
  el.gridHeight.addEventListener("input", () => {
    const v = el.gridHeight.value === "" ? null : parseInt(el.gridHeight.value, 10);
    updateConfig(store, { height: Number.isFinite(v) ? v : null });
  });
  el.wrapToggle.addEventListener("change", () => {
    updateConfig(store, { wrap: el.wrapToggle.checked });
  });

  // Conflict policy
  document.querySelectorAll('input[name="conflict"]').forEach(radio => {
    radio.addEventListener("change", () => {
      if (radio.checked) updateConfig(store, { conflictPolicy: radio.value });
    });
  });

  // Parallel
  el.parallelToggle.addEventListener("change", () => {
    updateConfig(store, { parallel: el.parallelToggle.checked });
  });

  // Obstacle add
  el.addObstacle.addEventListener("click", () => {
    const x = parseInt(el.obsX.value, 10);
    const y = parseInt(el.obsY.value, 10);
    if (Number.isFinite(x) && Number.isFinite(y)) {
      const config = store.get().config;
      const obstacles = [...(config.obstacles ?? []), { x, y }];
      updateConfig(store, { obstacles });
      el.obsX.value = "";
      el.obsY.value = "";
    }
  });

  // Add rover
  el.addRover.addEventListener("click", () => {
    const config = store.get().config;
    const existing = config.rovers ?? [];
    const nextId = nextRoverId(existing);
    const rovers = [...existing, { id: nextId, x: 0, y: 0, direction: "N", commands: "" }];
    updateConfig(store, { rovers });
  });

  // Initial render + subscribe for re-renders
  store.subscribe(state => renderControls(state, el));
}

function updateConfig(store, patch) {
  const cur = store.get().config;
  store.update({ config: { ...cur, ...patch } });
}

function nextRoverId(existing) {
  const used = new Set(existing.map(r => r.id));
  for (let i = 1; i <= 99; i++) {
    if (!used.has(`R${i}`)) return `R${i}`;
  }
  return `R${existing.length + 1}`;
}

function renderControls(state, el) {
  const cfg = state.config;

  // Grid fields — only update if the value actually changed (prevents cursor jump)
  if (document.activeElement !== el.gridWidth && el.gridWidth.value !== (cfg.width ?? "").toString())
    el.gridWidth.value = cfg.width ?? "";
  if (document.activeElement !== el.gridHeight && el.gridHeight.value !== (cfg.height ?? "").toString())
    el.gridHeight.value = cfg.height ?? "";
  el.wrapToggle.checked = !!cfg.wrap;
  el.parallelToggle.checked = !!cfg.parallel;

  // Conflict policy
  const radio = document.querySelector(`input[name="conflict"][value="${cfg.conflictPolicy}"]`);
  if (radio) radio.checked = true;

  // Obstacle list — only rebuild when count changes
  const obstacles = cfg.obstacles ?? [];
  if (obstacles.length !== lastObstacleCount) {
    rebuildObstacleList(obstacles, el.obstacleList);
    lastObstacleCount = obstacles.length;
  }

  // Rover list — structural rebuild only when IDs change
  const currentIds = (cfg.rovers ?? []).map(r => r.id);
  if (!arraysEqual(currentIds, lastRoverIds)) {
    rebuildRoverCards(cfg.rovers ?? [], el.roverList);
    lastRoverIds = [...currentIds];
  } else {
    updateRoverCardValues(cfg.rovers ?? [], el.roverList);
  }
}

function arraysEqual(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}

// ---------- Obstacle list ----------

function rebuildObstacleList(obstacles, listEl) {
  listEl.innerHTML = "";
  obstacles.forEach((o, i) => {
    const li = document.createElement("li");
    li.innerHTML = `<span>\u2022 (${o.x}, ${o.y})</span>`;
    const btn = document.createElement("button");
    btn.textContent = "\u00d7";
    btn.type = "button";
    btn.addEventListener("click", () => {
      const liveCfg = window.__roverStore.get().config;
      const next = (liveCfg.obstacles ?? []).filter((_, j) => j !== i);
      window.__roverStore.update({ config: { ...liveCfg, obstacles: next } });
    });
    li.appendChild(btn);
    listEl.appendChild(li);
  });
}

// ---------- Rover cards ----------

function rebuildRoverCards(rovers, listEl) {
  listEl.innerHTML = "";
  rovers.forEach((rover, index) => {
    listEl.appendChild(buildRoverCard(rover, index));
  });
}

/**
 * Patches existing rover card DOM elements in-place.
 * Skips any input that currently has focus so the cursor is not lost.
 */
function updateRoverCardValues(rovers, listEl) {
  const cards = listEl.querySelectorAll(".rover-card");
  rovers.forEach((rover, index) => {
    if (index >= cards.length) return;
    const card = cards[index];
    const inputs = card.querySelectorAll("input");
    const selects = card.querySelectorAll("select");

    // inputs[0]=x, inputs[1]=y, inputs[2]=commands
    if (inputs[0] && document.activeElement !== inputs[0])
      inputs[0].value = rover.x;
    if (inputs[1] && document.activeElement !== inputs[1])
      inputs[1].value = rover.y;
    if (inputs[2] && document.activeElement !== inputs[2])
      inputs[2].value = rover.commands ?? "";

    // selects[0]=direction
    if (selects[0] && document.activeElement !== selects[0])
      selects[0].value = rover.direction;
  });
}

function buildRoverCard(rover, index) {
  const card = document.createElement("div");
  card.className = "rover-card";

  const header = document.createElement("div");
  header.className = "rover-card-header";
  const title = document.createElement("strong");
  title.textContent = rover.id;
  header.appendChild(title);
  const delBtn = document.createElement("button");
  delBtn.textContent = "\u00d7";
  delBtn.type = "button";
  delBtn.addEventListener("click", () => {
    const liveCfg = window.__roverStore.get().config;
    const next = (liveCfg.rovers ?? []).filter((_, j) => j !== index);
    window.__roverStore.update({ config: { ...liveCfg, rovers: next } });
  });
  header.appendChild(delBtn);
  card.appendChild(header);

  // Position + direction row
  const row = document.createElement("div");
  row.className = "rover-field-row";

  row.appendChild(labeledInput("x", "number", rover.x, v => updateRover(index, { x: parseInt(v, 10) || 0 })));
  row.appendChild(labeledInput("y", "number", rover.y, v => updateRover(index, { y: parseInt(v, 10) || 0 })));

  const dirLabel = document.createElement("label");
  dirLabel.textContent = "dir ";
  const dirSelect = document.createElement("select");
  ["N", "E", "S", "W"].forEach(d => {
    const opt = document.createElement("option");
    opt.value = d; opt.textContent = d;
    if (rover.direction === d) opt.selected = true;
    dirSelect.appendChild(opt);
  });
  dirSelect.addEventListener("change", () => updateRover(index, { direction: dirSelect.value }));
  dirLabel.appendChild(dirSelect);
  row.appendChild(dirLabel);
  card.appendChild(row);

  // Commands input (full width)
  const cmdRow = document.createElement("div");
  cmdRow.className = "rover-field-row";
  const cmdLabel = document.createElement("label");
  cmdLabel.textContent = "cmd ";
  const cmdInput = document.createElement("input");
  cmdInput.type = "text";
  cmdInput.className = "commands";
  cmdInput.placeholder = "e.g., MMRMM";
  cmdInput.value = rover.commands ?? "";
  cmdInput.addEventListener("input", () => updateRover(index, { commands: cmdInput.value }));
  cmdLabel.appendChild(cmdInput);
  cmdLabel.style.flex = "1";
  cmdRow.appendChild(cmdLabel);
  card.appendChild(cmdRow);

  return card;
}

function updateRover(index, patch) {
  const cfg = window.__roverStore.get().config;
  const rovers = (cfg.rovers ?? []).map((r, i) => i === index ? { ...r, ...patch } : r);
  window.__roverStore.update({ config: { ...cfg, rovers } });
}

function labeledInput(labelText, type, value, onChange) {
  const label = document.createElement("label");
  label.textContent = labelText + " ";
  const input = document.createElement("input");
  input.type = type;
  input.value = value;
  input.style.width = "55px";
  input.addEventListener("input", () => onChange(input.value));
  label.appendChild(input);
  return label;
}
