# NEET Paper Coach

A Spring Boot and Thymeleaf chatbot that retrieves passages from past NEET paper PDFs and uses LangChain4j with an OpenAI-compatible chat API to explain them. Answers show the paper filename and page for the retrieved sources.

## Requirements

- Java 17 or newer
- Maven 3.6 or newer
- An API key for the configured OpenAI-compatible chat service

## Run

Set a real API key in your environment, then run from the repository root. The app accepts either `OPENAI_API_KEY` or `NEET_LLM_API_KEY`:

```powershell
$env:OPENAI_API_KEY = "your-api-key"
# or
$env:NEET_LLM_API_KEY = "your-api-key"
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) and enter a question.

The app indexes text-readable PDF files beneath `Questions` at startup. Scanned/image-only PDFs need OCR before they can be searched. Set `NEET_DOCUMENTS_DIRECTORY` to use a different folder. It searches locally and sends only the selected text passages and the student's question to the configured language-model API.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | — | Required API key |
| `NEET_LLM_API_KEY` | — | Alternate project-specific API key variable |
| `NEET_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible API base URL |
| `NEET_LLM_MODEL` | `gpt-4o-mini` | Chat model name |
| `NEET_DOCUMENTS_DIRECTORY` | `Questions` | Directory containing source PDFs |

## Test

```powershell
mvn test
```
