# Demo Run Sheet — Hello Spring AI AgentCore

The whole story in one line: **add `@AgentCoreInvocation` to a plain Spring AI
method and it's ready to ship to Amazon Bedrock AgentCore — no controllers, no
health-check code, no server plumbing.**

Follow the steps top to bottom. Every command is copy-paste ready.

---

## 0. One-time setup (do this the night before)

Open a terminal and go to the project folder:

```bash
cd agentcore-deploy
```

Sanity check you're in the right place (should print `gradlew`):

```bash
ls gradlew
```

That's it for setup.

---

## THE DEMO

### Step 1 — Show the code (the whole point)

Open `src/main/java/com/example/demo/DemoApplication.java` and show this method:

```java
@AgentCoreInvocation
public String myAgent(PromptRequest request) {
    String prompt = (request != null && request.prompt() != null && !request.prompt().isBlank())
            ? request.prompt()
            : "tell me a joke";
    return chatClient.prompt(prompt).call().content();
}

record PromptRequest(String prompt) {}
```

**Say:** "This one annotation is the deploy contract. That's the entire agent.
No controller, no `/ping` code, no JSON plumbing — the Spring AI AgentCore
starter auto-exposes `/invocations` and `/ping` for me."

---

### Step 2 — Start it locally

```bash
AWS_PROFILE=<your-profile> AWS_REGION=us-east-1 ./gradlew bootRun --args='--server.port=8081'
```

Wait for this line in the logs:

```
Started DemoApplication ... Tomcat started on port 8081
```

Leave this terminal running. **Open a second terminal** for the curls below
(and `cd` to the same folder in it).

---

### Step 3 — Health check (`/ping`)

**Say:** "AgentCore checks `/ping` to know the agent is healthy. I wrote zero
lines for this — the starter provides it."

```bash
curl -s http://localhost:8081/ping
```

Expect HTTP 200 / a healthy response.

---

### Step 4 — Talk to the agent (`/invocations`)

Ask for a joke (empty body works — it defaults to "tell me a joke"):

```bash
curl -s -X POST http://localhost:8081/invocations \
  -H "Content-Type: application/json" \
  -d '{}'
```

Now show it takes a real prompt AND gives a different answer each time
(temperature is set to 1.0):

```bash
curl -s -X POST http://localhost:8081/invocations \
  -H "Content-Type: application/json" \
  -d '{"prompt":"tell me a pun about databases"}'
```

Run this one two or three times to show the answers vary:

```bash
curl -s -X POST http://localhost:8081/invocations \
  -H "Content-Type: application/json" \
  -d '{"prompt":"give me a short joke about Java developers"}'
```

**Say:** "Same Spring `ChatClient`, backed by Bedrock (Nova Lite). I only wrote
the agent logic — the runtime, endpoints, and health checks come for free."

---

### Step 5 — The punchline: less infra, same artifact ships to the cloud

**Say:** "To go to production I don't rewrite anything. This same app becomes an
arm64 container and runs serverless on AgentCore Runtime — pay-per-use, managed
scaling, built-in health monitoring, rate limiting, and streaming. I bring the
agent method; AWS brings the runtime."

(Optional, only if deploying live — see the Appendix.)

---

## If something breaks (quick fixes)

| Problem | Fix |
|---|---|
| `zsh: no such file or directory: ./gradlew` | You're in the wrong folder. Run the `cd` in Step 0 (path is nested twice), then `ls gradlew`. |
| `Port 8080 was already in use` | We already use **8081**. If 8081 is busy too, pick another: change `--server.port=8082` and use that port in the curls. |
| Bedrock auth / `ExpiredToken` / `Unable to load credentials` | Make sure the command has `AWS_PROFILE=<your-profile>`. If still failing, run `aws sts get-caller-identity --profile <your-profile>` to confirm the profile works. |
| `AccessDenied` on the model | Model access for `amazon.nova-lite-v1:0` must be enabled in `us-east-1` (it already is for this account). |
| curl returns nothing / connection refused | The app isn't up yet. Wait for the "Tomcat started on port 8081" log line. |

**Stop the app when done:** press `Ctrl+C` in the terminal running `bootRun`.

---

## Appendix — Deploy to AgentCore Runtime (only if doing it live)

Same jar, packaged as a linux/arm64 container (AgentCore requirement).

```bash
# build jar
AWS_PROFILE=<your-profile> ./gradlew bootJar

# build arm64 image (Finch on Apple Silicon builds arm64 natively)
finch build --platform linux/arm64 \
  -t <account-id>.dkr.ecr.us-east-1.amazonaws.com/<repo>/hello-spring-ai-agentcore:latest .

# push to ECR
aws ecr get-login-password --region us-east-1 --profile <your-profile> \
  | finch login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
finch push <account-id>.dkr.ecr.us-east-1.amazonaws.com/<repo>/hello-spring-ai-agentcore:latest
```

Then create an AgentCore Runtime (PUBLIC network) pointing at that image, with an
execution role that trusts `bedrock-agentcore.amazonaws.com` and allows
`bedrock:InvokeModel`. Invoke it the same way as the curls above.

> Changed the code? Rebuild jar → rebuild image → re-push → update the runtime.

---

## Cheat: the three commands you actually need live

```bash
cd agentcore-deploy
AWS_PROFILE=<your-profile> AWS_REGION=us-east-1 ./gradlew bootRun --args='--server.port=8081'
curl -s -X POST http://localhost:8081/invocations -H "Content-Type: application/json" -d '{"prompt":"tell me a joke"}'
```
