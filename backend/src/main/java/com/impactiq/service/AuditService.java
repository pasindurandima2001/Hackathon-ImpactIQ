package com.impactiq.service;

import com.impactiq.entity.AuditEvent;
import com.impactiq.repository.AuditEventRepository;
import com.impactiq.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit rows in their own transaction so an audit failure never rolls back the audited action. */
@Service
public class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser actor, String action, String entityType, Long entityId, Long projectId, String detail) {
        try {
            AuditEvent event = new AuditEvent();
            if (actor != null) {
                event.setActorUserId(actor.id());
                event.setActorUsername(actor.username());
            }
            event.setAction(action);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setProjectId(projectId);
            event.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), MAX_DETAIL_LENGTH)));
            auditEventRepository.save(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Audit event could not be recorded: action={} entityType={} category={}",
                    action, entityType, exception.getClass().getSimpleName());
        }
    }
}
