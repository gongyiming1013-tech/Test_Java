/**
 * App entry point. Creates the store, binds controls/toolbar, subscribes
 * the canvas + legend + status bar to state changes, and bootstraps a session.
 */

import { createStore } from "./state/store.js";
import * as api from "./api/client.js";
import { bindControls } from "./ui/controls.js";
import { bindToolbar } from "./ui/toolbar.js";
import { renderArena } from "./ui/canvas.js";
import { renderLegend } from "./ui/legend.js";
import { renderStatusBar } from "./ui/statusBar.js";

const store = createStore();
window.__roverStore = store; // exposed so controls.js event handlers can reach back

// Cache DOM refs
const el = {
  gridWidth:     document.getElementById("grid-width"),
  gridHeight:    document.getElementById("grid-height"),
  wrapToggle:    document.getElementById("wrap-toggle"),
  parallelToggle:document.getElementById("parallel-toggle"),
  obstacleList:  document.getElementById("obstacle-list"),
  obsX:          document.getElementById("obs-x"),
  obsY:          document.getElementById("obs-y"),
  addObstacle:   document.getElementById("add-obstacle"),
  roverList:     document.getElementById("rover-list"),
  addRover:      document.getElementById("add-rover"),
  runButton:     document.getElementById("run-button"),
  resetButton:   document.getElementById("reset-button"),
  themeSelect:   document.getElementById("theme-select"),
  canvas:        document.getElementById("arena-canvas"),
  viewportStrip: document.getElementById("viewport-strip"),
  legendColors:  document.getElementById("legend-colors"),
  legendGridInfo:document.getElementById("legend-grid-info"),
  statusLine:    document.getElementById("status-line"),
  lastRunLine:   document.getElementById("last-run-line"),
  statsLine:     document.getElementById("stats-line"),
};

bindControls(store, el);
bindToolbar(store, el);

// Subscribe visual layers to state changes
store.subscribe(state => renderArena(el.canvas, state.config, state.snapshot));
store.subscribe(state => renderLegend(state, el));
store.subscribe(state => renderStatusBar(state, el));

// Bootstrap: create a fresh session
(async () => {
  try {
    const sessionId = await api.createSession();
    store.update({ sessionId });
  } catch (err) {
    console.error("Failed to create session:", err);
    store.update({ status: "error", error: "Could not connect to server" });
  }
})();
