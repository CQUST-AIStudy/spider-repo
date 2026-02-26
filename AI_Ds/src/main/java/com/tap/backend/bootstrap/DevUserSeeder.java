package com.tap.backend.bootstrap;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.domain.user.UserRole;
import com.tap.backend.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DevUserSeeder implements CommandLineRunner {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    seed("admin", "admin123", UserRole.ADMIN);
    seed("teacher1", "teacher123", UserRole.TEACHER);
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
}
