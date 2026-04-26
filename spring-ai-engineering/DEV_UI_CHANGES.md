# Dev UI — Change Log

---

## 2026-04-26 — Slash-Command Tool Picker Backed by API

### Files changed
- `src/main/java/com/aiengineering/web/controller/AgentController.java` *(new)*
- `src/main/java/com/aiengineering/web/dto/chat/ToolInfoResponse.java` *(new)*
- `src/main/resources/static/dev-ui/chat.html`

### Problem
The tool list in the slash-command picker was hardcoded in `chat.html`. Adding or renaming a `@Tool` method required a manual JS edit — two places to keep in sync.

### Solution
A new `GET /api/v1/agent/tools` endpoint reflects the actual `@Tool` annotations at startup using Spring AI's `MethodToolCallbackProvider`. The frontend fetches this list once on page load and populates the picker dynamically.

### Backend — `GET /api/v1/agent/tools`

**`AgentController`** uses `MethodToolCallbackProvider` to introspect `@Tool`-annotated methods at startup:

```java
ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
        .toolObjects(agentTools, webSearchTool)
        .build()
        .getToolCallbacks();
```

Each `ToolCallback.getToolDefinition()` provides `name()` and `description()` directly from the annotation. The list is built once and cached as an immutable field — the endpoint is O(1).

**`ToolInfoResponse`** DTO:
```json
{ "name": "searchWeb", "description": "Search the web for...", "template": "Search the web for: " }
```

`template` is a UI hint (the text pre-filled into the chat input on selection). It lives in `AgentController.TEMPLATES` — a `Map<String, String>` keyed by tool name — since it is presentation-specific and not part of Spring AI tool metadata.

**To add a new tool**: add the `@Tool` annotation as usual, then add one entry in `AgentController.TEMPLATES`. The picker picks it up on the next page load automatically.

### Frontend — `chat.html`

| # | Change | Detail |
|---|---|---|
| 1 | **`TOOLS` array removed** | Replaced hardcoded `const TOOLS = [...]` with `let TOOLS = []` |
| 2 | **`loadTools()`** | `async` function that `GET /api/v1/agent/tools` with the current JWT; populates `TOOLS` |
| 3 | **Called on init** | `loadTools()` invoked inside the `init()` IIFE when a valid token is present — runs on every page load |
| 4 | **`t.desc` → `t.description`** | `renderPicker` updated to use the API field name |

### Slash-command picker behaviour (unchanged)

| Interaction | Effect |
|---|---|
| Type `/` | Picker floats above the textarea with all tools |
| Continue typing (`/sea`) | List filters to matching tool names |
| `↑` / `↓` | Move highlight |
| `Enter` or `Tab` | Fill input with the tool's template string |
| `Escape` or click outside | Dismiss picker |

---

## 2026-04-26 — Slash-Command Tool Picker (chat.html)

### Files changed
- `src/main/resources/static/dev-ui/chat.html`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **CSS** | `.input-wrap` (position:relative wrapper), `#tool-picker` (absolute, floats above textarea), `.tp-item`, `.tp-name`, `.tp-desc`, `.tp-slash`, `.tp-active` |
| 2 | **HTML** | Textarea wrapped in `<div class="input-wrap">` with `<div id="tool-picker">` sibling; placeholder updated to "Type / for tools…" |
| 3 | **`TOOLS` array** | Tool definitions with `name`, `description`, `template` (pre-fill string) |
| 4 | **`renderPicker(query)`** | Filters `TOOLS` by name, renders items, stores filtered list on the picker element |
| 5 | **`hidePicker()`** | Hides picker, clears `_filtered` and `tpIndex` |
| 6 | **`selectTool(index)`** | Sets `input.value = tool.template`, closes picker, moves cursor to end |
| 7 | **`movePicker(delta)`** | Moves highlight up/down, scrolls active item into view |
| 8 | **`onMsgInput()`** | `oninput` handler — calls `renderPicker` or `hidePicker` based on `/` prefix |
| 9 | **`onMsgKeydown` updated** | Routes `↑↓`, `Enter`/`Tab`, `Escape` to picker when open; falls through to `sendMessage` when closed |
| 10 | **Outside-click closes picker** | `document.addEventListener('mousedown', ...)` dismisses picker when clicking outside input area |

