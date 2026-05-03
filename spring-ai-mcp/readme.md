# spring-ai-mcp — Instagram Auto-Poster

Spring Boot MCP server that watches a local folder and automatically posts images to Instagram. Uses OpenAI Vision to generate captions, stamps a watermark, and optionally creates narrated Reels using OpenAI TTS + FFmpeg.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker + Docker Compose | any recent | Recommended way to run |
| Java 21 + Maven | 3.9+ | Only needed for local (non-Docker) dev |
| ngrok (or similar) | any | Makes localhost reachable by Instagram |
| FFmpeg | any | Only needed outside Docker; included in the Docker image |

---

## Quick start (Docker)

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and fill in required values:

```env
# Instagram credentials — one of:
INSTAGRAM_ACCESS_TOKEN=       # pre-issued long-lived token (skip OAuth)
INSTAGRAM_USER_ID=            # numeric user ID (required with above)

# OR use the OAuth flow (set all four below, leave above blank):
INSTAGRAM_APP_ID=
INSTAGRAM_APP_SECRET=
INSTAGRAM_APP_NAME=My App
INSTAGRAM_REDIRECT_URI=https://YOUR_NGROK_URL/instagram/oauth/callback

# Public URL Instagram can reach to download images/videos
# Must be externally accessible — localhost will be rejected by Instagram
PUBLIC_BASE_URL=https://YOUR_NGROK_URL

# OpenAI (for AI captions and optional Reel narration)
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini

# Optional: post as Reels with AI-narrated audio
REEL_ENABLED=false
REEL_VOICE=nova
```

### 2. Expose your local server

Instagram's API must be able to fetch images/videos from your server. Use ngrok:

```bash
ngrok http 8081
```

Copy the `https://xxxx.ngrok-free.app` URL and set it as `PUBLIC_BASE_URL` in `.env`.
If using OAuth, also set `INSTAGRAM_REDIRECT_URI=https://xxxx.ngrok-free.app/instagram/oauth/callback`.

> The ngrok URL changes on each restart (free plan). Update `.env` and restart the container each session.

### 3. Build and run

```bash
docker-compose up --build
```

For a clean rebuild (required after Dockerfile changes):

```bash
docker-compose down
docker-compose build --no-cache
docker-compose up
```

### 4. Authenticate with Instagram

**Option A — Pre-issued long-lived token** (simplest):
Set `INSTAGRAM_ACCESS_TOKEN` and `INSTAGRAM_USER_ID` in `.env` and restart.

**Option B — OAuth flow**:
1. Set `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET`, `INSTAGRAM_REDIRECT_URI` in `.env`.
2. Open `http://localhost:8081/instagram/oauth/login` in a browser.
3. Complete the Instagram login. The token is stored in memory and used immediately.
4. Check status: `GET http://localhost:8081/instagram/oauth/status`

### 5. Post an image

Drop any image into the `source-images/` folder:

```bash
cp /path/to/photo.jpg ./source-images/
```

The watcher polls every 30 seconds (configurable). On the next scan it will:
1. Convert the image if it's HEIC/iPhone format
2. Generate a caption with OpenAI Vision (or use a filename-based fallback)
3. Append `#naturesrawclicks` and default hashtags
4. Stamp the `@naturesrawclicks` watermark at the top-right
5. Post to Instagram (photo or Reel depending on `REEL_ENABLED`)
6. Move the original to `uploaded-images/`

Logs show progress:

```
uploadAndMove: IMG_1247.jpeg → mediaId=12345, moved to uploaded-images/IMG_1247.jpeg
```

---

## Running locally (without Docker)

```bash
# Install FFmpeg (required for HEIC conversion and Reels)
brew install ffmpeg

# Copy and fill in env vars
cp .env.example .env

# Run
mvn spring-boot:run
```

Environment variables from `.env` are NOT auto-loaded when running with Maven.
Either export them or use:

```bash
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

---

## MCP tools

The server exposes two tools over SSE at `/mcp/sse` (configurable):

| Tool | Description |
|---|---|
| `listInstagramImages` | Lists all images in `source-folder` waiting to be posted |
| `postImageToInstagram` | Posts a specific image by filename; caption is optional (auto-generated if blank) |

Connect any MCP client (e.g. Claude Desktop) to `http://localhost:8081/mcp/sse`.

---

## Key configuration reference

All under `app.instagram.*` in `application.yml` / `.env`:

| Env var | Default | Description |
|---|---|---|
| `INSTAGRAM_ACCESS_TOKEN` | — | Pre-issued long-lived token |
| `INSTAGRAM_USER_ID` | — | Numeric Instagram user ID |
| `PUBLIC_BASE_URL` | `http://localhost:8081` | Must be a public HTTPS URL in production |
| `SOURCE_FOLDER` | `source-images` | Folder the watcher reads from |
| `DESTINATION_FOLDER` | `uploaded-images` | Folder files move to after successful post |
| `REEL_TEMP_FOLDER` | `reel-temp` | Temp folder for audio/video during Reel creation |
| `OPENAI_API_KEY` | — | Required for AI captions; optional for Reels narration |
| `OPENAI_MODEL` | `gpt-4o-mini` | Vision model used for caption generation |
| `REEL_ENABLED` | `false` | Set `true` to post as Reels with AI audio |
| `REEL_VOICE` | `nova` | TTS voice: alloy / echo / fable / onyx / nova / shimmer |
| `WATCHER_ENABLED` | `true` | Disable to use MCP tools only |
| `WATCHER_POLL_MS` | `30000` | Polling interval in milliseconds |

---

## Troubleshooting

**"Media could not be fetched" (error 9004)**
`PUBLIC_BASE_URL` is set to `localhost`. Instagram cannot reach your local server.
Run `ngrok http 8081` and update `PUBLIC_BASE_URL` in `.env`.

**"Image format not supported" / HEIC conversion failed**
The Dockerfile installs `libheif 1.17+` via the `ppa:strukturag/libheif` PPA.
Always rebuild with `--no-cache` after any Dockerfile change:
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up
```

**Captions are just filenames with no hashtags**
`OPENAI_API_KEY` is blank. Set it in `.env` and restart.
The fallback still appends `#naturesrawclicks` and default nature hashtags.

**OAuth token lost after restart**
The token is in-memory only. Either re-run the OAuth flow or set
`INSTAGRAM_ACCESS_TOKEN` + `INSTAGRAM_USER_ID` in `.env` for persistence.

**Reel processing times out**
Instagram takes up to 2 minutes to process short videos. Longer videos may need more time.
Check `REEL_ENABLED` is `true` and `OPENAI_API_KEY` is set.
