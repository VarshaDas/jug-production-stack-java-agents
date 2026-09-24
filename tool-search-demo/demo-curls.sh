#!/usr/bin/env bash
#
# Tool Search Tool demo — Bengaluru scenario (core Spring AI 2.0, lucene index).
# Three-way comparison mirroring the blog's before / after / tool-search.
#
# Prereqs:
#   export AWS_ACCESS_KEY_ID=...
#   export AWS_SECRET_ACCESS_KEY=...
#   ./mvnw spring-boot:run        # wait for "Started ... on port 8085"
#
# Usage:
#   ./demo-curls.sh              # runs all three endpoints
#   ./demo-curls.sh tst          # runs just one: no-tools | all-tools | tst
#
# The prompt names NO tools — "asthma" implies air quality, "run outside this
# afternoon" implies weather + UV. The model must reason about what it needs
# and (in tst mode) discover those tools on its own.

set -euo pipefail

BASE="http://localhost:8085"
PROMPT="I have asthma and I want to go for a run outside in Bengaluru this afternoon. Is that a good idea?"

# Build the JSON body safely (jq escapes the prompt).
BODY=$(jq -n --arg p "$PROMPT" '{prompt: $p}')

call () {
  local endpoint="$1"
  echo "════════════════════════════════════════════════"
  echo "POST /chat/${endpoint}"
  echo "PROMPT: ${PROMPT}"
  echo "────────────────────────────────────────────────"
  curl -s -X POST "${BASE}/chat/${endpoint}" \
    -H "Content-Type: application/json" \
    -d "${BODY}" | jq '{toolIndexType, totalTokens, promptTokens, requests, toolsInScope, toolsCalled, answer}'
  echo
}

if [[ $# -ge 1 ]]; then
  call "$1"
else
  # no-tools  = token floor (blog's /before)
  # all-tools = all 28 sent upfront (blog's /after)
  # tst       = Tool Search Tool, dynamic discovery (the fix)
  call "no-tools"
  call "all-tools"
  call "tst"
fi
