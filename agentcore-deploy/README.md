Hello Spring AI AgentCore
-------------------------

Run Locally:

1. [Create a Bedrock Bearer token](https://us-east-1.console.aws.amazon.com/bedrock/home?region=us-east-1#/api-keys/long-term/create)
2. Set the env var: `export AWS_BEARER_TOKEN_BEDROCK=YOUR_TOKEN`
3. Run the app: `./gradlew bootRun`

Run on AgentCore:

1. Tell your AI agent:
   ```
   Set up Agent Toolkit for AWS by following instructions:
   https://raw.githubusercontent.com/aws/agent-toolkit-for-aws/refs/heads/main/setup-instructions/setup.md
   
   Deploy this agent on AgentCore Runtime
   ```
