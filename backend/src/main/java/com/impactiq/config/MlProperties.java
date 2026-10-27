package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Optional trained triage model service (ml/serve.py). Investigations work unchanged when it is off or down. */
@Component
@ConfigurationProperties(prefix = "impactiq.ml")
public class MlProperties {

    private boolean enabled = false;
    private String url = "";
    private long timeoutMs = 2_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
