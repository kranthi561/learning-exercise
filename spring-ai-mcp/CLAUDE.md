# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4 / Spring AI 1.0 MCP server that exposes two MCP tools (`listInstagramImages`, `postImageToInstagram`) over HTTP/SSE. The server watches a local folder for images, generates captions via OpenAI's vision API, and publishes to Instagram using the Graph API v21.0 two-step container/publish flow.

## Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Run a single test class
mvn test -Dtest=SomeTestClass

# Docker
cp .env.example .env   # fill in values first
docker-compose up --build
```

## Environment Setup

Copy `.env.example` to `.env` and fill in values. Key variables:

| Variable | Purpose |
|---|---|
| `PUBLIC_BASE_URL` | Publicly reachable URL of this server — **must be externally accessible** (use ngrok for local dev). The IG Graph API fetches image bytes from this URL; `localhost` will be rejected with error code 9004. |
| `INSTAGRAM_ACCESS_TOKEN` + `INSTAGRAM_USER_ID` | Pre-issued long-lived token — skip the OAuth flow if set. |
| `INSTAGRAM_APP_ID` + `INSTAGRAM_APP_SECRET` | Required for the OAuth flow (`/instagram/oauth/login`). |
| `OPENAI_API_KEY` | Optional. Caption generation degrades to a filename-derived fallback when blank. |

## Architecture

### Upload pipeline (shared by both triggers)

Both the folder watcher and the MCP tool funnel through a single shared path:

```
InstagramFolderWatcher  ──┐
                           ├──▶  InstagramUploadService.uploadAndMove()
InstagramMcpTools       ──┘         │
                                    ├─ 1. CaptionService.generateCaption()   (OpenAI vision or fallback)
                                    ├─ 2. Build public image URL             ({PUBLIC_BASE_URL}/instagram/images/{filename})
                                    ├─ 3. InstagramService.postImage()       (Graph API: create container → publish)
                                    └─ 4. Move file → destination-folder     (prevents re-posting)
```

`InstagramImageController` (`GET /instagram/images/{filename}`) serves the file bytes so the Graph API can download the image during step 3.

### Credential resolution

`InstagramCredentials` merges two sources at call time (no restart needed after OAuth):
- **Priority 1**: token stored in `InstagramTokenStore` (in-memory, populated by the OAuth callback)
- **Priority 2**: `INSTAGRAM_ACCESS_TOKEN` / `INSTAGRAM_USER_ID` env vars

### OAuth flow

`GET /instagram/oauth/login` → Instagram → `GET /instagram/oauth/callback` → exchanges short-lived code for a 60-day long-lived token via `InstagramOAuthService` → stored in `InstagramTokenStore`.

### MCP tool registration

`McpToolConfig` builds a `MethodToolCallbackProvider` with `InstagramMcpTools` as the tool object. To add a new MCP tool:
1. Create a `@Component` class in `mcp/tool/` with `@Tool`-annotated methods.
2. Inject it into `McpToolConfig.mcpToolCallbackProvider()` and add it to `.toolObjects(...)`.
3. Add a response DTO in `mcp/dto/` if needed.

### Bean cycle warning

`CaptionService` injects `ChatClient.Builder` with `@Lazy` and builds the `ChatClient` on first use. This breaks a circular dependency: Spring AI's `ToolCallingAutoConfiguration` wires every `ToolCallbackProvider` (including the MCP one) into the chat model, which would otherwise create a cycle through `InstagramMcpTools → InstagramUploadService → CaptionService → ChatClient → MCP provider`. Do not remove `@Lazy` without resolving this cycle.

## Key Constraints

- The `image_url` sent to the Graph API must be a publicly reachable HTTPS/HTTP URL. `localhost` URLs are rejected with `error_subcode 2207052`. Always set `PUBLIC_BASE_URL` to an ngrok URL or deployed domain during development.
- Supported image extensions: `.jpg`, `.jpeg`, `.png`, `.gif`.
- The watcher skips files younger than `WATCHER_MIN_FILE_AGE_MS` (default 3 s) to avoid reading partially-written files.
- `InstagramTokenStore` is in-memory only; tokens are lost on restart. Use `INSTAGRAM_ACCESS_TOKEN` env var for persistence.
