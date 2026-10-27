package com.impactiq.security;

import com.impactiq.entity.AppUser;
import com.impactiq.entity.UserRole;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/** Session principal. Holds no password after authentication (credentials are erased by the provider). */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String displayName;
    private final UserRole role;
    private final boolean enabled;
    private String passwordHash;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
        this.passwordHash = user.getPasswordHash();
    }

    public Long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
