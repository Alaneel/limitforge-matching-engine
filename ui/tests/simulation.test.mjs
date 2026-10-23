import assert from "node:assert/strict";
import test from "node:test";
import { validateScenario, parseSimulationResult, formatInteger, submitSimulation, SimulationError, MAX_SCENARIO_BYTES } from "../lib/simulation.ts";
import { scenarios } from "../lib/scenarios.ts";

const scenario = () => JSON.parse(scenarios[0].json);
const resultText = '{"executions":[{"time":"10:00:01","instrument":"SIA","price":32.1000000000000000001,"quantity":100,"seller":"SELLER","buyer":"BUYER"}],"rejections":[],"positions":[{"clientId":"BUYER","instrument":"SIA","netPosition":9223372036854775807},{"clientId":"SELLER","instrument":"SIA","netPosition":-9223372036854775808}],"openPrices":{"SIA":3.210E+1},"closePrices":{}}';

test("all synthetic presets satisfy input shape without calculating matching results", () => {
  for (const preset of scenarios) {
    const validation = validateScenario(preset.json);
    assert.equal(validation.valid, true, `${preset.id}: ${JSON.stringify(validation.issues)}`);
    assert.ok(validation.counts.orders >= 2);
  }
  assert.equal(JSON.parse(scenarios.find((entry) => entry.id === "risk").json).clients[1].positionCheck, true);
});

test("validation reports precise field paths for malformed shape and numeric types", () => {
  const data = scenario();
  data.clients[0].positionCheck = null;
  data.clients[0].positions = { SIA: 2_147_483_648 };
  data.instruments[0].lotSize = 0;
  data.orders[0].quantity = 1.5;
  data.orders[0].price = "32.10";
  data.orders[0].time = "24:01";
  data.orders[0].unexpected = true;
  const validation = validateScenario(JSON.stringify(data));
  assert.equal(validation.valid, false);
  const paths = validation.issues.map((entry) => entry.path);
  for (const path of ["clients[0].positionCheck", "clients[0].positions.SIA", "instruments[0].lotSize", "orders[0].quantity", "orders[0].price", "orders[0].time", "orders[0].unexpected"]) {
    assert.ok(paths.includes(path), path);
  }
});

test("validates request arrays, unique entity IDs, JSON syntax, and UTF-8 byte limit", () => {
  for (const raw of ["{", "null", "[]", "{}", '{"clients":[],"instruments":[],"orders":[]}']) assert.equal(validateScenario(raw).valid, false);
  const data = scenario();
  data.clients.push(data.clients[0]);
  assert.match(validateScenario(JSON.stringify(data)).issues.map((entry) => entry.message).join(" "), /unique/);
  const oversized = "é".repeat(MAX_SCENARIO_BYTES / 2 + 1);
  assert.equal(validateScenario(oversized).bytes, MAX_SCENARIO_BYTES + 2);
  assert.match(validateScenario(oversized).issues[0].message, /1 MiB/);
});

test("empty order batches are rejected to match the Java API contract", () => {
  const data = scenario();
  data.orders = [];
  const validation = validateScenario(JSON.stringify(data));
  assert.equal(validation.valid, false);
  assert.ok(validation.issues.some((issue) => issue.path === "orders"));
  assert.equal(validation.counts.orders, 0);
  data.clients = [];
  assert.equal(validateScenario(JSON.stringify(data)).valid, false);
});

test("domain rejection scenarios reach Java rather than being matched or risk-evaluated in the browser", () => {
  const data = scenario();
  data.orders[0].quantity = 150;
  data.orders[0].clientId = "UNKNOWN";
  data.orders[1].orderId = data.orders[0].orderId;
  assert.equal(validateScenario(JSON.stringify(data)).valid, true);
});

test("result codec preserves decimals, exponents, and full signed int64 values", () => {
  const result = parseSimulationResult(resultText);
  assert.equal(result.executions[0].price, "32.1000000000000000001");
  assert.equal(result.openPrices.SIA, "3.210E+1");
  assert.equal(result.positions[0].netPosition, "9223372036854775807");
  assert.equal(result.positions[1].netPosition, "-9223372036854775808");
  assert.equal(formatInteger(result.positions[0].netPosition, true), "+9,223,372,036,854,775,807");
  assert.equal(formatInteger(result.positions[1].netPosition, true), "-9,223,372,036,854,775,808");
});

test("result codec keeps escaped quotes and numeric text in identifiers intact", () => {
  const raw = resultText.replace('"BUYER"', '"BUYER-123-\\\"42\\\""');
  assert.equal(parseSimulationResult(raw).executions[0].buyer, 'BUYER-123-"42"');
  assert.throws(() => parseSimulationResult("{}"));
  assert.throws(() => parseSimulationResult('{"executions":null}'));
  assert.throws(() => parseSimulationResult(resultText.replace('"quantity":100', '"quantity":{}')));
  assert.throws(() => parseSimulationResult(resultText.replace('"quantity":100', '"quantity":"100"')));
  assert.throws(() => parseSimulationResult(resultText.replace('"price":32.1000000000000000001', '"price":"32.1"')));
  assert.throws(() => parseSimulationResult(resultText.replace('"instrument":"SIA"', '"instrument":123')));
  assert.throws(() => parseSimulationResult(resultText.replace('"netPosition":9223372036854775807', '"netPosition":9223372036854775808')));
});

test("submission forwards original JSON and AbortSignal unchanged to same-origin bridge", async () => {
  const raw = scenarios[0].json.replace("32.1", "32.1000000000000000001");
  const controller = new AbortController();
  const response = await submitSimulation(raw, controller.signal, async (url, options) => {
    assert.equal(url, "/api/simulations");
    assert.equal(options.method, "POST");
    assert.equal(options.body, raw);
    assert.equal(options.signal, controller.signal);
    return new Response(resultText, { headers: { "Content-Type": "application/json" } });
  });
  assert.equal(response.rawResponse, resultText);
  assert.equal(response.result.positions[0].netPosition, "9223372036854775807");
});

test("submission exposes structured gateway errors and handles invalid success bodies", async () => {
  const signal = new AbortController().signal;
  await assert.rejects(() => submitSimulation("{}", signal, async () => new Response('{"error":{"code":"ENGINE_UNAVAILABLE","message":"Start the Java service"}}', { status: 503 })),
    (error) => error instanceof SimulationError && error.code === "ENGINE_UNAVAILABLE" && error.message === "Start the Java service");
  await assert.rejects(() => submitSimulation("{}", signal, async () => new Response("<html>Bad gateway</html>", { status: 502 })),
    (error) => error.code === "HTTP_502");
  await assert.rejects(() => submitSimulation("{}", signal, async () => new Response("{}")),
    (error) => error.code === "INVALID_RESPONSE");
});
