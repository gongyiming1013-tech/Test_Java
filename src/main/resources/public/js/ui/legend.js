/**
 * Renders the three-row legend below the canvas:
 *   Symbols | Colors (dynamic) | Grid info (dynamic)
 */

export function renderLegend(state, el) {
  const { config, snapshot } = state;

  // Colors row — one dot per rover
  el.legendColors.innerHTML = "";
  const rovers = config.rovers ?? [];
  rovers.forEach((rover, index) => {
    const span = document.createElement("span");
    span.className = "legend-item";
    const dot = document.createElement("span");
    dot.className = "legend-dot";
    dot.style.background = `var(--rover-${index % 8})`;
    span.appendChild(dot);
    span.appendChild(document.createTextNode(rover.id));
    el.legendColors.appendChild(span);
  });
  const obsSpan = document.createElement("span");
  obsSpan.className = "legend-item";
  const obsDot = document.createElement("span");
  obsDot.className = "legend-dot";
  obsDot.style.background = "var(--obstacle)";
  obsSpan.appendChild(obsDot);
  obsSpan.appendChild(document.createTextNode("Obstacle"));
  el.legendColors.appendChild(obsSpan);

  // Grid info row
  const viewport = snapshot?.viewport ?? null;
  const mode = config.width && config.height ? `Bounded ${config.width} × ${config.height}` : "Unbounded (∞)";
  const vpStr = viewport
    ? `x[${viewport.xMin}..${viewport.xMax}] y[${viewport.yMin}..${viewport.yMax}]`
    : "—";
  el.legendGridInfo.textContent =
    `Mode: ${mode}    Origin (0,0) = bottom-left    Viewport: ${vpStr}`;

  // Viewport strip (above canvas)
  el.viewportStrip.textContent = `Viewport: ${vpStr}`;
}
