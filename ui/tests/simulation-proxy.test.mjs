import assert from "node:assert/strict";
import test from "node:test";
import { handleSimulationProxy } from "../lib/simulation-proxy.ts";

const ORIGIN = "https://limitforge.example";
const BACKEND = { SIMULATION_API_URL: "http://127.0.0.1:8080" };

function request(body = "{}", headers = {}, method = "POST") {
  return new Request(`${ORIGIN}/api/simulations`, {
    method,
    headers: { Origin: ORIGIN, "Content-Type": "application/json", ...headers },
    ...(method === "GET" ? {} : { body }),
  });
}

function backendJson(value, status = 200, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

async function errorCode(response) {
  return (await response.json()).error.code;
}

test("an unconfigured dashboard gives a clear unavailable result instead of fabricated trades", async () => {
  const response = await handleSimulationProxy(request(), {});
  assert.equal(response.status, 503);
  assert.equal(await errorCode(response), "SIMULATION_BACKEND_UNCONFIGURED");
});

test("forwards exact JSON bytes without browser credentials or backend response cookies", async () => {
  // Duplicate keys and decimal spelling must be left for Java to validate.
  const body = '{"quantity":100,"quantity":100.5,"price":32.10}';
  let calls = 0;
  const response = await handleSimulationProxy(request(body, {
    Cookie: "session=private",
    Authorization: "Bearer do-not-forward",
    "X-Internal-Token": "private",
  }), BACKEND, {
    fetcher: async (forwarded) => {
      calls++;
      assert.equal(forwarded.url, "http://127.0.0.1:8080/api/v1/simulations");
      assert.equal(forwarded.method, "POST");
      assert.equal(await forwarded.text(), body);
      assert.equal(forwarded.credentials, "omit");
      assert.equal(forwarded.redirect, "manual");
      assert.deepEqual([...forwarded.headers.keys()].sort(), ["accept", "content-type"]);
      return backendJson({ error: { code: "INVALID_JSON", message: "Invalid quantity" } }, 400,
        { "Set-Cookie": "private=1", "X-Backend-Secret": "hidden" });
    },
  });
  assert.equal(calls, 1);
  assert.equal(response.status, 400);
  assert.equal(await errorCode(response), "INVALID_JSON");
  assert.equal(response.headers.get("Set-Cookie"), null);
  assert.equal(response.headers.get("X-Backend-Secret"), null);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
});

test("rejects methods, foreign or missing origins, and non-JSON media types before fetching", async () => {
  const noOrigin = request();
  noOrigin.headers.delete("Origin");
  const cases = [
    [request(undefined, {}, "GET"), 405, "METHOD_NOT_ALLOWED"],
    [request("{}", { Origin: "https://other.example" }), 403, "CROSS_ORIGIN_REQUEST"],
    [noOrigin, 403, "CROSS_ORIGIN_REQUEST"],
    [request("{}", { "Content-Type": "application/jsonp" }), 415, "UNSUPPORTED_MEDIA_TYPE"],
  ];
  for (const [input, status, code] of cases) {
    const response = await handleSimulationProxy(input, BACKEND, {
      fetcher: async () => { assert.fail("Rejected requests must not reach Java"); },
    });
    assert.equal(response.status, status);
    if (status === 405) assert.equal(response.headers.get("Allow"), "POST");
    assert.equal(await errorCode(response), code);
  }
});

test("enforces request limit in UTF-8 bytes even without a Content-Length header", async () => {
  for (const input of [
    request("{}", { "Content-Length": "1048577" }),
    request("é".repeat(524_289)),
  ]) {
    const response = await handleSimulationProxy(input, BACKEND, {
      fetcher: async () => { assert.fail("Oversize requests must not reach Java"); },
    });
    assert.equal(response.status, 413);
    assert.equal(await errorCode(response), "REQUEST_TOO_LARGE");
  }
});

test("refuses backend URLs containing credentials or unexpected paths", async () => {
  for (const origin of ["file:///tmp/api", "https://user:password@example.com", "https://example.com/api", "not a URL"]) {
    const response = await handleSimulationProxy(request(), { SIMULATION_API_URL: origin });
    assert.equal(response.status, 503);
    assert.equal(await errorCode(response), "SIMULATION_BACKEND_MISCONFIGURED");
  }
});

test("returns clear errors for unavailable, redirecting, non-JSON, and malformed backends", async () => {
  const cases = [
    [async () => { throw new Error("connect refused: secret internal details"); }, "SIMULATION_BACKEND_UNAVAILABLE"],
    [async () => new Response(null, { status: 302, headers: { Location: "https://other.example" } }), "SIMULATION_BACKEND_UNAVAILABLE"],
    [async () => new Response("<html>upstream</html>"), "INVALID_BACKEND_RESPONSE"],
    [async () => new Response("not-json", { headers: { "Content-Type": "application/json" } }), "INVALID_BACKEND_RESPONSE"],
  ];
  for (const [fetcher, code] of cases) {
    const response = await handleSimulationProxy(request(), BACKEND, { fetcher });
    assert.equal(response.status, 502);
    assert.equal(await errorCode(response), code);
  }
});

test("applies the deadline while reading an upstream response body", async () => {
  const response = await handleSimulationProxy(request(), BACKEND, {
    timeoutMs: 10,
    fetcher: async () => new Response(new ReadableStream({ start() {} }), {
      headers: { "Content-Type": "application/json" },
    }),
  });
  assert.equal(response.status, 504);
  assert.equal(await errorCode(response), "SIMULATION_TIMEOUT");
});

test("bounds upstream JSON response size", async () => {
  const response = await handleSimulationProxy(request(), BACKEND, {
    fetcher: async () => new Response("x".repeat(8 * 1_048_576 + 1), {
      headers: { "Content-Type": "application/json" },
    }),
  });
  assert.equal(response.status, 502);
  assert.equal(await errorCode(response), "INVALID_BACKEND_RESPONSE");
});
