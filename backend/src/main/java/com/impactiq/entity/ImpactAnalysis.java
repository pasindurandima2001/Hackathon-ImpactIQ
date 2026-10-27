package com.impactiq.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "impact_analyses")
public class ImpactAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long defectId;

    @Column(nullable = false)
    private Integer impactScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImpactRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private ImpactPriority priority;

    @Column(nullable = false)
    private Integer confidence;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "impact_analysis_recommended_actions", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "action_order")
    @Column(name = "action", nullable = false, length = 1000)
    private List<String> recommendedActions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "impact_analysis_affected_areas", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "area_order")
    @Column(name = "area", nullable = false, length = 255)
    private List<String> affectedAreas = new ArrayList<>();

    @Column(nullable = false)
    private Integer baselineScore;

    @Column
    private Integer userImpactScore;

    @Column
    private Integer businessImpactScore;

    @Column
    private Integer customerImpactScore;

    @Column
    private Integer technicalImpactScore;

    @Column
    private Integer frequencyScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ImpactAnalysis() {
    }

    public Long getId() {
        return id;
    }

    public Long getDefectId() {
        return defectId;
    }

    public void setDefectId(Long defectId) {
        this.defectId = defectId;
    }

    public Integer getImpactScore() {
        return impactScore;
    }

    public void setImpactScore(Integer impactScore) {
        this.impactScore = impactScore;
    }

    public ImpactRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ImpactRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public ImpactPriority getPriority() {
        return priority;
    }

    public void setPriority(ImpactPriority priority) {
        this.priority = priority;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(List<String> recommendedActions) {
        this.recommendedActions = new ArrayList<>(recommendedActions);
    }

    public List<String> getAffectedAreas() {
        return affectedAreas;
    }

    public void setAffectedAreas(List<String> affectedAreas) {
        this.affectedAreas = new ArrayList<>(affectedAreas);
    }

    public Integer getBaselineScore() {
        return baselineScore;
    }

    public void setBaselineScore(Integer baselineScore) {
        this.baselineScore = baselineScore;
    }

    public Integer getUserImpactScore() {
        return userImpactScore;
    }

    public void setUserImpactScore(Integer userImpactScore) {
        this.userImpactScore = userImpactScore;
    }

    public Integer getBusinessImpactScore() {
        return businessImpactScore;
    }

    public void setBusinessImpactScore(Integer businessImpactScore) {
        this.businessImpactScore = businessImpactScore;
    }

    public Integer getCustomerImpactScore() {
        return customerImpactScore;
    }

    public void setCustomerImpactScore(Integer customerImpactScore) {
        this.customerImpactScore = customerImpactScore;
    }

    public Integer getTechnicalImpactScore() {
        return technicalImpactScore;
    }

    public void setTechnicalImpactScore(Integer technicalImpactScore) {
        this.technicalImpactScore = technicalImpactScore;
    }

    public Integer getFrequencyScore() {
        return frequencyScore;
    }

    public void setFrequencyScore(Integer frequencyScore) {
        this.frequencyScore = frequencyScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
