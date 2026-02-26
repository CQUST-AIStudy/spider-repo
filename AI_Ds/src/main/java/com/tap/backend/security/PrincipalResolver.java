package com.tap.backend.security;

import com.tap.backend.domain.user.UserRole;
import com.tap.backend.repo.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves a UserPrincipal, falling back to the default "teacher1" user
 * when the principal is null (unauthenticated permitAll requests).
 */
@Component
public class PrincipalResolver {
    private final UserRepository userRepository;
    private volatile UserPrincipal cachedDefault;

    public PrincipalResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserPrincipal resolve(UserPrincipal principal) {
        if (principal != null) return principal;
        if (cachedDefault != null) return cachedDefault;
        var user = userRepository.findByUsername("teacher1").orElse(null);
        if (user != null) {
            cachedDefault = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
        } else {
            cachedDefault = new UserPrincipal(1L, "teacher1", UserRole.TEACHER);
        }
        return cachedDefault;
    }
}
