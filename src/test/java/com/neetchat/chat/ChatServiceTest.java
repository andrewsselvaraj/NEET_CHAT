package com.neetchat.chat;

import com.neetchat.search.QuestionPaperIndex;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void answersWithRetrievedPassageAndCitesItsPaper() throws IOException {
        writePdf(temporaryDirectory.resolve("neet-2023.pdf"),
                "The electric potential at the centre of a charged sphere is constant.");
        QuestionPaperIndex index = new QuestionPaperIndex(temporaryDirectory.toString());
        index.loadDocuments();

        ChatLanguageModel languageModel = mock(ChatLanguageModel.class);
        when(languageModel.generate(org.mockito.ArgumentMatchers.<String>argThat(prompt ->
                prompt.contains("What is electric potential?")
                && prompt.contains("neet-2023.pdf, page 1")
                && prompt.contains("charged sphere"))))
                .thenReturn("The potential is constant inside the sphere [1].");

        ChatAnswer answer = new ChatService(languageModel, index).answer("What is electric potential?");

        assertThat(answer.answer()).isEqualTo("The potential is constant inside the sphere [1].");
        assertThat(answer.sources()).containsExactly(new PaperCitation(1, "neet-2023.pdf", 1));
    }

    @Test
    void doesNotAskTheModelToGuessWhenNoPaperPassageMatches() {
        QuestionPaperIndex index = new QuestionPaperIndex(temporaryDirectory.toString());
        index.loadDocuments();
        ChatLanguageModel languageModel = mock(ChatLanguageModel.class);

        ChatAnswer answer = new ChatService(languageModel, index).answer("Explain quantum entanglement.");

        assertThat(answer.answer()).contains("couldn't find a relevant passage");
        assertThat(answer.sources()).isEmpty();
        verifyNoInteractions(languageModel);
    }

    private void writePdf(Path path, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
    }
}
