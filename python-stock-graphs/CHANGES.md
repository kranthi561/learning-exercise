# AI Stock Advisor — Initial Build

## What was created

| File | Purpose |
|------|---------|
| `app.py` | Flask backend — fetches stock data, calculates RSI/MACD, calls Claude API |
| `templates/index.html` | Dark-themed chat UI with BUY/SELL/HOLD signal badges |
| `requirements.txt` | Python dependencies (no pandas-ta — incompatible with Python 3.14) |
| `.env.example` | Template for API keys |

## How to run

```bash
# 1. Copy and fill in your API key
cp .env.example .env
# Edit .env → set ANTHROPIC_API_KEY=sk-ant-...

# 2. Start the server
python3 app.py

# 3. Open browser
open http://localhost:5050
```

## Architecture

- **RSI** — calculated with EWM (exponential weighted mean) in plain pandas
- **MACD / Signal** — EMA 12/26/9 in plain pandas (no pandas-ta; incompatible with Python 3.14)
- **News** — uses NewsAPI if `NEWS_API_KEY` is set in `.env`; falls back to a neutral stub
- **LLM** — `claude-sonnet-4-6` with the conservative trading system prompt; returns strict JSON

## Chat UI features

- Dark trading-terminal aesthetic
- Color-coded signal badge: green BUY / red SELL / amber HOLD
- Confidence bar, strength/risk/sentiment tags
- RSI and MACD values color-coded by signal direction
- Collapsible news headlines
- Quick-access chip buttons for AAPL / TSLA / MSFT / NVDA / AMZN
