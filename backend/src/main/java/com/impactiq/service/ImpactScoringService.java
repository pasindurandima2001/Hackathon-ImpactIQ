package com.impactiq.service;

import com.impactiq.config.AffectedUsersScoringProperties;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Defect;
import com.impactiq.entity.TechnicalImpact;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ImpactScoringService {

    private static final int USER_WEIGHT = 30;
    private static final int BUSINESS_WEIGHT = 30;
    private static final int CUSTOMER_WEIGHT = 20;
    private static final int TECHNICAL_WEIGHT = 20;

    private final AffectedUsersScoringProperties affectedUsersRanges;

    public ImpactScoringService(AffectedUsersScoringProperties affectedUsersRanges) {
        this.affectedUsersRanges = Objects.requireNonNull(affectedUsersRanges);
        if (affectedUsersRanges.getLowMax() < 0
                || affectedUsersRanges.getMediumMax() <= affectedUsersRanges.getLowMax()
                || affectedUsersRanges.getHighMax() <= affectedUsersRanges.getMediumMax()) {
            throw new IllegalArgumentException("Affected-user thresholds must be non-negative and strictly increasing");
        }
    }

    public ImpactScoreResult calculate(Defect defect) {
        Objects.requireNonNull(defect, "defect is required");

        int userImpactScore = scoreAffectedUsers(defect.getAffectedUsers());
        int businessImpactScore = score(defect.getBusinessCriticality());
        int customerImpactScore = score(defect.getCustomerImpact());
        int technicalImpactScore = score(defect.getTechnicalImpact());

        int weightedTotal = userImpactScore * USER_WEIGHT
                + businessImpactScore * BUSINESS_WEIGHT
                + customerImpactScore * CUSTOMER_WEIGHT
                + technicalImpactScore * TECHNICAL_WEIGHT;
        int totalScore = (weightedTotal + 50) / 100;

        String riskLevel = riskLevel(totalScore);
        return new ImpactScoreResult(
                totalScore,
                userImpactScore,
                businessImpactScore,
                customerImpactScore,
                technicalImpactScore,
                riskLevel,
                priority(riskLevel)
        );
    }

    private int scoreAffectedUsers(Integer affectedUsers) {
        int count = Math.max(0, affectedUsers == null ? 0 : affectedUsers);
        if (count <= affectedUsersRanges.getLowMax()) {
            return 25;
        }
        if (count <= affectedUsersRanges.getMediumMax()) {
            return 50;
        }
        if (count <= affectedUsersRanges.getHighMax()) {
            return 75;
        }
        return 100;
    }

    private int score(BusinessCriticality value) {
        if (value == null) {
            return 0;
        }
        return switch (value) {
            case LOW -> 25;
            case MEDIUM -> 50;
            case HIGH -> 75;
            case CRITICAL -> 100;
        };
    }

    private int score(CustomerImpact value) {
        if (value == null) {
            return 0;
        }
        return switch (value) {
            case LOW -> 25;
            case MEDIUM -> 50;
            case HIGH -> 75;
            case CRITICAL -> 100;
        };
    }

    private int score(TechnicalImpact value) {
        if (value == null) {
            return 0;
        }
        return switch (value) {
            case LOW -> 25;
            case MEDIUM -> 50;
            case HIGH -> 75;
            case CRITICAL -> 100;
        };
    }

    private String riskLevel(int totalScore) {
        if (totalScore < 30) {
            return "LOW";
        }
        if (totalScore < 60) {
            return "MEDIUM";
        }
        if (totalScore < 80) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    private String priority(String riskLevel) {
        return switch (riskLevel) {
            case "LOW" -> "P3";
            case "MEDIUM" -> "P2";
            case "HIGH" -> "P1";
            case "CRITICAL" -> "P0";
            default -> throw new IllegalArgumentException("Unsupported risk level: " + riskLevel);
        };
    }
}
