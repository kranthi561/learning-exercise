# AI Stock Advisor

A conservative stock trading assistant that combines technical indicators (RSI, MACD) with live news sentiment to generate **BUY / SELL / HOLD** signals using GPT-4o.

---

## Features

- **Price chart** — interactive line chart with gradient fill, volume bars, and local peak/trough markers
- **Top-10 stock selector** — quick access to AAPL, MSFT, NVDA, AMZN, TSLA, GOOG, META, NFLX, AMD, JPM
- **Time range buttons** — 1D, 5D, 1M, 6M, YTD, 1Y, 5Y, MAX
- **AI analysis chat** — GPT-4o reads RSI, MACD, and news headlines and returns a structured decision
- **Configurable lookback** — choose analysis period (1M–2Y) and interval (daily/weekly) from the input bar
- **Live news feed** — Yahoo Finance headlines sorted newest-first with clickable links and Bullish/Bearish tags, auto-refreshes every 30 seconds

---

## Project Structure

```
python-stock-graphs/
├── app.py              # Flask backend — indicators, chart data, news, OpenAI call
├── templates/
│   └── index.html      # Single-page chat UI with Chart.js
├── requirements.txt    # Python dependencies
├── .env                # API keys (not committed)
└── .env.example        # Key name reference
```

---

## Prerequisites

- Python **3.10 – 3.13** (pandas-ta is incompatible with 3.14+; use pyenv if needed)
- An **OpenAI API key** — https://platform.openai.com/api-keys
- _(Optional)_ A **NewsAPI key** for richer headlines — https://newsapi.org

---

## Setup & Execution

### 1. Clone / navigate to the project

```bash
cd python-stock-graphs
```

### 2. Create and activate a virtual environment

```bash
python3 -m venv .venv
source .venv/bin/activate        # macOS / Linux
# .venv\Scripts\activate         # Windows
```

### 3. Install dependencies

```bash
pip install -r requirements.txt
```

### 4. Configure API keys

```bash
cp .env.example .env
```

Open `.env` and fill in your keys:

```env
OPENAI_API_KEY=sk-...
NEWS_API_KEY=...        # optional
```

### 5. Run the server

```bash
python3 app.py
```

Expected output:

```
 * Running on http://127.0.0.1:5050
```

### 6. Open in browser

```
http://localhost:5050
```

---

## How to Use

1. **Select a stock** from the Top-10 dropdown in the header, or type any ticker in the input box.
2. **Choose the analysis window** — Period (1M, 3M, 6M, 1Y, 2Y) and Interval (Daily, Weekly).
3. Click **Analyze →** (or press Enter) to get the AI decision.
4. The chart updates to the selected symbol; the **Live News Feed** panel on the right refreshes automatically.

---

## Stopping the Server

Press **Ctrl+C** in the terminal where the server is running.

If the port is stuck:

```bash
lsof -ti:5050 | xargs kill -9
```

---

## Environment Variables

| Variable         | Required | Description                          |
|------------------|----------|--------------------------------------|
| `OPENAI_API_KEY` | Yes      | OpenAI key used for GPT-4o analysis  |
| `NEWS_API_KEY`   | No       | NewsAPI key for richer news headlines |
