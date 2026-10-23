"use client";

import { useEffect, useState } from "react";
import SimulationLab from "../components/SimulationLab";
import session from "../public/session.json";

const trades = session.executions.map((execution) => ({
  ...execution,
  time: execution.time.length === 5 ? `${execution.time}:00` : execution.time,
  priceLabel: execution.price.toFixed(2),
  quantityLabel: execution.quantity.toLocaleString("en-US"),
}));

const asks = [
  { price: "32.20", quantity: "300", total: "300" },
  { price: "32.10", quantity: "100", total: "400" },
  { price: "32.00", quantity: "600", total: "1,000" },
];

const bids = [
  { price: "31.90", quantity: "400", total: "400" },
  { price: "31.80", quantity: "2,000", total: "2,400" },
  { price: "31.70", quantity: "800", total: "3,200" },
];

const positions = session.positions.map((position) => ({
  client: position.clientId,
  instrument: position.instrument,
  position: new Intl.NumberFormat("en-US", { signDisplay: "always" })
    .format(position.netPosition)
    .replace("-", "−"),
  tone: position.netPosition >= 0 ? "positive" : "negative",
}));

const rejections = session.rejections.map((rejection) => ({
  id: rejection.orderId,
  reason: rejection.reason.replace(/^REJECTED-/, "").replaceAll("-", " ").toLowerCase(),
}));

const dashboardViews = [
  { id: "overview", label: "Overview" },
  { id: "book", label: "Order book" },
  { id: "risk", label: "Risk" },
  { id: "simulate", label: "Simulation Lab" },
] as const;

function formatPrice(value: number | null | undefined, digits = 2) {
  return value == null ? "—" : value.toFixed(digits);
}

