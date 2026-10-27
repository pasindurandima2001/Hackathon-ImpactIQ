package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impactiq.chat")
public class ChatProperties {

    /** Maximum characters in one user message; enforced server-side. */
    private int maxMessageChars = 4000;
    /** Recent user/assistant turns sent to the model as conversation context. */
    private int historyTurns = 10;
    /** Characters kept from each earlier message in the prompt history. */
    private int maxHistoryMessageChars = 2000;
    /** Model invocations allowed per user in a sliding 60-second window (counted in the database). */
    private int rateLimitPerMinute = 10;
    /** Wall-clock budget for one assistant reply, including retries. */
    private long timeoutMs = 90_000;
    /** Concurrent model calls per application instance. */
    private int maxConcurrent = 4;
    /** Conversations without activity for this many days are purged. 0 or less disables the age-based purge. */
    private int retentionDays = 90;
    /** Deleted conversations (content already wiped) are physically removed after this many hours. */
    private int deletedPurgeHours = 24;
    /** Conversations returned by the list endpoint. */
    private int listLimit = 100;

    public int getMaxMessageChars() {
        return maxMessageChars;
    }

    public void setMaxMessageChars(int maxMessageChars) {
        this.maxMessageChars = maxMessageChars;
    }

    public int getHistoryTurns() {
        return historyTurns;
    }

    public void setHistoryTurns(int historyTurns) {
        this.historyTurns = historyTurns;
    }

    public int getMaxHistoryMessageChars() {
        return maxHistoryMessageChars;
    }

    public void setMaxHistoryMessageChars(int maxHistoryMessageChars) {
        this.maxHistoryMessageChars = maxHistoryMessageChars;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getDeletedPurgeHours() {
        return deletedPurgeHours;
    }

    public void setDeletedPurgeHours(int deletedPurgeHours) {
        this.deletedPurgeHours = deletedPurgeHours;
    }

    public int getListLimit() {
        return listLimit;
    }

    public void setListLimit(int listLimit) {
        this.listLimit = listLimit;
    }
}
