package com.neetchat.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

@Service
public class HelloWorldService {

    private final ChatLanguageModel languageModel;

    public HelloWorldService(ChatLanguageModel languageModel) {
        this.languageModel = languageModel;
    }

    public String sayHello(String name) {
        String prompt = "Say a short, friendly 'Hello World' greeting to %s.".formatted(name);
        String answer = languageModel.generate(prompt);
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("The language model returned an empty answer.");
        }
        return answer.trim();
    }
}
