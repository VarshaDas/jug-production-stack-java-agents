# java-agent-patterns-demos

> **Design Patterns for AI Agents — A Java Developer's Guide** (Bangalore JUG)

Runnable Spring AI demos on **Amazon Bedrock** that accompany the talk. Each demo showcases one production pattern for building and shipping Java AI agents.

## Demos in this repo

| Demo | Folder | Pattern | Stack |
|---|---|---|---|
| **1. Dynamic Tool Discovery** | `/` (root) | Tool Search Tool — discover tools on demand instead of sending all upfront | Spring Boot 3.5 / Spring AI 1.1.2 (Maven) |
| **2. One-Click Deploy** | [`agentcore-deploy/`](agentcore-deploy/) | `@AgentCoreInvocation` — a plain Spring AI agent, deploy-ready for Amazon Bedrock AgentCore | Spring Boot 4.1 / Spring AI 2.0.0 (Gradle) |

The two demos are **independent projects** on different stacks (the AgentCore SDK requires Spring Boot 4.x / Spring AI 2.0.0), so they live side by side rather than as one build. Demo 2 has its own [README](agentcore-deploy/README.md) and [run sheet](agentcore-deploy/DEMO.md).

---

# Demo 1 — Dynamic Tool Discovery (Tool Search Tool)

Shows how much context — and money — an AI agent wastes when it receives all its tools upfront, and how the **Tool Search Tool (TST)** pattern fixes it by letting the model discover tools on demand.

Two endpoints, same prompt, same tools, measured side by side.

> **Result on this demo: ~40–60% fewer prompt tokens**, identical answer quality.

---

## The Pattern

Most agent setups send **every** tool definition to the LLM on **every** request. With 28 tools but only 3 relevant to a task, you pay to describe 25 useless tools on every call.

