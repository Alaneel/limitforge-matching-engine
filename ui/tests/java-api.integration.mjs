import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { readFile } from "node:fs/promises";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { scenarios } from "../lib/scenarios.ts";
import { parseSimulationResult } from "../lib/simulation.ts";

const repoRoot = fileURLToPath(new URL("../../", import.meta.url));

async function startJavaApi() {
  const child = spawn("java", [
    "-Dlogback.configurationFile=logback-container.xml",
    "-cp", "target/limitforge-engine-1.0.0.jar",
    "com.trading.api.SimulationServer", "0",
  ], { cwd: repoRoot, stdio: ["ignore", "pipe", "pipe"] });
  let output = "";
  const baseUrl = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error(`Java API did not start: ${output}`));
    }, 15_000);
    function fail(error) {
      clearTimeout(timer);
      reject(error);
    }
    child.once("error", fail);
    child.once("exit", (code) => fail(new Error(`Java API exited (${code}): ${output}`)));
    function collect(chunk) {
      output = (output + chunk.toString()).slice(-8_192);
      const match = output.match(/listening on (http:\/\/127\.0\.0\.1:(\d+))/);
      if (match && Number(match[2]) > 0) {
        clearTimeout(timer);
        resolve(match[1]);
      }
    }
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
  });
  return { child, baseUrl };
}

async function stopJavaApi(child) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  const exited = once(child, "exit");
  child.kill("SIGTERM");
  const timer = setTimeout(() => child.kill("SIGKILL"), 3_000);
  try { await exited; } finally { clearTimeout(timer); }
}

test("built dashboard Worker forwards isolated batches to the real Java API", { timeout: 30_000 }, async () => {
  const { child, baseUrl } = await startJavaApi();
  try {
    const { default: worker } = await import(new URL("../dist/server/index.js", import.meta.url));
    const scenario = await readFile(new URL("../../examples/simulation-request.json", import.meta.url), "utf8");
    const context = { waitUntil() {}, passThroughOnException() {} };
    const environment = {
      ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) },
      SIMULATION_API_URL: baseUrl,
    };
    const run = (body, origin = "http://localhost", env = environment) => worker.fetch(
      new Request("http://localhost/api/simulations", {
        method: "POST", headers: { "Content-Type": "application/json", Origin: origin }, body,
      }), env, context,
    );

    const first = await run(scenario);
    assert.equal(first.status, 200);
    const result = await first.json();
    assert.deepEqual(result.executions, [{
      time: "10:00:01", instrument: "SIA", price: 32.1, quantity: 100,
      seller: "SELLER", buyer: "BUYER",
    }]);
    assert.deepEqual(result.positions, [
      { clientId: "BUYER", instrument: "SIA", netPosition: 100 },
      { clientId: "SELLER", instrument: "SIA", netPosition: -100 },
    ]);
    assert.deepEqual(result.rejections, []);
    assert.deepEqual(await (await run(scenario)).json(), result);

    const empty = JSON.parse(scenario);
    empty.orders = [];
    const emptyBatch = await run(JSON.stringify(empty));
    assert.equal(emptyBatch.status, 400);
    assert.equal((await emptyBatch.json()).error.code, "INVALID_REQUEST");
    const noFills = await run(scenarios.find((scenario) => scenario.id === "no-fill").json);
    assert.equal(noFills.status, 200);
    assert.deepEqual((await noFills.json()).executions, []);

    const auctionResponse = await run(scenarios.find((scenario) => scenario.id === "auction").json);
    assert.equal(auctionResponse.status, 200);
    const auction = parseSimulationResult(await auctionResponse.text());
    assert.deepEqual(auction.executions.map(({ time, price }) => ({ time, price })), [
      { time: "09:30", price: "32.2" }, { time: "16:10", price: "32.1" },
    ]);
    assert.deepEqual(auction.openPrices, { SIA: "32.2" });
    assert.deepEqual(auction.closePrices, { SIA: "32.1" });

    const riskResponse = await run(scenarios.find((scenario) => scenario.id === "risk").json);
    assert.equal(riskResponse.status, 200);
    const risk = parseSimulationResult(await riskResponse.text());
    assert.equal(risk.executions.length, 1);
    assert.deepEqual(risk.rejections, [
      { orderId: "BAD-LOT", reason: "REJECTED-INVALID LOT SIZE" },
      { orderId: "OVERSELL", reason: "REJECTED-POSITION CHECK FAILED" },
    ]);
    assert.deepEqual(risk.positions, [{ clientId: "BUYER", instrument: "SIA", netPosition: "100" }]);

    const invalid = await run('{"clients":[],"clients":[]}');
    assert.equal(invalid.status, 400);
    assert.equal((await invalid.json()).error.code, "INVALID_JSON");
    assert.equal((await run(scenario, "https://other.example")).status, 403);
    const unconfigured = await run(scenario, "http://localhost", { ASSETS: environment.ASSETS });
    assert.equal(unconfigured.status, 503);
    assert.equal((await unconfigured.json()).error.code, "SIMULATION_BACKEND_UNCONFIGURED");
  } finally {
    await stopJavaApi(child);
  }
});
