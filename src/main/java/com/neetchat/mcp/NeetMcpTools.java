package com.neetchat.mcp;

import com.neetchat.chat.ChatAnswer;
import com.neetchat.chat.ChatService;
import com.neetchat.chat.PaperCitation;
import com.neetchat.search.IndexedPaper;
import com.neetchat.search.PaperPassage;
import com.neetchat.search.QuestionPaperIndex;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Exposes the NEET paper index and chat service as MCP tools.
 */
public class NeetMcpTools {

    static final String SEARCH_PAPERS = "search_neet_papers";
    static final String LIST_PAPERS = "list_neet_papers";
    static final String GET_PAPER_PAGE = "get_neet_paper_page";
    static final String ASK_QUESTION = "ask_neet_question";

    private static final Logger LOGGER = LoggerFactory.getLogger(NeetMcpTools.class);
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MAX_QUESTION_LENGTH = 1_000;

    private final McpJsonMapper jsonMapper;
    private final QuestionPaperIndex questionPaperIndex;
    private final ChatService chatService;
    private final CompletableFuture<?> indexLoaded;

    /**
     * @param chatService optional; when null the {@value #ASK_QUESTION} tool is not offered
     * @param indexLoaded completes once the papers are indexed; tool calls wait for it
     */
    public NeetMcpTools(McpJsonMapper jsonMapper, QuestionPaperIndex questionPaperIndex, ChatService chatService,
                        CompletableFuture<?> indexLoaded) {
        this.jsonMapper = jsonMapper;
        this.questionPaperIndex = questionPaperIndex;
        this.chatService = chatService;
        this.indexLoaded = indexLoaded;
    }

    public List<SyncToolSpecification> specifications() {
        List<SyncToolSpecification> specifications = new ArrayList<>();
        specifications.add(tool(SEARCH_PAPERS, "Search NEET papers",
                "Search the indexed past NEET question papers and solutions for passages relevant to a "
                        + "topic, concept or question. Returns the best matching passages with the paper "
                        + "filename and page number so they can be cited.",
                """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Topic, concept or question text to search for, e.g. 'electric potential charged sphere'."
                    },
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 20,
                      "description": "Maximum number of passages to return (default 5)."
                    }
                  },
                  "required": ["query"]
                }
                """, true, this::searchPapers));
        specifications.add(tool(LIST_PAPERS, "List NEET papers",
                "List the NEET question paper PDFs that are indexed, with their page counts and how many "
                        + "searchable passages each produced. Papers with 0 passages are scanned images "
                        + "that need OCR before they can be searched.",
                """
                {"type": "object", "properties": {}}
                """, true, arguments -> listPapers()));
        specifications.add(tool(GET_PAPER_PAGE, "Read a NEET paper page",
                "Return the full extracted text of one page of an indexed NEET paper. Use it to read a "
                        + "whole question, its options and its solution after a search result points to "
                        + "that page.",
                """
                {
                  "type": "object",
                  "properties": {
                    "paper": {
                      "type": "string",
                      "description": "Paper filename exactly as returned by search_neet_papers or list_neet_papers."
                    },
                    "page": {
                      "type": "integer",
                      "minimum": 1,
                      "description": "1-based page number."
                    }
                  },
                  "required": ["paper", "page"]
                }
                """, true, this::getPaperPage));
        if (chatService != null) {
            specifications.add(tool(ASK_QUESTION, "Ask the NEET Paper Coach",
                    "Answer a NEET study question with the app's own language model, grounded only in "
                            + "passages retrieved from the past papers, and return the answer with its "
                            + "paper and page citations.",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "question": {
                          "type": "string",
                          "description": "The student's question (up to 1,000 characters)."
                        }
                      },
                      "required": ["question"]
                    }
                    """, false, this::askQuestion));
        }
        return List.copyOf(specifications);
    }

    CallToolResult searchPapers(Map<String, Object> arguments) {
        String query = requiredString(arguments, "query");
        int limit = optionalInteger(arguments, "limit", DEFAULT_SEARCH_LIMIT);
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new IllegalArgumentException("'limit' must be between 1 and " + MAX_SEARCH_LIMIT + ".");
        }

        List<PaperPassage> passages = questionPaperIndex.search(query, limit);
        if (passages.isEmpty()) {
            return text("No passages in the indexed NEET papers matched '" + query
                    + "'. Try different keywords, the exam year, or a more specific topic.");
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < passages.size(); index++) {
            PaperPassage passage = passages.get(index);
            result.append("[%d] %s, page %d (score %.2f)%n%s%n%n".formatted(
                    index + 1, passage.source(), passage.page(), passage.score(), passage.text()));
        }
        return text(result.toString().trim());
    }

    CallToolResult listPapers() {
        List<IndexedPaper> papers = questionPaperIndex.papers();
        if (papers.isEmpty()) {
            return text("No NEET papers are indexed. Check that NEET_DOCUMENTS_DIRECTORY points to a folder of PDFs.");
        }
        StringBuilder result = new StringBuilder("Indexed NEET papers (" + papers.size() + "):\n");
        for (IndexedPaper paper : papers) {
            result.append("- %s: %d pages, %d passages%s%n".formatted(paper.name(), paper.pages(),
                    paper.passages(), paper.passages() == 0 ? " (no extractable text; needs OCR)" : ""));
        }
        return text(result.toString().trim());
    }

    CallToolResult getPaperPage(Map<String, Object> arguments) {
        String paper = requiredString(arguments, "paper");
        int page = optionalInteger(arguments, "page", 0);
        if (page < 1) {
            throw new IllegalArgumentException("'page' must be a page number of 1 or more.");
        }
        String pageText = questionPaperIndex.pageText(paper, page);
        if (pageText.isEmpty()) {
            return text(paper + ", page " + page + " has no extractable text (it may be a scanned image).");
        }
        return text(paper + ", page " + page + "\n\n" + pageText);
    }

    CallToolResult askQuestion(Map<String, Object> arguments) {
        String question = requiredString(arguments, "question");
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Please keep the question under 1,000 characters.");
        }
        ChatAnswer answer = chatService.answer(question);
        StringBuilder result = new StringBuilder(answer.answer());
        if (!answer.sources().isEmpty()) {
            result.append("\n\nSources:");
            for (PaperCitation citation : answer.sources()) {
                result.append("\n[%d] %s, page %d".formatted(citation.reference(), citation.paper(), citation.page()));
            }
        }
        return text(result.toString());
    }

    private SyncToolSpecification tool(String name, String title, String description, String inputSchema,
                                       boolean readOnly, Function<Map<String, Object>, CallToolResult> handler) {
        Tool tool = Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(jsonMapper, inputSchema)
                .annotations(new ToolAnnotations(title, readOnly, false, readOnly, !readOnly, null))
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(request, handler))
                .build();
    }

    private CallToolResult call(CallToolRequest request, Function<Map<String, Object>, CallToolResult> handler) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        try {
            indexLoaded.join();
            return handler.apply(arguments);
        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("MCP tool '{}' failed.", request.name(), exception);
            return error("The " + request.name() + " tool failed: " + exception.getMessage());
        }
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' is required.");
        }
        return text;
    }

    private static int optionalInteger(Map<String, Object> arguments, String name, int defaultValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + name + "' must be a whole number.");
        }
    }

    private static CallToolResult text(String text) {
        return CallToolResult.builder().addTextContent(text).isError(false).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
