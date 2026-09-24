package com.aws.jug.agentpatterns.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aws.jug.agentpatterns.advisor.TokenCounterAdvisor;

/**
 * Three endpoints, same prompt — measured side by side (mirrors the blog's
 * before / after / tool-search comparison):
 *   POST /chat/no-tools   no tools at all — the token floor (blog's /before)
 *   POST /chat/all-tools  all 28 tools sent upfront every round (blog's /after)
 *   POST /chat/tst        Tool Search Tool: tools discovered on demand
 */
@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final ChatClient chatClientNoTools;
    private final ChatClient chatClientAllTools;
    private final ChatClient chatClientWithTST;

    public DemoController(
            @Qualifier("chatClientNoTools")  ChatClient chatClientNoTools,
            @Qualifier("chatClientAllTools") ChatClient chatClientAllTools,
            @Qualifier("chatClientWithTST")  ChatClient chatClientWithTST) {
        this.chatClientNoTools  = chatClientNoTools;
        this.chatClientAllTools = chatClientAllTools;
        this.chatClientWithTST  = chatClientWithTST;
    }

    /** No tools at all — the token floor (mirrors the blog's /before) */
    @PostMapping("/chat/no-tools")
    public Map<String, Object> chatNoTools(@RequestBody(required = false) Map<String, String> body) {
        String prompt = requirePrompt(body);
        logHeader("POST /chat/no-tools", "No tools — token floor", prompt);
        Map<String, Object> result = runWith(chatClientNoTools, prompt);
        logResult(result);
        return result;
    }

    /** Baseline: all 28 tools sent to the LLM upfront on every round */
    @PostMapping("/chat/all-tools")
    public Map<String, Object> chatAllTools(@RequestBody(required = false) Map<String, String> body) {
        String prompt = requirePrompt(body);
        logHeader("POST /chat/all-tools", "All 28 tools sent upfront every round", prompt);
        Map<String, Object> result = runWith(chatClientAllTools, prompt);
        logResult(result);
        return result;
    }

    /** TST: LLM starts with only toolSearchTool, discovers tools on demand */
    @PostMapping("/chat/tst")
    public Map<String, Object> chatTst(@RequestBody(required = false) Map<String, String> body) {
        String prompt = requirePrompt(body);
        logHeader("POST /chat/tst", "Tool Search Tool — dynamic discovery", prompt);
        Map<String, Object> result = runWith(chatClientWithTST, prompt);
        logResult(result);
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Prompt is required — no default. Returns HTTP 400 with a clear message if
     * the body is missing or has a blank "prompt".
     */
    private String requirePrompt(Map<String, String> body) {
        String prompt = (body != null) ? body.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body must include a non-blank \"prompt\", e.g. {\"prompt\": \"...\"}");
        }
        return prompt;
    }

    private Map<String, Object> runWith(ChatClient client, String prompt) {
        // Fresh per-request advisor tracks tokens + tools for this call
        TokenCounterAdvisor counter = new TokenCounterAdvisor();

        // Always set a fresh conversation id. The core tool-search advisor
        // requires chat_memory_conversation_id to track discovered tools per
        // conversation; setting it unconditionally is harmless for the
        // no-tools / all-tools clients and guarantees the TST endpoint can't
        // fail with a missing-id error.
        String answer = client.prompt(prompt)
                .advisors(counter)
                .advisors(a -> a.param(
                        ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
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
