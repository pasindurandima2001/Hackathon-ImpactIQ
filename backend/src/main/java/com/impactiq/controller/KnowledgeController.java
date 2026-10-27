package com.impactiq.controller;

import com.impactiq.dto.KnowledgeDtos.DocumentDetailResponse;
import com.impactiq.dto.KnowledgeDtos.DocumentResponse;
import com.impactiq.dto.KnowledgeDtos.SearchRequest;
import com.impactiq.dto.KnowledgeDtos.SearchResponse;
import com.impactiq.rag.KnowledgeDocumentService;
import com.impactiq.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Project knowledge sources. Processing is asynchronous: uploads return 202 and the status is polled. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class KnowledgeController {

    private final KnowledgeDocumentService documentService;
    private final CurrentUserService currentUser;

    public KnowledgeController(KnowledgeDocumentService documentService, CurrentUserService currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/documents")
    public List<DocumentResponse> list(@PathVariable Long projectId, @RequestParam(required = false) Long componentId) {
        return documentService.list(currentUser.require(), projectId, componentId);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) String title
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(documentService.upload(currentUser.require(), projectId, componentId, title, file));
    }

    @GetMapping("/documents/{documentId}")
    public DocumentDetailResponse get(@PathVariable Long projectId, @PathVariable Long documentId) {
        return documentService.get(currentUser.require(), projectId, documentId);
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadVersion(
            @PathVariable Long projectId,
            @PathVariable Long documentId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(documentService.uploadVersion(currentUser.require(), projectId, documentId, file));
    }

    @PostMapping("/documents/{documentId}/reprocess")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable Long projectId, @PathVariable Long documentId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(documentService.reprocess(currentUser.require(), projectId, documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long documentId) {
        documentService.delete(currentUser.require(), projectId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    public SearchResponse search(@PathVariable Long projectId, @Valid @RequestBody SearchRequest request) {
        return documentService.search(currentUser.require(), projectId, request.componentId(), request.query(), request.topK());
    }
}
