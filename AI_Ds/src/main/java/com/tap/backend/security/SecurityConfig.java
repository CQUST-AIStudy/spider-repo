package com.tap.backend.security;

import com.tap.backend.domain.user.UserRole;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * TAP API 路径使用 JWT 认证 (stateless)
   * 匹配 /api/auth/**, /api/documents/**, /api/uploads/**, /api/papers/**,
   *       /api/chat, /api/agent/**, /api/admin/**, /api/hello, /actuator/**
   */
  @Bean
  @Order(1)
  public SecurityFilterChain tapSecurityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
    http.securityMatcher(
        "/api/auth/**", "/api/documents/**", "/api/uploads/**", "/api/papers/**",
        "/api/tap-chat", "/api/agent/**", "/api/admin/**", "/api/hello", "/actuator/**",
        "/api/course-spaces/**", "/api/annotations/**", "/api/classes/**",
        "/api/grading/**", "/api/rag/**", "/api/pta-cookie/**"
    );
    http.csrf(csrf -> csrf.disable());
    http.cors(cors -> {});
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/api/auth/login").permitAll()
        .requestMatchers("/api/admin/**").hasRole(UserRole.ADMIN.name())
        .requestMatchers("/api/classes/**").permitAll()
        .requestMatchers("/api/pta-cookie/**").permitAll()
        .requestMatchers("/api/grading/**").permitAll()
        .requestMatchers("/api/rag/**").permitAll()
        .requestMatchers("/api/documents/**").permitAll()
        .requestMatchers("/api/uploads/**").permitAll()
        .requestMatchers("/api/papers/**").permitAll()
        .requestMatchers("/api/tap-chat").permitAll()
        .requestMatchers("/api/agent/**").permitAll()
        .requestMatchers("/api/course-spaces/**").permitAll()
        .requestMatchers("/api/annotations/**").permitAll()
        .requestMatchers("/api/analytics/**").permitAll()
        .anyRequest().authenticated()
    );
    http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * AI_Ds 原有路径全部放行 (session-based, 由原有 LoginController 管理)
   */
  @Bean
  @Order(2)
  public SecurityFilterChain aiDsSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/**");
    http.csrf(csrf -> csrf.disable());
    http.cors(cors -> {});
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
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
