"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent, KeyboardEvent } from "react";
import { scenarios } from "../lib/scenarios";
import { formatInteger, MAX_SCENARIO_BYTES, SimulationError, submitSimulation, validateScenario } from "../lib/simulation";
import type { SimulationResult } from "../lib/simulation";
import styles from "./SimulationLab.module.css";

type CompletedRun = { result: SimulationResult; rawResponse: string; completedAt: string; elapsed: number; label: string };
const ROW_LIMIT = 100;

function downloadJson(text: string, filename: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

export default function SimulationLab() {
  const [text, setText] = useState<string>(scenarios[0].json);
  const [preset, setPreset] = useState<string>(scenarios[0].id);
  const [running, setRunning] = useState(false);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState<{ code: string; message: string } | null>(null);
  const [completed, setCompleted] = useState<CompletedRun | null>(null);
  const activeRequest = useRef<AbortController | null>(null);
  const generation = useRef(0);
  const fileInput = useRef<HTMLInputElement>(null);
  const validation = useMemo(() => validateScenario(text), [text]);

  useEffect(() => () => {
    generation.current += 1;
    activeRequest.current?.abort();
  }, []);

  function invalidate() {
    generation.current += 1;
    activeRequest.current?.abort();
    activeRequest.current = null;
    setRunning(false);
    setImporting(false);
    setCompleted(null);
    setError(null);
    setNotice("");
  }

  function edit(next: string, presetId = "custom") {
    invalidate();
    setText(next);
    setPreset(presetId);
  }

  async function importFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    invalidate();
    const id = generation.current;
    if (file.size > MAX_SCENARIO_BYTES) {
      setError({ code: "IMPORT_TOO_LARGE", message: "Choose a JSON file no larger than 1 MiB." });
      return;
    }
    setImporting(true);
    try {
      const content = await file.text();
      if (id !== generation.current) return;
      setText(content);
      setPreset("custom");
      setNotice(`Imported ${file.name}. Review the scenario before running it.`);
    } catch {
      if (id === generation.current) setError({ code: "IMPORT_FAILED", message: "This file could not be read. Choose another JSON file." });
    } finally {
      if (id === generation.current) setImporting(false);
    }
  }

  async function run() {
    if (!validation.valid || running || importing) return;
    invalidate();
    const id = generation.current;
    const controller = new AbortController();
    activeRequest.current = controller;
    setRunning(true);
    setNotice("Running an isolated batch in the Java engine…");
    let timedOut = false;
    const timeout = window.setTimeout(() => { timedOut = true; controller.abort(); }, 15_000);
    try {
      const response = await submitSimulation(text, controller.signal);
      if (id !== generation.current) return;
      setCompleted({ ...response, completedAt: new Date().toLocaleTimeString(),
        elapsed: response.elapsedMs,
        label: scenarios.find((scenario) => scenario.id === preset)?.label ?? "Custom scenario" });
      setNotice("Simulation complete. Results below belong to the current input.");
    } catch (failure) {
      if (id !== generation.current) return;
      setNotice("");
      if (controller.signal.aborted) {
        setError({ code: timedOut ? "REQUEST_TIMEOUT" : "REQUEST_CANCELLED",
          message: timedOut ? "The engine did not respond within 15 seconds. Check the API connection and try again." : "Request cancelled. No result is shown." });
      } else if (failure instanceof SimulationError) {
        setError({ code: failure.code, message: failure.message });
      } else {
        setError({ code: "CONNECTION_FAILED", message: "Could not reach the simulation service. Check the API connection and try again." });
      }
    } finally {
      window.clearTimeout(timeout);
      if (id === generation.current) {
        activeRequest.current = null;
        setRunning(false);
      }
    }
  }

  function cancel() {
    invalidate();
    setNotice("Request cancelled. The engine may finish its batch, but its result will not be shown.");
  }

  function editorKeys(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void run();
    }
  }

  return (
    <section className={styles.lab} aria-labelledby="simulation-lab-title">
      <div className={styles.heading}>
        <div><span className={styles.eyebrow}>Experiment / observe / understand</span><h2 id="simulation-lab-title">Simulation lab</h2>
          <p>Edit a synthetic scenario and run the Java matching engine. Each request starts a fresh batch with its own clients, positions, and order books.</p></div>
        <span className={styles.batchBadge}>Isolated batch · not a live market</span>
      </div>

      <fieldset className={styles.presetGroup}>
        <legend>Start with a scenario</legend>
        <div className={styles.presets}>{scenarios.map((scenario, index) => (
          <button type="button" className={preset === scenario.id ? `${styles.preset} ${styles.selected}` : styles.preset}
            key={scenario.id} aria-pressed={preset === scenario.id} onClick={() => edit(scenario.json, scenario.id)}>
            <span className={styles.presetTop}><span>0{index + 1}</span><span>{scenario.phase}</span></span>
            <strong>{scenario.label}</strong><small>{scenario.description}</small>
          </button>
        ))}</div>
      </fieldset>

      <div className={styles.workspace}>
        <section className={styles.editorPanel} aria-labelledby="scenario-editor-title">
          <div className={styles.panelHeading}><div><span className={styles.eyebrow}>01 / Input</span><h3 id="scenario-editor-title">Scenario JSON</h3></div>
            <div className={styles.fileActions}><button type="button" onClick={() => fileInput.current?.click()}>Import JSON</button>
              <button type="button" disabled={!validation.valid || importing} onClick={() => downloadJson(text, "limitforge-scenario.json")}>Export JSON</button></div>
          </div>
          <input ref={fileInput} type="file" accept="application/json,.json" onChange={(event) => void importFile(event)}
            className={styles.fileInput} aria-label="Import a scenario JSON file" />
          <label className={styles.editorLabel} htmlFor="scenario-json">Clients, instruments, and orders</label>
          <textarea id="scenario-json" value={text} onChange={(event) => edit(event.target.value)} onKeyDown={editorKeys}
            spellCheck={false} autoCapitalize="off" autoComplete="off" aria-invalid={!validation.valid}
            aria-describedby="scenario-help scenario-validation" aria-keyshortcuts="Control+Enter Meta+Enter" />
          <div className={styles.editorFooter}><span>{preset === "custom" ? "Custom input" : "Synthetic preset"}</span>
            <span>{validation.bytes.toLocaleString("en-US")} / 1,048,576 bytes</span></div>
          <p id="scenario-help" className={styles.hint}>Times select the opening auction, continuous session, or closing auction. Java checks price validity, lot sizes, currencies, duplicate order IDs, and sell-position risk. Your original JSON is sent unchanged.</p>
        </section>

        <aside className={styles.runPanel} aria-label="Simulation controls">
          <span className={styles.eyebrow}>02 / Execute</span><h3>Run your session</h3>
          <dl className={styles.inputCounts}><div><dt>Clients</dt><dd>{validation.counts.clients}</dd></div>
            <div><dt>Instruments</dt><dd>{validation.counts.instruments}</dd></div><div><dt>Orders</dt><dd>{validation.counts.orders}</dd></div></dl>
          <div id="scenario-validation" className={validation.valid ? styles.valid : styles.invalid}>
            {validation.valid ? <p>JSON shape ready. Matching decisions come from Java.</p> : <>
              <strong>Check your input</strong><ul>{validation.issues.map((issue, index) => <li key={index}><code>{issue.path}</code><span>{issue.message}</span></li>)}</ul>
              {validation.issues.length === 30 && <p>Showing the first 30 issues.</p>}</>}
          </div>
          <button type="button" className={styles.runButton} onClick={() => void run()} disabled={!validation.valid || running || importing}>
            {running ? "Running in Java…" : importing ? "Reading file…" : "Run simulation"}<span aria-hidden="true">{running ? "◌" : "↗"}</span>
          </button>
          {running && <button type="button" className={styles.cancelButton} onClick={cancel}>Cancel request</button>}
          <p className={styles.shortcut}>⌘ / Ctrl + Enter to run</p>
          <p className={styles.runNote}>Editing, importing, or selecting a preset clears earlier results and cancels any pending request.</p>
        </aside>
      </div>

      <div className={styles.status} role="status" aria-live="polite">{notice}</div>
      {error && <div className={styles.error} role="alert"><strong>{error.code}</strong><p>{error.message}</p></div>}

      {completed ? <Results run={completed} /> : <div className={styles.empty} aria-busy={running}>
        <span className={styles.emptyMark} aria-hidden="true">{running ? "◌" : "→"}</span>
        <h3>{running ? "The engine is processing your scenario" : "Your next session starts here"}</h3>
        <p>{running ? "Executions, rejections, and final positions will appear when this batch completes." : "Choose a preset or import your own JSON, then run a simulation to inspect the outcome."}</p>
      </div>}
    </section>
  );
}

