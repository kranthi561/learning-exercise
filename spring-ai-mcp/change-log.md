# spring-ai-mcp — change log

## 2026-05-03 — HEIC Docker fix (Ubuntu 24.04 default repos)

### Problem
iPhone HEIC conversion failed inside Docker with `heif-convert` exit=1:
```
Metadata not correctly assigned to image
```
This is a known libheif 1.12 bug. Ubuntu 22.04's default repos ship libheif 1.12.
Intermediate attempts:
- `python3 + pillow-heif` — `Cannot run program "python3"` (python3 not in base image)
- `strukturag/libheif` PPA — connection timeout; PPA has no `noble` release channel

### Root cause discovered
`eclipse-temurin:21-jre` resolves to **Ubuntu 24.04 (noble)**, not 22.04.
Ubuntu 24.04 ships **libheif 1.17.6** in its default apt repos — no PPA needed.
The PPA approach was wrong because noble is already beyond the PPA's coverage.

### Fix — remove PPA, use Ubuntu 24.04 default repos
Simplified the Dockerfile `RUN` layer to a plain `apt-get install`:

```dockerfile
# eclipse-temurin:21-jre resolves to Ubuntu 24.04 (noble) which ships
# libheif 1.17.6 in its default repos — no PPA needed.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates ffmpeg libheif-examples \
    && rm -rf /var/lib/apt/lists/*
```

`ImageConversionService` continues to use `heif-convert` for HEIC files. No Java
code changes. Extra debug log lines in `runProcess` downgraded from `INFO` to `DEBUG`.

### Rebuild required
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up
```

---

## 2026-05-03 — Watermark · Brand tags · Reels · iPhone HEIC support

### Watermark (`WatermarkService`)

Every image is watermarked with `@naturesrawclicks` at the top-right corner before
being sent to Instagram. The watermark is applied to a temp `_wm_` copy; the
original file is preserved and moved to `destination-folder` unchanged after publish.
The watcher skips `_wm_` and `_conv_` prefixed files so temp files are never re-queued.

### Caption always includes brand tags (`CaptionService`, `InstagramUploadService`)

- AI prompt updated to instruct the model to always include `#naturesrawclicks` as
  the first hashtag and generate 5–8 nature/wildlife hashtags.
- Fallback caption (used when `OPENAI_API_KEY` is absent or AI fails) now produces
  `"<filename> #naturesrawclicks #nature #naturephotography #wildlife #photography"`
  instead of the raw filename.
- `withBrandTag()` in `InstagramUploadService` appends `#naturesrawclicks` to every
  caption — including manually provided override captions — if not already present.

### Public-URL validation

`InstagramUploadService.uploadAndMove()` now fails fast with a clear error if
`PUBLIC_BASE_URL` is `localhost` or `127.0.0.1`, rather than letting Instagram
return a cryptic 9004 error.

### Instagram Reels with AI-narrated audio (`AudioService`, `VideoService`, `InstagramVideoController`)

Set `REEL_ENABLED=true` to post as Reels instead of photos.

Pipeline:
1. `AudioService` — OpenAI TTS (`tts-1` model) narrates the caption text (hashtags stripped) and writes a temp MP3.
2. `VideoService` — FFmpeg combines the watermarked image + MP3 into a 1080×1080 MP4 (`libx264` + AAC 128k).
3. `InstagramVideoController` — serves temp video files at `/instagram/videos/{filename}` so the Graph API can download them.
4. `InstagramService.postReel()` — creates the Reel container, polls `status_code` every 5 s until `FINISHED` (max 2 min), then publishes.
5. Temp audio and video files are deleted after publish.

Falls back to a regular photo post if TTS or FFmpeg fails.

New env vars:

| Variable | Default | Notes |
|---|---|---|
| `REEL_ENABLED` | `false` | Set `true` to post as Reels |
| `REEL_VOICE` | `nova` | OpenAI TTS voice (alloy/echo/fable/onyx/nova/shimmer) |
| `REEL_TEMP_FOLDER` | `reel-temp` | Temp folder for MP3/MP4 files |

### iPhone HEIC support (`ImageConversionService`)

iPhone images (`.heic`, `.heif`, and `.jpeg` files that contain HEIC bytes) are
now automatically converted to JPEG before caption generation and watermarking.

Detection uses **magic bytes** (bytes 4-7 `ftyp`, bytes 8-11 brand `heic`/`hei*/hev*/mif1`)
rather than file extension alone, so misnamed files (e.g. `IMG.jpeg` with HEIC content)
are correctly identified.

Conversion uses `heif-convert` from `libheif-examples` (purpose-built for HEIC;
the standard apt FFmpeg build does not include the HEIC codec). Other unreadable
formats fall back to FFmpeg.

- Watcher and `listInstagramImages` MCP tool now accept `.heic` / `.heif` files.
- Converted temp files (`_conv_` prefix) are deleted after publish.
- `libheif-examples` and `ffmpeg` added to the Docker runtime image.
- `REEL_TEMP_FOLDER=/app/reel-temp` added to Docker `ENV` defaults.

### Rebuild required

```bash
docker-compose down
docker-compose build --no-cache
docker-compose up
```

---

## 2026-05-02 — OAuth flow + Docker

### OAuth (Instagram API with Instagram Login)

