package com.mouss.ebankbot.controllers;

import com.mouss.ebankbot.agents.EbankAIAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EbankChatbotController {

    private final EbankAIAgent agent;

    public EbankChatbotController(EbankAIAgent agent) {
        this.agent = agent;
    }

    /**
     * Simple REST client (browser / curl): GET /chat?query=...
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(name = "query", defaultValue = "Bonjour") String query,
                       @RequestParam(name = "conversationId", defaultValue = "default") String conversationId) {
        return agent.chat(conversationId, query);
    }

    /**
     * JSON client (React app): POST /chat { "conversationId": "...", "message": "..." }
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String conversationId = request.conversationId() == null ? "default" : request.conversationId();
        return new ChatResponse(conversationId, agent.chat(conversationId, request.message()));
    }

    public record ChatRequest(String conversationId, String message) {
    }

    public record ChatResponse(String conversationId, String answer) {
    }
}
