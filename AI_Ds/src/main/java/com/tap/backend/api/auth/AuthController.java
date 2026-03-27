package com.tap.backend.api.auth;

import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.domain.user.UserRole;
import com.tap.backend.repo.UserRepository;
import com.tap.backend.security.JwtService;
import com.tap.common.api.ApiResponse;
import com.tap.common.api.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LegacySessionAccessResolver legacySessionAccessResolver;

  public AuthController(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      LegacySessionAccessResolver legacySessionAccessResolver
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.legacySessionAccessResolver = legacySessionAccessResolver;
  }

  public record LoginRequest(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 128) String password
  ) {}

  @PostMapping("/login")
  public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
    UserEntity user = userRepository.findByUsername(req.username())
        .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
    if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new IllegalArgumentException("invalid username or password");
    }
    String token = jwtService.issue(user);
    return ApiResponse.of(Maps.of(
        "accessToken", token,
        "tokenType", "Bearer",
        "role", user.getRole().name(),
        "userId", user.getId()
    ));
  }

  @PostMapping("/session")
  public ApiResponse<Map<String, Object>> issueFromLegacySession(HttpServletRequest request) {
    com.cqust.ai_server.entity.UserEntity legacyUser = legacySessionAccessResolver.requireAuthenticated(request);
    UserRole role = mapRole(legacyUser.getRole());
    UserEntity tapUser = userRepository.findByUsername(legacyUser.getUsername())
        .orElseGet(UserEntity::new);

    tapUser.setUsername(legacyUser.getUsername());
    tapUser.setDisplayName(resolveDisplayName(legacyUser));
    tapUser.setRole(role);
    tapUser = userRepository.save(tapUser);

    String token = jwtService.issue(tapUser);
    return ApiResponse.of(Maps.of(
        "accessToken", token,
        "tokenType", "Bearer",
        "role", tapUser.getRole().name(),
        "userId", tapUser.getId()
    ));
  }

  private String resolveDisplayName(com.cqust.ai_server.entity.UserEntity legacyUser) {
    if (legacyUser.getUsername() == null || legacyUser.getUsername().isBlank()) {
      return "user";
    }
    return legacyUser.getUsername();
  }

  private UserRole mapRole(String legacyRole) {
    if (legacyRole == null) {
      throw new IllegalArgumentException("tap jwt only supports teacher/admin session exchange");
    }
    String normalized = legacyRole.trim().toUpperCase();
    return switch (normalized) {
      case "ADMIN" -> UserRole.ADMIN;
      case "TEACHER" -> UserRole.TEACHER;
      default -> throw new IllegalArgumentException("tap jwt only supports teacher/admin session exchange");
    };
  }
}
