package com.neetchat.chat;

import java.util.List;

public record ChatAnswer(String answer, List<PaperCitation> sources) {
}
