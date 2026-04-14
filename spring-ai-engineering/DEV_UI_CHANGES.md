# Dev UI — Change Log

---

## 2026-04-13 — Image Generation UI (`chat.html`)

### Files changed
- `src/main/resources/static/dev-ui/chat.html`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **"🖼 Image" button** | Added below the Send button (purple, `btn-image` style). Both Send and Image are disabled together while a request is in flight. |
| 2 | **`generateImage()` function** | POSTs `{ prompt }` to `POST /api/v1/chat/sessions/{id}/images`. Shows the user's prompt as a chat bubble, then renders returned image(s). |
| 3 | **`appendImageBubble(urls, meta)`** | Renders each returned URL as an `<img>` inside an assistant-style bubble. Clicking the image or "Open full size ↗" link opens it in a new tab. |
| 4 | **`showTyping(show, msg)`** | Now accepts an optional message — Send shows "AI is thinking…", Image shows "Generating image…". |
| 5 | **CSS — image bubble** | `.msg-image` bubble, `img` sizing/border-radius (max 480 px, rounded), `.img-open-link` purple link styles. |

---

## 2026-04-13 — Image Generation Backend

### Files changed
- `src/main/java/com/aiengineering/web/dto/chat/ImageGenerateRequest.java` *(new)*
- `src/main/java/com/aiengineering/web/dto/chat/ImageGenerateResponse.java` *(new)*
- `src/main/java/com/aiengineering/service/AgentService.java`
- `src/main/java/com/aiengineering/web/controller/ChatController.java`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **New DTO `ImageGenerateRequest`** | Record with `@NotBlank String prompt`. |
| 2 | **New DTO `ImageGenerateResponse`** | Record with `List<String> imageUrls` and `long latencyMs`. |
| 3 | **`AgentService.generateImage()`** | Injects Spring AI's `ImageModel` (auto-configured via `spring-ai-starter-model-openai`). Calls DALL-E 3 at 1024×1024 standard quality. Validates session ownership before spending API credits. |
| 4 | **New endpoint `POST /api/v1/chat/sessions/{sessionId}/images`** | Added to `ChatController`. Accepts `{ "prompt": "..." }`, returns `{ "imageUrls": [...], "latencyMs": ... }`. JWT auth required. |

### No new Maven dependency needed
`spring-ai-starter-model-openai` already bundles `ImageModel` support. The existing `spring.ai.openai.api-key` property is reused.

### Example
```http
POST /api/v1/chat/sessions/1/images
Authorization: Bearer <token>
Content-Type: application/json

{ "prompt": "a futuristic city at sunset" }
```
```json
{ "imageUrls": ["https://...dall-e-url..."], "latencyMs": 4200 }
```

---

## 2026-04-12 — Markdown Rendering (`chat.html`)

### Files changed
- `src/main/resources/static/dev-ui/chat.html`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **marked.js added** | Loaded from CDN (`cdn.jsdelivr.net/npm/marked@9`). No build step needed. |
| 2 | **Assistant replies parsed as Markdown** | `marked.parse(text)` with `gfm: true, breaks: true` — supports bold, italic, headings, lists, code blocks, tables, blockquotes, horizontal rules. |
| 3 | **User messages stay plain text** | Escaped with `escapeHtml()` to prevent XSS; newlines still render as `<br>`. |
| 4 | **Markdown CSS** | Scoped to `.msg-assistant .msg-bubble` — styled code blocks (dark theme), tables, blockquotes, headings, lists, inline code. |

---

## All active behaviours

| # | Behaviour |
|---|---|
| 1 | User message shown right-aligned immediately on send |
| 2 | Assistant text reply rendered as Markdown, left-aligned |
| 3 | Assistant image reply rendered inline with "Open full size ↗" link |
| 4 | Chat window scrolls to bottom after every bubble |
| 5 | Correct DTO field `assistantMessage` used for text replies |
| 6 | Hide / Show toggle on ② Chat header |
| 7 | Clear button wipes log without ending the session |
| 8 | Send disabled during in-flight requests; Image button disabled in parallel |
