export const MAX_SCENARIO_BYTES = 1_048_576;
export const MAX_SCENARIO_ORDERS = 100_000;

export type ValidationIssue = { path: string; message: string };
export type ScenarioValidation = {
  valid: boolean;
  bytes: number;
  issues: ValidationIssue[];
  counts: { clients: number; instruments: number; orders: number };
};

type JsonObject = Record<string, unknown>;
const isObject = (value: unknown): value is JsonObject =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const nonblank = (value: unknown): value is string =>
  typeof value === "string" && value.trim().length > 0;
const int32 = (value: unknown): value is number =>
  typeof value === "number" && Number.isInteger(value)
  && value >= -2_147_483_648 && value <= 2_147_483_647;

/** Checks input shape only. The exact original text is sent to Java for matching. */
export function validateScenario(text: string): ScenarioValidation {
  const bytes = new TextEncoder().encode(text).byteLength;
  const issues: ValidationIssue[] = [];
  const counts = { clients: 0, instruments: 0, orders: 0 };
  const issue = (path: string, message: string) => {
    if (issues.length < 30) issues.push({ path, message });
  };
  const done = () => ({ valid: issues.length === 0, bytes, issues, counts });
  if (bytes > MAX_SCENARIO_BYTES) {
    issue("$", "Scenario exceeds the 1 MiB UTF-8 limit.");
    return done();
  }
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    issue("$", "Enter valid JSON: check quotes, commas, and closing brackets.");
    return done();
  }
  if (!isObject(data)) {
    issue("$", "Use a JSON object containing clients, instruments, and orders.");
    return done();
  }
  const fields = (value: JsonObject, allowed: string[], path: string) => {
    for (const key of Object.keys(value)) {
      if (!allowed.includes(key)) issue(`${path}.${key}`, "Unknown field.");
    }
  };
  const strings = (value: JsonObject, keys: string[], path: string) => {
    for (const key of keys) {
      if (!nonblank(value[key])) issue(`${path}.${key}`, "A non-empty string is required.");
    }
  };
  fields(data, ["clients", "instruments", "orders"], "$");
  for (const key of ["clients", "instruments", "orders"] as const) {
    if (!Array.isArray(data[key])) {
      issue(key, "Provide an array.");
    } else if (data[key].length === 0) {
      issue(key, "Provide at least one entry.");
    } else {
      counts[key] = data[key].length;
    }
  }
  const clientIds = new Set<string>();
  if (Array.isArray(data.clients)) data.clients.forEach((client: unknown, index: number) => {
    const path = `clients[${index}]`;
    if (!isObject(client)) { issue(path, "Client must be an object."); return; }
    fields(client, ["clientId", "currencies", "positionCheck", "rating", "positions"], path);
    strings(client, ["clientId"], path);
    if (nonblank(client.clientId)) {
      if (clientIds.has(client.clientId)) issue(`${path}.clientId`, "Client IDs must be unique.");
      clientIds.add(client.clientId);
    }
    if (!Array.isArray(client.currencies) || client.currencies.length === 0
      || client.currencies.some((currency) => !nonblank(currency))) {
      issue(`${path}.currencies`, "Provide an array of non-empty currency strings.");
    }
    if (typeof client.positionCheck !== "boolean") issue(`${path}.positionCheck`, "Use true or false.");
    if (!int32(client.rating)) issue(`${path}.rating`, "A 32-bit integer is required.");
    if (client.positions !== undefined && client.positions !== null) {
      if (!isObject(client.positions)) issue(`${path}.positions`, "Positions must be an object.");
      else Object.entries(client.positions).forEach(([instrument, quantity]) => {
        if (!nonblank(instrument) || !int32(quantity)) {
          issue(`${path}.positions.${instrument}`, "Use an instrument ID and a 32-bit integer quantity.");
        }
      });
    }
  });
  const instrumentIds = new Set<string>();
  if (Array.isArray(data.instruments)) data.instruments.forEach((instrument: unknown, index: number) => {
    const path = `instruments[${index}]`;
    if (!isObject(instrument)) { issue(path, "Instrument must be an object."); return; }
    fields(instrument, ["instrumentId", "currency", "lotSize"], path);
    strings(instrument, ["instrumentId", "currency"], path);
    if (nonblank(instrument.instrumentId)) {
      if (instrumentIds.has(instrument.instrumentId)) issue(`${path}.instrumentId`, "Instrument IDs must be unique.");
      instrumentIds.add(instrument.instrumentId);
    }
    if (!int32(instrument.lotSize) || instrument.lotSize <= 0) {
      issue(`${path}.lotSize`, "Use a positive 32-bit integer.");
    }
  });
  if (Array.isArray(data.orders)) {
    if (data.orders.length > MAX_SCENARIO_ORDERS) issue("orders", "At most 100,000 orders are allowed.");
    else data.orders.forEach((order: unknown, index: number) => {
      const path = `orders[${index}]`;
      if (!isObject(order)) { issue(path, "Order must be an object."); return; }
      fields(order, ["time", "orderId", "instrumentId", "quantity", "clientId", "type", "price", "side"], path);
      strings(order, ["time", "orderId", "instrumentId", "clientId", "type", "side"], path);
      if (typeof order.time === "string"
        && !/^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/.test(order.time)) {
        issue(`${path}.time`, "Use a local time such as 09:15:00 or 16:05:00.");
      }
      if (!int32(order.quantity) || order.quantity <= 0) issue(`${path}.quantity`, "Use a positive 32-bit integer.");
      if (typeof order.side === "string" && !["BUY", "SELL"].includes(order.side.toUpperCase())) {
        issue(`${path}.side`, "Use BUY or SELL.");
      }
      if (typeof order.type === "string" && !["MARKET", "LIMIT"].includes(order.type.toUpperCase())) {
        issue(`${path}.type`, "Use MARKET or LIMIT.");
      }
      if (typeof order.type === "string" && order.type.toUpperCase() === "LIMIT" && typeof order.price !== "number") {
        issue(`${path}.price`, "Limit orders need a JSON number, not a quoted price.");
      } else if (order.price !== undefined && order.price !== null && typeof order.price !== "number") {
        issue(`${path}.price`, "Price must be a JSON number.");
      }
    });
  }
  return done();
}

