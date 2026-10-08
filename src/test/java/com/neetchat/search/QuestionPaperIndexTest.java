package com.neetchat.search;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionPaperIndexTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesPdfAndReturnsRelevantPassageWithPaperAndPage() throws IOException {
        Path paper = temporaryDirectory.resolve("neet-2023.pdf");
        writePdf(paper, "The electric potential at the centre of a charged sphere is constant.");

        QuestionPaperIndex index = new QuestionPaperIndex(temporaryDirectory.toString());
        index.loadDocuments();
        List<PaperPassage> results = index.search("electric potential charged sphere", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).contains("electric potential");
        assertThat(results.get(0).source()).isEqualTo("neet-2023.pdf");
        assertThat(results.get(0).page()).isEqualTo(1);
    }

    @Test
    void returnsNoResultsForTermsNotPresentInThePaper() throws IOException {
        writePdf(temporaryDirectory.resolve("neet-2023.pdf"), "The cell is the basic unit of life.");

        QuestionPaperIndex index = new QuestionPaperIndex(temporaryDirectory.toString());
        index.loadDocuments();

        assertThat(index.search("quantum entanglement", 5)).isEmpty();
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