export default function Home() {
  const [view, setView] = useState<(typeof dashboardViews)[number]["id"]>("overview");
  const [replayState, setReplayState] = useState<"ready" | "running" | "complete">("ready");
  const [replayStep, setReplayStep] = useState(trades.length);

  useEffect(() => {
    if (replayState !== "running") return;
    const timer = window.setTimeout(() => {
      setReplayStep((step) => step + 1);
      if (replayStep + 1 >= trades.length) setReplayState("complete");
    }, 220);
    return () => window.clearTimeout(timer);
  }, [replayState, replayStep]);

  function runReplay() {
    setReplayStep(0);
    setReplayState("running");
  }

  const visibleTrades = trades.slice(0, replayStep);
  const lastTrade = trades.at(-1);
  const change = lastTrade && session.instrument.openPrice != null && session.instrument.openPrice > 0
    ? ((lastTrade.price - session.instrument.openPrice) / session.instrument.openPrice) * 100
    : null;

  return (
    <main className="terminal-shell">
      <header className="topbar">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">LF</div>
          <div>
            <h1>LimitForge</h1>
            <p>Java order matching engine</p>
          </div>
        </div>
        <nav className="dashboard-nav" aria-label="Dashboard views">
          {dashboardViews.map((item) => (
            <button
              type="button"
              className={view === item.id ? "nav-button active" : "nav-button"}
              key={item.id}
              onClick={() => setView(item.id)}
              aria-pressed={view === item.id}
              aria-controls="dashboard-content"
            >
              {item.label}
            </button>
          ))}
        </nav>
        <div className="session-status">
          <span className="status-dot" />
          {view === "simulate"
            ? <div><strong>Simulation workspace</strong><span>Editable scenarios · isolated batches</span></div>
            : <div><strong>Engine export loaded</strong><span>Schema v{session.schemaVersion} · {session.source}</span></div>}
        </div>
      </header>

      {view !== "simulate" && <section className="hero-strip" aria-label="Exported sample session metrics">
        <div>
          <span className="eyebrow">{session.instrument.id || "Session"} · exported sample</span>
          <div className="last-price">{lastTrade?.priceLabel ?? "—"} <span className={change != null && change < 0 ? "negative" : "positive"}>{change == null ? "—" : `${change > 0 ? "+" : ""}${change.toFixed(2)}%`}</span></div>
          <p>{lastTrade ? `Last exported execution at ${lastTrade.time}` : "No executions in this export"}</p>
        </div>
        <div className="metric"><span>Open</span><strong>{formatPrice(session.instrument.openPrice)}</strong><small>Opening auction</small></div>
        <div className="metric"><span>High / Low</span><strong>{formatPrice(session.instrument.high)} / {formatPrice(session.instrument.low)}</strong><small>Engine-calculated range</small></div>
        <div className="metric"><span>Volume</span><strong>{session.instrument.totalVolume.toLocaleString("en-US")}</strong><small>{trades.length} executions</small></div>
        <div className="metric"><span>VWAP</span><strong>{formatPrice(session.instrument.vwap, 4)}</strong><small>Quantity-weighted mean</small></div>
        <button type="button" className="replay-button" onClick={runReplay} disabled={replayState === "running" || trades.length === 0}>
          {replayState === "ready" && "Replay session"}
          {replayState === "running" && "Replaying…"}
          {replayState === "complete" && "Replay complete"}
        </button>
      </section>}

      <div id="dashboard-content">
      {view === "simulate" && <SimulationLab />}

      {view === "overview" && (
        <div className="dashboard-grid">
          <section className="panel chart-panel">
            <div className="panel-heading">
              <div><span className="eyebrow">Price discovery</span><h2>Session trajectory</h2></div>
              <div className="legend"><span className="green-key" /> Executed price <span className="amber-key" /> 09:30 / 16:10</div>
            </div>
            <ExecutionChart rows={visibleTrades} />
          </section>

          <OrderBook />

          <section className="panel executions-panel">
            <div className="panel-heading"><div><span className="eyebrow">Audit trail</span><h2>Recent executions</h2></div><span className="count-pill">{visibleTrades.length} / {trades.length}</span></div>
            <TradeTable trades={visibleTrades} />
          </section>

          <section className="panel positions-panel">
            <div className="panel-heading"><div><span className="eyebrow">Post-trade</span><h2>Client positions</h2></div><span className="instrument-pill">{session.instrument.id}</span></div>
            <div className="position-list">
              {positions.map((item) => <div className="position-row" key={`${item.client}-${item.instrument}`}><span className="client-avatar">{item.client}</span><span>Client {item.client} · {item.instrument}</span><strong className={item.tone}>{item.position}</strong></div>)}
            </div>
          </section>
        </div>
      )}

      {view === "book" && (
        <div className="focus-layout"><OrderBook expanded /><section className="panel"><div className="panel-heading"><div><span className="eyebrow">Matched orders</span><h2>Execution ledger</h2></div></div><TradeTable trades={visibleTrades} /></section></div>
      )}

      {view === "risk" && (
        <div className="risk-layout">
          <section className="panel risk-summary"><span className="eyebrow">Controls</span><h2>Risk checks</h2><div className="risk-score">{rejections.length} <span>rejected orders</span></div><p>Currency, lot-size and position rules were evaluated before admission to the order book.</p></section>
          <section className="panel rejection-panel"><div className="panel-heading"><div><span className="eyebrow">Exchange report</span><h2>Rejected orders</h2></div></div>{rejections.map((item) => <div className="rejection-row" key={item.id}><span className="reject-icon">!</span><div><strong>{item.id}</strong><small>ENGINE</small></div><p>{item.reason}</p></div>)}</section>
          <section className="panel position-risk"><div className="panel-heading"><div><span className="eyebrow">Exposure</span><h2>Net positions</h2></div></div><div className="position-list">{positions.map((item) => <div className="position-row" key={`${item.client}-${item.instrument}`}><span className="client-avatar">{item.client}</span><span>Client {item.client} · {item.instrument}</span><strong className={item.tone}>{item.position}</strong></div>)}</div></section>
        </div>
      )}

      </div>
      <footer><span>LimitForge · exchange simulator</span><span>Exported sample · batch API · BigDecimal engine pricing</span><a href="https://github.com/Alaneel/limitforge-matching-engine">View source on GitHub ↗</a></footer>
    </main>
  );
}

function OrderBook({ expanded = false }: { expanded?: boolean }) {
  return <section className={expanded ? "panel orderbook-panel expanded" : "panel orderbook-panel"}><div className="panel-heading"><div><span className="eyebrow">Illustrative depth</span><h2>Order book</h2></div><span className="spread-pill">Example spread 0.10</span></div><p className="book-disclaimer">These example levels illustrate book layout. Order-book depth is not included in the engine export or batch API response.</p><div className="book-header"><span>Price</span><span>Quantity</span><span>Total</span></div><div className="book-side asks">{asks.map((row) => <div className="book-row" key={row.price}><strong>{row.price}</strong><span>{row.quantity}</span><span>{row.total}</span></div>)}</div><div className="mid-market"><span>31.95</span><small>Example midpoint</small></div><div className="book-side bids">{bids.map((row) => <div className="book-row" key={row.price}><strong>{row.price}</strong><span>{row.quantity}</span><span>{row.total}</span></div>)}</div></section>;
}

