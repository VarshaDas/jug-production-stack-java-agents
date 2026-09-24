# jug-production-stack-java-agents

> **Design Patterns for AI Agents — A Java Developer's Guide** (Bangalore JUG)

Three small, runnable Spring AI projects that each demonstrate one production
pattern for building and shipping Java AI agents. Pick a folder, follow its
README, run it in a minute.

- **Wasting tokens on tools?** → Demo 1 discovers tools on demand instead of
  sending them all every request.
- **Ready to ship an agent?** → Demo 2 deploys a Spring AI agent to Amazon
  Bedrock AgentCore.
- **Want your agent to expose tools to *other* clients?** → Demo 3 is an MCP
  server with tools, resources, prompts and interactive UIs.

## Demos in this repo

| Demo | Folder | Pattern | Stack |
|---|---|---|---|
| **1. Dynamic Tool Discovery** | [`tool-search-demo/`](tool-search-demo/) | Tool Search Tool — discover tools on demand instead of sending them all upfront | Spring Boot 4.0 / Spring AI 2.0.1 (Maven) |
| **2. One-Click Deploy** | [`agentcore-deploy/`](agentcore-deploy/) | `@AgentCoreInvocation` — a plain Spring AI agent, deploy-ready for Amazon Bedrock AgentCore | Spring Boot 4.1 / Spring AI 2.0.0 (Gradle) |
| **3. MCP Server** | [`mcp-server-demo/`](mcp-server-demo/) | Expose tools, resources, prompts and MCP Apps UIs over the Model Context Protocol | Spring Boot 4.0.5 / Spring AI 2.0.0-M4 (Maven) |

Each demo is an **independent project** with its own build and README — they are
deliberately not one aggregated build, because they sit on different stacks
(AgentCore needs Spring Boot 4.x; the MCP Apps support in Demo 3 is only on the
Spring AI milestone line).

| Demo | Build | Run | Port |
|---|---|---|---|
| 1 | `cd tool-search-demo && ./mvnw clean compile` | `./mvnw spring-boot:run` | 8085 |
| 2 | `cd agentcore-deploy && ./gradlew build` | see [DEMO.md](agentcore-deploy/DEMO.md) | — |
| 3 | `cd mcp-server-demo && ./mvnw test` (20 tests) | `./mvnw spring-boot:run` | 8080 |

Demos 1 and 2 talk to **Amazon Bedrock** and need AWS credentials
(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) with `bedrock:InvokeModel` in
`us-east-1`. Demo 3 needs none — it's an MCP *server*, so the model lives on the
client side.

---

## License

Apache 2.0
