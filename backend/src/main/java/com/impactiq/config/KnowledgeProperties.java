package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "impactiq.knowledge")
public class KnowledgeProperties {

    /** Upload storage root. Must be outside any web root; defaults to the user's home directory. */
    private String storageDir = System.getProperty("user.home") + "/.impactiq/storage";
    private long maxUploadBytes = 10L * 1024 * 1024;
    private int maxPdfPages = 300;
    private int maxExtractedChars = 2_000_000;
    private int chunkTargetChars = 1500;
    private int chunkOverlapLines = 2;
    private int maxCandidateChunks = 5000;
    private String embeddingModel = "gemini-embedding-001";
    private int embeddingDimensions = 768;
    private int embeddingBatchSize = 50;
    private boolean embeddingsEnabled = true;
    private int processingThreads = 1;
    private int processingQueueCapacity = 50;
    private List<String> allowedExtensions = List.of(
            "txt", "log", "md", "json", "pdf",
            "java", "kt", "js", "jsx", "ts", "tsx", "py", "go", "cs", "sql", "xml",
            "yaml", "yml", "properties", "html", "css", "sh", "csv"
    );

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public int getMaxPdfPages() {
        return maxPdfPages;
    }

    public void setMaxPdfPages(int maxPdfPages) {
        this.maxPdfPages = maxPdfPages;
    }

    public int getMaxExtractedChars() {
        return maxExtractedChars;
    }

    public void setMaxExtractedChars(int maxExtractedChars) {
        this.maxExtractedChars = maxExtractedChars;
    }

    public int getChunkTargetChars() {
        return chunkTargetChars;
    }

    public void setChunkTargetChars(int chunkTargetChars) {
        this.chunkTargetChars = chunkTargetChars;
    }

    public int getChunkOverlapLines() {
        return chunkOverlapLines;
    }

    public void setChunkOverlapLines(int chunkOverlapLines) {
        this.chunkOverlapLines = chunkOverlapLines;
    }

    public int getMaxCandidateChunks() {
        return maxCandidateChunks;
    }

    public void setMaxCandidateChunks(int maxCandidateChunks) {
        this.maxCandidateChunks = maxCandidateChunks;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public boolean isEmbeddingsEnabled() {
        return embeddingsEnabled;
    }

    public void setEmbeddingsEnabled(boolean embeddingsEnabled) {
        this.embeddingsEnabled = embeddingsEnabled;
    }

    public int getProcessingThreads() {
        return processingThreads;
    }

    public void setProcessingThreads(int processingThreads) {
        this.processingThreads = processingThreads;
    }

    public int getProcessingQueueCapacity() {
        return processingQueueCapacity;
    }

    public void setProcessingQueueCapacity(int processingQueueCapacity) {
        this.processingQueueCapacity = processingQueueCapacity;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
