package com.impactiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impactiq.auth")
public class AuthProperties {

    /** Self-registration. When no user exists yet, the first registered account becomes ADMIN. */
    private boolean registrationEnabled = true;
    /** Optional bootstrap admin, created once when the user table is empty. Supply via environment only. */
    private String bootstrapAdminUsername = "";
    private String bootstrapAdminPassword = "";
    private int maxFailedLogins = 5;
    private long lockoutSeconds = 300;

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public String getBootstrapAdminUsername() {
        return bootstrapAdminUsername;
    }

    public void setBootstrapAdminUsername(String bootstrapAdminUsername) {
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    public String getBootstrapAdminPassword() {
        return bootstrapAdminPassword;
    }

    public void setBootstrapAdminPassword(String bootstrapAdminPassword) {
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    public int getMaxFailedLogins() {
        return maxFailedLogins;
    }

    public void setMaxFailedLogins(int maxFailedLogins) {
        this.maxFailedLogins = maxFailedLogins;
    }

    public long getLockoutSeconds() {
        return lockoutSeconds;
    }

    public void setLockoutSeconds(long lockoutSeconds) {
        this.lockoutSeconds = lockoutSeconds;
    }
}
