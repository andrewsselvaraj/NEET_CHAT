package com.neetchat.search;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class QuestionPaperIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionPaperIndex.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "how", "i",
            "in", "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "what",
            "when", "where", "which", "who", "why", "with");
    private static final int CHUNK_SIZE = 1_200;
    private static final int CHUNK_OVERLAP = 180;

    private final Path documentsDirectory;
    private volatile List<PaperPassage> passages = List.of();

    public QuestionPaperIndex(@Value("${neet.documents.directory:Questions}") String documentsDirectory) {
        this.documentsDirectory = Path.of(documentsDirectory);
    }

    @PostConstruct
    public void loadDocuments() {
        if (!Files.isDirectory(documentsDirectory)) {
            LOGGER.warn("NEET papers directory '{}' does not exist; no papers were indexed.",
                    documentsDirectory.toAbsolutePath());
            return;
        }

        List<PaperPassage> loadedPassages = new ArrayList<>();
        try (Stream<Path> files = Files.walk(documentsDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted()
                    .forEach(path -> indexPdf(path, loadedPassages));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read NEET papers directory: " + documentsDirectory.toAbsolutePath(),
                    exception);
        }

        passages = List.copyOf(loadedPassages);
        LOGGER.info("Indexed {} text passages from NEET papers in '{}'.",
                passages.size(), documentsDirectory.toAbsolutePath());
        if (passages.isEmpty()) {
            LOGGER.warn("No readable PDF text was found in '{}'.", documentsDirectory.toAbsolutePath());
        }
    }

    public List<PaperPassage> search(String question, int limit) {
        List<String> queryTokens = tokenize(question);
        List<PaperPassage> indexedPassages = passages;
        if (queryTokens.isEmpty() || indexedPassages.isEmpty() || limit <= 0) {
            return List.of();
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        List<List<String>> passageTokens = new ArrayList<>(indexedPassages.size());
        double averageLength = 0;
        for (PaperPassage passage : indexedPassages) {
            List<String> tokens = tokenize(passage.text());
            passageTokens.add(tokens);
            averageLength += tokens.size();
            for (String token : new HashSet<>(tokens)) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        averageLength /= indexedPassages.size();

        List<PaperPassage> scored = new ArrayList<>();
        for (int index = 0; index < indexedPassages.size(); index++) {
            List<String> tokens = passageTokens.get(index);
            double score = score(queryTokens, tokens, documentFrequency, indexedPassages.size(), averageLength);
            if (score > 0) {
                PaperPassage passage = indexedPassages.get(index);
                scored.add(new PaperPassage(passage.text(), passage.source(), passage.page(), score));
            }
        }

        scored.sort(Comparator.comparingDouble(PaperPassage::score).reversed());
        Set<String> includedPages = new HashSet<>();
        List<PaperPassage> results = new ArrayList<>();
        for (PaperPassage passage : scored) {
            String pageKey = passage.source() + ":" + passage.page();
            if (includedPages.add(pageKey)) {
                results.add(passage);
                if (results.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(results);
    }

    private void indexPdf(Path path, List<PaperPassage> destination) {
        int passagesBefore = destination.size();
        try (PDDocument document = PDDocument.load(path.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                addPagePassages(destination, stripper.getText(document), path.getFileName().toString(), page);
            }
            int indexedCount = destination.size() - passagesBefore;
            if (indexedCount == 0) {
                LOGGER.warn("No extractable text found in '{}'; scanned PDFs need OCR before they can be searched.",
                        path.getFileName());
            } else {
                LOGGER.info("Indexed {} passages from '{}'.", indexedCount, path.getFileName());
            }
        } catch (IOException exception) {
            LOGGER.error("Unable to extract text from NEET paper '{}'.", path.toAbsolutePath(), exception);
        }
    }

    private void addPagePassages(List<PaperPassage> destination, String text, String source, int page) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int wordBoundary = normalized.lastIndexOf(' ', end);
                if (wordBoundary > start + CHUNK_SIZE / 2) {
                    end = wordBoundary;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                destination.add(new PaperPassage(chunk, source, page, 0));
            }
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
    }

    private double score(List<String> query, List<String> document,
                         Map<String, Integer> documentFrequency, int documentCount,
                         double averageLength) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : document) {
            termFrequency.merge(token, 1, Integer::sum);
        }
        double lengthNormalization = averageLength == 0 ? 1 : document.size() / averageLength;
        double score = 0;
        for (String token : query) {
            int frequency = termFrequency.getOrDefault(token, 0);
            if (frequency == 0) {
                continue;
            }
            int frequencyAcrossDocuments = documentFrequency.getOrDefault(token, 0);
            double inverseDocumentFrequency = Math.log(
                    1 + (documentCount - frequencyAcrossDocuments + 0.5) / (frequencyAcrossDocuments + 0.5));
            score += inverseDocumentFrequency * frequency * 2.2
                    / (frequency + 1.2 * (0.25 + 0.75 * lengthNormalization));
        }
        return score;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
