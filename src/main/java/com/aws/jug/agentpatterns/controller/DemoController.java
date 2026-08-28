package com.aws.jug.agentpatterns.controller;

import com.aws.jug.agentpatterns.advisor.TokenCounterAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Two endpoints, same prompt, same tools — measured side by side:
 *   POST /chat/all-tools  baseline: all 28 tools sent upfront every round
 *   POST /chat/tst        Tool Search Tool: tools discovered on demand
 */
@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private static final String DEFAULT_PROMPT =
            "Help me plan what to wear today in Amsterdam. " +
            "Please suggest clothing shops that are open right now.";

    private final ChatClient chatClientAllTools;
    private final ChatClient chatClientWithTST;

    public DemoController(
            @Qualifier("chatClientAllTools") ChatClient chatClientAllTools,
            @Qualifier("chatClientWithTST")  ChatClient chatClientWithTST) {
        this.chatClientAllTools = chatClientAllTools;
        this.chatClientWithTST  = chatClientWithTST;
    }

    /** Baseline: all 28 tools sent to the LLM upfront on every round */
    @PostMapping("/chat/all-tools")
    public Map<String, Object> chatAllTools(@RequestBody(required = false) Map<String, String> body) {
        String prompt = promptOrDefault(body);
        logHeader("POST /chat/all-tools", "All 28 tools sent upfront every round", prompt);
        Map<String, Object> result = runWith(chatClientAllTools, prompt);
        logResult(result);
        return result;
    }

    /** TST: LLM starts with only toolSearchTool, discovers tools on demand */
    @PostMapping("/chat/tst")
    public Map<String, Object> chatTst(@RequestBody(required = false) Map<String, String> body) {
        String prompt = promptOrDefault(body);
        logHeader("POST /chat/tst", "Tool Search Tool — dynamic discovery", prompt);
        Map<String, Object> result = runWith(chatClientWithTST, prompt);
        logResult(result);
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String promptOrDefault(Map<String, String> body) {
        return (body != null && body.get("prompt") != null && !body.get("prompt").isBlank())
                ? body.get("prompt")
                : DEFAULT_PROMPT;
    }

    private Map<String, Object> runWith(ChatClient client, String prompt) {
        // Fresh per-request advisor tracks tokens + tools for this call
        TokenCounterAdvisor counter = new TokenCounterAdvisor();

        String answer = client.prompt(prompt)
                .advisors(counter)
                .call()
                .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer",           answer);
        result.put("totalTokens",      counter.getTotalTokens());
        result.put("promptTokens",     counter.getPromptTokens());
        result.put("completionTokens", counter.getCompletionTokens());
        result.put("requests",         counter.getRequests());
        result.put("toolsInScope",     counter.getToolsInScope());
        result.put("toolsCalled",      counter.getToolsCalled());
        return result;
    }

    private void logHeader(String endpoint, String mode, String prompt) {
        log.info("════════════════════════════════════════════════");
        log.info("  ENDPOINT: {}", endpoint);
        log.info("  MODE    : {}", mode);
        log.info("  PROMPT  : {}", prompt);
        log.info("════════════════════════════════════════════════");
    }

    private void logResult(Map<String, Object> result) {
        log.info("  RESULT  : totalTokens={} promptTokens={} completionTokens={}",
                result.get("totalTokens"), result.get("promptTokens"), result.get("completionTokens"));
        log.info("════════════════════════════════════════════════");
    }
}
