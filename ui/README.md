# LimitForge Dashboard

An interactive companion UI for the LimitForge matching engine. It presents a
sample trading session through market statistics, order-book depth, execution
history, rejected orders, and client positions.

## Prerequisites

- Node.js `>=22.13.0`

## Local development

```bash
npm ci
npm run dev
```

Open `http://localhost:3000`.

## Verification

```bash
npm run lint
npm test
```

The production build runs on vinext and is deployable through OpenAI Sites.
