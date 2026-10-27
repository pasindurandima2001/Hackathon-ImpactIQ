package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "defects")
public class Defect {

    /** Maps to MariaDB MEDIUMTEXT (max 16,777,215 bytes). */
    private static final int DESCRIPTION_COLUMN_LENGTH = 16_777_215;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // MEDIUMTEXT: DESCRIPTION_MAX_LENGTH utf8mb4 chars can exceed TEXT's 65,535-byte limit.
    @Lob
    @Column(nullable = false, length = DESCRIPTION_COLUMN_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    private Environment environment;

    private String service;

    private String businessFunction;

    private Integer affectedUsers;

    @Enumerated(EnumType.STRING)
    private Frequency frequency;

    @Enumerated(EnumType.STRING)
    private BusinessCriticality businessCriticality;

    @Enumerated(EnumType.STRING)
    private CustomerImpact customerImpact;

    @Enumerated(EnumType.STRING)
    private TechnicalImpact technicalImpact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status = DefectStatus.OPEN;

    /** Null for legacy defects created before projects existed; those are visible to every signed-in user. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "component_id")
    private Long componentId;

    private Long createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Defect() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getBusinessFunction() {
        return businessFunction;
    }

    public void setBusinessFunction(String businessFunction) {
        this.businessFunction = businessFunction;
    }

    public Integer getAffectedUsers() {
        return affectedUsers;
    }

    public void setAffectedUsers(Integer affectedUsers) {
        this.affectedUsers = affectedUsers;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public BusinessCriticality getBusinessCriticality() {
        return businessCriticality;
    }

    public void setBusinessCriticality(BusinessCriticality businessCriticality) {
        this.businessCriticality = businessCriticality;
    }

    public CustomerImpact getCustomerImpact() {
        return customerImpact;
    }

    public void setCustomerImpact(CustomerImpact customerImpact) {
        this.customerImpact = customerImpact;
    }

    public TechnicalImpact getTechnicalImpact() {
        return technicalImpact;
    }

    public void setTechnicalImpact(TechnicalImpact technicalImpact) {
        this.technicalImpact = technicalImpact;
    }

    public DefectStatus getStatus() {
        return status;
    }

    public void setStatus(DefectStatus status) {
        this.status = status;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
