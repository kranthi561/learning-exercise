# chat.html — Changes

## Latest: Markdown rendering

| # | Change | Detail |
|---|---|---|
| 1 | **marked.js added** | Loaded from CDN (`cdn.jsdelivr.net/npm/marked@9`). No build step needed. |
| 2 | **Assistant replies parsed as Markdown** | `marked.parse(text)` with `gfm: true, breaks: true` — supports bold, italic, headings, lists, code blocks, tables, blockquotes, horizontal rules. |
| 3 | **User messages stay plain text** | Escaped with `escapeHtml()` to prevent XSS; newlines still render as `<br>`. |
| 4 | **Markdown CSS** | Scoped to `.msg-assistant .msg-bubble` — styled code blocks (dark theme), tables, blockquotes, headings, lists, inline code. |

## All active behaviours

| # | Behaviour |
|---|---|
| 1 | User message (You) shown right-aligned immediately on send |
| 2 | Assistant reply rendered as Markdown, left-aligned |
| 3 | Chat window scrolls to bottom after every bubble |
| 4 | Correct DTO field `assistantMessage` used |
| 5 | Hide / Show toggle on ② Chat header |
| 6 | Clear button wipes log without ending the session |

## File changed

`src/main/resources/static/dev-ui/chat.html`
