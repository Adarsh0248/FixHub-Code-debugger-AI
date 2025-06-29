package com.razeef.BugBrother.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ChatClient chatClient;
    private final OpenAiChatModel chatModel;

    AiService(OpenAiChatModel chatModel){
        this.chatModel=chatModel;
        this.chatClient= ChatClient.builder(chatModel).build();
    }

    public String chatWithSystem(String systemMessage, String userMessage){
        return chatClient
                .prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content();
    }
}
