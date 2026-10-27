package com.impactiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.SourceRef;
import com.impactiq.agent.AgentException;
import com.impactiq.agent.ChatAssistantAgent;
import com.impactiq.agent.ProjectContextAgent;
import com.impactiq.config.ChatProperties;
import com.impactiq.dto.ChatDtos.ActionResponse;
import com.impactiq.dto.ChatDtos.ChatLimits;
import com.impactiq.dto.ChatDtos.CitationResponse;
import com.impactiq.dto.ChatDtos.CitationSource;
import com.impactiq.dto.ChatDtos.ContextInfo;
import com.impactiq.dto.ChatDtos.ConversationDetail;
import com.impactiq.dto.ChatDtos.ConversationSummary;
import com.impactiq.dto.ChatDtos.CreateConversationRequest;
import com.impactiq.dto.ChatDtos.MessageResponse;
import com.impactiq.dto.ChatDtos.SendMessageRequest;
import com.impactiq.dto.ChatDtos.SendMessageResponse;
import com.impactiq.dto.ChatDtos.SuggestionsResponse;
import com.impactiq.dto.ChatDtos.UpdateConversationRequest;
import com.impactiq.entity.ChatConversation;
import com.impactiq.entity.ChatMessage;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DocumentChunk;
import com.impactiq.entity.FeedbackVerdict;
import com.impactiq.entity.Investigation;
import com.impactiq.entity.InvestigationFeedback;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.entity.Project;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.rag.PromptInjectionDetector;
import com.impactiq.rag.SecretRedactor;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.ChatConversationRepository;
import com.impactiq.repository.ChatMessageRepository;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Private, project-scoped chatbot conversations.
 * <p>Every request re-checks: conversation ownership (another user's conversation is a 404, also for admins),
 * current project membership (revoked membership is a 404), and that an attached component or defect still belongs
 * to the conversation's project. Answers come from {@link ChatAssistantAgent} over evidence the server retrieves
 * for this request only; client-supplied history, roles or citations are never trusted.
 * <p>The model call runs outside any database transaction: a USER row and a PENDING assistant row are committed
 * first (under the user's row lock, for the rate limit), then the assistant row is completed.
 */
@Service
public class ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private static final String DEFAULT_TITLE = "New conversation";
    private static final String DELETED_TITLE = "Deleted conversation";
    private static final int AUTO_TITLE_MAX = 60;
    private static final int EXCERPT_MAX = 1500;
    private static final int DEFECT_EVIDENCE_MAX = 6000;
    private static final int MAX_INVESTIGATION_FINDINGS = 3;
    private static final int RETRIEVAL_QUERY_MAX = 2000;
    private static final int NAME_IN_SUGGESTION_MAX = 80;
    private static final String SOURCE_CHUNK = "DOCUMENT_CHUNK";
    private static final String SOURCE_DEFECT = "DEFECT";
    private static final String SOURCE_INVESTIGATION = "INVESTIGATION";
    private static final TypeReference<List<CitationResponse>> CITATION_LIST = new TypeReference<>() {
    };

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AppUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final DefectRepository defectRepository;
    private final InvestigationRepository investigationRepository;
    private final InvestigationFeedbackRepository feedbackRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AccessControlService accessControl;
    private final ProjectContextAgent projectContextAgent;
    private final ChatAssistantAgent assistantAgent;
    private final SecretRedactor secretRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ChatService(
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            AppUserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            DefectRepository defectRepository,
            InvestigationRepository investigationRepository,
            InvestigationFeedbackRepository feedbackRepository,
            DocumentChunkRepository chunkRepository,
            AccessControlService accessControl,
            ProjectContextAgent projectContextAgent,
            ChatAssistantAgent assistantAgent,
            SecretRedactor secretRedactor,
            PromptInjectionDetector injectionDetector,
            ChatRateLimiter rateLimiter,
            ChatProperties properties,
            AuditService auditService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.defectRepository = defectRepository;
        this.investigationRepository = investigationRepository;
        this.feedbackRepository = feedbackRepository;
        this.chunkRepository = chunkRepository;
        this.accessControl = accessControl;
        this.projectContextAgent = projectContextAgent;
        this.assistantAgent = assistantAgent;
        this.secretRedactor = secretRedactor;
        this.injectionDetector = injectionDetector;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------------------------------------------------------------- conversations

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(CurrentUser user, Long projectId) {
        Set<Long> projectIds;
        if (projectId != null) {
            accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
            projectIds = Set.of(projectId);
        } else {
            projectIds = accessControl.visibleProjectIds(user);
        }
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<ChatConversation> conversations = conversationRepository.findVisible(user.id(), projectIds,
                Limit.of(Math.max(1, properties.getListLimit())));
        Names names = names(conversations);
        return conversations.stream().map(conversation -> toSummary(conversation, names)).toList();
    }

    @Transactional
    public ConversationDetail create(CurrentUser user, CreateConversationRequest request) {
        ProjectRole role = accessControl.requireProjectRole(user, request.projectId(), ProjectRole.VIEWER);
        Defect defect = validateContext(user, request.projectId(), request.componentId(), request.defectId());
        ChatConversation conversation = new ChatConversation();
        conversation.setOwnerUserId(user.id());
        conversation.setProjectId(request.projectId());
        conversation.setComponentId(request.componentId());
        conversation.setDefectId(request.defectId());
        String title = request.title() == null ? "" : request.title().strip();
        if (title.isEmpty()) {
            title = defect == null ? DEFAULT_TITLE : truncate(defectKey(defect.getId()) + " " + defect.getTitle(), AUTO_TITLE_MAX);
        }
        conversation.setTitle(title);
        conversation.setUpdatedAt(Instant.now());
        ChatConversation saved = conversationRepository.saveAndFlush(conversation);
        auditService.record(user, "CHAT_CONVERSATION_CREATED", "CHAT_CONVERSATION", saved.getId(), saved.getProjectId(),
                contextDetail(saved));
        return detail(user, saved, role);
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(CurrentUser user, Long conversationId) {
        Owned owned = requireConversation(user, conversationId);
        return detail(user, owned.conversation(), owned.role());
    }

    @Transactional
    public ConversationDetail update(CurrentUser user, Long conversationId, UpdateConversationRequest request) {
        Owned owned = requireConversation(user, conversationId);
        ChatConversation conversation = owned.conversation();
        if (request.title() != null) {
            String title = request.title().strip();
            if (title.isEmpty()) {
                throw ApiException.badRequest("Title cannot be empty");
            }
            conversation.setTitle(title);
        }
        if (Boolean.TRUE.equals(request.updateContext())) {
            validateContext(user, conversation.getProjectId(), request.componentId(), request.defectId());
            conversation.setComponentId(request.componentId());
            conversation.setDefectId(request.defectId());
            auditService.record(user, "CHAT_CONTEXT_CHANGED", "CHAT_CONVERSATION", conversation.getId(),
                    conversation.getProjectId(), contextDetail(conversation));
        }
        conversation.setUpdatedAt(Instant.now());
        return detail(user, conversationRepository.saveAndFlush(conversation), owned.role());
    }

    /** Wipes message content immediately; the rows are physically removed later by the retention job. */
    @Transactional
    public void delete(CurrentUser user, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, user.id())
                .orElseThrow(ChatService::conversationNotFound);
        // Deleting your own conversation is allowed even after losing project access: it only removes your data.
        conversation.setDeletedAt(Instant.now());
        conversation.setTitle(DELETED_TITLE);
        conversation.setDefectId(null);
        conversation.setComponentId(null);
        conversationRepository.saveAndFlush(conversation);
        messageRepository.wipeConversation(conversationId);
        auditService.record(user, "CHAT_CONVERSATION_DELETED", "CHAT_CONVERSATION", conversationId,
                conversation.getProjectId(), null);
    }

    @Transactional(readOnly = true)
    public SuggestionsResponse suggestions(CurrentUser user, Long projectId, Long componentId, Long defectId) {
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        Defect defect = validateContext(user, projectId, componentId, defectId);
        return new SuggestionsResponse(suggestedQuestions(projectId, componentId, defect), limits());
    }

    // ---------------------------------------------------------------- messages

    public SendMessageResponse send(CurrentUser user, Long conversationId, SendMessageRequest request) {
        String content = request.content() == null ? "" : request.content().replace("\r\n", "\n").strip();
        if (content.isEmpty()) {
            throw ApiException.badRequest("Message is required");
        }
        if (content.length() > properties.getMaxMessageChars()) {
            throw ApiException.badRequest("Message must be at most " + properties.getMaxMessageChars() + " characters");
        }
        try (ChatRateLimiter.Permit ignored = rateLimiter.acquireModelSlot()) {
            Prepared prepared = transactionTemplate.execute(status -> prepareNewTurn(user, conversationId, content));
            return complete(user, Objects.requireNonNull(prepared));
        }
    }

    /** Re-runs a failed reply. Only the latest assistant message, and only when it is in ERROR, can be retried. */
    public SendMessageResponse retry(CurrentUser user, Long conversationId, Long assistantMessageId) {
        try (ChatRateLimiter.Permit ignored = rateLimiter.acquireModelSlot()) {
            Prepared prepared = transactionTemplate.execute(status -> prepareRetry(user, conversationId, assistantMessageId));
            return complete(user, Objects.requireNonNull(prepared));
        }
    }

    private Prepared prepareNewTurn(CurrentUser user, Long conversationId, String content) {
        Instant now = Instant.now();
        lockUser(user);
        Owned owned = requireConversation(user, conversationId);
        enforceQuota(user, owned.conversation(), now);
        List<ChatMessage> history = history(conversationId, null);

        SecretRedactor.RedactionResult redacted = secretRedactor.redact(content);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setOwnerUserId(user.id());
        userMessage.setRole(ChatMessage.ROLE_USER);
        userMessage.setStatus(ChatMessage.STATUS_COMPLETE);
        userMessage.setContent(redacted.text());
        userMessage.setRedactionCount(redacted.redactionCount());
        userMessage.setSuspicious(injectionDetector.isSuspicious(redacted.text()));
        userMessage.setCompletedAt(now);
        userMessage = messageRepository.save(userMessage);

        ChatMessage placeholder = messageRepository.saveAndFlush(placeholder(conversationId, user.id()));
        ChatConversation conversation = owned.conversation();
        if (conversation.getMessageCount() == 0 && DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.setTitle(autoTitle(redacted.text()));
        }
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setUpdatedAt(now);
        conversationRepository.saveAndFlush(conversation);
        return new Prepared(conversationId, owned.role(), userMessage.getId(), userMessage.getContent(),
                userMessage.isSuspicious(), userMessage.getRedactionCount(), placeholder.getId(), toTurns(history));
    }

    private Prepared prepareRetry(CurrentUser user, Long conversationId, Long assistantMessageId) {
        Instant now = Instant.now();
        lockUser(user);
        Owned owned = requireConversation(user, conversationId);
        ChatMessage failed = messageRepository.findByIdAndConversationId(assistantMessageId, conversationId)
                .orElseThrow(() -> ApiException.notFound("Message " + assistantMessageId + " was not found"));
        List<ChatMessage> latest = messageRepository.findByConversationIdOrderByIdDesc(conversationId, Limit.of(2));
        boolean retryable = ChatMessage.ROLE_ASSISTANT.equals(failed.getRole())
                && (ChatMessage.STATUS_ERROR.equals(failed.getStatus()) || isStalePending(failed, now))
                && latest.size() == 2 && latest.get(0).getId().equals(failed.getId())
                && ChatMessage.ROLE_USER.equals(latest.get(1).getRole());
        if (!retryable) {
            throw ApiException.conflict("Only the latest failed reply can be retried");
        }
        // The failed row is replaced, so the stale PENDING check and the rate limit both see the new attempt.
        messageRepository.delete(failed);
        messageRepository.flush();
        enforceQuota(user, owned.conversation(), now);
        ChatMessage question = latest.get(1);
        ChatMessage placeholder = messageRepository.saveAndFlush(placeholder(conversationId, user.id()));
        ChatConversation conversation = owned.conversation();
        conversation.setUpdatedAt(now);
        conversationRepository.saveAndFlush(conversation);
        return new Prepared(conversationId, owned.role(), question.getId(), question.getContent(), question.isSuspicious(),
                question.getRedactionCount(), placeholder.getId(), toTurns(history(conversationId, question.getId())));
    }

    private void enforceQuota(CurrentUser user, ChatConversation conversation, Instant now) {
        try {
            rateLimiter.checkUserQuota(user.id(), now);
        } catch (ApiException exception) {
            auditService.record(user, "CHAT_RATE_LIMITED", "CHAT_CONVERSATION", conversation.getId(),
                    conversation.getProjectId(), exception.status().value() + "");
            throw exception;
        }
    }

    /** Retrieval and the model call run without a transaction; the result is written in a short one. */
    private SendMessageResponse complete(CurrentUser user, Prepared prepared) {
        long started = System.currentTimeMillis();
        Instant deadline = Instant.now().plusMillis(properties.getTimeoutMs());
        Outcome outcome;
        try {
            outcome = answer(user, prepared, deadline);
        } catch (AgentException exception) {
            LOGGER.warn("Chat reply failed: conversation={} category={}", prepared.conversationId(), exception.category());
            outcome = Outcome.failure(exception.category().name(), failureMessage(exception));
        } catch (ApiException exception) {
            outcome = Outcome.failure("ACCESS_DENIED", "You no longer have access to this project or its context.");
        } catch (RuntimeException exception) {
            LOGGER.warn("Chat reply failed unexpectedly: conversation={} type={}", prepared.conversationId(),
                    exception.getClass().getSimpleName());
            outcome = Outcome.failure("INTERNAL", "The assistant failed unexpectedly. Retry the message.");
        }
        long duration = System.currentTimeMillis() - started;
        Outcome finalOutcome = outcome;
        SendMessageResponse response = transactionTemplate.execute(status -> persistReply(user, prepared, finalOutcome, duration));
        if (response == null) {
            // Deleted conversation or revoked membership: the reply row is committed but never shown.
            throw conversationNotFound();
        }
        return response;
    }

    private Outcome answer(CurrentUser user, Prepared prepared, Instant deadline) {
        // Re-validate everything against the database; nothing from the first transaction is trusted blindly.
        Owned owned = requireConversation(user, prepared.conversationId());
        ChatConversation conversation = owned.conversation();
        List<String> warnings = new ArrayList<>();
        ResolvedContext context = resolveContext(user, conversation, warnings);
        if (prepared.redactionCount() > 0) {
            warnings.add(prepared.redactionCount() + " secret-like value(s) were redacted from your message before analysis.");
        }
        if (prepared.suspicious()) {
            warnings.add("Your message contains instruction-like text; it was treated only as a question.");
        }

        List<EvidenceItem> evidence = new ArrayList<>();
        if (context.defect() != null) {
            evidence.add(defectEvidence(context.defect()));
            evidence.addAll(investigationEvidence(context.defect()));
        }
        String retrievalQuery = retrievalQuery(prepared, context);
        var knowledge = projectContextAgent.run(new ProjectContextAgent.Input(conversation.getProjectId(),
                context.component() == null ? null : context.component().getId(), user, retrievalQuery), deadline);
        evidence.addAll(knowledge.output().knowledgeItems());
        if (knowledge.output().knowledgeItems().stream().anyMatch(EvidenceItem::suspicious)) {
            warnings.add("Some retrieved passages contain instruction-like text and were treated only as data.");
        }

        Map<String, Object> promptContext = new LinkedHashMap<>();
        promptContext.put("project", context.project().getName());
        if (context.component() != null) {
            promptContext.put("component", context.component().getName());
        }
        if (context.defect() != null) {
            promptContext.put("attachedDefect", defectKey(context.defect().getId()) + " (evidence D1)");
        }
        promptContext.put("knowledgeRetrievalMode", knowledge.output().retrievalMode());
        promptContext.put("knowledgePassagesFound", knowledge.output().knowledgeItems().size());

        ChatAssistantAgent.Reply reply = assistantAgent.answer(new ChatAssistantAgent.Request(promptContext,
                prepared.history(), prepared.question(), prepared.suspicious(), evidence), deadline);
        warnings.addAll(reply.warnings());
        Map<String, EvidenceItem> byId = evidence.stream()
                .collect(Collectors.toMap(EvidenceItem::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<CitationResponse> citations = reply.citedEvidenceIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ChatService::snapshot)
                .toList();
        return new Outcome(true, reply.answer(), reply.answerStatus(), citations,
                new Details(reply.clarifyingQuestion(), reply.missingInformation(), reply.followUpQuestions(), warnings),
                null);
    }

    private SendMessageResponse persistReply(CurrentUser user, Prepared prepared, Outcome outcome, long durationMs) {
        ChatMessage message = messageRepository.findById(prepared.assistantMessageId())
                .orElseThrow(ChatService::conversationNotFound);
        ChatConversation conversation = conversationRepository.findById(prepared.conversationId())
                .orElseThrow(ChatService::conversationNotFound);
        if (conversation.getDeletedAt() != null) {
            // Deleted while the model was answering: keep the row for rate limiting but store nothing.
            message.setStatus(ChatMessage.STATUS_COMPLETE);
            message.setContent("");
            message.setCompletedAt(Instant.now());
            messageRepository.saveAndFlush(message);
            return null;
        }
        message.setStatus(outcome.success() ? ChatMessage.STATUS_COMPLETE : ChatMessage.STATUS_ERROR);
        message.setContent(outcome.content());
        message.setAnswerStatus(outcome.answerStatus());
        message.setCitationsJson(outcome.citations().isEmpty() ? null : write(outcome.citations()));
        message.setDetailsJson(write(outcome.details()));
        message.setErrorCategory(outcome.errorCategory());
        message.setEngine(truncate(assistantAgent.engine(), 16));
        message.setModel(truncate(assistantAgent.model(), 64));
        message.setDurationMs(durationMs);
        message.setCompletedAt(Instant.now());
        messageRepository.saveAndFlush(message);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);

        auditService.record(user, "CHAT_MESSAGE", "CHAT_CONVERSATION", conversation.getId(), conversation.getProjectId(),
                "status=" + (outcome.success() ? outcome.answerStatus() : "ERROR:" + outcome.errorCategory())
                        + " citations=" + outcome.citations().size() + " redactions=" + prepared.redactionCount()
                        + " suspicious=" + prepared.suspicious() + " durationMs=" + durationMs);

        // Membership may have been revoked during the call; never return content to a non-member.
        Optional<ProjectRole> role = accessControl.roleFor(user, conversation.getProjectId());
        if (role.isEmpty()) {
            return null;
        }
        Names names = names(List.of(conversation));
        CitationAvailability availability = availability(conversation, List.of(message));
        ChatMessage userMessage = messageRepository.findById(prepared.userMessageId()).orElse(null);
        return new SendMessageResponse(
                toSummary(conversation, names),
                userMessage == null ? null : toMessage(userMessage, availability, Instant.now()),
                toMessage(message, availability, Instant.now()),
                actions(conversation, role.get()));
    }

    /** Runs left PENDING by a crash or restart can never complete; mark them as retryable errors. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        Instant cutoff = Instant.now().minusMillis(properties.getTimeoutMs()).minus(ChatRateLimiter.PENDING_GRACE);
        transactionTemplate.executeWithoutResult(status -> {
            List<ChatMessage> stale = messageRepository.findByStatusAndCreatedAtBefore(ChatMessage.STATUS_PENDING, cutoff);
            for (ChatMessage message : stale) {
                markInterrupted(message);
            }
            if (!stale.isEmpty()) {
                LOGGER.info("Marked {} interrupted chat repl(ies) as ERROR (retry eligible)", stale.size());
            }
        });
    }

    // ---------------------------------------------------------------- access and context

    private void lockUser(CurrentUser user) {
        userRepository.findByIdForUpdate(user.id()).orElseThrow(() -> ApiException.unauthorized("Sign in to continue"));
    }

    private Owned requireConversation(CurrentUser user, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, user.id())
                .orElseThrow(ChatService::conversationNotFound);
        ProjectRole role = accessControl.roleFor(user, conversation.getProjectId())
                .orElseThrow(ChatService::conversationNotFound);
        return new Owned(conversation, role);
    }

    /** Validates an optional component and defect against the project; returns the defect when one is given. */
    private Defect validateContext(CurrentUser user, Long projectId, Long componentId, Long defectId) {
        if (componentId != null && componentRepository.findByIdAndProjectId(componentId, projectId).isEmpty()) {
            throw ApiException.badRequest("Component does not belong to the conversation's project");
        }
        if (defectId == null) {
            return null;
        }
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> ApiException.notFound("Defect " + defectId + " was not found"));
        accessControl.requireDefectAccess(user, defect, ProjectRole.VIEWER);
        if (defect.getProjectId() == null) {
            throw ApiException.badRequest("Defects that are not linked to a project cannot be attached to a conversation");
        }
        if (!projectId.equals(defect.getProjectId())) {
            throw ApiException.badRequest("Defect does not belong to the conversation's project");
        }
        return defect;
    }

    /**
     * Current context for a message. A component or defect that was deleted or moved to another project since it
     * was attached is ignored for this answer (with a warning) rather than leaking cross-project data.
     */
    private ResolvedContext resolveContext(CurrentUser user, ChatConversation conversation, List<String> warnings) {
        Project project = projectRepository.findById(conversation.getProjectId()).orElseThrow(ChatService::conversationNotFound);
        ProjectComponent component = null;
        if (conversation.getComponentId() != null) {
            component = componentRepository.findByIdAndProjectId(conversation.getComponentId(), conversation.getProjectId()).orElse(null);
            if (component == null) {
                warnings.add("The selected component no longer exists in this project and was ignored.");
            }
        }
        Defect defect = null;
        if (conversation.getDefectId() != null) {
            defect = defectRepository.findById(conversation.getDefectId())
                    .filter(candidate -> conversation.getProjectId().equals(candidate.getProjectId()))
                    .filter(candidate -> accessControl.canAccessDefect(user, candidate, ProjectRole.VIEWER))
                    .orElse(null);
            if (defect == null) {
                warnings.add("The attached defect is no longer available in this project and was not used.");
            }
        }
        return new ResolvedContext(project, component, defect);
    }

    // ---------------------------------------------------------------- evidence

    private EvidenceItem defectEvidence(Defect defect) {
        StringBuilder text = new StringBuilder();
        text.append("Key: ").append(defectKey(defect.getId())).append('\n');
        text.append("Title: ").append(defect.getTitle()).append('\n');
        text.append("Status: ").append(defect.getStatus()).append('\n');
        appendField(text, "Environment", defect.getEnvironment());
        appendField(text, "Service", defect.getService());
        appendField(text, "Business function", defect.getBusinessFunction());
        appendField(text, "Affected users", defect.getAffectedUsers());
        appendField(text, "Customer impact", defect.getCustomerImpact());
        appendField(text, "Technical impact", defect.getTechnicalImpact());
        text.append("Description:\n").append(defect.getDescription());
        String redacted = secretRedactor.redact(text.toString()).text();
        boolean truncated = redacted.length() > DEFECT_EVIDENCE_MAX;
        String content = truncated ? redacted.substring(0, DEFECT_EVIDENCE_MAX) : redacted;
        return new EvidenceItem("D1", "DEFECT_REPORT", defectKey(defect.getId()) + " " + truncate(defect.getTitle(), 100),
                content, new SourceRef(SOURCE_DEFECT, defect.getId(), null, null, null, null, null, null, null, null),
                injectionDetector.isSuspicious(content), truncated);
    }

    /** Hypotheses from the latest finished investigation, labelled with their developer review state. */
    private List<EvidenceItem> investigationEvidence(Defect defect) {
        Optional<Investigation> latest = investigationRepository.findByDefectIdOrderByCreatedAtDescIdDesc(defect.getId()).stream()
                .filter(run -> run.getStatus() == InvestigationStatus.COMPLETED || run.getStatus() == InvestigationStatus.PARTIAL)
                .filter(run -> run.getReportJson() != null)
                .findFirst();
        if (latest.isEmpty()) {
            return List.of();
        }
        Investigation run = latest.get();
        JsonNode hypotheses;
        try {
            hypotheses = objectMapper.readTree(run.getReportJson()).path("hypotheses");
        } catch (JsonProcessingException exception) {
            return List.of();
        }
        Map<Integer, FeedbackVerdict> verdicts = new HashMap<>();
        for (InvestigationFeedback feedback : feedbackRepository.findByInvestigationIdOrderByCreatedAtDescIdDesc(run.getId())) {
            if (feedback.getHypothesisRank() != null
                    && (feedback.getVerdict() == FeedbackVerdict.CONFIRMED || feedback.getVerdict() == FeedbackVerdict.REJECTED)) {
                verdicts.putIfAbsent(feedback.getHypothesisRank(), feedback.getVerdict());
            }
        }
        List<EvidenceItem> items = new ArrayList<>();
        for (JsonNode hypothesis : hypotheses) {
            if (items.size() >= MAX_INVESTIGATION_FINDINGS) {
                break;
            }
            int rank = hypothesis.path("rank").asInt(items.size() + 1);
            FeedbackVerdict verdict = verdicts.get(rank);
            String review = verdict == null ? "Not reviewed: unconfirmed hypothesis"
                    : verdict == FeedbackVerdict.CONFIRMED ? "Confirmed by a developer" : "Rejected by a developer";
            String content = "Investigation #" + run.getId() + " (" + run.getStatus() + "), hypothesis " + rank + ". Review: "
                    + review + ". Likelihood " + hypothesis.path("likelihood").asText() + ", confidence "
                    + hypothesis.path("confidence").asInt() + "%.\nTitle: " + hypothesis.path("title").asText()
                    + "\nDescription: " + hypothesis.path("description").asText()
                    + "\nReasoning: " + hypothesis.path("reasoning").asText();
            String redacted = secretRedactor.redact(truncate(content, DEFECT_EVIDENCE_MAX)).text();
            items.add(new EvidenceItem("I" + (items.size() + 1), "INVESTIGATION_FINDING",
                    "Investigation #" + run.getId() + " hypothesis " + rank,
                    redacted, new SourceRef(SOURCE_INVESTIGATION, run.getId(), null, null, null, null, null, null, null, null),
                    injectionDetector.isSuspicious(redacted), false));
        }
        return items;
    }

    private String retrievalQuery(Prepared prepared, ResolvedContext context) {
        StringBuilder query = new StringBuilder(prepared.question());
        // Follow-ups ("why does that happen?") need the previous question to retrieve the right passages.
        for (int index = prepared.history().size() - 1; index >= 0; index--) {
            ChatAssistantAgent.HistoryTurn turn = prepared.history().get(index);
            if (ChatMessage.ROLE_USER.equals(turn.role())) {
                query.append('\n').append(turn.content());
                break;
            }
        }
        if (context.defect() != null) {
            query.append('\n').append(context.defect().getTitle());
        }
        if (context.component() != null) {
            query.append('\n').append(context.component().getName());
        }
        return truncate(query.toString(), RETRIEVAL_QUERY_MAX);
    }

    private static CitationResponse snapshot(EvidenceItem item) {
        SourceRef source = item.source();
        CitationSource citationSource = source == null ? null : new CitationSource(
                source.type(),
                source.documentId(),
                source.versionNumber(),
                source.filename(),
                source.chunkId(),
                source.pageNumber(),
                source.startLine(),
                source.endLine(),
                SOURCE_DEFECT.equals(source.type()) ? source.evidenceId() : null,
                SOURCE_INVESTIGATION.equals(source.type()) ? source.evidenceId() : null);
        return new CitationResponse(item.id(), item.kind(), item.label(), truncate(item.content(), EXCERPT_MAX), true,
                item.suspicious(), citationSource);
    }

    // ---------------------------------------------------------------- views

    private ConversationDetail detail(CurrentUser user, ChatConversation conversation, ProjectRole role) {
        Names names = names(List.of(conversation));
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
        CitationAvailability availability = availability(conversation, messages);
        Instant now = Instant.now();
        List<MessageResponse> views = messages.stream().map(message -> toMessage(message, availability, now)).toList();
        Defect defect = attachedDefect(user, conversation);
        Investigation latest = defect == null ? null
                : investigationRepository.findByDefectIdOrderByCreatedAtDescIdDesc(defect.getId()).stream().findFirst().orElse(null);
        boolean active = latest != null && InvestigationStatus.ACTIVE.contains(latest.getStatus());
        ContextInfo context = new ContextInfo(role.name(), defect != null, defect == null ? null : defect.getStatus().name(),
                active, latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus().name());
        List<String> suggestions = messages.isEmpty()
                ? suggestedQuestions(conversation.getProjectId(), conversation.getComponentId(), defect)
                : List.of();
        return new ConversationDetail(toSummary(conversation, names), context, views, actions(conversation, role),
                suggestions, limits());
    }

    private Defect attachedDefect(CurrentUser user, ChatConversation conversation) {
        if (conversation.getDefectId() == null) {
            return null;
        }
        return defectRepository.findById(conversation.getDefectId())
                .filter(defect -> conversation.getProjectId().equals(defect.getProjectId()))
                .filter(defect -> accessControl.canAccessDefect(user, defect, ProjectRole.VIEWER))
                .orElse(null);
    }

    /**
     * The only action the chatbot offers. It is computed by the server from the user's current role and the
     * defect's run state; the client must still confirm, and the investigation API re-checks everything.
     */
    private List<ActionResponse> actions(ChatConversation conversation, ProjectRole role) {
        if (conversation.getDefectId() == null) {
            return List.of();
        }
        Defect defect = defectRepository.findById(conversation.getDefectId())
                .filter(candidate -> conversation.getProjectId().equals(candidate.getProjectId()))
                .orElse(null);
        if (defect == null) {
            return List.of();
        }
        // Only the reporter or an administrator can attach a defect, and both may investigate it.
        boolean active = investigationRepository.existsByDefectIdAndStatusIn(defect.getId(), InvestigationStatus.ACTIVE);
        String reason = active ? "An investigation is already running for this defect."
                : "Runs the existing multi-agent investigation for " + defectKey(defect.getId()) + ". It uses AI quota.";
        return List.of(new ActionResponse(com.impactiq.dto.ChatDtos.ACTION_START_INVESTIGATION, !active,
                "Start investigation", reason, defect.getId()));
    }

    private List<String> suggestedQuestions(Long projectId, Long componentId, Defect defect) {
        List<String> questions = new ArrayList<>();
        String project = projectRepository.findById(projectId).map(Project::getName).map(name -> truncate(name, NAME_IN_SUGGESTION_MAX)).orElse("this project");
        String component = componentId == null ? null : componentRepository.findByIdAndProjectId(componentId, projectId)
                .map(ProjectComponent::getName).map(name -> truncate(name, NAME_IN_SUGGESTION_MAX)).orElse(null);
        boolean hasKnowledge = chunkRepository.countByProjectId(projectId) > 0;
        if (defect != null) {
            String key = defectKey(defect.getId());
            questions.add("What evidence exists for " + key + "?");
            questions.add("What could cause " + key + ", and what information is still missing?");
            boolean investigated = investigationRepository.findByDefectIdOrderByCreatedAtDescIdDesc(defect.getId()).stream()
                    .anyMatch(run -> run.getStatus() == InvestigationStatus.COMPLETED || run.getStatus() == InvestigationStatus.PARTIAL);
            if (investigated) {
                questions.add("Summarize the previous investigation findings for " + key + ".");
            }
        }
        if (component != null) {
            questions.add("Explain the " + component + " component's responsibilities.");
        }
        if (hasKnowledge) {
            questions.add("Explain the architecture of " + project + ".");
            questions.add("How does authentication work in " + project + "?");
            questions.add("Which documented APIs are relevant to " + (component == null ? project : component) + "?");
        } else {
            questions.add("What project knowledge is available for " + project + "?");
        }
        return questions.stream().distinct().limit(6).toList();
    }

    private ConversationSummary toSummary(ChatConversation conversation, Names names) {
        Defect defect = conversation.getDefectId() == null ? null : names.defects().get(conversation.getDefectId());
        boolean defectInProject = defect != null && conversation.getProjectId().equals(defect.getProjectId());
        ProjectComponent component = conversation.getComponentId() == null ? null : names.components().get(conversation.getComponentId());
        boolean componentInProject = component != null && conversation.getProjectId().equals(component.getProjectId());
        Project project = names.projects().get(conversation.getProjectId());
        return new ConversationSummary(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getProjectId(),
                project == null ? null : project.getName(),
                componentInProject ? component.getId() : null,
                componentInProject ? component.getName() : null,
                defectInProject ? defect.getId() : null,
                defectInProject ? defect.getTitle() : null,
                conversation.getMessageCount(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    private MessageResponse toMessage(ChatMessage message, CitationAvailability availability, Instant now) {
        Details details = read(message.getDetailsJson(), Details.class).orElse(Details.EMPTY);
        List<CitationResponse> citations = readCitations(message.getCitationsJson()).stream()
                .map(availability::apply)
                .toList();
        String status = message.getStatus();
        String content = message.getContent();
        String errorCategory = message.getErrorCategory();
        if (isStalePending(message, now)) {
            status = ChatMessage.STATUS_ERROR;
            content = "The reply was interrupted. Retry the message.";
            errorCategory = "INTERRUPTED";
        }
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                status,
                content,
                message.getAnswerStatus(),
                citations,
                details.clarifyingQuestion(),
                details.missingInformation(),
                details.followUpQuestions(),
                details.warnings(),
                message.getRedactionCount(),
                message.isSuspicious(),
                errorCategory,
                message.getDurationMs(),
                message.getCreatedAt(),
                message.getCompletedAt());
    }

    /** Citations are re-served only if their source still exists in the conversation's project. */
    private CitationAvailability availability(ChatConversation conversation, Collection<ChatMessage> messages) {
        Set<Long> chunkIds = new HashSet<>();
        Set<Long> investigationIds = new HashSet<>();
        Set<Long> defectIds = new HashSet<>();
        for (ChatMessage message : messages) {
            for (CitationResponse citation : readCitations(message.getCitationsJson())) {
                CitationSource source = citation.source();
                if (source == null) {
                    continue;
                }
                if (SOURCE_CHUNK.equals(source.type()) && source.chunkId() != null) {
                    chunkIds.add(source.chunkId());
                } else if (SOURCE_INVESTIGATION.equals(source.type()) && source.investigationId() != null) {
                    investigationIds.add(source.investigationId());
                } else if (SOURCE_DEFECT.equals(source.type()) && source.defectId() != null) {
                    defectIds.add(source.defectId());
                }
            }
        }
        Long projectId = conversation.getProjectId();
        Set<Long> liveChunks = chunkIds.isEmpty() ? Set.of() : chunkRepository.findAllById(chunkIds).stream()
                .filter(chunk -> projectId.equals(chunk.getProjectId())).map(DocumentChunk::getId).collect(Collectors.toSet());
        Set<Long> liveInvestigations = investigationIds.isEmpty() ? Set.of() : investigationRepository.findAllById(investigationIds)
                .stream().filter(run -> projectId.equals(run.getProjectId())).map(Investigation::getId).collect(Collectors.toSet());
        Set<Long> liveDefects = defectIds.isEmpty() ? Set.of() : defectRepository.findAllById(defectIds).stream()
                .filter(defect -> projectId.equals(defect.getProjectId())).map(Defect::getId).collect(Collectors.toSet());
        return new CitationAvailability(liveChunks, liveInvestigations, liveDefects);
    }

    private Names names(Collection<ChatConversation> conversations) {
        Set<Long> projectIds = new HashSet<>();
        Set<Long> componentIds = new HashSet<>();
        Set<Long> defectIds = new HashSet<>();
        for (ChatConversation conversation : conversations) {
            projectIds.add(conversation.getProjectId());
            if (conversation.getComponentId() != null) {
                componentIds.add(conversation.getComponentId());
            }
            if (conversation.getDefectId() != null) {
                defectIds.add(conversation.getDefectId());
            }
        }
        return new Names(
                index(projectIds.isEmpty() ? List.of() : projectRepository.findAllById(projectIds), Project::getId),
                index(componentIds.isEmpty() ? List.of() : componentRepository.findAllById(componentIds), ProjectComponent::getId),
                index(defectIds.isEmpty() ? List.of() : defectRepository.findAllById(defectIds), Defect::getId));
    }

    private ChatLimits limits() {
        return new ChatLimits(properties.getMaxMessageChars(), properties.getRateLimitPerMinute(), properties.getHistoryTurns());
    }

    // ---------------------------------------------------------------- helpers

    /** Earlier completed turns, oldest first, excluding {@code beforeMessageId} and anything after it. */
    private List<ChatMessage> history(Long conversationId, Long beforeMessageId) {
        int turns = Math.max(0, properties.getHistoryTurns());
        if (turns == 0) {
            return List.of();
        }
        // Fetch a little more than needed so ERROR rows and the excluded tail do not shrink the window.
        List<ChatMessage> recent = new ArrayList<>(messageRepository.findByConversationIdOrderByIdDesc(conversationId,
                Limit.of(turns * 2 + 6)));
        Collections.reverse(recent);
        List<ChatMessage> usable = recent.stream()
                .filter(message -> beforeMessageId == null || message.getId() < beforeMessageId)
                .filter(message -> ChatMessage.STATUS_COMPLETE.equals(message.getStatus()))
                .filter(message -> !message.getContent().isBlank())
                .toList();
        return usable.size() <= turns * 2 ? usable : usable.subList(usable.size() - turns * 2, usable.size());
    }

    private List<ChatAssistantAgent.HistoryTurn> toTurns(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> new ChatAssistantAgent.HistoryTurn(message.getRole(),
                        truncate(message.getContent(), properties.getMaxHistoryMessageChars())))
                .toList();
    }

    private ChatMessage placeholder(Long conversationId, Long userId) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setOwnerUserId(userId);
        message.setRole(ChatMessage.ROLE_ASSISTANT);
        message.setStatus(ChatMessage.STATUS_PENDING);
        message.setContent("");
        return message;
    }

    private boolean isStalePending(ChatMessage message, Instant now) {
        return ChatMessage.STATUS_PENDING.equals(message.getStatus()) && message.getCreatedAt() != null
                && message.getCreatedAt().isBefore(now.minusMillis(properties.getTimeoutMs()).minus(ChatRateLimiter.PENDING_GRACE));
    }

    private void markInterrupted(ChatMessage message) {
        message.setStatus(ChatMessage.STATUS_ERROR);
        message.setErrorCategory("INTERRUPTED");
        message.setContent("The reply was interrupted (server restart or timeout). Retry the message.");
        message.setCompletedAt(Instant.now());
    }

    static String failureMessage(AgentException exception) {
        return switch (exception.category()) {
            case MISSING_API_KEY -> "The AI assistant is not configured on the server (missing Gemini API key).";
            case AUTH_FAILED -> "The AI provider rejected the server's credentials. Ask an administrator to check them.";
            case RATE_LIMITED -> "The AI provider's rate limit was reached. Retry in a minute.";
            case MODEL_UNAVAILABLE -> "The AI model is temporarily unavailable. Retry shortly.";
            case MODEL_TIMEOUT, RUN_TIMEOUT -> "The AI model did not respond in time. Retry the message.";
            case INVALID_OUTPUT -> "The AI response could not be validated. Retry the message.";
            case ACCESS_DENIED -> "You no longer have access to this project.";
            case INTERNAL -> "The assistant failed unexpectedly. Retry the message.";
        };
    }

    private String autoTitle(String text) {
        String firstLine = text.strip().split("\n", 2)[0].strip();
        return firstLine.isEmpty() ? DEFAULT_TITLE : truncate(firstLine, AUTO_TITLE_MAX);
    }

    private static String contextDetail(ChatConversation conversation) {
        return "component=" + conversation.getComponentId() + " defect=" + conversation.getDefectId();
    }

    static String defectKey(Long id) {
        return "IQ-" + String.format("%04d", id);
    }

    private static void appendField(StringBuilder text, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            text.append(label).append(": ").append(value).append('\n');
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chat payload could not be serialised", exception);
        }
    }

    private <T> Optional<T> read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private List<CitationResponse> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CITATION_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (first, second) -> first));
    }

    private static ApiException conversationNotFound() {
        return ApiException.notFound("Conversation was not found");
    }

    // ---------------------------------------------------------------- records

    private record Owned(ChatConversation conversation, ProjectRole role) {
    }

    private record ResolvedContext(Project project, ProjectComponent component, Defect defect) {
    }

    private record Names(Map<Long, Project> projects, Map<Long, ProjectComponent> components, Map<Long, Defect> defects) {
    }

    record Prepared(
            Long conversationId,
            ProjectRole role,
            Long userMessageId,
            String question,
            boolean suspicious,
            int redactionCount,
            Long assistantMessageId,
            List<ChatAssistantAgent.HistoryTurn> history
    ) {
    }

    record Details(String clarifyingQuestion, List<String> missingInformation, List<String> followUpQuestions,
                   List<String> warnings) {
        static final Details EMPTY = new Details("", List.of(), List.of(), List.of());
    }

    private record Outcome(boolean success, String content, String answerStatus, List<CitationResponse> citations,
                           Details details, String errorCategory) {
        static Outcome failure(String category, String message) {
            return new Outcome(false, message, null, List.of(), Details.EMPTY, category);
        }
    }

    private record CitationAvailability(Set<Long> chunks, Set<Long> investigations, Set<Long> defects) {
        CitationResponse apply(CitationResponse citation) {
            CitationSource source = citation.source();
            boolean available = source == null
                    || (SOURCE_CHUNK.equals(source.type()) && source.chunkId() != null && chunks.contains(source.chunkId()))
                    || (SOURCE_INVESTIGATION.equals(source.type()) && investigations.contains(source.investigationId()))
                    || (SOURCE_DEFECT.equals(source.type()) && defects.contains(source.defectId()));
            return available ? citation : new CitationResponse(citation.id(), citation.kind(), citation.label(), null, false,
                    citation.suspicious(), source);
        }
    }
}
