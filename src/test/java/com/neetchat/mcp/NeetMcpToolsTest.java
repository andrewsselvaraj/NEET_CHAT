package com.neetchat.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neetchat.chat.ChatService;
import com.neetchat.search.QuestionPaperIndex;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NeetMcpToolsTest {

    @TempDir
    Path temporaryDirectory;

    private QuestionPaperIndex index;

    @BeforeEach
    void indexPapers() throws IOException {
        writePdf(temporaryDirectory.resolve("neet-2023.pdf"),
                "The electric potential at the centre of a charged sphere is constant.",
                "Mitochondria are the powerhouse of the cell.");
        index = new QuestionPaperIndex(temporaryDirectory.toString());
        index.loadDocuments();
    }

    @Test
    void offersReadOnlyToolsWithoutALanguageModel() {
        assertThat(tools(null).specifications())
                .extracting(specification -> specification.tool().name())
                .containsExactly(NeetMcpTools.SEARCH_PAPERS, NeetMcpTools.LIST_PAPERS, NeetMcpTools.GET_PAPER_PAGE);
    }

    @Test
    void searchReturnsPassagesWithPaperAndPage() {
        CallToolResult result = call(tools(null), NeetMcpTools.SEARCH_PAPERS,
                Map.of("query", "electric potential charged sphere", "limit", 3));

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).startsWith("[1] neet-2023.pdf, page 1").contains("charged sphere");
    }

    @Test
    void searchRejectsMissingQueryAndOutOfRangeLimit() {
        assertThat(call(tools(null), NeetMcpTools.SEARCH_PAPERS, Map.of()).isError()).isTrue();
        CallToolResult result = call(tools(null), NeetMcpTools.SEARCH_PAPERS, Map.of("query", "cell", "limit", 50));
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("between 1 and 20");
    }

    @Test
    void listsIndexedPapers() {
        CallToolResult result = call(tools(null), NeetMcpTools.LIST_PAPERS, Map.of());

        assertThat(text(result)).contains("neet-2023.pdf: 2 pages, 2 passages");
    }

    @Test
    void readsAWholePageAndRejectsUnknownPapersAndPages() {
        CallToolResult page = call(tools(null), NeetMcpTools.GET_PAPER_PAGE, Map.of("paper", "neet-2023.pdf", "page", 2));
        assertThat(text(page)).startsWith("neet-2023.pdf, page 2").contains("Mitochondria");

        CallToolResult unknownPaper = call(tools(null), NeetMcpTools.GET_PAPER_PAGE,
                Map.of("paper", "../secret.pdf", "page", 1));
        assertThat(unknownPaper.isError()).isTrue();
        assertThat(text(unknownPaper)).contains("Unknown paper");

        CallToolResult missingPage = call(tools(null), NeetMcpTools.GET_PAPER_PAGE,
                Map.of("paper", "neet-2023.pdf", "page", 9));
        assertThat(missingPage.isError()).isTrue();
        assertThat(text(missingPage)).contains("pages 1 to 2");
    }

    @Test
    void askAnswersWithCitationsWhenALanguageModelIsConfigured() {
        ChatLanguageModel languageModel = mock(ChatLanguageModel.class);
        when(languageModel.generate(anyString())).thenReturn("It is constant inside the sphere [1].");
        NeetMcpTools tools = tools(new ChatService(languageModel, index));

        CallToolResult result = call(tools, NeetMcpTools.ASK_QUESTION,
                Map.of("question", "What is the electric potential inside a charged sphere?"));

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).isEqualTo("It is constant inside the sphere [1].\n\nSources:\n[1] neet-2023.pdf, page 1");
    }

    @Test
    void reportsLanguageModelFailuresAsToolErrors() {
        ChatLanguageModel languageModel = mock(ChatLanguageModel.class);
        when(languageModel.generate(anyString())).thenThrow(new RuntimeException("401 Unauthorized"));
        NeetMcpTools tools = tools(new ChatService(languageModel, index));

        CallToolResult result = call(tools, NeetMcpTools.ASK_QUESTION, Map.of("question", "electric potential"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("401 Unauthorized");
    }

    private NeetMcpTools tools(ChatService chatService) {
        return new NeetMcpTools(new JacksonMcpJsonMapper(new ObjectMapper()), index, chatService,
                CompletableFuture.completedFuture(null));
    }

    private CallToolResult call(NeetMcpTools tools, String name, Map<String, Object> arguments) {
        SyncToolSpecification specification = tools.specifications().stream()
                .filter(candidate -> candidate.tool().name().equals(name))
                .findFirst()
                .orElseThrow();
        return specification.callHandler().apply(null, new CallToolRequest(name, arguments));
    }

    private String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private void writePdf(Path path, String... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : List.of(pages)) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
