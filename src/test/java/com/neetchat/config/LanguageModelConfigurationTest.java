package com.neetchat.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageModelConfigurationTest {

    @Test
    void acceptsApiKeyValueFromConfiguration() {
        ChatLanguageModel model = new LanguageModelConfiguration()
                .chatLanguageModel("test-key", "https://api.openai.com/v1", "gpt-4o-mini");

        assertThat(model).isNotNull();
    }

    @Test
    void rejectsBlankApiKey() {
        assertThatThrownBy(() -> new LanguageModelConfiguration()
                .chatLanguageModel("", "https://api.openai.com/v1", "gpt-4o-mini"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
