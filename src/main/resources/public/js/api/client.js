/**
 * REST client for the Rover Web API.
 * Pure data layer — no DOM, no state. Easy to unit-test and replace.
 */

async function request(method, path, body) {
  const opts = {
    method,
    headers: { "Content-Type": "application/json" },
  };
  if (body !== undefined) opts.body = JSON.stringify(body);

  const resp = await fetch(path, opts);
  const text = await resp.text();
  const parsed = text ? JSON.parse(text) : null;

  if (!resp.ok) {
    const err = new Error(parsed?.message || `HTTP ${resp.status}`);
    err.status = resp.status;
    err.code = parsed?.code;
    throw err;
  }
  return parsed;
}

export async function createSession() {
  const { sessionId } = await request("POST", "/api/session");
  return sessionId;
}

export async function configureSession(sessionId, config) {
  return request("PUT", `/api/session/${sessionId}/config`, config);
}

export async function runSession(sessionId, commands, delayMs) {
  const body = {};
  if (commands) body.commands = commands;
  if (delayMs !== undefined && delayMs !== null) body.delayMs = delayMs;
  return request("POST", `/api/session/${sessionId}/run`,
                 Object.keys(body).length ? body : undefined);
}

/**
 * Opens an SSE stream for this session. Returns a handle with close().
 * Callbacks fire for named events: state, step, complete.
 */
export function subscribeEvents(sessionId, { onState, onStep, onComplete, onError } = {}) {
  const es = new EventSource(`/api/session/${sessionId}/events`);

  es.addEventListener("state", (e) => onState?.(JSON.parse(e.data)));
  es.addEventListener("step", (e) => onStep?.(JSON.parse(e.data)));
  es.addEventListener("complete", (e) => onComplete?.(JSON.parse(e.data)));
  es.onerror = (e) => onError?.(e);

  return { close: () => es.close() };
}

export async function getSessionState(sessionId) {
  return request("GET", `/api/session/${sessionId}/state`);
}

export async function resetSession(sessionId) {
  return request("POST", `/api/session/${sessionId}/reset`);
}

export async function deleteSession(sessionId) {
  return request("DELETE", `/api/session/${sessionId}`);
}
