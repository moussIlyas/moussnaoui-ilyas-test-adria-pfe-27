package com.mouss.ebankbot.agents;

import com.mouss.ebankbot.tools.WebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultEbankAIAgent implements EbankAIAgent {

    private final ChatClient chatClient;

    public DefaultEbankAIAgent(AgentProvider agentProvider,
                               ChatMemory chatMemory,
                               WebSearchTool webSearchTool,
                               ObjectProvider<ToolCallbackProvider> mcpToolCallbackProvider,
                               @Value("${app.agent.system-prompt}") String systemPrompt) {
        ChatClient.Builder builder = agentProvider.agentBuilder()
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(webSearchTool);

        // Tools discovered from the MCP servers (account-service + transaction-service).
        mcpToolCallbackProvider.ifAvailable(provider -> builder.defaultTools(provider));

        this.chatClient = builder.build();
    }

    @Override
    public String chat(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
