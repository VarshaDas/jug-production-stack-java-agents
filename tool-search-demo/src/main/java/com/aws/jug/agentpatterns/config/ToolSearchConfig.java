package com.aws.jug.agentpatterns.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aws.jug.agentpatterns.tools.MyTools;

/**
 * Wiring for the Dynamic Tool Discovery (Tool Search Tool) pattern demo.
 *
 * CORE Spring AI 2.0 (matches the blog's stack). The Tool Search Tool advisor
 * is part of core Spring AI and is AUTO-CONFIGURED from properties — no manual
 * advisor/searcher wiring. See application.properties:
 *
 *     spring.ai.chat.client.tool-search-advisor.enabled=true
 *     spring.ai.chat.client.tool-search-advisor.tool-index-type=lucene
 *     spring.ai.chat.client.tool-search-advisor.max-results=5
 *
 * We use tool-index-type=lucene (keyword) rather than the blog's vector index,
 * so there is NO embedding model and NO ONNX/network dependency at startup.
 *
 * Three clients for the blog's before / after / tool-search comparison:
 *   - chatClientNoTools  — no tools at all (token floor; blog's /before)
 *   - chatClientAllTools — all tools sent upfront every round (blog's /after).
 *                          Built from ChatModel so the auto-configured
 *                          tool-search advisor does NOT leak into it.
 *   - chatClientWithTST  — built from the auto-configured ChatClient.Builder so
 *                          the property-enabled tool-search advisor applies;
 *                          the model starts with only the search tool.
 *   - ToolLoggingAdvisor — prints the tools in scope before each LLM round so
 *                          progressive discovery is visible in the logs.
 */
@Configuration
public class ToolSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolSearchConfig.class);

    /**
     * No tools at all — the cheapest baseline (mirrors the blog's /before).
     * Built from ChatModel: no tools, no tool-search advisor.
     */
    @Bean
    public ChatClient chatClientNoTools(ChatModel chatModel) {
        return ChatClient.create(chatModel)
                .mutate()
                .defaultAdvisors(new ToolLoggingAdvisor())
                .build();
    }

    /**
     * Baseline: all tools sent upfront every round (blog's /after).
     *
     * Built from ChatModel via ChatClient.create(...) — a FRESH builder that
     * does NOT carry the auto-configured tool-search advisor. In core Spring AI
     * 2.0 the tool-search advisor is auto-registered on the shared
     * ChatClient.Builder when enabled; building from ChatModel keeps this client
     * a true "everything upfront" comparison (and avoids the advisor demanding a
     * conversation id it never gets).
     */
    @Bean
    public ChatClient chatClientAllTools(ChatModel chatModel) {
        return ChatClient.create(chatModel)
                .mutate()
                .defaultTools(new MyTools())
                .defaultAdvisors(new ToolLoggingAdvisor())
                .build();
    }

    /**
     * TST: built from the auto-configured ChatClient.Builder. With
     * spring.ai.chat.client.tool-search-advisor.enabled=true the core
     * tool-search advisor is registered; only the search tool is sent initially
     * and discovered tools are expanded into context on later rounds.
     *
     * Tools are still registered so the (lucene) index has something to search.
     * Callers MUST supply a chat_memory_conversation_id per request (see
     * DemoController) — the advisor tracks discovered tools per conversation.
     */
    @Bean
    public ChatClient chatClientWithTST(ChatClient.Builder builder) {
        return builder
                .defaultTools(new MyTools())
                .defaultAdvisors(new ToolLoggingAdvisor())
                .build();
    }

    /**
     * Logs the tool names in scope before each LLM call, then what the assistant
     * did that round. Runs at order 0 so it fires once per round inside the
     * recursive tool-execution loop, making progressive discovery visible.
     */
    static class ToolLoggingAdvisor implements CallAdvisor {

        @Override
        public String getName() { return "ToolLoggingAdvisor"; }

        @Override
        public int getOrder() { return 0; }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            if (request.prompt().getOptions() instanceof ToolCallingChatOptions opts
                    && opts.getToolCallbacks() != null) {

                List<String> tools = opts.getToolCallbacks().stream()
                        .map(cb -> cb.getToolDefinition().name())
                        .sorted()
                        .toList();

                log.info(">>> Tools in scope ({}): {}", tools.size(), tools);
            }

            ChatClientResponse response = chain.nextCall(request);

            if (response.chatResponse() != null
                    && response.chatResponse().getResult() != null
                    && response.chatResponse().getResult().getOutput() instanceof AssistantMessage msg) {

                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    msg.getToolCalls().forEach(tc ->
                            log.info("    \u21b3 assistant wants to call: {}({})", tc.name(), tc.arguments()));
                }
                if (msg.getText() != null && !msg.getText().isBlank()) {
                    String text = msg.getText().strip();
                    String preview = text.length() > 120 ? text.substring(0, 120) + "\u2026" : text;
                    log.info("    \u21b3 assistant text: {}", preview);
                }
            }

            return response;
        }
    }
}
