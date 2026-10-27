package com.impactiq.service;

import com.impactiq.config.ChatProperties;
import com.impactiq.entity.ChatMessage;
import com.impactiq.exception.RetryableApiException;
import com.impactiq.repository.ChatMessageRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

/**
 * Chat resource limits.
 * <ul>
 *   <li>Per-user quota: assistant rows (one per model invocation, created before the call) counted in the shared
 *   database over a sliding 60-second window. {@link #checkUserQuota} must run in a transaction that holds the
 *   user's row lock, which serialises concurrent sends from one user across all application instances.</li>
 *   <li>One reply in progress per user (a PENDING assistant row), also enforced through the database.</li>
 *   <li>Concurrent model calls: an in-memory semaphore, so this cap applies per application instance.</li>
 * </ul>
 */
@Component
public class ChatRateLimiter {

    static final Duration WINDOW = Duration.ofSeconds(60);
    private static final long BUSY_RETRY_SECONDS = 5;
    /** A PENDING reply older than the model timeout plus this grace is treated as interrupted. */
    static final Duration PENDING_GRACE = Duration.ofSeconds(60);

    private final ChatMessageRepository messageRepository;
    private final ChatProperties properties;
    private final Semaphore modelSlots;

    public ChatRateLimiter(ChatMessageRepository messageRepository, ChatProperties properties) {
        this.messageRepository = messageRepository;
        this.properties = properties;
        this.modelSlots = new Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
    }

    /** Reserves a model slot on this instance or fails fast with 503 + Retry-After. */
    public Permit acquireModelSlot() {
        if (!modelSlots.tryAcquire()) {
            throw RetryableApiException.unavailable("The assistant is busy with other requests. Retry in a few seconds.",
                    BUSY_RETRY_SECONDS);
        }
        return new Permit(modelSlots);
    }

    public void checkUserQuota(Long userId, Instant now) {
        Instant pendingCutoff = now.minusMillis(properties.getTimeoutMs()).minus(PENDING_GRACE);
        if (messageRepository.existsByOwnerUserIdAndRoleAndStatusAndCreatedAtAfter(userId, ChatMessage.ROLE_ASSISTANT,
                ChatMessage.STATUS_PENDING, pendingCutoff)) {
            throw RetryableApiException.tooManyRequests("Wait for the current reply to finish before sending another message.",
                    BUSY_RETRY_SECONDS);
        }
        int limit = properties.getRateLimitPerMinute();
        if (limit <= 0) {
            return;
        }
        Instant windowStart = now.minus(WINDOW);
        long used = messageRepository.countByOwnerUserIdAndRoleAndCreatedAtAfter(userId, ChatMessage.ROLE_ASSISTANT, windowStart);
        if (used >= limit) {
            long retryAfter = messageRepository
                    .findFirstByOwnerUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtAsc(userId, ChatMessage.ROLE_ASSISTANT, windowStart)
                    .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(WINDOW)).toSeconds() + 1)
                    .orElse(WINDOW.toSeconds());
            throw RetryableApiException.tooManyRequests(
                    "Message limit reached (" + limit + " per minute). Try again in " + Math.max(1, retryAfter) + " seconds.",
                    retryAfter);
        }
    }

    /** Releases its model slot exactly once. */
    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
