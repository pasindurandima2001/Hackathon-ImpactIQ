package com.impactiq.controller;

import com.impactiq.dto.AuthDtos.AuthStatusResponse;
import com.impactiq.dto.AuthDtos.CsrfResponse;
import com.impactiq.dto.AuthDtos.LoginRequest;
import com.impactiq.dto.AuthDtos.RegisterRequest;
import com.impactiq.dto.AuthDtos.UserResponse;
import com.impactiq.exception.ApiException;
import com.impactiq.security.AppUserPrincipal;
import com.impactiq.security.CurrentUser;
import com.impactiq.security.LoginAttemptService;
import com.impactiq.service.AuditService;
import com.impactiq.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            LoginAttemptService loginAttemptService,
            AuthService authService,
            AuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginAttemptService = loginAttemptService;
        this.authService = authService;
        this.auditService = auditService;
    }

    /** Reading the token makes the CSRF filter write the XSRF-TOKEN cookie. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public AuthStatusResponse me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return new AuthStatusResponse(true, authService.toResponse(principal), authService.registrationEnabled());
        }
        return new AuthStatusResponse(false, null, authService.registrationEnabled());
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String username = request.username().trim();
        if (loginAttemptService.isLocked(username)) {
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Try again in a few minutes.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(username);
            auditService.record(null, "LOGIN_FAILED", "USER", null, null, "username=" + username);
            throw ApiException.unauthorized("Invalid username or password");
        }
        loginAttemptService.recordSuccess(username);

        // New session id prevents session fixation; a fresh CSRF token prevents token fixation.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        auditService.record(new CurrentUser(principal.id(), principal.getUsername(), principal.isAdmin()),
                "LOGIN", "USER", principal.id(), null, null);
        return authService.toResponse(principal);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
