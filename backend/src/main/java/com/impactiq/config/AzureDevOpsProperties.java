package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Azure DevOps work item import. The personal access token comes only from the environment (AZURE_DEVOPS_PAT);
 * it needs the "Work Items (Read)" scope and is never sent to the browser.
 */
@Component
@ConfigurationProperties(prefix = "impactiq.azure-devops")
public class AzureDevOpsProperties {

    /** API host. Links are parsed from user input, but requests only ever go to this configured host. */
    private String baseUrl = "https://dev.azure.com";
    private String pat = "";
    /** Organizations users may import from. Empty allows any organization the token can read. */
    private List<String> allowedOrganizations = new ArrayList<>();
    private long timeoutMs = 15_000;
    private int maxComments = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPat() {
        return pat;
    }

    public void setPat(String pat) {
        this.pat = pat;
    }

    public List<String> getAllowedOrganizations() {
        return allowedOrganizations;
    }

    public void setAllowedOrganizations(List<String> allowedOrganizations) {
        this.allowedOrganizations = allowedOrganizations;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxComments() {
        return maxComments;
    }

    public void setMaxComments(int maxComments) {
        this.maxComments = maxComments;
    }
}
