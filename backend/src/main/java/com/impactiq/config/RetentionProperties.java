package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impactiq.retention")
public class RetentionProperties {

    private int auditDays = 365;
    /** Stored files of superseded (non-active) document versions are deleted after this many days; metadata is kept. */
    private int supersededFileDays = 90;

    public int getAuditDays() {
        return auditDays;
    }

    public void setAuditDays(int auditDays) {
        this.auditDays = auditDays;
    }

    public int getSupersededFileDays() {
        return supersededFileDays;
    }

    public void setSupersededFileDays(int supersededFileDays) {
        this.supersededFileDays = supersededFileDays;
    }
}