function Results({ run }: { run: CompletedRun }) {
  const { result } = run;
  return <section className={styles.results} aria-labelledby="simulation-results-title">
    <div className={styles.resultHeading}><div><span className={styles.eyebrow}>03 / Observe</span><h3 id="simulation-results-title">Engine results</h3>
      <p>{run.label} · completed {run.completedAt} · {run.elapsed.toLocaleString("en-US")} ms round trip</p></div>
      <button type="button" onClick={() => downloadJson(run.rawResponse, "limitforge-result.json")}>Export result JSON</button></div>
    <div className={styles.metrics}><div><span>Executions</span><strong>{result.executions.length.toLocaleString("en-US")}</strong></div>
      <div><span>Rejected orders</span><strong>{result.rejections.length.toLocaleString("en-US")}</strong></div>
      <div><span>Non-zero positions</span><strong>{result.positions.length.toLocaleString("en-US")}</strong></div></div>
    <ResultTable title="Executions" headers={["Time", "Instrument", "Price", "Quantity", "Seller", "Buyer"]} count={result.executions.length}
      rows={result.executions.slice(0, ROW_LIMIT).map((row) => [row.time, row.instrument, row.price, formatInteger(row.quantity), row.seller, row.buyer])}
      empty="No executions. Orders did not produce a match in this batch." />
    <div className={styles.resultGrid}>
      <ResultTable title="Rejected orders" headers={["Order ID", "Reason"]} count={result.rejections.length}
        rows={result.rejections.slice(0, ROW_LIMIT).map((row) => [row.orderId, row.reason])} empty="No orders were rejected." />
      <ResultTable title="Final positions" headers={["Client", "Instrument", "Net position"]} count={result.positions.length}
        rows={result.positions.slice(0, ROW_LIMIT).map((row) => [row.clientId, row.instrument, formatInteger(row.netPosition, true)])}
        empty="No non-zero final positions." />
      <ResultTable title="Opening prices" headers={["Instrument", "Clearing price"]} count={Object.keys(result.openPrices).length}
        rows={Object.entries(result.openPrices).slice(0, ROW_LIMIT)} empty="No opening auction price was established." />
      <ResultTable title="Closing prices" headers={["Instrument", "Clearing price"]} count={Object.keys(result.closePrices).length}
        rows={Object.entries(result.closePrices).slice(0, ROW_LIMIT)} empty="No closing auction price was established." />
    </div>
    <p className={styles.precisionNote}>Prices and integer positions retain the engine&apos;s exact JSON digits. These are final batch results; unmatched order-book depth is not returned by this API.</p>
  </section>;
}

function ResultTable({ title, headers, rows, empty, count }: { title: string; headers: string[]; rows: string[][]; empty: string; count: number }) {
  return <section className={styles.resultPanel}><div className={styles.tableHeading}><h4>{title}</h4><span>{count.toLocaleString("en-US")}</span></div>
    {rows.length === 0 ? <p className={styles.tableEmpty}>{empty}</p> : <>
      <div className={styles.tableScroll} role="region" aria-label={`${title} table`} tabIndex={0}><table>
        <caption className={styles.srOnly}>{title} returned by the Java matching engine</caption>
        <thead><tr>{headers.map((header) => <th key={header} scope="col">{header}</th>)}</tr></thead>
        <tbody>{rows.map((row, index) => <tr key={index}>{row.map((cell, column) => <td key={column}>{cell}</td>)}</tr>)}</tbody>
      </table></div>
      {count > ROW_LIMIT && <p className={styles.rowLimit}>Showing the first {ROW_LIMIT} rows. Export result JSON for all {count.toLocaleString("en-US")}.</p>}
    </>}
  </section>;
}