The Tool Search Tool pattern (pioneered by [Anthropic](https://www.anthropic.com/engineering/advanced-tool-use), implemented for Spring AI via [recursive advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)) flips this:

1. The model starts with a **single** `toolSearchTool`
2. When it needs a capability, it **searches** for it
3. Matching tool definitions are **expanded** into the next request
4. The model calls the discovered tool and finishes the task

Only the tools the model actually needs ever enter the context window.

---

## Two Endpoints

| Endpoint | Behaviour | Tokens |
|---|---|---|
| `POST /chat/all-tools` | All 28 tool definitions sent every round | High |
| `POST /chat/tst` | Model discovers tools on demand via Lucene search | Low |

---

## The Scenario

**28 tools registered:**
- 3 relevant: `weather`, `clothing`, `currentTime`
- 25 dummies: `checkFlightStatus`, `bookHotel`, `sendSlackMessage`, `getStockPrice`, ... (noise)

**Prompt:**
```
Help me plan what to wear today in Amsterdam.
Please suggest clothing shops that are open right now.
```

**Progressive discovery (visible in logs):**
```
Round 1: Tools in scope (1)  → [toolSearchTool]           searches "weather"
Round 2: Tools in scope (6)  → + weather (+ near matches) calls weather()
Round 3: Tools in scope (8)  → + clothing                 calls clothing()
Final:   answer generated using only discovered tools
```

The baseline stays pinned at `Tools in scope (28)` on every round.

---

## Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.0 |
| Spring AI | 1.1.2 |
| tool-search-tool | 1.0.1 (`org.springaicommunity`) |
| tool-searcher-lucene | 1.0.1 (`org.springaicommunity`) |
| Amazon Bedrock Converse | region `us-east-1` |
| Model | `us.anthropic.claude-sonnet-4-20250514-v1:0` |
| Port | 8085 |

> **Version note:** Demo 1 uses the community 1.0.x line for Spring Boot 3. In Spring AI 2.0.0 GA the Tool Search Tool is now core — `org.springframework.ai:spring-ai-starter-tool-search-advisor` — enabled with a single `spring.ai.chat.client.tool-search-advisor.enabled=true` property (no manual advisor wiring needed).

---

## Prerequisites

- Java 21+
- Maven 3.6+ (or the bundled `./mvnw`)
- AWS account with Bedrock access to Claude Sonnet 4 in `us-east-1`
- Credentials with `bedrock:InvokeModel` permission

---

## Running

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key

./mvnw spring-boot:run
```

App starts on **port 8085**.

---

## API

### `POST /chat/all-tools` — baseline
```bash
curl -s -X POST http://localhost:8085/chat/all-tools \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Help me plan what to wear today in Amsterdam. Please suggest clothing shops that are open right now."}' | jq
```

### `POST /chat/tst` — Tool Search Tool
```bash
curl -s -X POST http://localhost:8085/chat/tst \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Help me plan what to wear today in Amsterdam. Please suggest clothing shops that are open right now."}' | jq
```

Both accept an optional `{"prompt": "..."}` body. Omit it (or send `{}`) to use the default Amsterdam prompt.

**Response shape:**
```json
{
  "answer": "Based on the sunny 15°C weather...",
  "totalTokens": 7321,
  "promptTokens": 6638,
  "completionTokens": 683,
  "requests": 6,
  "toolsInScope": ["toolSearchTool", "weather", "currentTime", "clothing", "getOpeningHours"],
  "toolsCalled": ["..."]
}
```

> **On the numbers:** `totalTokens` is the **aggregate across all LLM rounds** (matching the Spring AI blog's methodology) — the true billed cost of the whole interaction, since each round resends conversation history. `requests` is the number of LLM round-trips. Compare TST vs. the all-tools baseline *within this demo* rather than against the blog's table (different models/accounts give different absolute numbers). `toolsInScope` is the distinct set of tools that ever entered context — far fewer than the 28 the baseline sends every round.

---

## What You'll See in the Logs

```
════════════════════════════════════════════════
  ENDPOINT: POST /chat/tst
  MODE    : Tool Search Tool — dynamic discovery
  PROMPT  : Help me plan what to wear today in Amsterdam...
════════════════════════════════════════════════
>>> Tools in scope (1): [toolSearchTool]
>>> Tools in scope (6): [convertTemperature, getStockPrice, ... toolSearchTool, weather]
>>> Tools in scope (8): [clothing, ... toolSearchTool, weather]
  RESULT  : totalTokens=2177 promptTokens=1877 completionTokens=300
════════════════════════════════════════════════
```

---

## Project Structure

```
src/main/java/com/aws/jug/agentpatterns/
├── JavaAgentPatternsDemoApplication.java
├── tools/
│   └── MyTools.java              # 3 relevant + 25 dummy @Tool methods
├── config/
│   └── ToolSearchConfig.java     # LuceneToolSearcher, 2 ChatClients, ToolLoggingAdvisor
├── advisor/
│   └── TokenCounterAdvisor.java  # CallAdvisor tracking tokens + tools per request
└── controller/
    └── DemoController.java       # POST /chat/all-tools, POST /chat/tst
```

---

## Key Design Notes

- **Don't set `internalToolExecutionEnabled(false)`.** `ToolSearchToolCallAdvisor` extends `ToolCallAdvisor`, which owns the recursive execution loop. That flag breaks the loop.
- **`maxResults(5)`** — Lucene returns the top 5 matches per search; plus `toolSearchTool` itself = 6 tools in scope after the first search. Enough headroom for the right tool to surface even when it isn't ranked first.
- **`ChatClient.CallResponseSpec` is single-use.** Call `.content()` once — don't also call `.chatResponse()` on the same spec, or it fires a second LLM call.

---

## About This Talk

Part of **"Design Patterns for AI Agents — A Java Developer's Guide"** (Bangalore JUG). This repo holds two runnable demos: **Dynamic Tool Discovery** (above) and **One-Click Deploy** with the Spring AI AgentCore SDK (see [`agentcore-deploy/`](agentcore-deploy/)). The talk also covers the Human-in-the-Loop (Ask Before Acting) pattern.

---

## References

- [Spring AI Tool Search Tool blog](https://spring.io/blog/2025/12/11/spring-ai-tool-search-tools-tzolov)
- [spring-ai-tool-search-tool (GitHub, v1.0.x)](https://github.com/spring-ai-community/spring-ai-tool-search-tool/tree/1.0.x)
- [Anthropic — Advanced Tool Use](https://www.anthropic.com/engineering/advanced-tool-use)
- [Spring AI Recursive Advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)

---

## License

Apache 2.0
