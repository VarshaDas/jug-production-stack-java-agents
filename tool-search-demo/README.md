# Demo 1 — Dynamic Tool Discovery (Tool Search Tool)

Shows how much context — and money — an AI agent wastes when it receives all its tools upfront, and how the **Tool Search Tool (TST)** pattern fixes it by letting the model discover tools on demand.

Three endpoints, same prompt, same tools, measured side by side.

> **Result on this demo: 28 tools in scope drops to 4**, with roughly 25–50%
> fewer tokens and identical answer quality. The tool count is the deterministic
> win; the token delta varies per run (see *the numbers* below).

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

**Response shape** (from `/chat/tst`):
```json
{
  "answer": "With an AQI of 156 (Unhealthy) in Bengaluru, running outside isn't a good idea for someone with asthma...",
  "totalTokens": 5100,
  "promptTokens": 4357,
  "completionTokens": 743,
  "requests": 4,
  "toolsInScope": ["toolSearchTool", "getAirQualityIndex", "currentTime", "weather"],
  "toolsCalled": ["getAirQualityIndex", "weather"]
}
```

**Reading the numbers:**
- **`toolsInScope`** — the distinct tools that ever entered the model's context.
  This is the headline: **4 with TST vs 28 for the baseline**. It's deterministic,
  so it's the number to trust.
- **`totalTokens`** — summed across *all* LLM rounds (each round resends the
  conversation), so it reflects the true billed cost of the whole interaction.
  It varies run to run because the model decides how many search rounds to make —
  compare TST vs the baseline *within the same run*, not against a fixed figure.
- **`requests`** — number of LLM round-trips.
- **`toolsCalled`** — the tools the model actually invoked (a subset of scope).

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

- **Tool Search is enabled by properties, not code.** With
  `spring.ai.chat.client.tool-search-advisor.enabled=true`, the advisor is
  auto-registered — no manual wiring. See `application.properties`.
- **The baseline client opts out of it.** Because the advisor is auto-registered
  globally, `/chat/all-tools` and `/chat/no-tools` are built from `ChatModel`
  directly (not the shared `ChatClient.Builder`), so the search advisor doesn't
  leak in and the "all tools upfront" comparison stays honest.
- **`max-results=5`** — Lucene returns the top 5 matches per search; plus the
  search tool itself. Enough headroom for the right tool to surface even when it
  isn't ranked first.
- **The TST endpoint needs a conversation id.** The advisor scopes discovered
  tools per session via `ChatMemory.CONVERSATION_ID`; the controller sets a fresh
  UUID per request so each call is an independent, clean measurement.

---

## About This Talk

Part of **"Design Patterns for AI Agents — A Java Developer's Guide"** (Bangalore
JUG). See the [repo root](../README.md) for the other demos.

---

## References

- [Spring AI — Dynamic Tool Discovery guide](https://docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html)
- [Spring AI Tool Search Tool blog](https://spring.io/blog/2025/12/11/spring-ai-tool-search-tools-tzolov)
- [Anthropic — Advanced Tool Use](https://www.anthropic.com/engineering/advanced-tool-use)

---

## License

Apache 2.0
