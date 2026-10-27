package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impactiq.scoring.affected-users")
public class AffectedUsersScoringProperties {

    private int lowMax = 10;
    private int mediumMax = 100;
    private int highMax = 1000;

    public int getLowMax() {
        return lowMax;
    }

    public void setLowMax(int lowMax) {
        this.lowMax = lowMax;
    }

    public int getMediumMax() {
        return mediumMax;
    }

    public void setMediumMax(int mediumMax) {
        this.mediumMax = mediumMax;
    }

    public int getHighMax() {
        return highMax;
    }

    public void setHighMax(int highMax) {
        this.highMax = highMax;
    }
}