---

## 2026-04-25 — Agentic AI: Tools, Web Search, Task Endpoint, Step Recorder

### Files changed
- `src/main/java/com/aiengineering/agent/AgentStepRecorder.java` *(new)*
- `src/main/java/com/aiengineering/agent/AgentTools.java`
- `src/main/java/com/aiengineering/agent/WebSearchTool.java` *(new)*
- `src/main/java/com/aiengineering/config/AiClientConfig.java`
- `src/main/java/com/aiengineering/service/AgentService.java`
- `src/main/java/com/aiengineering/web/dto/chat/AgentReplyResponse.java`
- `src/main/java/com/aiengineering/web/dto/chat/AgentTaskRequest.java` *(new)*
- `src/main/java/com/aiengineering/web/controller/ChatController.java`
- `src/main/resources/application-dev.yml`
- `src/main/resources/static/dev-ui/chat.html`

### AgentStepRecorder — ThreadLocal tool-call tracker

`AgentStepRecorder` is a static utility backed by `ThreadLocal`. It gives the blocking `chat()` path a per-request scratchpad without requiring any Spring scope:

| Method | Purpose |
|---|---|
| `start()` | Clear steps + memory at the start of each turn |
| `recordTool(name)` | Called by each `@Tool` method as it executes |
| `getAndClear()` | Harvested by `AgentService` after the LLM call returns |
| `saveMemory(k, v)` / `readMemory(k)` | Scratchpad used by `saveToMemory` / `readFromMemory` tools |

Not safe for the streaming path (Reactor thread-hops lose `ThreadLocal` state).

### AgentTools — new tools

| Tool | When to use |
|---|---|
| `searchKnowledgeBase(query)` | Wraps `VectorStore`; agent can search internal docs explicitly |
| `saveToMemory(key, value)` | Persist an intermediate result for later use in the same turn |
| `readFromMemory(key)` | Retrieve a value saved earlier in the same turn |

All existing tools (`findUserProfileByEmail`, `fetchExternalReference`, `getCurrentDateTime`, `calculate`) now call `AgentStepRecorder.recordTool()` so their names appear in the response.

### WebSearchTool — Tavily web search

Calls the Tavily Search API (`POST https://api.tavily.com/search`) and returns the top 5 results as formatted Markdown text. The model can then cite sources in its answer.

**Setup**: set `TAVILY_API_KEY` in the environment.  
**Graceful degradation**: if the key is blank the tool returns `"Web search is unavailable"` so the model falls back to its own knowledge instead of crashing.

```yaml
# application-dev.yml
app:
  web-search:
    tavily:
      api-key: ${TAVILY_API_KEY:}
```

Works in both the blocking `chat()` path and the streaming path — Spring AI executes tool calls transparently in both modes.

### AiClientConfig — ReAct system prompt

System prompt upgraded from one paragraph to a full ReAct-style agent instruction set:

```
1. PLAN  — decide which tools are needed
2. ACT   — call tools, in sequence if results depend on each other
3. OBSERVE — interpret results; save intermediate facts with saveToMemory
4. ANSWER — respond citing sources
```

Tool selection guidance added per tool so the model knows when to prefer `searchWeb` vs `searchKnowledgeBase`.

### AgentReplyResponse — toolsUsed field

```java
// Before
record AgentReplyResponse(String assistantMessage, int ragChunksUsed, long latencyMs)

// After
record AgentReplyResponse(String assistantMessage, int ragChunksUsed, long latencyMs, List<String> toolsUsed)
```

### AgentService — step recorder integration + runTask()

- `AgentStepRecorder.start()` called before `chatClient.prompt()...call()`
- `AgentStepRecorder.getAndClear()` passed as `toolsUsed` in the returned `AgentReplyResponse`
- New `runTask()` wraps the user's input in a ReAct preamble (`Task: ... Think step by step...`) then delegates to `chat()` — history, RAG, metrics, and step recording all apply unchanged

