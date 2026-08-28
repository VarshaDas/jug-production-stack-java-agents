package com.aws.jug.agentpatterns.config;

import com.aws.jug.agentpatterns.tools.MyTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springaicommunity.tool.search.ToolSearcher;
import org.springaicommunity.tool.search.ToolSearchToolCallAdvisor;
import org.springaicommunity.tool.searcher.LuceneToolSearcher;

import java.util.List;

/**
 * Wiring for the Dynamic Tool Discovery (Tool Search Tool) pattern demo.
 *
 *   - LuceneToolSearcher   — keyword index over all registered tools
 *   - chatClientAllTools   — baseline: all 28 tools sent to the LLM every round
 *   - chatClientWithTST    — TST: model starts with only toolSearchTool and
 *                            discovers tools on demand
 *   - ToolLoggingAdvisor   — prints the tools in scope before each LLM round
 *                            so the progressive discovery is visible in logs
 */
@Configuration
public class ToolSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolSearchConfig.class);

    @Bean
    public ToolSearcher toolSearcher() {
        return new LuceneToolSearcher();
    }

    /**
     * Baseline: all 28 tools sent upfront every round.
     * ToolCallAdvisor drives the recursive execution loop.
     */
    @Bean
    public ChatClient chatClientAllTools(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultTools(new MyTools())
                .defaultAdvisors(ToolCallAdvisor.builder().build(), new ToolLoggingAdvisor())
                .build();
    }

    /**
     * TST: ToolSearchToolCallAdvisor drives the loop AND intercepts tool
     * registration so only toolSearchTool is sent initially. Discovered tools
     * are expanded into the context on subsequent rounds.
     */
    // NOTE: This uses the community 1.0.x line for Spring Boot 3, wiring the
    // advisor manually below. In Spring AI 2.0.0 GA the Tool Search Tool is now
    // core — add org.springframework.ai:spring-ai-starter-tool-search-advisor
    // and enable it with a single property, no manual wiring:
    //     spring.ai.chat.client.tool-search-advisor.enabled=true
    @Bean
    public ChatClient chatClientWithTST(ChatModel chatModel, ToolSearcher toolSearcher) {
        return ChatClient.builder(chatModel)
                .defaultTools(new MyTools())
                .defaultAdvisors(
                        ToolSearchToolCallAdvisor.builder()
                                .toolSearcher(toolSearcher)
                                .maxResults(5)
                                .build(),
                        new ToolLoggingAdvisor())
                .build();
    }

    /**
     * Logs the tool names in scope before each LLM call. Runs at order 0 —
     * inside ToolCallAdvisor's recursive loop — so it fires once per round,
     * making the progressive tool discovery visible in the logs.
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

            // Log the assistant message this round returned: either the tool
            // calls it requested, or the text it produced. This makes each step
            // of the discover -> call -> answer loop visible in the logs.
            if (response.chatResponse() != null
                    && response.chatResponse().getResult() != null
                    && response.chatResponse().getResult().getOutput() instanceof AssistantMessage msg) {

                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    msg.getToolCalls().forEach(tc ->
                            log.info("    ↳ assistant wants to call: {}({})", tc.name(), tc.arguments()));
                }
                if (msg.getText() != null && !msg.getText().isBlank()) {
                    String text = msg.getText().strip();
                    String preview = text.length() > 120 ? text.substring(0, 120) + "…" : text;
                    log.info("    ↳ assistant text: {}", preview);
                }
            }

            return response;
        }
    }
}
