# Dev UI — Local Test Pages

> **For local development only.** Never deploy these pages to production.

## Pages

| Page | URL |
|---|---|
| Login / Register | http://localhost:8080/dev-ui/auth.html |
| Create Session + Chat | http://localhost:8080/dev-ui/chat.html |

---

## auth.html

- **Login tab** — pre-filled with `dev@example.com` / `DevPassword123!`
- **Register tab** — pre-filled with the same credentials + display name `Dev User`
- On successful login the JWT is stored in `localStorage` and a **Go to Chat →** link appears

## chat.html

### Left panel
| Section | What it does |
|---|---|
| **① Create Session** | Calls `POST /api/v1/chat/sessions`. Pre-filled title. Shows active session ID chip after creation. |
| **Override Session ID** | Type any existing session ID to skip session creation and chat directly. |
| **Last Raw Response** | Shows the full JSON of the last API response for debugging. |

### Right panel — Chat
- Calls `POST /api/v1/chat/sessions/{sessionId}/messages`
- Each exchange is appended to the conversation — **messages are never cleared on new sends**
- `Enter` sends the message; `Shift+Enter` inserts a new line
- Shows `RAG docs` count and response duration from the API response
- **Clear** button wipes the chat log without ending the session

---

## Files changed

| File | Change |
|---|---|
| `src/main/resources/static/dev-ui/auth.html` | New — login/register UI |
| `src/main/resources/static/dev-ui/chat.html` | New — session + chat UI |
| `src/main/java/com/aiengineering/security/SecurityConfig.java` | Added `.requestMatchers("/dev-ui/**").permitAll()` |

## Login:
<img width="608" height="550" alt="image" src="https://github.com/user-attachments/assets/87a5c62c-83b1-415c-a9e3-1efe31993cee" />

## Chat with AI Agent:
<img width="1508" height="748" alt="image" src="https://github.com/user-attachments/assets/9a4a9118-a2dc-4c53-8018-bcedd81ba889" />





