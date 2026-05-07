import os
import json
import re
from datetime import datetime, timedelta

import openai
import pandas as pd
import requests
import yfinance as yf
from dotenv import load_dotenv
from flask import Flask, jsonify, render_template, request

load_dotenv()

app = Flask(__name__)

_openai: openai.OpenAI | None = None

def _get_client() -> openai.OpenAI:
    global _openai
    if _openai is None:
        _openai = openai.OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    return _openai

SYSTEM_PROMPT = """You are a conservative AI stock trading assistant.

Your goal is to minimize risk and avoid unnecessary trades.

--------------------------------
INTERPRETATION RULES
--------------------------------

RSI:
- RSI < 30 → Strong Oversold (Bullish)
- RSI 30–40 → Weak Bullish
- RSI 40–60 → Neutral (no trade zone)
- RSI 60–70 → Weak Bearish
- RSI > 70 → Strong Overbought (Bearish)

MACD:
- MACD > Signal → Bullish momentum
- MACD < Signal → Bearish momentum

NEWS SENTIMENT:
- Positive news → supports BUY
- Negative news → supports SELL
- Mixed/unclear → Neutral

--------------------------------
DECISION LOGIC
--------------------------------

- If RSI and MACD strongly agree → consider BUY or SELL
- If indicators conflict → HOLD
- If RSI is between 40–60 → HOLD (no trade zone)
- If news contradicts indicators → HOLD
- If news is strongly negative → avoid BUY
- If uncertainty exists → HOLD

--------------------------------
RISK MANAGEMENT RULES
--------------------------------

- Prefer HOLD over risky trades
- Only return BUY or SELL if confidence is high
- Avoid overtrading
- Be conservative

--------------------------------
OUTPUT FORMAT (STRICT JSON ONLY)
--------------------------------

{
  "decision": "BUY | SELL | HOLD",
  "confidence": 0-100,
  "strength": "STRONG | MEDIUM | WEAK",
  "risk": "LOW | MEDIUM | HIGH",
  "news_sentiment": "Positive | Negative | Neutral",
  "reason": "max 20 words explaining decision"
}"""


def _calc_rsi(close: pd.Series, period: int = 14) -> float:
    delta = close.diff()
    gain = delta.clip(lower=0).ewm(com=period - 1, min_periods=period).mean()
    loss = (-delta.clip(upper=0)).ewm(com=period - 1, min_periods=period).mean()
    rs = gain / loss.replace(0, float("nan"))
    rsi = 100 - (100 / (1 + rs))
    return round(float(rsi.dropna().iloc[-1]), 2)


def _calc_macd(close: pd.Series) -> tuple[float, float]:
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd = ema12 - ema26
    signal = macd.ewm(span=9, adjust=False).mean()
    return round(float(macd.iloc[-1]), 4), round(float(signal.iloc[-1]), 4)


VALID_PERIODS   = {"1mo", "3mo", "6mo", "1y", "2y"}
VALID_INTERVALS = {"1d", "1wk"}


def fetch_indicators(symbol: str, period: str = "3mo", interval: str = "1d") -> dict:
    if period not in VALID_PERIODS:
        period = "3mo"
    if interval not in VALID_INTERVALS:
        interval = "1d"
    ticker = yf.Ticker(symbol)
    hist = ticker.history(period=period, interval=interval)
    if hist.empty:
        raise ValueError(f"No data found for symbol: {symbol}")

    close = hist["Close"]
    current_price = round(float(close.iloc[-1]), 2)
    rsi = _calc_rsi(close)
    macd_val, signal_val = _calc_macd(close)

    return {
        "price": current_price,
        "rsi": rsi,
        "macd": macd_val,
        "signal": signal_val,
    }


def fetch_news(symbol: str) -> str:
    api_key = os.getenv("NEWS_API_KEY")
    if not api_key:
        return f"No recent news available for {symbol}. Market conditions appear stable."

    try:
        ticker = yf.Ticker(symbol)
        company = ticker.info.get("shortName", symbol)
        url = (
            "https://newsapi.org/v2/everything"
            f"?q={company}&sortBy=publishedAt&pageSize=5"
            f"&from={(datetime.now()-timedelta(days=3)).strftime('%Y-%m-%d')}"
            f"&apiKey={api_key}"
        )
        resp = requests.get(url, timeout=5)
        articles = resp.json().get("articles", [])
        if not articles:
            return f"No recent news found for {company}."
        headlines = [a["title"] for a in articles[:5] if a.get("title")]
        return "\n".join(f"- {h}" for h in headlines)
    except Exception:
        return f"News fetch failed for {symbol}. Treating sentiment as Neutral."


def get_trading_decision(symbol: str, price: float, rsi: float, macd: float, signal: float, news: str) -> dict:
    user_message = f"""Symbol: {symbol}
Price: {price}

Technical Indicators:
- RSI: {rsi}
- MACD: {macd}
- Signal: {signal}

Recent News:
{news}"""

    response = _get_client().chat.completions.create(
        model="gpt-4o",
        max_tokens=512,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
    )

    raw = response.choices[0].message.content.strip()
    match = re.search(r"\{[\s\S]*\}", raw)
    if not match:
        raise ValueError("Model returned non-JSON response")
    return json.loads(match.group())


