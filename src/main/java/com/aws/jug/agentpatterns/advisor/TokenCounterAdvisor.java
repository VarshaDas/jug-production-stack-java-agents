package com.aws.jug.agentpatterns.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks cumulative token usage and tools across the full conversation.
 *
 * NOTE: ToolCallAdvisor (and ToolSearchToolCallAdvisor) run their recursive
 * loop internally — outer advisors are only called once per top-level request,
 * not once per LLM round-trip. This advisor therefore captures:
 *   - toolsInScope at the START of the conversation (what was registered)
 *   - toolsCalled in the FINAL assistant response
 *   - total tokens from the FINAL response metadata
 *     (Bedrock Converse returns cumulative usage on the last response)
 *
 * Create a fresh instance per HTTP request.
 */
public class TokenCounterAdvisor implements CallAdvisor {

    private final AtomicLong promptTokens     = new AtomicLong(0);
    private final AtomicLong completionTokens = new AtomicLong(0);

    private final List<String> toolsInScope = new ArrayList<>();
    private final List<String> toolsCalled  = new ArrayList<>();

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

        // Accumulate tokens from the final response
        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            var usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
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
}
