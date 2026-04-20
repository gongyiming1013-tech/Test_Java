/**
 * Minimal observable store — subscribe/publish pattern.
 * Keeps the app state, notifies subscribers on change.
 * Future framework migration (React/Vue) can replace this with Redux/Pinia
 * without touching api/ or ui/.
 */

const DEFAULT_CONFIG = {
  width: 10,
  height: 10,
  wrap: false,
  obstacles: [],
  conflictPolicy: "fail",
  rovers: [
    { id: "R1", x: 0, y: 0, direction: "N", commands: "" }
  ],
  parallel: false,
};

const INITIAL_STATE = {
  sessionId: null,
  config: structuredClone(DEFAULT_CONFIG),
  snapshot: null,       // last SessionSnapshot from server
  status: "ready",      // "ready" | "running" | "done" | "error"
  error: null,
  theme: "modern",
  delayMs: 200,         // V6b: per-run animation pacing (slider value)
  progress: null,       // V6b: { stepIndex, totalSteps, blocked } | null
};

export function createStore() {
  let state = structuredClone(INITIAL_STATE);
  const listeners = new Set();

  function get() { return state; }

  function update(patch) {
    state = { ...state, ...patch };
    listeners.forEach(fn => fn(state));
  }

  function subscribe(fn) {
    listeners.add(fn);
    fn(state);
    return () => listeners.delete(fn);
  }

  function reset() {
    state = structuredClone(INITIAL_STATE);
    listeners.forEach(fn => fn(state));
  }

  /**
   * V6b: apply an SSE `step` event incrementally. Updates the rover's live
   * position/direction in snapshot.rovers and appends to trails[roverId].
   */
  function applyStepEvent(ev) {
    const snap = state.snapshot ? structuredClone(state.snapshot) : { rovers: {}, trails: {}, stats: null };
    snap.rovers = snap.rovers || {};
    snap.trails = snap.trails || {};

    snap.rovers[ev.roverId] = { x: ev.newX, y: ev.newY, direction: ev.newDir };

    const trail = snap.trails[ev.roverId] || [];
    const last = trail[trail.length - 1];
    const newPt = { x: ev.newX, y: ev.newY };
    if (!last || last.x !== newPt.x || last.y !== newPt.y) trail.push(newPt);
    snap.trails[ev.roverId] = trail;

    state = {
      ...state,
      snapshot: snap,
      progress: { stepIndex: ev.stepIndex, totalSteps: ev.totalSteps, blocked: !!ev.blocked },
      status: "running",
    };
    listeners.forEach(fn => fn(state));
  }

  /**
   * V6b: apply an SSE `complete` event. Merges final states + stats into
   * the snapshot and sets status=done.
   */
  function applyCompleteEvent(ev) {
    const snap = state.snapshot ? structuredClone(state.snapshot) : { rovers: {}, trails: {} };
    snap.stats = ev.stats;
    if (ev.finalStates) snap.rovers = { ...snap.rovers, ...ev.finalStates };
    state = { ...state, snapshot: snap, status: "done", progress: null };
    listeners.forEach(fn => fn(state));
  }

  return { get, update, subscribe, reset, applyStepEvent, applyCompleteEvent };
}
