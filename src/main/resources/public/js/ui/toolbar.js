/**
 * Wires the Run/Reset/Theme buttons to the store + API.
 *
 * Run flow:
 *   - Always reconfigures to pick up structural changes (added/removed rovers, obstacles).
 *   - For Continue Run: before reconfiguring, injects current rover positions from the
 *     last snapshot into the config, so rovers "start" from where they left off.
 *   - Commands are NOT cleared after Run.
 *
 * Reset flow:
 *   - Sends the ORIGINAL config (from the form) to reconfigure → rovers return to starting positions.
 */

import * as api from "../api/client.js";

export function bindToolbar(store, el) {
  el.runButton.addEventListener("click", () => triggerRun(store, /*skip*/ false));
  if (el.skipButton) {
    el.skipButton.addEventListener("click", () => triggerRun(store, /*skip*/ true));
  }

  if (el.delaySlider) {
    el.delaySlider.addEventListener("input", () => {
      const v = Number(el.delaySlider.value);
      if (el.delayValue) el.delayValue.textContent = String(v);
      store.update({ delayMs: v });
    });
  }

  el.resetButton.addEventListener("click", async () => {
    const { sessionId, config } = store.get();
    if (!sessionId) return;
    try {
      // Reset: send the ORIGINAL config (form values) — rovers go to their configured starting positions
      await api.configureSession(sessionId, config);
      const snapshot = await api.getSessionState(sessionId);
      store.update({ snapshot, status: "ready", error: null });
    } catch (err) {
      console.warn("reset failed:", err.message);
      store.update({ status: "error", error: err.message });
    }
  });

  el.themeSelect.addEventListener("change", () => {
    const theme = el.themeSelect.value;
    document.body.className = `theme-${theme}`;
    store.update({ theme });
  });
}

/**
 * Reconfigures (to pick up structural changes + Continue Run positions),
 * then triggers POST /run with a per-run delayMs override. Subsequent SSE
 * events from the stream drive the animation; this handler only kicks off
 * the run and handles setup errors.
 */
async function triggerRun(store, skip) {
  const { sessionId, config, snapshot, delayMs } = store.get();
  if (!sessionId) return;
  try {
    store.update({ status: "running", error: null, progress: null });

    const configToSend = buildRunConfig(config, snapshot);
    await api.configureSession(sessionId, configToSend);

    const commands = {};
    for (const rover of (config.rovers ?? [])) {
      commands[rover.id] = rover.commands ?? "";
    }
    const effectiveDelay = skip ? 0 : (delayMs ?? 0);
    await api.runSession(sessionId, commands, effectiveDelay);
    // SSE handler in main.js drives the rest.
  } catch (err) {
    console.error(err);
    store.update({ status: "error", error: err.message });
  }
}

/**
 * For Continue Run: if a previous snapshot exists, update rover starting
 * positions and directions to their current values from the snapshot.
 * This way, reconfigure builds the Arena with rovers at their last positions,
 * and the new Run continues from there.
 *
 * If no snapshot (first run), returns config as-is.
 */
function buildRunConfig(config, snapshot) {
  if (!snapshot || !snapshot.rovers) return config;

  const updatedRovers = (config.rovers ?? []).map(rover => {
    const serverState = snapshot.rovers[rover.id];
    if (serverState) {
      return {
        ...rover,
        x: serverState.x,
        y: serverState.y,
        direction: shortDirection(serverState.direction),
      };
    }
    return rover; // new rover not in snapshot yet — use config position
  });

  return { ...config, rovers: updatedRovers };
}

function shortDirection(fullName) {
  switch (fullName) {
    case "NORTH": return "N";
    case "EAST":  return "E";
    case "SOUTH": return "S";
    case "WEST":  return "W";
    default:      return fullName;
  }
}