### New endpoint — `POST /api/v1/chat/sessions/{sessionId}/tasks`

```http
POST /api/v1/chat/sessions/1/tasks
Authorization: Bearer <token>
Content-Type: application/json

{ "task": "Find the latest Spring AI release notes and summarise the key changes" }
```

```json
{
  "assistantMessage": "...",
  "ragChunksUsed": 0,
  "latencyMs": 2340,
  "toolsUsed": ["searchWeb", "saveToMemory"]
}
```

### chat.html — Task button + tools-used meta

| # | Change | Detail |
|---|---|---|
| 1 | **🤖 Task button** | Green button between Stream and Image. Posts to `/tasks`; typing indicator says "Agent is planning…" |
| 2 | **`runTask()` function** | Mirrors `sendMessage()` but hits `/tasks` with `{ task }`. All four buttons disabled during flight. |
| 3 | **tools-used in meta** | `sendMessage()` and `runTask()` append `• tools: searchWeb, calculate` when `toolsUsed` is non-empty |
| 4 | **All buttons disabled together** | Send, Stream, Task, Image all disabled during any in-flight request |

---

## 2026-04-25 — LoggingInterceptor Execution Order (before RateLimitFilter)

### Files changed
- `src/main/java/com/aiengineering/web/interceptor/LoggingInterceptor.java`
- `src/main/java/com/aiengineering/security/RateLimitFilter.java`
- `src/main/java/com/aiengineering/config/WebMvcConfig.java`

### Why this required converting to a filter
The servlet pipeline layer is fixed — `HandlerInterceptor`s always run inside `DispatcherServlet`, after all servlet filters. There is no way to order a `HandlerInterceptor` before a `Filter` without moving it into the filter chain.

```
Before (broken order)                After (correct order)
──────────────────────               ──────────────────────
Filters:                             Filters:
  RateLimitFilter       ← ran first    @Order(1) LoggingInterceptor  ← MDC set here
  Spring Security                      @Order(2) RateLimitFilter     ← logs carry [requestId]
DispatcherServlet:                     Spring Security
  LoggingInterceptor    ← ran last   DispatcherServlet → Controller
```

### Changes

| # | File | Change |
|---|---|---|
| 1 | **`LoggingInterceptor`** | Converted from `HandlerInterceptor` to `OncePerRequestFilter`. `preHandle`/`afterCompletion` merged into a single `doFilterInternal` with `try/finally`. Added `@Order(1)`. |
| 2 | **`RateLimitFilter`** | Added `@Order(2)` — runs right after `LoggingInterceptor`, so its own log lines already carry `[requestId]` from MDC. |
| 3 | **`WebMvcConfig`** | Removed `LoggingInterceptor` injection and `addInterceptors` registration — registering a `Filter` as a `HandlerInterceptor` would cause a startup error. |

### New execution order
```
Request
  ↓ @Order(1)   LoggingInterceptor  — MDC.put(requestId), start timer
  ↓ @Order(2)   RateLimitFilter     — rate check (logs carry [requestId])
  ↓ order=-100  Spring Security     — JWT auth
  ↓             DispatcherServlet   → Controller
  ↑ finally     LoggingInterceptor  — log "POST /api/... → 200 | 45ms", MDC.remove
```

---

## 2026-04-25 — Fix: Stream SSE Newline Parsing (`chat.html`)

### Problem
Markdown headings, bullet points, and code fences were not rendering in stream responses. Root cause: newline characters emitted by the model were silently dropped during SSE parsing.

### Why newlines were lost
Per the SSE spec, a single `\n` character is serialised as **two empty `data:` lines**:
```
data:
data:

```
The previous loop did `accumulated += line.slice(5)` for each line independently:
- Line 1: `"data:".slice(5)` → `""` (empty string added)
- Line 2: `"data:".slice(5)` → `""` (empty string added)
- Net result: `""` — the newline was lost.