The app can now exchange an authorization code for a 60-day long-lived token
without requiring the user to paste one manually.

New config (under `app.instagram.oauth.*`):

| property        | env var                  | notes                                                  |
| --------------- | ------------------------ | ------------------------------------------------------ |
| `app-name`      | `INSTAGRAM_APP_NAME`     | Display name only (shown on /oauth/status).            |
| `app-id`        | `INSTAGRAM_APP_ID`       | From Meta App dashboard.                               |
| `app-secret`    | `INSTAGRAM_APP_SECRET`   | From Meta App dashboard.                               |
| `redirect-uri`  | `INSTAGRAM_REDIRECT_URI` | Defaults to `${PUBLIC_BASE_URL}/instagram/oauth/callback`. Must be registered in Meta. |
| `scope`         | `INSTAGRAM_OAUTH_SCOPE`  | Defaults to `instagram_business_basic,instagram_business_content_publish`. |

New endpoints:

- `GET  /instagram/oauth/login`    — 302 redirect to `instagram.com/oauth/authorize`.
- `GET  /instagram/oauth/callback` — receives `?code=`, exchanges for short-lived
  token via `api.instagram.com/oauth/access_token`, then exchanges for the 60-day
  long-lived token via `graph.instagram.com/access_token`. Stored in memory.
- `GET  /instagram/oauth/status`   — JSON: whether a token is loaded + expiry.
- `POST /instagram/oauth/logout`   — clears the in-memory token.

New beans:

- `oauth.InstagramTokenStore` — in-memory `AtomicReference<TokenSnapshot>`.
  Token + user ID + expiry. Lost on restart (per requirement).
- `oauth.InstagramCredentials` — resolves credentials at call time, preferring
  the OAuth token store and falling back to env-var config. This is what the
  Graph API service and upload pipeline now read instead of going to
  `InstagramConfig` directly, so the OAuth callback effectively "swaps in"
  fresh credentials with no restart.
- `oauth.InstagramOAuthService` — pure HTTP client around the two token
  endpoints, plus `buildAuthorizeUrl`.
- `oauth.InstagramOAuthController` — see endpoints above.

`InstagramService` and `InstagramUploadService` now depend on
`InstagramCredentials` instead of `InstagramConfig` for the token/user-id pair.

### Docker

Added a multi-stage `Dockerfile`:

- Stage 1 — `maven:3.9-eclipse-temurin-21` builds the fat jar with a
  cached dependency layer (pom.xml first, sources second).
- Stage 2 — `eclipse-temurin:21-jre`. Drops to a non-root user, installs
  curl for healthcheck, exposes 8081.

`docker-compose.yml`:

- One service `spring-ai-mcp` reading `.env` for secrets.
- Bind mounts `./source-images` and `./uploaded-images` so dropping a file
  on the host triggers an upload from inside the container.
- Default-overridable `SERVER_PORT` (8081).

`.env.example` — copy to `.env`, fill in `INSTAGRAM_APP_ID` /
`INSTAGRAM_APP_SECRET` / `OPENAI_API_KEY` / `PUBLIC_BASE_URL`.

`.dockerignore` — keeps `target/`, `.git/`, IDE folders, and secrets out of
the build context.

### How to run

```bash
cp .env.example .env       # then edit .env with real values
docker compose up --build
# In another terminal, expose the host so Instagram can fetch images:
ngrok http 8081
# Update PUBLIC_BASE_URL in .env to the ngrok URL and restart compose.
# Then visit:
open http://localhost:8081/instagram/oauth/login
# After the callback, drop a .jpg into ./source-images and watch the logs.
```

## 2026-05-02 — folder watcher + AI captions + auto-move

(See previous entries.)

- Added `spring-ai-starter-model-openai` dependency for vision/caption generation.
- Split `app.instagram.images-folder` into `source-folder` and
  `destination-folder`; watcher polls `source-folder` and moves on success.
- Added `app.instagram.watcher.*` config (`enabled`, `poll-interval-ms`,
  `initial-delay-ms`, `min-file-age-ms`).
- Added `caption.CaptionService`, `upload.InstagramUploadService`,
  `watcher.InstagramFolderWatcher`.
- `@EnableScheduling` activated.

## 2026-05-02 — initial scaffold

```
spring-ai-mcp/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .env.example
└── src/main/
    ├── resources/
    │   └── application.yml
    └── java/com/instagram/mcp/
        ├── SpringAiMcpApplication.java
        ├── config/
        │   └── InstagramProperties.java
        ├── service/
        │   └── InstagramService.java
        ├── caption/
        │   └── CaptionService.java
        ├── upload/
        │   └── InstagramUploadService.java
        ├── watcher/
        │   └── InstagramFolderWatcher.java
        ├── oauth/
        │   ├── InstagramTokenStore.java
        │   ├── InstagramCredentials.java
        │   ├── InstagramOAuthService.java
        │   └── InstagramOAuthController.java
        ├── web/controller/
        │   └── InstagramImageController.java
        └── mcp/
            ├── config/McpToolConfig.java
            ├── dto/
            │   ├── InstagramListImagesMcpResponse.java
            │   └── InstagramPostMcpResponse.java
            └── tool/
                └── InstagramMcpTools.java
```