export type SimulationResult = {
  executions: { time: string; instrument: string; price: string; quantity: string; seller: string; buyer: string }[];
  rejections: { orderId: string; reason: string }[];
  positions: { clientId: string; instrument: string; netPosition: string }[];
  openPrices: Record<string, string>;
  closePrices: Record<string, string>;
};

const numberPattern = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/;

/** Preserve decimal and int64 lexemes without converting the Java output to JS numbers. */
export function parseSimulationResult(text: string): SimulationResult {
  // Match complete JSON strings first, so digits and escaped quotes inside IDs stay untouched.
  const preserved = text.replace(/"(?:\\.|[^"\\])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
    (token) => token.startsWith('"') ? token : JSON.stringify(token));
  const invalid = () => { throw new Error("The engine returned an unexpected result format."); };
  const object = (value: unknown): JsonObject => isObject(value) ? value : invalid();
  const string = (value: unknown): string => typeof value === "string" ? value : invalid();
  const numeric = (value: unknown, original: unknown): string =>
    typeof original === "number" && typeof value === "string" && numberPattern.test(value) ? value : invalid();
  const integer = (value: unknown, original: unknown, minimum: bigint, maximum: bigint): string => {
    const source = numeric(value, original);
    if (!/^-?\d+$/.test(source)) return invalid();
    const exact = BigInt(source);
    return exact >= minimum && exact <= maximum ? source : invalid();
  };
  const array = (value: unknown): unknown[] => Array.isArray(value) ? value : invalid();
  // Native parsing supplies only type/shape checks; preserved tokens supply every displayed number.
  const original = object(JSON.parse(text));
  const root = object(JSON.parse(preserved));
  const originalExecutions = array(original.executions);
  const originalPositions = array(original.positions);
  const prices = (value: unknown, source: unknown) => {
    const sourcePrices = object(source);
    return Object.fromEntries(Object.entries(object(value)).map(([key, price]) => [key, numeric(price, sourcePrices[key])]));
  };
  return {
    executions: array(root.executions).map((entry, index) => {
      const row = object(entry);
      const source = object(originalExecutions[index]);
      return { time: string(source.time), instrument: string(source.instrument), price: numeric(row.price, source.price),
        quantity: integer(row.quantity, source.quantity, BigInt(1), BigInt(2_147_483_647)),
        seller: string(source.seller), buyer: string(source.buyer) };
    }),
    rejections: array(original.rejections).map((entry) => {
      const row = object(entry);
      return { orderId: string(row.orderId), reason: string(row.reason) };
    }),
    positions: array(root.positions).map((entry, index) => {
      const row = object(entry);
      const source = object(originalPositions[index]);
      return { clientId: string(source.clientId), instrument: string(source.instrument),
        netPosition: integer(row.netPosition, source.netPosition, BigInt("-9223372036854775808"), BigInt("9223372036854775807")) };
    }),
    openPrices: prices(root.openPrices, original.openPrices),
    closePrices: prices(root.closePrices, original.closePrices),
  };
}

export function formatInteger(value: string, signed = false): string {
  const integer = BigInt(value);
  return `${signed && integer >= BigInt(0) ? "+" : ""}${integer.toLocaleString("en-US")}`;
}

export class SimulationError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.name = "SimulationError";
    this.code = code;
  }
}

export async function submitSimulation(text: string, signal: AbortSignal, fetcher: typeof fetch = fetch) {
  const started = performance.now();
  const response = await fetcher("/api/simulations", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: text, signal, cache: "no-store", redirect: "error",
  });
  const rawResponse = await response.text();
  if (!response.ok) {
    let message = `The simulation request failed (HTTP ${response.status}).`;
    let code = `HTTP_${response.status}`;
    try {
      const data: unknown = JSON.parse(rawResponse);
      if (isObject(data) && isObject(data.error)) {
        if (nonblank(data.error.code)) code = data.error.code;
        if (nonblank(data.error.message)) message = data.error.message;
      }
    } catch { /* Keep a readable fallback for non-JSON gateway errors. */ }
    throw new SimulationError(code, message);
  }
  try {
    return { result: parseSimulationResult(rawResponse), rawResponse, elapsedMs: Math.round(performance.now() - started) };
  } catch {
    throw new SimulationError("INVALID_RESPONSE", "The engine returned an unreadable result. Try again or check the API connection.");
  }
}
