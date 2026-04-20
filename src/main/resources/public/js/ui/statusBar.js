/**
 * Renders the three-line status bar at the bottom of the page.
 */

export function renderStatusBar(state, el) {
  const { status, error, snapshot, progress } = state;

  // Line 1b: progress (V6b)
  if (el.progressLine) {
    if (progress) {
      const n = progress.stepIndex + 1;
      const m = progress.totalSteps;
      const pct = m ? Math.round((n / m) * 100) : 0;
      const tag = progress.blocked ? " [BLOCKED]" : "";
      el.progressLine.textContent = `Progress: Step ${n}/${m} (${pct}%)${tag}`;
    } else {
      el.progressLine.textContent = "Progress: —";
    }
  }

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
