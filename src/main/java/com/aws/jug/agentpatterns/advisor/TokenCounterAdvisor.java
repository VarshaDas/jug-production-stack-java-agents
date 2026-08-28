package com.aws.jug.agentpatterns.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports AGGREGATE token usage and tools for a single HTTP request, matching
 * the methodology in the Spring AI Tool Search Tool blog.
 *
 * IMPORTANT: ToolSearchToolCallAdvisor extends ToolCallAdvisor, which owns the
 * recursive tool-execution loop. This advisor sits INSIDE that loop, so
 * adviseCall() is invoked once PER ROUND (once per LLM request), not once per
 * top-level HTTP request.
 *
 * This advisor therefore:
 *   - tokens        : SUMS each round's reported usage. This is the true billed
 *                     cost of the whole interaction — you pay for the full
 *                     prompt (including resent history) on every request. This
 *                     is what the blog reports as "Total Tokens".
 *   - requests      : counts the number of LLM round-trips (blog's "Requests").
 *   - toolsInScope  : records the DISTINCT set of tools seen across all rounds.
 *   - toolsCalled   : records the DISTINCT set of tools the model invoked
 *                     across all rounds (captured every round, not just final).
 *
 * Create a fresh instance per HTTP request.
 */
public class TokenCounterAdvisor implements CallAdvisor {

    private final AtomicLong promptTokens     = new AtomicLong(0);
    private final AtomicLong completionTokens = new AtomicLong(0);
    private final AtomicLong requests         = new AtomicLong(0);

    // LinkedHashSet: distinct, but preserves first-seen order for readable output
    private final Set<String> toolsInScope = new LinkedHashSet<>();
    private final Set<String> toolsCalled  = new LinkedHashSet<>();

    // ── CallAdvisor ───────────────────────────────────────────────────────────

    @Override
    public String getName() {
        return "TokenCounterAdvisor";
    }

    @Override
    public int getOrder() {
        // Run after ToolSearchToolCallAdvisor (which uses a low order)
        // so we see the final resolved state
        return Integer.MAX_VALUE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Capture tools registered at the start of this request
        if (request.prompt().getOptions() instanceof ToolCallingChatOptions opts) {
            if (opts.getToolCallbacks() != null) {
                opts.getToolCallbacks().forEach(cb ->
                        toolsInScope.add(cb.getToolDefinition().name()));
            }
        }

        ChatClientResponse response = chain.nextCall(request);

        // Aggregate THIS round's usage into the running total. adviseCall runs
        // once per LLM round-trip, so summing across rounds yields the true
        // billed cost of the whole interaction (this matches the blog's
        // "Total Tokens", which sums per-request usage across all requests).
        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            var usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                requests.incrementAndGet();
                if (usage.getPromptTokens()     != null) promptTokens.addAndGet(usage.getPromptTokens());
                if (usage.getCompletionTokens() != null) completionTokens.addAndGet(usage.getCompletionTokens());
            }
        }

        // Capture tool calls from the final assistant message
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            var output = response.chatResponse().getResult().getOutput();
            if (output instanceof AssistantMessage msg && msg.getToolCalls() != null) {
                msg.getToolCalls().forEach(tc -> toolsCalled.add(tc.name()));
            }
        }

        return response;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<String> getToolsInScope()    { return List.copyOf(toolsInScope); }
    public List<String> getToolsCalled()     { return List.copyOf(toolsCalled); }
    public long getPromptTokens()            { return promptTokens.get(); }
    public long getCompletionTokens()        { return completionTokens.get(); }
    public long getTotalTokens()             { return promptTokens.get() + completionTokens.get(); }
    public long getRequests()                { return requests.get(); }
}
