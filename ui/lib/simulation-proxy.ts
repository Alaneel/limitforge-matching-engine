/** Same-origin bridge to the Java batch API. It never simulates trades itself. */
export interface SimulationProxyEnv {
  SIMULATION_API_URL?: string;
}

interface SimulationProxyOptions {
  fetcher?: (request: Request) => Promise<Response>;
  timeoutMs?: number;
}

const REQUEST_LIMIT_BYTES = 1_048_576;
const RESPONSE_LIMIT_BYTES = 8 * REQUEST_LIMIT_BYTES;
const DEFAULT_TIMEOUT_MS = 10_000;

class BodyLimitError extends Error {}

function jsonError(status: number, code: string, message: string, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...headers,
    },
  });
}

function isJson(contentType: string | null): boolean {
  return contentType?.split(";", 1)[0].trim().toLowerCase() === "application/json";
}

async function readBounded(
  body: ReadableStream<Uint8Array> | null,
  maxBytes: number,
  signal: AbortSignal,
): Promise<ArrayBuffer> {
  if (!body) return new ArrayBuffer(0);
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  const cancel = () => { void reader.cancel().catch(() => {}); };
  signal.addEventListener("abort", cancel, { once: true });
  try {
    while (true) {
      if (signal.aborted) throw new Error("Request aborted");
      const { value, done } = await reader.read();
      if (signal.aborted) throw new Error("Request aborted");
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        void reader.cancel().catch(() => {});
        throw new BodyLimitError("Body exceeds configured limit");
      }
      chunks.push(value);
    }
  } finally {
    signal.removeEventListener("abort", cancel);
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes.buffer;
}

function backendUrl(configuredOrigin: string): URL | null {
  try {
    const url = new URL(configuredOrigin);
    if (!["http:", "https:"].includes(url.protocol)
      || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
      return null;
    }
    return new URL("/api/v1/simulations", url.origin);
  } catch {
    return null;
  }
}

export async function handleSimulationProxy(
  request: Request,
  env: SimulationProxyEnv,
  options: SimulationProxyOptions = {},
): Promise<Response> {
  if (new URL(request.url).pathname !== "/api/simulations") {
    return jsonError(404, "NOT_FOUND", "Route not found");
  }
  if (request.method !== "POST") {
    return jsonError(405, "METHOD_NOT_ALLOWED", "Expected POST", { Allow: "POST" });
  }
  if (request.headers.get("Origin") !== new URL(request.url).origin) {
    return jsonError(403, "CROSS_ORIGIN_REQUEST", "Submit simulations from this dashboard's origin");
  }
  if (!isJson(request.headers.get("Content-Type"))) {
    return jsonError(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
  }
  const contentLength = request.headers.get("Content-Length");
  if (contentLength !== null && (!/^\d+$/.test(contentLength) || Number(contentLength) > REQUEST_LIMIT_BYTES)) {
    return jsonError(413, "REQUEST_TOO_LARGE", "Request body exceeds 1 MiB");
  }
  const configuredOrigin = env.SIMULATION_API_URL?.trim();
  if (!configuredOrigin) {
    return jsonError(503, "SIMULATION_BACKEND_UNCONFIGURED",
      "The Java simulation backend is not configured for this dashboard. Run the Java API locally and use the local dashboard, or configure SIMULATION_API_URL on the server.");
  }
  const target = backendUrl(configuredOrigin);
  if (!target) {
    return jsonError(503, "SIMULATION_BACKEND_MISCONFIGURED",
      "SIMULATION_API_URL must be an HTTP(S) origin without credentials, path, query, or fragment");
  }

  const controller = new AbortController();
  const timeoutMs = Math.min(30_000, Math.max(1, options.timeoutMs ?? DEFAULT_TIMEOUT_MS));
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const abort = () => controller.abort();
  request.signal.addEventListener("abort", abort, { once: true });
  if (request.signal.aborted) controller.abort();
  let receivingResponse = false;
  try {
    const bytes = await readBounded(request.body, REQUEST_LIMIT_BYTES, controller.signal);
    receivingResponse = true;
    // Preserve the original JSON bytes: the Java API owns schema validation,
    // including duplicate keys, decimal prices, and strict integer quantities.
    const upstreamRequest = new Request(target, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: bytes,
      credentials: "omit",
      // workerd supports only follow/manual. Reject 3xx below before reading
      // or following the target; no request data reaches a redirect location.
      redirect: "manual",
      signal: controller.signal,
    });
    const upstream = await (options.fetcher ?? fetch)(upstreamRequest);
    if (upstream.status >= 300 && upstream.status < 400) {
      void upstream.body?.cancel().catch(() => {});
      return jsonError(502, "SIMULATION_BACKEND_UNAVAILABLE", "The Java backend returned an unexpected redirect");
    }
    if (!isJson(upstream.headers.get("Content-Type"))) {
      void upstream.body?.cancel().catch(() => {});
      return jsonError(502, "INVALID_BACKEND_RESPONSE", "The Java backend did not return JSON");
    }
    const responseBytes = await readBounded(upstream.body, RESPONSE_LIMIT_BYTES, controller.signal);
    try {
      const parsed: unknown = JSON.parse(new TextDecoder().decode(responseBytes));
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") throw new Error("Expected object");
    } catch {
      return jsonError(502, "INVALID_BACKEND_RESPONSE", "The Java backend returned an invalid JSON response");
    }
    return new Response(responseBytes, {
      status: upstream.status,
      headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
    });
  } catch (error) {
    if (controller.signal.aborted) {
      return jsonError(504, "SIMULATION_TIMEOUT", "The Java simulation did not finish within the request deadline");
    }
    if (error instanceof BodyLimitError) {
      return receivingResponse
        ? jsonError(502, "INVALID_BACKEND_RESPONSE", "The Java backend response exceeds the bridge size limit")
        : jsonError(413, "REQUEST_TOO_LARGE", "Request body exceeds 1 MiB");
    }
    return jsonError(502, "SIMULATION_BACKEND_UNAVAILABLE",
      "The Java simulation backend could not be reached. Start the Java API and check SIMULATION_API_URL.");
  } finally {
    clearTimeout(timeout);
    request.signal.removeEventListener("abort", abort);
  }
}
