package com.neetchat.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neetchat.chat.ChatService;
import com.neetchat.config.LanguageModelConfiguration;
import com.neetchat.search.QuestionPaperIndex;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Runs the NEET Paper Coach as an MCP server over stdio, without starting the web app.
 */
public final class NeetMcpServer {

    private static final String SERVER_NAME = "neet-paper-coach";
    private static final String SERVER_VERSION = "0.1.0";
    private static final String INSTRUCTIONS = """
            Tools for studying with past NEET (Indian medical entrance exam) question papers and solutions.
            Use search_neet_papers to find relevant questions and solutions, get_neet_paper_page to read a
            whole page, and cite answers with the paper filename and page number.""";

    private NeetMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        // stdout is reserved for MCP messages, so anything else that prints there goes to stderr instead.
        PrintStream mcpOutput = System.out;
        System.setOut(System.err);
        System.setProperty("logback.configurationFile", "logback-mcp.xml");
        Logger logger = LoggerFactory.getLogger(NeetMcpServer.class);

        QuestionPaperIndex index = new QuestionPaperIndex(environment("NEET_DOCUMENTS_DIRECTORY", "Questions"));
        // Index in the background so the client's initialize handshake isn't held up by PDF parsing.
        CompletableFuture<Void> indexLoaded = CompletableFuture.runAsync(index::loadDocuments);

        ChatService chatService = null;
        String apiKey = environment("NEET_LLM_API_KEY", environment("OPENAI_API_KEY", ""));
        if (apiKey.isBlank()) {
            logger.info("No OPENAI_API_KEY or NEET_LLM_API_KEY set; the ask_neet_question tool is disabled.");
        } else {
            chatService = new ChatService(new LanguageModelConfiguration().chatLanguageModel(apiKey,
                    environment("NEET_LLM_BASE_URL", "https://api.openai.com/v1"),
                    environment("NEET_LLM_MODEL", "gpt-4o-mini")), index);
        }

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        CountDownLatch inputClosed = new CountDownLatch(1);
        InputStream mcpInput = new EndOfInputListener(System.in, inputClosed::countDown);
        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(jsonMapper, mcpInput, mcpOutput))
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(INSTRUCTIONS)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(new NeetMcpTools(jsonMapper, index, chatService, indexLoaded).specifications())
                .build();
        logger.info("NEET MCP server is running on stdio.");
        indexLoaded.thenRun(() -> logger.info("{} NEET papers are ready to search.", index.papers().size()));

        // The client ends the session by closing our stdin.
        inputClosed.await();
        server.closeGracefully();
        System.exit(0);
    }

    private static final class EndOfInputListener extends FilterInputStream {

        private final Runnable onEndOfInput;

        EndOfInputListener(InputStream input, Runnable onEndOfInput) {
            super(input);
            this.onEndOfInput = onEndOfInput;
        }

        @Override
        public int read() throws IOException {
            return notifyAtEnd(super.read());
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return notifyAtEnd(super.read(buffer, offset, length));
        }

        private int notifyAtEnd(int result) {
            if (result == -1) {
                onEndOfInput.run();
            }
            return result;
        }
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
