package com.impactiq.security;

/** Minimal identity passed into services and background jobs instead of the security context. */
public record CurrentUser(Long id, String username, boolean admin) {
}