function timeInSeconds(time: string) {
  const [hours, minutes, seconds = 0] = time.split(":").map(Number);
  return hours * 3600 + minutes * 60 + seconds;
}

function ExecutionChart({ rows }: { rows: typeof trades }) {
  const prices = trades.map((trade) => trade.price);
  const firstTime = trades.length ? Math.min(...trades.map((trade) => timeInSeconds(trade.time))) : 0;
  const lastTime = trades.length ? Math.max(...trades.map((trade) => timeInSeconds(trade.time))) : 0;
  const lowestPrice = prices.length ? Math.min(...prices) : 0;
  const highestPrice = prices.length ? Math.max(...prices) : 1;
  const padding = Math.max((highestPrice - lowestPrice) * 0.12, 0.01);
  const minimumPrice = lowestPrice - padding;
  const maximumPrice = highestPrice + padding;
  const x = (time: string) => firstTime === lastTime ? 381 : 66 + (timeInSeconds(time) - firstTime) / (lastTime - firstTime) * 630;
  const y = (price: number) => 224 - (price - minimumPrice) / (maximumPrice - minimumPrice) * 200;
  const points = rows.map((trade) => `${x(trade.time)},${y(trade.price)}`).join(" ");

  return <div className="execution-chart">
    <svg viewBox="0 0 720 272" role="img" aria-labelledby="execution-chart-title execution-chart-description">
      <title id="execution-chart-title">Executed prices from the engine export</title>
      <desc id="execution-chart-description">Each point represents an exported execution at its recorded time and price. Amber points occur at the 09:30 or 16:10 auction boundaries; the export does not identify the trading phase. Multiple executions at the same time and price may overlap. The line connects recorded executions only.</desc>
      {[0, 1, 2, 3].map((tick) => {
        const price = maximumPrice - tick / 3 * (maximumPrice - minimumPrice);
        return <g key={tick}>
          <line className="execution-grid" x1="66" x2="696" y1={y(price)} y2={y(price)} />
          <text className="execution-axis" x="54" y={y(price) + 4} textAnchor="end">{price.toFixed(2)}</text>
        </g>;
      })}
      <line className="execution-baseline" x1="66" x2="696" y1="224" y2="224" />
      {trades.length > 0 && (firstTime === lastTime ? [1.5] : [0, 1, 2, 3]).map((tick) => {
        const seconds = firstTime + tick / 3 * (lastTime - firstTime);
        const label = `${String(Math.floor(seconds / 3600)).padStart(2, "0")}:${String(Math.floor(seconds % 3600 / 60)).padStart(2, "0")}`;
        return <text key={tick} className="execution-axis" x={66 + tick / 3 * 630} y="248" textAnchor={tick === 0 ? "start" : tick === 3 ? "end" : "middle"}>{label}</text>;
      })}
      {rows.length > 1 && <polyline className="execution-line" points={points} />}
      {rows.map((trade) => <circle className={trade.time.startsWith("09:30:") || trade.time.startsWith("16:10:") ? "execution-point auction-point" : "execution-point"} key={trade.sequence} cx={x(trade.time)} cy={y(trade.price)} r="4">
        <title>{`#${trade.sequence} · ${trade.time} · ${trade.priceLabel} · ${trade.quantityLabel} units`}</title>
      </circle>)}
      {rows.length === 0 && <text className="execution-empty" x="380" y="126" textAnchor="middle">{trades.length ? "Replay starting…" : "No exported executions"}</text>}
    </svg>
    <p className="chart-caption">{rows.length} / {trades.length} exported executions · event-time axis · points may overlap</p>
  </div>;
}

function TradeTable({ trades: rows }: { trades: typeof trades }) {
  return <div className="trade-table"><div className="trade-header"><span>Time</span><span>Price</span><span>Qty</span><span>Flow</span><span>Parties</span></div>{rows.map((trade) => <div className="trade-row" key={trade.sequence}><span>{trade.time}</span><strong>{trade.priceLabel}</strong><span>{trade.quantityLabel}</span><span className="match-flow">MATCH</span><span>{trade.seller} → {trade.buyer}</span></div>)}</div>;
}
