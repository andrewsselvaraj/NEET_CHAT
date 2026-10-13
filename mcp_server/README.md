# NEET Paper Coach MCP server

This MCP server lets Claude (Claude Desktop, Claude Code, or any MCP client) search and read the past NEET papers in this repository. It reuses the app's own PDF index and chat service, and runs over stdio from the same jar as the web app, so nothing extra needs deploying.

## Tools

| Tool | What it does | Needs an API key |
| --- | --- | --- |
| `search_neet_papers` | Finds the passages most relevant to a topic or question (`query`, optional `limit` 1 to 20, default 5) and returns each with its paper filename and page. | No |
| `list_neet_papers` | Lists the indexed PDFs with page and passage counts. Papers with 0 passages are scanned images that need OCR. | No |
| `get_neet_paper_page` | Returns the full text of one page (`paper`, `page`), for reading a whole question, its options and its solution. | No |
| `ask_neet_question` | Answers a `question` with the app's own language model, grounded in retrieved passages, with paper and page citations. | Yes |

Search and page reading happen locally. `ask_neet_question` is only offered when `OPENAI_API_KEY` or `NEET_LLM_API_KEY` is set; without it, Claude answers from the search results itself, which is usually all you need.

## Build

From the repository root (Java 17+ and Maven 3.6+):

```shell
mvn -DskipTests package
```

This produces `target/neet-question-chat-0.1.0.jar`. The server starts answering immediately and indexes the PDFs in the background; the first tool call waits until indexing finishes (about 15 seconds for the papers in `Questions`).

## Connect to Claude Desktop

Open **Settings > Developer > Edit Config** and add the server to `claude_desktop_config.json`, replacing the paths with where you cloned this repository.

Windows:

```json
{
  "mcpServers": {
    "neet-papers": {
      "command": "java",
      "args": [
        "-cp", "C:\\path\\to\\NEET_CHAT\\target\\neet-question-chat-0.1.0.jar",
        "-Dloader.main=com.neetchat.mcp.NeetMcpServer",
        "org.springframework.boot.loader.launch.PropertiesLauncher"
      ],
      "env": {
        "NEET_DOCUMENTS_DIRECTORY": "C:\\path\\to\\NEET_CHAT\\Questions"
      }
    }
  }
}
```

macOS or Linux:

```json
{
  "mcpServers": {
    "neet-papers": {
      "command": "/path/to/NEET_CHAT/mcp_server/run-mcp-server.sh"
    }
  }
}
```

Restart Claude Desktop, then ask something like "Find NEET questions about electric potential of a charged sphere and explain the answer."

To enable `ask_neet_question`, add `"OPENAI_API_KEY": "your-api-key"` to the `env` block (and optionally `NEET_LLM_BASE_URL` and `NEET_LLM_MODEL`, as for the web app).

## Connect to Claude Code

```shell
claude mcp add neet-papers -- /path/to/NEET_CHAT/mcp_server/run-mcp-server.sh
```

On Windows:

```powershell
claude mcp add neet-papers -- powershell -NoProfile -ExecutionPolicy Bypass -File C:\path\to\NEET_CHAT\mcp_server\run-mcp-server.ps1
```

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `NEET_DOCUMENTS_DIRECTORY` | `Questions` (relative to the working directory; the launch scripts default to this repository's `Questions` folder) | Directory containing source PDFs |
| `OPENAI_API_KEY` / `NEET_LLM_API_KEY` | — | Enables `ask_neet_question` |
| `NEET_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible API base URL |
| `NEET_LLM_MODEL` | `gpt-4o-mini` | Chat model name |

Set `NEET_DOCUMENTS_DIRECTORY` to an absolute path when you don't use the launch scripts, because MCP clients start the server from their own working directory.

## Try it without Claude

The [MCP Inspector](https://github.com/modelcontextprotocol/inspector) lets you call the tools by hand:

```shell
npx @modelcontextprotocol/inspector ./mcp_server/run-mcp-server.sh
```

## How it works

`com.neetchat.mcp.NeetMcpServer` starts without Spring, builds the same `QuestionPaperIndex` and `ChatService` the web app uses, and serves the tools in `NeetMcpTools` with the official [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk). Stdout carries only MCP messages; all logging goes to stderr (`src/main/resources/logback-mcp.xml`). The server exits when the client closes its input.
