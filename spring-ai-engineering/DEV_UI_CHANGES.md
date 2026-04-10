# chat.html — Changes

## Latest fixes

| # | Problem | Fix |
|---|---|---|
| 1 | **Scroll not working** | Added `min-height: 0` to `#message-log` — without it flex items refuse to shrink below content height so `overflow-y: auto` never activates. Also added `overflow: hidden` to the card so the height bound propagates correctly. |
| 2 | **User message not shown** | Restored `appendBubble('user', content)` before the API call. User messages appear right-aligned in indigo; assistant messages appear left-aligned in grey. |

## All active behaviours

| # | Behaviour |
|---|---|
| 1 | User message (You) shown right-aligned immediately on send |
| 2 | Assistant reply shown left-aligned after API response |
| 3 | `\n` in AI text renders as real line breaks via `\n → <br>` |
| 4 | Correct DTO field `assistantMessage` extracted from response |
| 5 | Chat window scrolls to bottom after every bubble |
| 6 | Hide / Show toggle on ② Chat header |
| 7 | Clear button wipes log without ending the session |

## File changed

`src/main/resources/static/dev-ui/chat.html`
