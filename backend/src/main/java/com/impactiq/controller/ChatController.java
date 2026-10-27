package com.impactiq.controller;

import com.impactiq.dto.ChatDtos.ConversationDetail;
import com.impactiq.dto.ChatDtos.ConversationSummary;
import com.impactiq.dto.ChatDtos.CreateConversationRequest;
import com.impactiq.dto.ChatDtos.SendMessageRequest;
import com.impactiq.dto.ChatDtos.SendMessageResponse;
import com.impactiq.dto.ChatDtos.SuggestionsResponse;
import com.impactiq.dto.ChatDtos.UpdateConversationRequest;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Private chatbot conversations. Sending a message is synchronous (no streaming): the response carries the
 * stored user message and the assistant reply. The chatbot never starts investigations itself; clients call
 * the existing investigation endpoint after an explicit user confirmation.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserService currentUser;

    public ChatController(ChatService chatService, CurrentUserService currentUser) {
        this.chatService = chatService;
        this.currentUser = currentUser;
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> list(@RequestParam(required = false) Long projectId) {
        return chatService.list(currentUser.require(), projectId);
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDetail> create(@Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.create(currentUser.require(), request));
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetail get(@PathVariable Long conversationId) {
        return chatService.get(currentUser.require(), conversationId);
    }

    @PatchMapping("/conversations/{conversationId}")
    public ConversationDetail update(@PathVariable Long conversationId, @Valid @RequestBody UpdateConversationRequest request) {
        return chatService.update(currentUser.require(), conversationId, request);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable Long conversationId) {
        chatService.delete(currentUser.require(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public SendMessageResponse send(@PathVariable Long conversationId, @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(currentUser.require(), conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/retry")
    public SendMessageResponse retry(@PathVariable Long conversationId, @PathVariable Long messageId) {
        return chatService.retry(currentUser.require(), conversationId, messageId);
    }

    @GetMapping("/suggestions")
    public SuggestionsResponse suggestions(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) Long defectId
    ) {
        return chatService.suggestions(currentUser.require(), projectId, componentId, defectId);
    }
}
