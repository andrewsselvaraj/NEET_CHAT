package com.neetchat.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LanguageModelConfiguration {

    @Bean
    ChatLanguageModel chatLanguageModel(
            @Value("${neet.llm.api-key:${openai.api-key:${NEET_LLM_API_KEY:${OPENAI_API_KEY:}}}}") String apiKey,
            @Value("${neet.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${neet.llm.model:gpt-4o-mini}") String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set OPENAI_API_KEY or NEET_LLM_API_KEY before starting the chatbot.");
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }
}
