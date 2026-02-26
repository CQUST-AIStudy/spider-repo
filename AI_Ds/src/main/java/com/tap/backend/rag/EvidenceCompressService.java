package com.tap.backend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EvidenceCompressService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCompressService.class);

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。！？；\\n]+");
    private static final int DEFAULT_CONTEXT_WINDOW = 8000;
    private static final double TOKEN_RATIO_MIN = 0.25;
    private static final double TOKEN_RATIO_MAX = 0.40;
    private static final int MIN_SENTENCES = 4;
    private static final int MAX_SENTENCES = 8;

    public record CompressedEvidence(List<ScoredSentence> sentences, int totalTokens) {}

    public record ScoredSentence(String text, double score, int tokenCount,
                                  long chunkId, String chapterPath, String pageRange) {}

    /**
     * Compress parent content by extracting the most relevant sentences.
     * Uses term overlap scoring to avoid API calls.
     */
    public CompressedEvidence compress(String parentContent, String query,
                                        long chunkId, String chapterPath, String pageRange) {
        if (parentContent == null || parentContent.isBlank()) {
            return new CompressedEvidence(Collections.emptyList(), 0);
        }

        // 1. Split into sentences
        String[] rawSentences = SENTENCE_SPLIT.split(parentContent);
        List<String> sentences = Arrays.stream(rawSentences)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.length() > 2)
                .collect(Collectors.toList());

        if (sentences.isEmpty()) {
            return new CompressedEvidence(Collections.emptyList(), 0);
        }

        // 2. Extract query terms
        Set<String> queryTerms = extractTerms(query);

        // 3. Score each sentence by term overlap
        List<ScoredSentence> scored = new ArrayList<>();
        for (String sentence : sentences) {
            double score = computeTermOverlap(queryTerms, sentence);
            int tokenCount = estimateTokens(sentence);
            scored.add(new ScoredSentence(sentence, score, tokenCount,
                    chunkId, chapterPath, pageRange));
        }

        // 4. Sort by score descending
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 5. Select top sentences within token budget
        int tokenBudgetMin = (int) (DEFAULT_CONTEXT_WINDOW * TOKEN_RATIO_MIN);
        int tokenBudgetMax = (int) (DEFAULT_CONTEXT_WINDOW * TOKEN_RATIO_MAX);

        List<ScoredSentence> selected = new ArrayList<>();
        int totalTokens = 0;

        for (ScoredSentence ss : scored) {
            if (selected.size() >= MAX_SENTENCES) break;
            if (totalTokens + ss.tokenCount() > tokenBudgetMax && selected.size() >= MIN_SENTENCES) break;
            selected.add(ss);
            totalTokens += ss.tokenCount();
        }

        // Ensure minimum sentences if available
        if (selected.size() < MIN_SENTENCES && scored.size() > selected.size()) {
            for (int i = selected.size(); i < Math.min(MIN_SENTENCES, scored.size()); i++) {
                ScoredSentence ss = scored.get(i);
                if (totalTokens + ss.tokenCount() <= tokenBudgetMax) {
                    selected.add(ss);
                    totalTokens += ss.tokenCount();
                }
            }
        }

        log.debug("[EvidenceCompress] Selected {} sentences, {} tokens from {} total sentences",
                selected.size(), totalTokens, sentences.size());

        return new CompressedEvidence(selected, totalTokens);
    }

    /**
     * Extract terms from text by splitting on whitespace and punctuation.
     */
    private Set<String> extractTerms(String text) {
        if (text == null) return Collections.emptySet();
        // Split by whitespace, punctuation, and Chinese punctuation
        String[] tokens = text.split("[\\s\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\p{Punct}]+");
        Set<String> terms = new HashSet<>();
        for (String t : tokens) {
            String trimmed = t.trim().toLowerCase();
            if (!trimmed.isEmpty() && trimmed.length() > 1) {
                terms.add(trimmed);
                // Also add individual characters for Chinese text matching
                if (trimmed.length() > 2) {
                    for (int i = 0; i < trimmed.length() - 1; i++) {
                        terms.add(trimmed.substring(i, i + 2));
                    }
                }
            }
        }
        return terms;
    }

    /**
     * Compute term overlap score: count of query terms appearing in sentence / total query terms.
     */
    private double computeTermOverlap(Set<String> queryTerms, String sentence) {
        if (queryTerms.isEmpty()) return 0.0;
        String lowerSentence = sentence.toLowerCase();
        long matchCount = queryTerms.stream()
                .filter(lowerSentence::contains)
                .count();
        return (double) matchCount / queryTerms.size();
    }

    /**
     * Approximate token count: chars / 1.5 for Chinese text.
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 1.5));
    }
}
