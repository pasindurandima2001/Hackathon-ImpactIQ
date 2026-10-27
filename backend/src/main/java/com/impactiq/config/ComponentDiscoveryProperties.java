package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI component discovery from indexed project knowledge. */
@Component
@ConfigurationProperties(prefix = "impactiq.components")
public class ComponentDiscoveryProperties {

    /** Runs discovery automatically after each document version is indexed. */
    private boolean autoDiscover = true;
    /** Most components one discovery run may add. */
    private int maxPerRun = 8;
    /** Discovery stops adding once a project has this many components. */
    private int maxPerProject = 40;
    /** Document text sent to the model per run, split across the selected documents. */
    private int excerptChars = 16_000;
    /** Most recently updated indexed documents read by a project-wide run. */
    private int maxDocuments = 6;
    private long timeoutMs = 60_000;

    public boolean isAutoDiscover() {
        return autoDiscover;
    }

    public void setAutoDiscover(boolean autoDiscover) {
        this.autoDiscover = autoDiscover;
    }

    public int getMaxPerRun() {
        return maxPerRun;
    }

    public void setMaxPerRun(int maxPerRun) {
        this.maxPerRun = maxPerRun;
    }

    public int getMaxPerProject() {
        return maxPerProject;
    }

    public void setMaxPerProject(int maxPerProject) {
        this.maxPerProject = maxPerProject;
    }

    public int getExcerptChars() {
        return excerptChars;
    }

    public void setExcerptChars(int excerptChars) {
        this.excerptChars = excerptChars;
    }

    public int getMaxDocuments() {
        return maxDocuments;
    }

    public void setMaxDocuments(int maxDocuments) {
        this.maxDocuments = maxDocuments;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
