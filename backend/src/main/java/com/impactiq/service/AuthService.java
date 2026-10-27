package com.impactiq.service;

import com.impactiq.config.AuthProperties;
import com.impactiq.dto.AuthDtos.RegisterRequest;
import com.impactiq.dto.AuthDtos.UserResponse;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.UserRole;
import com.impactiq.exception.ApiException;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.security.AppUserPrincipal;
import com.impactiq.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final int MIN_BOOTSTRAP_PASSWORD_LENGTH = 12;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final AuditService auditService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    /** Synchronized so two concurrent first registrations cannot both become ADMIN on this node. */
    @Transactional
    public synchronized UserResponse register(RegisterRequest request) {
        if (!properties.isRegistrationEnabled()) {
            throw ApiException.forbidden("Self-registration is disabled");
        }
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("That username is already taken");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(blankToNull(request.displayName()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(userRepository.count() == 0 ? UserRole.ADMIN : UserRole.USER);
        AppUser saved = userRepository.saveAndFlush(user);
        auditService.record(new CurrentUser(saved.getId(), saved.getUsername(), saved.getRole() == UserRole.ADMIN),
                "USER_REGISTERED", "USER", saved.getId(), null, "role=" + saved.getRole());
        return toResponse(saved);
    }

    public UserResponse toResponse(AppUserPrincipal principal) {
        return new UserResponse(principal.id(), principal.getUsername(), principal.displayName(), principal.role().name());
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
    }

    public boolean registrationEnabled() {
        return properties.isRegistrationEnabled();
    }

    /** Creates the configured bootstrap admin once, only when no users exist. Credentials come from the environment. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmin() {
        String username = properties.getBootstrapAdminUsername();
        String password = properties.getBootstrapAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (userRepository.count() > 0) {
            return;
        }
        if (password.length() < MIN_BOOTSTRAP_PASSWORD_LENGTH) {
            LOGGER.warn("Bootstrap admin was not created: password must be at least {} characters",
                    MIN_BOOTSTRAP_PASSWORD_LENGTH);
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username.trim());
        admin.setDisplayName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);
        LOGGER.info("Bootstrap admin account created for username={}", admin.getUsername());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
