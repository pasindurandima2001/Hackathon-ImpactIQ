package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impactiq.investigation")
public class InvestigationProperties {

    /** "adk" runs each specialist as a Google ADK LlmAgent; "gemini" calls the existing Interactions client directly. */
    private String engine = "adk";
    private int maxOutputTokens = 2048;
    /** Gemini thinking level for agent calls (MINIMAL | LOW | MEDIUM | HIGH); blank leaves the model default. */
    private String thinkingLevel = "LOW";
    /** Attempts per agent call, including the first; only transient or invalid-output failures are retried. */
    private int agentMaxAttempts = 2;
    private long retryBackoffMs = 1500;
    /** Wall-clock budget for one whole investigation run. */
    private long runTimeoutMs = 240_000;
    /** Total runs in one retry chain (first run + retries). */
    private int maxAttempts = 3;
    /** Upper bound on concurrently queued or running investigations across all defects. */
    private int maxActive = 4;
    private int executorThreads = 2;
    private int executorQueueCapacity = 20;
    private int maxEvidenceItemChars = 6000;
    private int maxPromptEvidenceChars = 60_000;
    private int retrievalTopK = 6;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }

    public int getAgentMaxAttempts() {
        return agentMaxAttempts;
    }

    public void setAgentMaxAttempts(int agentMaxAttempts) {
        this.agentMaxAttempts = agentMaxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public long getRunTimeoutMs() {
        return runTimeoutMs;
    }

    public void setRunTimeoutMs(long runTimeoutMs) {
        this.runTimeoutMs = runTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public void setExecutorThreads(int executorThreads) {
        this.executorThreads = executorThreads;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public int getMaxEvidenceItemChars() {
        return maxEvidenceItemChars;
    }

    public void setMaxEvidenceItemChars(int maxEvidenceItemChars) {
        this.maxEvidenceItemChars = maxEvidenceItemChars;
    }

    public int getMaxPromptEvidenceChars() {
        return maxPromptEvidenceChars;
    }

    public void setMaxPromptEvidenceChars(int maxPromptEvidenceChars) {
        this.maxPromptEvidenceChars = maxPromptEvidenceChars;
    }

    public int getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }
}
