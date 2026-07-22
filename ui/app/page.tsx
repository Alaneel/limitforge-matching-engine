"use client";

import { useEffect, useState } from "react";
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
  position: new Intl.NumberFormat("en-US", { signDisplay: "always" })
    .format(position.netPosition)
    .replace("-", "−"),
  tone: position.netPosition >= 0 ? "positive" : "negative",
}));

const rejections = session.rejections.map((rejection) => ({
  id: rejection.orderId,
  reason: rejection.reason.replace(/^REJECTED-/, "").replaceAll("-", " ").toLowerCase(),
}));

const chartBars = [38, 52, 44, 71, 64, 88, 76, 69, 58, 63, 46, 41, 55, 48, 35, 28];

export default function Home() {
  const [view, setView] = useState<"overview" | "book" | "risk">("overview");
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
  const lastTrade = trades.at(-1)!;
  const change = ((lastTrade.price - session.instrument.openPrice) / session.instrument.openPrice) * 100;

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
        <nav aria-label="Dashboard views">
          {(["overview", "book", "risk"] as const).map((item) => (
            <button
              className={view === item ? "nav-button active" : "nav-button"}
              key={item}
              onClick={() => setView(item)}
            >
              {item === "book" ? "Order book" : item[0].toUpperCase() + item.slice(1)}
            </button>
          ))}
        </nav>
        <div className="session-status">
          <span className="status-dot" />
          <div><strong>Engine export loaded</strong><span>Schema v{session.schemaVersion} · {session.source}</span></div>
        </div>
      </header>

      <section className="hero-strip">
        <div>
          <span className="eyebrow">{session.instrument.id} · SGD</span>
          <div className="last-price">{lastTrade.priceLabel} <span>{change.toFixed(2)}%</span></div>
          <p>Last execution at {lastTrade.time}</p>
        </div>
        <div className="metric"><span>Open</span><strong>{session.instrument.openPrice.toFixed(2)}</strong><small>Opening auction</small></div>
        <div className="metric"><span>High / Low</span><strong>{session.instrument.high.toFixed(2)} / {session.instrument.low.toFixed(2)}</strong><small>Engine-calculated range</small></div>
        <div className="metric"><span>Volume</span><strong>{session.instrument.totalVolume.toLocaleString("en-US")}</strong><small>{trades.length} executions</small></div>
        <div className="metric"><span>VWAP</span><strong>{session.instrument.vwap.toFixed(4)}</strong><small>Exact decimal pricing</small></div>
        <button className="replay-button" onClick={runReplay} disabled={replayState === "running"}>
          {replayState === "ready" && "Replay session"}
          {replayState === "running" && "Replaying…"}
          {replayState === "complete" && "Replay complete"}
        </button>
      </section>

      {view === "overview" && (
        <div className="dashboard-grid">
          <section className="panel chart-panel">
            <div className="panel-heading">
              <div><span className="eyebrow">Price discovery</span><h2>Session trajectory</h2></div>
              <div className="legend"><span className="green-key" /> Executed price <span className="amber-key" /> Auction</div>
            </div>
            <div className="price-chart" aria-label="Intraday price chart from 09:30 to 16:10">
              <div className="price-scale"><span>32.20</span><span>32.10</span><span>32.00</span><span>31.90</span></div>
              <div className="chart-area">
                <div className="auction-band open-band"><span>OPEN</span></div>
                <div className="auction-band close-band"><span>CLOSE</span></div>
                <div className="grid-line line-one" /><div className="grid-line line-two" /><div className="grid-line line-three" />
                <div className="bar-series">
                  {chartBars.map((height, index) => <i key={index} style={{ height: `${height}%` }} />)}
                </div>
              </div>
              <div className="time-scale"><span>09:30</span><span>11:00</span><span>13:00</span><span>16:10</span></div>
            </div>
          </section>

          <OrderBook />

          <section className="panel executions-panel">
            <div className="panel-heading"><div><span className="eyebrow">Audit trail</span><h2>Recent executions</h2></div><span className="count-pill">{visibleTrades.length} / {trades.length}</span></div>
            <TradeTable trades={visibleTrades} />
          </section>

          <section className="panel positions-panel">
            <div className="panel-heading"><div><span className="eyebrow">Post-trade</span><h2>Client positions</h2></div><span className="instrument-pill">SIA</span></div>
            <div className="position-list">
              {positions.map((item) => <div className="position-row" key={item.client}><span className="client-avatar">{item.client}</span><span>Client {item.client}</span><strong className={item.tone}>{item.position}</strong></div>)}
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
          <section className="panel position-risk"><div className="panel-heading"><div><span className="eyebrow">Exposure</span><h2>Net positions</h2></div></div><div className="position-list">{positions.map((item) => <div className="position-row" key={item.client}><span className="client-avatar">{item.client}</span><span>Client {item.client}</span><strong className={item.tone}>{item.position}</strong></div>)}</div></section>
        </div>
      )}

      <footer><span>LimitForge v1.0</span><span>Price-time priority · BigDecimal pricing · FIX 4.4</span><a href="https://github.com/Alaneel/limitforge-matching-engine">View source on GitHub ↗</a></footer>
    </main>
  );
}

function OrderBook({ expanded = false }: { expanded?: boolean }) {
  return <section className={expanded ? "panel orderbook-panel expanded" : "panel orderbook-panel"}><div className="panel-heading"><div><span className="eyebrow">Depth</span><h2>Order book</h2></div><span className="spread-pill">Spread 0.10</span></div><div className="book-header"><span>Price</span><span>Quantity</span><span>Total</span></div><div className="book-side asks">{asks.map((row) => <div className="book-row" key={row.price}><strong>{row.price}</strong><span>{row.quantity}</span><span>{row.total}</span></div>)}</div><div className="mid-market"><span>31.95</span><small>Indicative midpoint</small></div><div className="book-side bids">{bids.map((row) => <div className="book-row" key={row.price}><strong>{row.price}</strong><span>{row.quantity}</span><span>{row.total}</span></div>)}</div></section>;
}

function TradeTable({ trades: rows }: { trades: typeof trades }) {
  return <div className="trade-table"><div className="trade-header"><span>Time</span><span>Price</span><span>Qty</span><span>Flow</span><span>Parties</span></div>{rows.map((trade) => <div className="trade-row" key={trade.sequence}><span>{trade.time}</span><strong>{trade.priceLabel}</strong><span>{trade.quantityLabel}</span><span className="match-flow">MATCH</span><span>{trade.seller} → {trade.buyer}</span></div>)}</div>;
}
