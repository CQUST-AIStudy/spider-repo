package com.tap.backend.bootstrap;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.domain.user.UserRole;
import com.tap.backend.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DevUserSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);
  private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
  private static final String DEFAULT_TEACHER_PASSWORD = "teacher123";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final boolean enabled;
  private final String adminPassword;
  private final String teacherPassword;

  public DevUserSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${TAP_DEV_SEED_USERS_ENABLED:true}") boolean enabled,
      @Value("${TAP_DEV_ADMIN_PASSWORD:" + DEFAULT_ADMIN_PASSWORD + "}") String adminPassword,
      @Value("${TAP_DEV_TEACHER_PASSWORD:" + DEFAULT_TEACHER_PASSWORD + "}") String teacherPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.enabled = enabled;
    this.adminPassword = adminPassword;
    this.teacherPassword = teacherPassword;
  }

  @Override
  public void run(String... args) {
    if (!enabled) {
      log.info("Skipping TAP dev user seeding because TAP_DEV_SEED_USERS_ENABLED=false");
      return;
    }
    warnIfUsingDefaultPassword("admin", adminPassword, DEFAULT_ADMIN_PASSWORD);
    warnIfUsingDefaultPassword("teacher1", teacherPassword, DEFAULT_TEACHER_PASSWORD);
    seed("admin", adminPassword, UserRole.ADMIN);
    seed("teacher1", teacherPassword, UserRole.TEACHER);
  }

  private void seed(String username, String password, UserRole role) {
    UserEntity user = userRepository.findByUsername(username).orElseGet(() -> {
      UserEntity u = new UserEntity();
      u.setUsername(username);
      u.setDisplayName(username);
      return u;
    });
    user.setRole(role);
    if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
      user.setPasswordHash(passwordEncoder.encode(password));
    }
    userRepository.save(user);
  }

  private void warnIfUsingDefaultPassword(String username, String actualPassword, String defaultPassword) {
    if (defaultPassword.equals(actualPassword)) {
      log.warn("TAP dev seed user '{}' is still using the default seeded password", username);
    }
  }
}
