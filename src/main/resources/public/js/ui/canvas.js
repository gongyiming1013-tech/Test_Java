/**
 * Canvas renderer: draws the grid, obstacles, rover trails, and rovers.
 * Pure function of (config, snapshot, theme) → pixels. No animation in V6a.
 */

const MIN_CELL_PX = 12;

export function renderArena(canvas, config, snapshot) {
  const ctx = canvas.getContext("2d");
  const W = canvas.width;
  const H = canvas.height;

  const bg = cssVar("--bg-panel");
  const border = cssVar("--border");
  const fgDim = cssVar("--fg-dim");
  const obstacleColor = cssVar("--obstacle");

  // Clear
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // Decide viewport
  const viewport = snapshot?.viewport ?? defaultViewport(config);
  const vpW = viewport.xMax - viewport.xMin + 1;
  const vpH = viewport.yMax - viewport.yMin + 1;

  const cellSize = Math.max(MIN_CELL_PX, Math.floor(Math.min(W, H) / Math.max(vpW, vpH)));
  const gridPxW = cellSize * vpW;
  const gridPxH = cellSize * vpH;
  const offsetX = Math.floor((W - gridPxW) / 2);
  const offsetY = Math.floor((H - gridPxH) / 2);

  // Helper: convert (gridX, gridY) → canvas (px, py). Note: y axis flipped.
  function toCanvas(gx, gy) {
    const cx = offsetX + (gx - viewport.xMin) * cellSize;
    const cy = offsetY + (viewport.yMax - gy) * cellSize;
    return [cx, cy];
  }

  // Grid lines
  ctx.strokeStyle = border;
  ctx.lineWidth = 1;
  for (let x = 0; x <= vpW; x++) {
    ctx.beginPath();
    ctx.moveTo(offsetX + x * cellSize + 0.5, offsetY);
    ctx.lineTo(offsetX + x * cellSize + 0.5, offsetY + gridPxH);
    ctx.stroke();
  }
  for (let y = 0; y <= vpH; y++) {
    ctx.beginPath();
    ctx.moveTo(offsetX, offsetY + y * cellSize + 0.5);
    ctx.lineTo(offsetX + gridPxW, offsetY + y * cellSize + 0.5);
    ctx.stroke();
  }

  // Axis labels (every cell if small enough, else stride)
  ctx.fillStyle = fgDim;
  ctx.font = `${Math.max(9, Math.floor(cellSize * 0.35))}px ui-monospace, monospace`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const labelStride = cellSize >= 22 ? 1 : Math.ceil(22 / cellSize);
  for (let x = 0; x < vpW; x++) {
    if ((x % labelStride) !== 0) continue;
    const [cx] = toCanvas(viewport.xMin + x, viewport.yMin);
    ctx.fillText(String(viewport.xMin + x), cx + cellSize / 2, offsetY - 8);
  }
  for (let y = 0; y < vpH; y++) {
    if ((y % labelStride) !== 0) continue;
    const [, cy] = toCanvas(viewport.xMin, viewport.yMin + y);
    ctx.fillText(String(viewport.yMin + y), offsetX - 12, cy - cellSize / 2);
  }

  // Obstacles
  ctx.fillStyle = obstacleColor;
  const obstacles = config.obstacles ?? [];
  for (const obs of obstacles) {
    if (!withinViewport(obs.x, obs.y, viewport)) continue;
    const [cx, cy] = toCanvas(obs.x, obs.y);
    ctx.fillRect(cx + 2, cy - cellSize + 2, cellSize - 4, cellSize - 4);
  }

  // Trails (from snapshot if available)
  const trails = snapshot?.trails ?? {};
  const roverIdList = (config.rovers ?? []).map(r => r.id);

  roverIdList.forEach((roverId, index) => {
    const trail = trails[roverId] ?? [];
    if (trail.length === 0) return;
    const color = roverColorCss(index);
    ctx.strokeStyle = color;
    ctx.lineWidth = Math.max(2, Math.floor(cellSize * 0.1));
    ctx.globalAlpha = 0.55;
    ctx.beginPath();
    let first = true;
    for (const p of trail) {
      if (!withinViewport(p.x, p.y, viewport)) continue;
      const [cx, cy] = toCanvas(p.x, p.y);
      const px = cx + cellSize / 2;
      const py = cy - cellSize / 2;
      if (first) { ctx.moveTo(px, py); first = false; }
      else ctx.lineTo(px, py);
    }
    ctx.stroke();
    ctx.globalAlpha = 1;
  });

  // Rovers (current positions if snapshot available, else start positions from config)
  const roverList = config.rovers ?? [];
  roverList.forEach((spec, index) => {
    let x, y, dir;
    if (snapshot?.rovers?.[spec.id]) {
      const r = snapshot.rovers[spec.id];
      x = r.x; y = r.y; dir = r.direction;
    } else {
      x = spec.x; y = spec.y; dir = directionName(spec.direction);
    }
    if (!withinViewport(x, y, viewport)) return;
    const [cx, cy] = toCanvas(x, y);
    drawRover(ctx, cx + cellSize / 2, cy - cellSize / 2, cellSize, dir, roverColorCss(index));
  });
}

function drawRover(ctx, cx, cy, cellSize, direction, color) {
  const r = cellSize * 0.35;
  ctx.fillStyle = color;
  ctx.beginPath();
  // Triangle pointing in the rover's facing direction
  const tips = triangleTips(direction, r);
  ctx.moveTo(cx + tips[0][0], cy + tips[0][1]);
  ctx.lineTo(cx + tips[1][0], cy + tips[1][1]);
  ctx.lineTo(cx + tips[2][0], cy + tips[2][1]);
  ctx.closePath();
  ctx.fill();
}

function triangleTips(direction, r) {
  // Canvas y is inverted — "up" is negative y
  switch (direction) {
    case "NORTH": return [[0, -r], [-r, r], [r, r]];
    case "SOUTH": return [[0, r], [-r, -r], [r, -r]];
    case "EAST":  return [[r, 0], [-r, -r], [-r, r]];
    case "WEST":  return [[-r, 0], [r, -r], [r, r]];
    default: return [[0, -r], [-r, r], [r, r]];
  }
}

function roverColorCss(index) {
  return cssVar(`--rover-${index % 8}`);
}

function cssVar(name) {
  return getComputedStyle(document.body).getPropertyValue(name).trim() || "#ccc";
}

function withinViewport(x, y, vp) {
  return x >= vp.xMin && x <= vp.xMax && y >= vp.yMin && y <= vp.yMax;
}

function directionName(short) {
  switch ((short || "N").toUpperCase()) {
    case "N": return "NORTH";
    case "E": return "EAST";
    case "S": return "SOUTH";
    case "W": return "WEST";
    default:  return "NORTH";
  }
}

function defaultViewport(config) {
  if (config.width && config.height) {
    return { xMin: 0, yMin: 0, xMax: config.width - 1, yMax: config.height - 1 };
  }
  return { xMin: -5, yMin: -5, xMax: 4, yMax: 4 };
}
