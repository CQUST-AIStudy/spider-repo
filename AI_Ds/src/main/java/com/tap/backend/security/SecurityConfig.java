package com.tap.backend.security;

import com.cqust.ai_server.security.LegacySessionAuthFilter;
import com.tap.backend.domain.user.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  private static final String[] TAP_API_MATCHERS = {
      "/api/auth/**",
      "/api/documents/**",
      "/api/uploads/**",
      "/api/papers/**",
      "/api/tap-chat",
      "/api/agent/**",
      "/api/admin/**",
      "/api/hello",
      "/actuator/**",
      "/api/course-spaces/**",
      "/api/annotations/**",
      "/api/classes/**",
      "/api/grading/**",
      "/api/rag/**",
      "/api/pta-cookie/**",
      "/api/analytics/**"
  };

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * TAP API 路径使用 JWT 认证 (stateless)
   * 匹配 /api/auth/**, /api/documents/**, /api/uploads/**, /api/papers/**,
   *       /api/chat, /api/agent/**, /api/admin/**, /api/hello, /actuator/**
   */
  /**
   * TAP API chain — JWT-only, fully stateless.
   * No legacy session fallback; TAP clients must use Bearer tokens.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain tapSecurityFilterChain(
      HttpSecurity http,
      JwtAuthFilter jwtAuthFilter) throws Exception {
    http.securityMatcher(TAP_API_MATCHERS);
    http.csrf(csrf -> csrf.disable());
    http.cors(cors -> {});
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/api/auth/login").permitAll()
        .requestMatchers("/api/admin/**").hasRole(UserRole.ADMIN.name())
        .requestMatchers("/api/documents/**").authenticated()
        .requestMatchers("/api/uploads/**").authenticated()
        .requestMatchers("/api/papers/**").authenticated()
        .requestMatchers("/api/tap-chat").authenticated()
        .requestMatchers("/api/agent/**").authenticated()
        .requestMatchers("/api/course-spaces/**").authenticated()
        .requestMatchers("/api/annotations/**").authenticated()
        .requestMatchers(HttpMethod.POST, "/api/classes/join").permitAll()
        .requestMatchers(HttpMethod.PUT, "/api/classes/*/pta-sync/callback").permitAll()
        .requestMatchers(HttpMethod.PUT, "/api/pta-cookie/status").permitAll()
        .requestMatchers("/api/pta-cookie/**").authenticated()
        .requestMatchers("/api/grading/**").authenticated()
        .requestMatchers("/api/rag/**").authenticated()
        .requestMatchers("/api/classes/**").authenticated()
        .requestMatchers("/api/analytics/student/**").authenticated()
        .requestMatchers("/api/analytics/**").authenticated()
        .anyRequest().authenticated()
    );
    http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Legacy AI_Ds chain — session-based auth managed by the original LoginController.
   * Covers all paths not matched by the TAP API chain above.
   */
  @Bean
  @Order(2)
  public SecurityFilterChain aiDsSecurityFilterChain(HttpSecurity http, LegacySessionAuthFilter legacySessionAuthFilter)
      throws Exception {
    http.securityMatcher("/**");
    http.csrf(csrf -> csrf.disable());
    http.cors(cors -> {});
    http.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/login", "/api/register", "/logout", "/api/logout").permitAll()
        .anyRequest().authenticated()
    );
    http.addFilterBefore(legacySessionAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of(
        "http://localhost:*",
        "http://127.0.0.1:*",
        "http://47.108.176.134:8090"
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("X-Trace-Id"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
