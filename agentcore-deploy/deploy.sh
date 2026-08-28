#!/usr/bin/env bash
# Rebuild + redeploy the hello-spring-ai-agentcore agent to Bedrock AgentCore Runtime.
# Builds the arm64 image in the cloud via CodeBuild (no local Docker needed).
set -euo pipefail

# ---- config (fill in with your own AWS resource identifiers) ----
ACCOUNT=${AWS_ACCOUNT_ID:-YOUR_ACCOUNT_ID}
REGION=${AWS_REGION:-us-east-1}
PROFILE=${AWS_PROFILE:-default}
REPO=hello-spring-ai-agentcore
BUCKET=agentcore-build-src-$ACCOUNT
CB_PROJECT=agentcore-arm64-build
RUNTIME_ID=${AGENTCORE_RUNTIME_ID:-YOUR_RUNTIME_ID}
RUNTIME_ARN=arn:aws:bedrock-agentcore:$REGION:$ACCOUNT:runtime/$RUNTIME_ID
ROLE_ARN=arn:aws:iam::$ACCOUNT:role/YOUR_AGENTCORE_EXECUTION_ROLE
ECR_URI=$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$REPO:latest
AWS="aws --region $REGION --profile $PROFILE"

echo "==> [1/5] Building fat jar"
./gradlew bootJar

echo "==> [2/5] Packaging + uploading source to s3://$BUCKET"
rm -f /tmp/agentsrc.zip
zip -q -r /tmp/agentsrc.zip Dockerfile buildspec.yml build/libs/hello-spring-ai-bedrock.jar
$AWS s3 cp /tmp/agentsrc.zip s3://$BUCKET/agentsrc.zip

echo "==> [3/5] Starting CodeBuild (arm64 image build + push)"
BUILD_ID=$($AWS codebuild start-build --project-name $CB_PROJECT --query 'build.id' --output text)
echo "    build id: $BUILD_ID"
while true; do
  STATUS=$($AWS codebuild batch-get-builds --ids "$BUILD_ID" --query 'builds[0].buildStatus' --output text)
  echo "    status: $STATUS"
  case "$STATUS" in
    SUCCEEDED) break;;
    FAILED|FAULT|STOPPED|TIMED_OUT) echo "Build failed ($STATUS)"; exit 1;;
  esac
  sleep 15
done

echo "==> [4/5] Updating AgentCore runtime to new image"
$AWS bedrock-agentcore-control update-agent-runtime \
  --agent-runtime-id "$RUNTIME_ID" \
  --agent-runtime-artifact "{\"containerConfiguration\":{\"containerUri\":\"$ECR_URI\"}}" \
  --role-arn "$ROLE_ARN" \
  --network-configuration '{"networkMode":"PUBLIC"}' \
  --query '{id:agentRuntimeId,version:agentRuntimeVersion,status:status}' --output json
# wait for READY
while true; do
  RS=$($AWS bedrock-agentcore-control get-agent-runtime --agent-runtime-id "$RUNTIME_ID" --query 'status' --output text)
  echo "    runtime status: $RS"
  case "$RS" in
    READY) break;;
    *FAILED*|DELETING) echo "Runtime update failed ($RS)"; exit 1;;
  esac
  sleep 10
done

echo "==> [5/5] Invoking runtime as a smoke test"
$AWS bedrock-agentcore invoke-agent-runtime \
  --agent-runtime-arn "$RUNTIME_ARN" \
  --runtime-session-id "sess-$(date +%s)-redeploy-smoke-test-padding-xxxxxx" \
  --payload '{}' --content-type application/json --accept application/json \
  --cli-binary-format raw-in-base64-out \
  /tmp/agent-response.json >/dev/null
echo "--- agent response ---"
cat /tmp/agent-response.json
echo
echo "==> Done."