Without newlines, `marked.parse()` sees a single flat block of text — no headings (`#`), no bullet items (`-`), no code fences (` ``` `).

### Fix
Collect all `data:` lines per event and join them with `\n` (spec-compliant):
```js
// Before (broken)
for (const line of event.split('\n')) {
  if (!line.startsWith('data:')) continue;
  accumulated += line.slice(5);   // drops empty-data newlines
}

// After (fixed)
const dataLines = event.split('\n')
  .filter(l => l.startsWith('data:'))
  .map(l => l.slice(5));
if (dataLines.length > 0) {
  accumulated += dataLines.join('\n');  // preserves embedded \n
}
```

---

## 2026-04-25 — Stream Response Format Options (`chat.html`)

### Files changed
- `src/main/resources/static/dev-ui/chat.html`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **Live Markdown rendering during streaming** | Switched from `bubble.textContent = accumulated` to `bubble.innerHTML = marked.parse(accumulated)` inside the SSE read loop — code blocks, bold, lists, and tables now format progressively as tokens arrive instead of appearing only after the stream closes. |
| 2 | **Markdown / Raw format toggle** | After stream completes, two buttons are appended below the bubble: **Markdown** (default, active) renders via `marked.parse()`; **Raw** shows the unprocessed text in a `<pre>` with `white-space: pre-wrap` so whitespace and newlines are preserved exactly. |
| 3 | **CSS — `.format-toggle`, `.fmt-btn`, `.fmt-btn.fmt-active`** | Toggle bar is `flex` row, left-aligned. Active button uses indigo fill; hover state highlights border + text. |

### Behaviour
- The toggle is added only on stream responses (Send / Image responses are unaffected).
- Switching formats re-renders from the original `accumulated` string held in the closure — no server round-trip.
- Copy buttons are re-attached each time Markdown mode is activated.

---

## 2026-04-25 — X-Request-ID Tracing

### Files changed
- `src/main/java/com/aiengineering/web/interceptor/LoggingInterceptor.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/static/dev-ui/chat.html`

### How it works
```
chat.html                           LoggingInterceptor (preHandle)
─────────                           ──────────────────────────────
crypto.randomUUID()  ──X-Request-ID──▶  read header (or generate if absent)
                                        MDC.put("requestId", id)
                                        response.setHeader("X-Request-ID", id)
                                        ↓ every log line on this thread now carries [requestId]
                     ◀─X-Request-ID──  afterCompletion: MDC.remove("requestId")
```

### Changes

| # | Where | Change |
|---|---|---|
| 1 | **`LoggingInterceptor.preHandle`** | Reads `X-Request-ID` header; generates a UUID if absent. Stores in `MDC.put("requestId", id)`. Echoes ID back via `response.setHeader`. |
| 2 | **`LoggingInterceptor.afterCompletion`** | Calls `MDC.remove("requestId")` — mandatory cleanup so pooled servlet threads don't leak the previous request's ID. |
| 3 | **`application.yml` console + file patterns** | Added `[%X{requestId}]` between thread name and logger name — empty brackets `[]` when no request context (e.g. startup logs). |
| 4 | **`application-dev.yml` console pattern** | Same addition, wrapped in `%clr(...){yellow}` for colour-coded visibility in the dev terminal. |
| 5 | **`chat.html` — all four fetch calls** | `crypto.randomUUID()` generated before each fetch; sent as `'X-Request-ID': reqId` header. First 8 chars shown in message meta (e.g. `req: a3f2c1d8  •  RAG: 2  •  412ms`) so you can grep server logs directly. |

### Example log output (dev)
```
2026-04-25 14:32:01.123  INFO 12345 --- [nio-8080-exec-3] [a3f2c1d8-...-uuid] c.a.web.interceptor.LoggingInterceptor  : POST /api/v1/chat/sessions/1/messages -> 200 | 418ms
2026-04-25 14:32:01.100 DEBUG 12345 --- [nio-8080-exec-3] [a3f2c1d8-...-uuid] c.a.service.AgentService                : chat: userId=1, sessionId=1, contentLength=42
```

---

## 2026-04-25 — Streaming Chat UI (`chat.html`)

### Files changed
- `src/main/resources/static/dev-ui/chat.html`

### Changes

| # | Change | Detail |
|---|---|---|
| 1 | **⚡ Stream button** | Added below the Send button (teal, `btn-stream` style). All three buttons (Send, Stream, Image) are disabled together while any request is in flight. |
| 2 | **`streamMessage()` function** | POSTs `{ content }` to `POST /api/v1/chat/sessions/{id}/messages/stream` with `Accept: text/event-stream`. Reads the response using `fetch` + `ReadableStream` (`EventSource` is GET-only and cannot be used here). |
| 3 | **SSE parsing loop** | Buffers raw bytes, splits on `\n\n` event boundaries, strips `data:` prefix from each line, and appends each token to the live bubble. Partial events at chunk boundaries are held in a buffer until complete. |
| 4 | **Live bubble with blinking cursor** | `createStreamingBubble()` creates the assistant bubble before any token arrives. Tokens are appended as plain text with a `<span class="stream-cursor">` while streaming; full Markdown is rendered only once the stream closes. |
| 5 | **`addCopyButtons(bubble)` extracted** | Copy-button logic moved out of `appendBubble` into a shared helper called by both the blocking and streaming paths. |
| 6 | **CSS — stream button + cursor** | `.btn-stream` teal style; `.stream-cursor` blinking block via `@keyframes blink`. |

### How it works
```
User clicks ⚡ Stream
  → POST .../messages/stream  (Content-Type: application/json, Accept: text/event-stream)
  → Server sends Flux<String> as SSE:   data:Hello\n\n  data: world\n\n  …
  → fetch ReadableStream reads bytes, TextDecoder decodes, SSE buffer splits on \n\n
  → Each "data:" line appended to live bubble (plain text + cursor)
  → On stream close: full text rendered as Markdown, copy buttons attached
```

### Existing Send button unchanged
`sendMessage()` still hits `POST .../messages` (blocking, full JSON response). Both modes co-exist.

---

## 2026-04-25 — AI Agent Upgrade (Tools, Streaming, RAG Advisor)

### Files changed
- `src/main/java/com/aiengineering/agent/AgentTools.java`
- `src/main/java/com/aiengineering/advisor/VectorStoreRagAdvisor.java` *(new)*
- `src/main/java/com/aiengineering/service/AgentService.java`
- `src/main/java/com/aiengineering/web/controller/ChatController.java`

### New tools — `AgentTools.java`

| Tool | Description |
|---|---|
| `getCurrentDateTime()` | Returns current ISO-8601 datetime — model can answer time-relative questions |
| `calculate(expression)` | SpEL-based arithmetic evaluator; whitelisted to `[0-9+\-*/(). ]` to prevent injection |

### New file — `VectorStoreRagAdvisor.java`
`QuestionAnswerAdvisor` was removed in Spring AI 1.0.0 GA. This custom `BaseAdvisor` replaces it: intercepts each request in `before()`, queries the vector store, and appends retrieved documents to the system prompt before the model sees the request.

### New method — `AgentService.streamChat()`
- Uses `.stream().content()` → returns `Flux<String>` token by token
- `VectorStoreRagAdvisor` replaces the manual `vectorStore.similaritySearch(...)` block
- `doOnNext` accumulates tokens; `doOnComplete` persists the full assistant reply to DB
- Not `@Transactional` — Spring Data's own method-level transactions handle each DB save
- Existing blocking `chat()` method is unchanged

### New endpoint — `POST /api/v1/chat/sessions/{sessionId}/messages/stream`

```http
POST /api/v1/chat/sessions/1/messages/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

{ "content": "Explain RAG in one paragraph" }
```
Returns tokens as Server-Sent Events as they arrive. No WebFlux dependency needed — Spring MVC handles `Flux<String>` with `text/event-stream` natively via `reactor-core` (already transitive from Spring AI).

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
| 8 | Send / Stream / Image buttons all disabled together during in-flight requests |
| 9 | ⚡ Stream button posts to SSE endpoint; tokens stream live into a blinking-cursor bubble |
| 10 | On stream complete: plain-text bubble replaced with full Markdown render + copy buttons |
| 11 | Every fetch call sends `X-Request-ID: <uuid>` — first 8 chars shown in message meta |