def fetch_live_news(symbol: str, seen_ids: set | None = None) -> dict:
    """
    Returns recent news items for the symbol via yfinance.
    seen_ids: set of already-sent UUIDs so the caller can request only new items.
    """
    ticker = yf.Ticker(symbol)
    raw = ticker.news or []

    from datetime import timezone

    messages = []
    for item in raw[:20]:
        uid     = item.get("id") or item.get("uuid") or ""
        if seen_ids and uid in seen_ids:
            continue
        content = item.get("content") or {}
        title   = content.get("title") or item.get("title") or ""
        pub_ts  = content.get("pubDate") or item.get("providerPublishTime") or 0

        if isinstance(pub_ts, (int, float)):
            created_at = datetime.fromtimestamp(pub_ts, tz=timezone.utc).isoformat()
        else:
            created_at = str(pub_ts)

        url = (
            (content.get("canonicalUrl") or {}).get("url")
            or (content.get("clickThroughUrl") or {}).get("url")
            or item.get("link")
            or ""
        )
        provider = (content.get("provider") or {}).get("displayName") or item.get("publisher") or "News"
        messages.append({
            "id":         uid,
            "user":       provider,
            "body":       title,
            "url":        url,
            "sentiment":  None,
            "created_at": created_at,
        })

    # Sort descending by publish time
    messages.sort(key=lambda m: m["created_at"], reverse=True)

    # Keyword-based sentiment tagging
    bull_words = {"surge", "soar", "rally", "beat", "record", "profit", "gain", "rise", "up", "buy", "growth"}
    bear_words = {"fall", "drop", "miss", "loss", "layoff", "cut", "down", "decline", "sell", "risk", "lawsuit"}
    for m in messages:
        words = set(m["body"].lower().split())
        if words & bull_words and not words & bear_words:
            m["sentiment"] = "Bullish"
        elif words & bear_words and not words & bull_words:
            m["sentiment"] = "Bearish"

    all_ids = [item.get("id") or item.get("uuid") or "" for item in raw[:20]]
    return {
        "messages": messages,
        "since": all_ids[0] if all_ids else None,
    }


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/analyze", methods=["POST"])
def analyze():
    body = request.get_json(force=True)
    symbol = body.get("symbol", "").strip().upper()
    if not symbol:
        return jsonify({"error": "Symbol is required"}), 400

    period   = body.get("period",   "3mo")
    interval = body.get("interval", "1d")

    try:
        indicators = fetch_indicators(symbol, period, interval)
        news = fetch_news(symbol)
        decision = get_trading_decision(
            symbol=symbol,
            price=indicators["price"],
            rsi=indicators["rsi"],
            macd=indicators["macd"],
            signal=indicators["signal"],
            news=news,
        )
        return jsonify({
            "symbol":   symbol,
            "period":   period,
            "interval": interval,
            "price":    indicators["price"],
            "rsi":      indicators["rsi"],
            "macd":     indicators["macd"],
            "signal":   indicators["signal"],
            "news":     news,
            **decision,
        })
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": f"Analysis failed: {str(e)}"}), 500


@app.route("/live/<symbol>")
def live(symbol: str):
    symbol = symbol.upper()
    since_id = request.args.get("since", type=str)
    seen = {since_id} if since_id else None
    try:
        return jsonify(fetch_live_news(symbol, seen))
    except ValueError as e:
        return jsonify({"error": str(e)}), 404
    except Exception as e:
        return jsonify({"error": f"Live feed failed: {str(e)}"}), 500


@app.route("/chart/<symbol>")
def chart_data(symbol: str):
    symbol = symbol.upper()
    period = request.args.get("period", "6M")

    period_map = {
        "1D":  ("1d",   "5m"),
        "5D":  ("5d",   "15m"),
        "1M":  ("1mo",  "1d"),
        "6M":  ("6mo",  "1d"),
        "YTD": ("ytd",  "1d"),
        "1Y":  ("1y",   "1d"),
        "5Y":  ("5y",   "1wk"),
        "MAX": ("max",  "1mo"),
    }

    yf_period, yf_interval = period_map.get(period, ("6mo", "1d"))

    try:
        ticker = yf.Ticker(symbol)
        hist = ticker.history(period=yf_period, interval=yf_interval)
        if hist.empty:
            return jsonify({"error": f"No chart data for {symbol}"}), 400

        intraday = yf_interval in ("5m", "15m", "30m")
        labels  = [str(d) for d in hist.index] if intraday else [d.strftime("%Y-%m-%d") for d in hist.index]
        closes  = [round(float(c), 2) for c in hist["Close"]]
        opens   = [round(float(o), 2) for o in hist["Open"]]
        volumes = [int(v) for v in hist["Volume"]]

        return jsonify({"labels": labels, "closes": closes, "opens": opens, "volumes": volumes})
    except Exception as e:
        return jsonify({"error": f"Chart fetch failed: {str(e)}"}), 500


if __name__ == "__main__":
    app.run(debug=True, use_reloader=False, port=5050)
