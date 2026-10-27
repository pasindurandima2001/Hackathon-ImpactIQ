package com.impactiq.service;

import com.impactiq.config.AffectedUsersScoringProperties;
import com.impactiq.dto.DefectRequest;
import com.impactiq.dto.DefectResponse;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.exception.DefectNotFoundException;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.ImpactAnalysisRepository;
import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.InvestigationStepRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import com.impactiq.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefectServiceTest {

    private DefectRepository defectRepository;
    private ImpactAnalysisRepository impactAnalysisRepository;
    private DefectRelations relations;
    private DefectService defectService;

    @BeforeEach
    void setUp() {
        defectRepository = mock(DefectRepository.class);
        impactAnalysisRepository = mock(ImpactAnalysisRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        CurrentUser user = new CurrentUser(1L, "developer", false);
        when(currentUser.require()).thenReturn(user);
        when(currentUser.find()).thenReturn(Optional.of(user));
        AccessControlService accessControl = mock(AccessControlService.class);
        when(accessControl.canAccessDefect(any(), any(), any())).thenReturn(true);
        relations = new DefectRelations(currentUser, accessControl, mock(ProjectRepository.class),
                mock(ProjectComponentRepository.class), mock(DefectEvidenceRepository.class), mock(InvestigationRepository.class),
                mock(InvestigationStepRepository.class), mock(InvestigationFeedbackRepository.class), mock(AuditService.class));
        defectService = new DefectService(defectRepository, impactAnalysisRepository,
                new ImpactScoringService(new AffectedUsersScoringProperties()), relations);
    }

    @Test
    void createMapsRequestAndDefaultsStatusToOpen() {
        when(defectRepository.saveAndFlush(any(Defect.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        defectService.create(request(null));

        ArgumentCaptor<Defect> captor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepository).saveAndFlush(captor.capture());
        assertEquals("Checkout fails", captor.getValue().getTitle());
        assertEquals(125, captor.getValue().getAffectedUsers());
        assertEquals(DefectStatus.OPEN, captor.getValue().getStatus());
        assertEquals(Environment.PRODUCTION, captor.getValue().getEnvironment());
    }

    @Test
    void createPreservesExplicitStatus() {
        when(defectRepository.saveAndFlush(any(Defect.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        defectService.create(request(DefectStatus.IN_PROGRESS));

        ArgumentCaptor<Defect> captor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepository).saveAndFlush(captor.capture());
        assertEquals(DefectStatus.IN_PROGRESS, captor.getValue().getStatus());
    }

    @Test
    void findAllReturnsTheDefectsTheUserReported() {
        Defect first = entity("First defect");
        Defect second = entity("Second defect");
        when(defectRepository.findByCreatedBy(1L)).thenReturn(List.of(first, second));

        List<DefectResponse> result = defectService.findAll();

        assertEquals(List.of("First defect", "Second defect"),
                result.stream().map(DefectResponse::title).toList());
    }

    @Test
    void findByIdReturnsResponse() {
        when(defectRepository.findById(7L)).thenReturn(Optional.of(entity("Existing defect")));

        DefectResponse result = defectService.findById(7L);

        assertEquals("Existing defect", result.title());
    }

    @Test
    void responseIncludesDeterministicBaselineScore() {
        Defect defect = entity("Scored defect");
        when(defectRepository.findById(7L)).thenReturn(Optional.of(defect));
        ImpactScoreResult expected = new ImpactScoringService(new AffectedUsersScoringProperties()).calculate(defect);

        DefectResponse result = defectService.findById(7L);

        assertEquals(expected.totalScore(), result.baselineScore());
        assertEquals(expected.riskLevel(), result.baselineRiskLevel());
        assertEquals(expected.priority(), result.baselinePriority());
    }

    @Test
    void updateFindsEntityAppliesRequestAndSaves() {
        Defect existing = entity("Old title");
        when(defectRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(defectRepository.saveAndFlush(existing)).thenReturn(existing);

        defectService.update(7L, request(DefectStatus.RESOLVED));

        assertEquals("Checkout fails", existing.getTitle());
        assertEquals(DefectStatus.RESOLVED, existing.getStatus());
        verify(defectRepository).saveAndFlush(existing);
    }

    @Test
    void updateResponseRecalculatesDeterministicBaselineFromNewValues() {
        Defect existing = entity("Old title");
        when(defectRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(defectRepository.saveAndFlush(existing)).thenReturn(existing);
        ImpactScoringService scoring = new ImpactScoringService(new AffectedUsersScoringProperties());
        int before = scoring.calculate(entity("Old title")).totalScore();

        DefectResponse result = defectService.update(7L, request(DefectStatus.OPEN));

        ImpactScoreResult expected = scoring.calculate(existing);
        assertEquals(expected.totalScore(), result.baselineScore());
        assertEquals(expected.riskLevel(), result.baselineRiskLevel());
        assertEquals(expected.priority(), result.baselinePriority());
        assertTrue(result.baselineScore() > before);
    }

    @Test
    void deleteRemovesAnalysisHistoryBeforeDeletingEntity() {
        Defect existing = entity("Delete me");
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(defectRepository.findById(7L)).thenReturn(Optional.of(existing));

        defectService.delete(7L);

        InOrder order = inOrder(impactAnalysisRepository, defectRepository);
        order.verify(impactAnalysisRepository).deleteByDefectId(7L);
        order.verify(defectRepository).delete(existing);
    }

    @Test
    void missingDefectFailsRetrievalAndDoesNotDelete() {
        when(defectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(DefectNotFoundException.class, () -> defectService.findById(404L));
        assertThrows(DefectNotFoundException.class, () -> defectService.delete(404L));
        verify(defectRepository, never()).delete(any(Defect.class));
        verify(impactAnalysisRepository, never()).deleteByDefectId(any());
    }

    private DefectRequest request(DefectStatus status) {
        return new DefectRequest(
                "Checkout fails",
                "Payment submission returns an error",
                Environment.PRODUCTION,
                "checkout-api",
                "Payments",
                125,
                Frequency.HIGH,
                BusinessCriticality.CRITICAL,
                CustomerImpact.HIGH,
                TechnicalImpact.MEDIUM,
                status
        );
    }

    private Defect entity(String title) {
        Defect defect = new Defect();
        defect.setTitle(title);
        defect.setDescription("Description");
        defect.setEnvironment(Environment.PRODUCTION);
        defect.setService("checkout-api");
        defect.setBusinessFunction("Payments");
        defect.setAffectedUsers(10);
        defect.setFrequency(Frequency.LOW);
        defect.setBusinessCriticality(BusinessCriticality.LOW);
        defect.setCustomerImpact(CustomerImpact.LOW);
        defect.setTechnicalImpact(TechnicalImpact.LOW);
        defect.setStatus(DefectStatus.OPEN);
        return defect;
    }
}
