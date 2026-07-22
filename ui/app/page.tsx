"use client";

import { useState } from "react";

const trades = [
  { time: "09:30:00", price: "32.10", quantity: "1,000", side: "BUY", buyer: "A", seller: "E" },
  { time: "09:30:00", price: "32.10", quantity: "500", side: "BUY", buyer: "A", seller: "B" },
  { time: "10:50:00", price: "32.10", quantity: "4,000", side: "BUY", buyer: "C", seller: "B" },
  { time: "10:50:00", price: "32.20", quantity: "200", side: "BUY", buyer: "C", seller: "B" },
  { time: "11:10:00", price: "32.00", quantity: "100", side: "SELL", buyer: "C", seller: "B" },
];

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

const positions = [
  { client: "A", position: "+2,300", tone: "positive" },
  { client: "B", position: "−5,800", tone: "negative" },
  { client: "C", position: "+4,300", tone: "positive" },
  { client: "E", position: "−800", tone: "negative" },
];

const rejections = [
  { id: "D1", reason: "Currency mismatch", time: "09:10:00" },
  { id: "B2", reason: "Invalid lot size", time: "09:29:01" },
  { id: "C2", reason: "Position check failed", time: "09:30:01" },
];

const chartBars = [38, 52, 44, 71, 64, 88, 76, 69, 58, 63, 46, 41, 55, 48, 35, 28];

export default function Home() {
  const [view, setView] = useState<"overview" | "book" | "risk">("overview");
  const [replayState, setReplayState] = useState<"ready" | "running" | "complete">("ready");

  function runReplay() {
    setReplayState("running");
    window.setTimeout(() => setReplayState("complete"), 1200);
  }

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
          <div><strong>Market closed</strong><span>SGT · Sample session</span></div>
        </div>
      </header>

      <section className="hero-strip">
        <div>
          <span className="eyebrow">SIA · SGD</span>
          <div className="last-price">31.90 <span>−0.62%</span></div>
          <p>Last execution at 13:50:00</p>
        </div>
        <div className="metric"><span>Open</span><strong>32.10</strong><small>Opening auction</small></div>
        <div className="metric"><span>High / Low</span><strong>32.20 / 31.90</strong><small>0.94% range</small></div>
        <div className="metric"><span>Volume</span><strong>6,800</strong><small>7 executions</small></div>
        <div className="metric"><span>VWAP</span><strong>32.0721</strong><small>Exact decimal pricing</small></div>
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
            <div className="panel-heading"><div><span className="eyebrow">Audit trail</span><h2>Recent executions</h2></div><span className="count-pill">7 total</span></div>
            <TradeTable />
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
        <div className="focus-layout"><OrderBook expanded /><section className="panel"><div className="panel-heading"><div><span className="eyebrow">Matched orders</span><h2>Execution ledger</h2></div></div><TradeTable /></section></div>
      )}

      {view === "risk" && (
        <div className="risk-layout">
          <section className="panel risk-summary"><span className="eyebrow">Controls</span><h2>Risk checks</h2><div className="risk-score">3 <span>rejected orders</span></div><p>Currency, lot-size and position rules were evaluated before admission to the order book.</p></section>
          <section className="panel rejection-panel"><div className="panel-heading"><div><span className="eyebrow">Exchange report</span><h2>Rejected orders</h2></div></div>{rejections.map((item) => <div className="rejection-row" key={item.id}><span className="reject-icon">!</span><div><strong>{item.id}</strong><small>{item.time}</small></div><p>{item.reason}</p></div>)}</section>
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

function TradeTable() {
  return <div className="trade-table"><div className="trade-header"><span>Time</span><span>Price</span><span>Qty</span><span>Flow</span><span>Parties</span></div>{trades.map((trade, index) => <div className="trade-row" key={`${trade.time}-${index}`}><span>{trade.time}</span><strong>{trade.price}</strong><span>{trade.quantity}</span><span className={trade.side === "BUY" ? "buy-flow" : "sell-flow"}>{trade.side}</span><span>{trade.seller} → {trade.buyer}</span></div>)}</div>;
}
