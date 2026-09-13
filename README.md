# jug-production-stack-java-agents

> **Design Patterns for AI Agents — A Java Developer's Guide** (Bangalore JUG)

Runnable Spring AI demos on **Amazon Bedrock** that accompany the talk. Each demo showcases one production pattern for building and shipping Java AI agents.

## Demos in this repo

| Demo | Folder | Pattern | Stack |
|---|---|---|---|
| **1. Dynamic Tool Discovery** | `/` (root) | Tool Search Tool — discover tools on demand instead of sending all upfront | Spring Boot 4.0 / Spring AI 2.0.1 (Maven) |
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

## Three Endpoints

| Endpoint | Behaviour | Tools in scope |
|---|---|---|
| `POST /chat/no-tools` | No tools at all — the token floor | 0 |
| `POST /chat/all-tools` | All 28 tool definitions sent every round | 28 |
| `POST /chat/tst` | Model discovers tools on demand via Lucene search | 3–4 |

---

## The Scenario

**28 tools registered:**
- Relevant to this scenario: `weather`, `getAirQualityIndex`, `getUvIndex`, `currentTime`
- 20+ dummies: `checkFlightStatus`, `bookHotel`, `sendSlackMessage`, `getStockPrice`, ... (noise)

**Prompt** — note it names *no* tools; the model must work out what it needs:
```
I have asthma and I want to go for a run outside in Bengaluru
this afternoon. Is that a good idea?
```

"asthma" implies air quality, "run outside this afternoon" implies weather.
The tool data is Bengaluru-realistic (AQI 156 *Unhealthy*, UV index 9 *very
high*), so the model has to combine signals to give a real answer.

**Progressive discovery (visible in logs):**
```
Round 1: Tools in scope (1)  → [toolSearchTool]        searches for air quality
Round 2: Tools in scope (3)  → + getAirQualityIndex    calls it
Round 3: Tools in scope (4)  → + weather               calls it
Final:   answer generated using only discovered tools
```

The baseline stays pinned at `Tools in scope (28)` on every round.

**Representative result** (same prompt, same answer quality):

| | `/chat/all-tools` | `/chat/tst` |
|---|---|---|
| tools in scope | **28** | **4** |
| totalTokens | ~6,800 | ~5,100 |

> `toolsInScope` (28 vs 4) is the **stable** metric — it's deterministic and it
> is the point of the pattern. The token delta varies run to run (roughly
> 25–50%) because the model chooses how many search rounds to make, and
> `totalTokens` is summed across rounds. The saving grows sharply once you pass
> 50+ tools, where the upfront tool payload dwarfs the search overhead.

---

## Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.0 |
| Spring AI | 2.0.1 |
| Tool Search | `org.springframework.ai:spring-ai-starter-tool-search-advisor` (core) |
| Tool index | `lucene` (keyword — no embedding model required) |
| Amazon Bedrock Converse | region `us-east-1` |
| Model | `us.anthropic.claude-sonnet-4-20250514-v1:0` |
| Port | 8085 |

> **Version note:** the Tool Search Tool is now **core** Spring AI (2.0+). It is
> enabled entirely by properties — no manual advisor wiring:
> ```properties
> spring.ai.chat.client.tool-search-advisor.enabled=true
> spring.ai.chat.client.tool-search-advisor.tool-index-type=lucene
> spring.ai.chat.client.tool-search-advisor.max-results=5
> spring.ai.chat.client.tool-search-advisor.reference-tool-name-accumulation=true
> ```
> `tool-index-type` also supports `vector` (semantic) and `regex`. This demo uses
> `lucene` deliberately: keyword search needs no embedding model, so startup is
> fast and has no model-download dependency.

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

All three endpoints take the **same** prompt so the results are comparable.
`prompt` is **required** — there is no built-in default. A missing or blank
`prompt` returns HTTP 400.

```bash
PROMPT="I have asthma and I want to go for a run outside in Bengaluru this afternoon. Is that a good idea?"

# 1. No tools — token floor
curl -s -X POST http://localhost:8085/chat/no-tools \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg p "$PROMPT" '{prompt:$p}')" | jq

# 2. All 28 tools upfront — the baseline
curl -s -X POST http://localhost:8085/chat/all-tools \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg p "$PROMPT" '{prompt:$p}')" | jq

# 3. Tool Search Tool — dynamic discovery
curl -s -X POST http://localhost:8085/chat/tst \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg p "$PROMPT" '{prompt:$p}')" | jq
```

Or just run all three at once:
```bash
./demo-curls.sh            # all three
./demo-curls.sh tst        # one: no-tools | all-tools | tst
```

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
  PROMPT  : I have asthma and I want to go for a run outside in Bengaluru...
════════════════════════════════════════════════
>>> Tools in scope (1): [toolSearchTool]
>>> Tools in scope (3): [getAirQualityIndex, currentTime, toolSearchTool]
>>> Tools in scope (4): [getAirQualityIndex, currentTime, toolSearchTool, weather]
  RESULT  : totalTokens=5100 promptTokens=4357 completionTokens=743
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
│   └── ToolSearchConfig.java     # 3 ChatClients (no-tools / all-tools / TST), ToolLoggingAdvisor
├── advisor/
│   └── TokenCounterAdvisor.java  # CallAdvisor tracking tokens + tools per request
└── controller/
    └── DemoController.java       # POST /chat/no-tools, /chat/all-tools, /chat/tst
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
