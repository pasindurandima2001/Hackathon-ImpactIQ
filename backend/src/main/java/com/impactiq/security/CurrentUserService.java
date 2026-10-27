package com.impactiq.security;

import com.impactiq.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(principal.id(), principal.getUsername(), principal.isAdmin()));
    }

    public CurrentUser require() {
        return find().orElseThrow(() -> ApiException.unauthorized("Sign in to continue"));
    }
}
