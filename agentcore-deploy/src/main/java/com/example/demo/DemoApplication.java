package com.example.demo;

import org.springaicommunity.agentcore.annotation.AgentCoreInvocation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class DemoApplication {

    static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    ChatClient chatClient;

    DemoApplication(ChatClient.Builder chatClientBuilder) {
        chatClient = chatClientBuilder.build();
    }

    @AgentCoreInvocation
    public String myAgent(PromptRequest request) {
        String prompt = (request != null && request.prompt() != null && !request.prompt().isBlank())
                ? request.prompt()
                : "tell me a joke";
        return chatClient.prompt(prompt).call().content();
    }

    record PromptRequest(String prompt) {}

}
