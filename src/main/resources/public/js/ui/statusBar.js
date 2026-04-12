/**
 * Renders the three-line status bar at the bottom of the page.
 */

export function renderStatusBar(state, el) {
  const { status, error, snapshot } = state;

  // Line 1: status with color class
  el.statusLine.className = status === "running" ? "running" : status === "error" ? "error" : "";
  el.statusLine.textContent = status === "error"
    ? `Status: error: ${error || "unknown"}`
    : `Status: ${status}`;

  // Line 2: last run positions
  if (snapshot && snapshot.rovers && Object.keys(snapshot.rovers).length > 0) {
    const parts = Object.entries(snapshot.rovers).map(
      ([id, r]) => `${id} → (${r.x},${r.y}) ${r.direction}`
    );
    el.lastRunLine.textContent = "Last run: " + parts.join("    ");
  } else {
    el.lastRunLine.textContent = "Last run: —";
  }

  // Line 3: stats
  if (snapshot && snapshot.stats) {
    const s = snapshot.stats;
    el.statsLine.textContent =
      `Stats: Steps: ${s.totalSteps}    Blocked: ${s.blockedCount}    ` +
      `Duration: ${s.durationMs}ms    Rovers: ${s.roverCount}    Obstacles: ${s.obstacleCount}`;
  } else {
    el.statsLine.textContent = "Stats: —";
  }
}
