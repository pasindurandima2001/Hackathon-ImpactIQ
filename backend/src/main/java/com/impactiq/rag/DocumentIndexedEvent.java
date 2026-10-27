package com.impactiq.rag;

/** Published after a document version's chunks have been committed and become searchable. */
public record DocumentIndexedEvent(Long documentId, Long projectId) {
}
