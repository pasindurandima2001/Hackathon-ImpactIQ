package com.impactiq.rag;

import com.impactiq.config.KnowledgeProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-aware chunking that keeps 1-based line ranges (per page for PDFs) so citations can point to the source.
 * Consecutive chunks overlap by a few lines to keep context across boundaries.
 */
@Service
public class ChunkingService {

    private final KnowledgeProperties properties;

    public ChunkingService(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(List<TextExtractionService.ExtractedText.Page> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        for (TextExtractionService.ExtractedText.Page page : pages) {
            chunkPage(page.text(), page.pageNumber(), chunks);
        }
        return chunks;
    }

    private void chunkPage(String text, Integer pageNumber, List<TextChunk> output) {
        int target = Math.max(200, properties.getChunkTargetChars());
        int overlap = Math.max(0, properties.getChunkOverlapLines());
        String[] lines = text.split("\n", -1);
        List<Line> buffer = new ArrayList<>();
        int bufferChars = 0;

        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index];
            // Very long single lines (minified JSON, long log lines) are split so each slice fits a chunk.
            for (String slice : slices(line, target)) {
                if (bufferChars + slice.length() > target && hasContent(buffer)) {
                    emit(buffer, pageNumber, output);
                    List<Line> carry = overlapLines(buffer, overlap, target / 3);
                    buffer.clear();
                    buffer.addAll(carry);
                    bufferChars = carry.stream().mapToInt(item -> item.text().length() + 1).sum();
                }
                buffer.add(new Line(lineNumber, slice));
                bufferChars += slice.length() + 1;
            }
        }
        if (hasContent(buffer)) {
            emit(buffer, pageNumber, output);
        }
    }

    /** Last {@code maxLines} lines, bounded to {@code maxChars} so overlap never dominates a chunk. */
    private List<Line> overlapLines(List<Line> buffer, int maxLines, int maxChars) {
        List<Line> carry = new ArrayList<>();
        int chars = 0;
        for (int index = buffer.size() - 1; index >= 0 && carry.size() < maxLines; index--) {
            Line line = buffer.get(index);
            if (chars + line.text().length() > maxChars) {
                break;
            }
            carry.add(0, line);
            chars += line.text().length() + 1;
        }
        return carry;
    }

    private List<String> slices(String line, int target) {
        if (line.length() <= target) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < line.length(); start += target) {
            parts.add(line.substring(start, Math.min(line.length(), start + target)));
        }
        return parts;
    }

    private boolean hasContent(List<Line> buffer) {
        return buffer.stream().anyMatch(line -> !line.text().isBlank());
    }

    private void emit(List<Line> buffer, Integer pageNumber, List<TextChunk> output) {
        int first = 0;
        while (first < buffer.size() - 1 && buffer.get(first).text().isBlank()) {
            first++;
        }
        int last = buffer.size() - 1;
        while (last > first && buffer.get(last).text().isBlank()) {
            last--;
        }
        StringBuilder content = new StringBuilder();
        for (int index = first; index <= last; index++) {
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(buffer.get(index).text());
        }
        output.add(new TextChunk(content.toString(), buffer.get(first).number(), buffer.get(last).number(), pageNumber));
    }

    private record Line(int number, String text) {
    }

    public record TextChunk(String content, int startLine, int endLine, Integer pageNumber) {
    }
}
