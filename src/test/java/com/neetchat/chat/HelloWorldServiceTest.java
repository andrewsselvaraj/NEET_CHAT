package com.neetchat.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelloWorldServiceTest {

    @Test
    void greetsTheGivenNameUsingTheLanguageModel() {
        ChatLanguageModel languageModel = mock(ChatLanguageModel.class);
        when(languageModel.generate(org.mockito.ArgumentMatchers.<String>argThat(prompt ->
                prompt.contains("Hello World") && prompt.contains("Andrew"))))
                .thenReturn("  Hello World, Andrew!  ");

        String greeting = new HelloWorldService(languageModel).sayHello("Andrew");

        assertThat(greeting).isEqualTo("Hello World, Andrew!");
    }
}
