const clients = [
  { clientId: "BUYER", currencies: ["SGD"], positionCheck: false, rating: 1 },
  { clientId: "SELLER", currencies: ["SGD"], positionCheck: false, rating: 1 },
];
const instruments = [{ instrumentId: "SIA", currency: "SGD", lotSize: 100 }];
const order = (orderId: string, side: string, price: number, time: string, quantity = 100) => ({
  time, orderId, instrumentId: "SIA", quantity, clientId: side === "BUY" ? "BUYER" : "SELLER",
  type: "LIMIT", price, side,
});
const json = (orders: ReturnType<typeof order>[], customClients: unknown[] = clients) =>
  JSON.stringify({ clients: customClients, instruments, orders }, null, 2);

export const scenarios = [
  {
    id: "crossing", label: "Crossing limits", phase: "Continuous",
    description: "A resting sell meets an incoming buy at the same price.",
    json: json([order("S1", "SELL", 32.1, "10:00:00"), order("B1", "BUY", 32.1, "10:00:01")]),
  },
  {
    id: "auction", label: "Auction discovery", phase: "Open + close",
    description: "Orders cross in the opening and closing auction windows.",
    json: json([
      order("OPEN-B", "BUY", 32.2, "09:10:00"), order("OPEN-S", "SELL", 32, "09:12:00"),
      order("CLOSE-B", "BUY", 32.1, "16:02:00"), order("CLOSE-S", "SELL", 31.9, "16:03:00"),
    ]),
  },
  {
    id: "risk", label: "Risk controls", phase: "Validation",
    description: "An oversell and invalid lot sit alongside a valid trade.",
    json: json([
      order("OVERSELL", "SELL", 32.1, "10:00:00", 200),
      order("BAD-LOT", "SELL", 32.1, "10:00:01", 150),
      order("B1", "BUY", 32.2, "10:01:00"), order("S1", "SELL", 32.1, "10:02:00"),
    ], [clients[0], { ...clients[1], positionCheck: true, positions: { SIA: 100 } }]),
  },
  {
    id: "no-fill", label: "No crossing price", phase: "Empty result",
    description: "A bid below the ask leaves both orders unmatched.",
    json: json([order("S1", "SELL", 32.2, "10:00:00"), order("B1", "BUY", 32, "10:00:01")]),
  },
] as const;
