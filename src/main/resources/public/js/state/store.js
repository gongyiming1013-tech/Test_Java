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

  return { get, update, subscribe, reset };
}
