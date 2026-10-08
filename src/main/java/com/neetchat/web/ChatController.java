package com.neetchat.web;

import com.neetchat.chat.ChatAnswer;
import com.neetchat.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_QUESTION_LENGTH = 1_000;

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam(name = "question", required = false) String question, Model model) {
        String normalizedQuestion = question == null ? "" : question.trim();
        model.addAttribute("question", normalizedQuestion);
        if (normalizedQuestion.isEmpty()) {
            model.addAttribute("error", "Enter a question to get started.");
            return "index";
        }
        if (normalizedQuestion.length() > MAX_QUESTION_LENGTH) {
            model.addAttribute("error", "Please keep your question under 1,000 characters.");
            return "index";
        }

        try {
            ChatAnswer answer = chatService.answer(normalizedQuestion);
            model.addAttribute("answer", answer.answer());
            model.addAttribute("sources", answer.sources());
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to answer NEET study question.", exception);
            model.addAttribute("error",
                    "I couldn't generate an answer just now. Please check the language-model configuration and try again.");
        }
        return "index";
    }
}
