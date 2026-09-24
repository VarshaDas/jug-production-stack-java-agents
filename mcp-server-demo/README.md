# Demo 3 — MCP Server (Spring AI)

A Spring AI **MCP (Model Context Protocol) server** that exposes tools,
resources, prompts and interactive **MCP Apps** UIs over streamable HTTP.

No AWS credentials or model keys are needed — this is an MCP *server*, so the LLM
lives on the client side.

---

## What it demonstrates

**Tools** (`@McpTool`) — a spread of MCP capabilities, not just plain functions:

| Tool | Shows off |
|---|---|
| `add` | the simplest possible tool |
| `multiply` | output schema generation + tool annotations (`readOnlyHint`, `idempotentHint`) |
| `sub` | logging back to the client via `McpSyncRequestContext.info()` |
| `divide` | **progress notifications** (0 → 100 with a deliberate delay) |
| `random` | **elicitation** — asks the *user* for a value mid-call |
| `loudJoke` | **sampling** — calls back into the client's own LLM |
| `roll-the-dice` | opens an **MCP App** UI (dice roller) |
| `shopping-list` | opens an **MCP App** UI (shopping list) |

**Resources** (`@McpResource`) — static server info plus a templated
`config://{key}` resource, with `@McpComplete` autocompletion over known keys.

**Prompts** (`@McpPrompt`) — a `greeting` prompt with argument completion.

**MCP Apps** — two interactive HTML UIs served as resources
(`mimeType: text/html;profile=mcp-app`) and opened by their tools:
- `roll-the-dice` → `ui://dice/dice-app.html`
- `shopping-list` → `ui://shopping/shopping-list.html`

UIs are [JTE](https://jte.gg/) templates in `src/main/jte/`, precompiled at build
time. The dice app calls back into the server's `add` tool; the shopping list
pushes its state to the conversation via `sendMessage`.

---

## Stack

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring AI | 2.0.0-M4 (milestone — pulls from the Spring milestone repo) |
| Transport | `spring-ai-starter-mcp-server-webflux`, streamable HTTP |
| Templating | JTE 3.2.3 (precompiled) |
| Port | `8080` (override with `PORT`) |

---

## Run

```bash
./mvnw spring-boot:run
```

Server starts on **8080** (streamable HTTP endpoint at `/mcp`). On a different
port:

```bash
PORT=8090 ./mvnw spring-boot:run
```

For **stdio** transport instead of HTTP, uncomment the `# stdio` block at the top
of `src/main/resources/application.properties` and comment out the streamable
block.

---

## Testing

Two ways to verify the server: the **automated suite** (fast, no browser), and a
**manual walkthrough** in MCP Inspector (needed to see the interactive apps).

### 1. Automated tests

```bash
./mvnw test
```

Boots the server and drives it through a real MCP client — covering tool
registration and invocation, output schemas, resources, templated resources,
completions, prompts, and MCP App resource registration.

### 2. Manual walkthrough with MCP Inspector

**Step 1 — start the server** (in one terminal):

```bash
./mvnw spring-boot:run
```

Wait for `Started Application ... on port 8080`.

**Step 2 — start MCP Inspector** (in a second terminal):

```bash
npx @modelcontextprotocol/inspector
```

On startup it prints **two** URLs — you need both:

```
http://127.0.0.1:6274?MCP_INSPECTOR_API_TOKEN=...   ← main UI
Sandbox (MCP Apps): http://127.0.0.1:6275/sandbox   ← where the apps run
```

**Step 3 — connect the main UI** to the server:

- Open the `6274` URL (it carries the auth token).
- Transport: **Streamable HTTP**
- URL: **`http://localhost:8080/mcp`**
- Click **Connect**.

**Step 4 — exercise the plain capabilities** in the main UI:

| Tab | Try | Expect |
|---|---|---|
| Tools | `add` → x=2, y=3 | `5` |
| Tools | `multiply` → x=4, y=6 | structured result `{ "result": 24 }` |
| Tools | `divide` → x=10, y=2 | a **progress bar** 0→100, then `5` (≈4s) |
| Tools | `random` | an **elicitation** prompt asks *you* for a number |
| Tools | `loudJoke` | `"NO JOKE"` (Inspector has no LLM for sampling) |
| Resources | `info://server` | server + Java version string |
| Resources | `config://database.url` | the key, reversed |
| Prompts | `greeting`, type `V` in `name` | completion suggests `Varsha` |

**Step 5 — run the interactive MCP Apps** (this is the important part).

The apps **do not work from the Tools tab** — invoking `roll-the-dice` or
`shopping-list` there just returns the text "Opening ... app." The UI runs in the
Inspector **Sandbox**:

1. Keep the main tab **connected** (that's the live session the app talks back to).
2. Open the Sandbox in a new browser tab: **`http://127.0.0.1:6275/sandbox`**
3. Launch the app there and interact:
   - **Dice** → click **Roll Dice**. The dice animate, then the app calls the
     server's `add` tool and pushes `Die 1 = … Sum = …` into the conversation.
   - **Shopping list** → add / check off / remove items. Each change is pushed to
     the conversation via `sendMessage`.

**Step 6 — confirm it's actually working.** Open browser DevTools (F12) → Console.
A healthy run has **no CSP errors** and no `Failed to fetch dynamically imported
module`. If you see either, you're likely viewing the app outside the sandbox host.

> **Why the sandbox matters:** the app HTML runs under a Content Security Policy
> (`script-src 'unsafe-inline'`). The MCP Apps JS bundle is therefore inlined
> directly into a single module `<script>` and its `App` export is exposed as a
> top-level `const` (done server-side in `McpApps.inlineBundle`). Loading the
> bundle any other way — e.g. `import()`-ing a `blob:` URL — is blocked by that
> CSP, which is what makes the app buttons silently dead.

---

## Notes

- **`hello` tool is intentionally not registered.** It returns `Mono<String>`,
  but the server runs in `sync` mode (`spring.ai.mcp.server.type=sync`), which
  can't handle reactive return types — you'll see a `Skipping method ... hello`
  warning at startup. Switch to `async` mode if you want it registered.
- Sampling (`loudJoke`) and elicitation (`random`) only work with clients that
  advertise those capabilities. Without sampling support, `loudJoke` returns
  `"NO JOKE"`.
