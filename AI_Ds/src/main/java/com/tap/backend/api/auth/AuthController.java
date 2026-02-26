package com.tap.backend.api.auth;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.UserRepository;
import com.tap.backend.security.JwtService;
import com.tap.common.api.ApiResponse;
import com.tap.common.api.Maps;
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

  public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
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
}
