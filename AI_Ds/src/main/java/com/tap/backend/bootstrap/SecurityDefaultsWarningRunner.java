package com.tap.backend.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SecurityDefaultsWarningRunner implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(SecurityDefaultsWarningRunner.class);
  private static final String DEFAULT_DB_PASSWORD = "123456";
  private static final String DEFAULT_JWT_SECRET =
      "CHANGE_ME_PLEASE_CHANGE_ME_32_BYTES_MINIMUM_123456";
  private static final String DEFAULT_MINIO_CREDENTIAL = "minioadmin";

  private final Environment environment;

  public SecurityDefaultsWarningRunner(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void run(String... args) {
    warnIfUsingDefaultDbPassword();
    warnIfUsingDefaultJwtSecret();
    warnIfUsingDefaultMinioCredentials();
    warnIfSchemaManagementIsSplit();
  }

  private void warnIfUsingDefaultDbPassword() {
    String dbPassword = environment.getProperty("spring.datasource.password", "");
    if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
      log.warn("Database password is still using the default fallback value; set DB_PASSWORD or DB_PASS");
    }
  }

  private void warnIfUsingDefaultJwtSecret() {
    String jwtSecret = environment.getProperty("tap.security.jwt.secret", "");
    if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
      log.warn("JWT secret is still using the default fallback value; set JWT_SECRET before deployment");
    }
  }

  private void warnIfUsingDefaultMinioCredentials() {
    String accessKey = environment.getProperty("tap.storage.minio.access-key", "");
    String secretKey = environment.getProperty("tap.storage.minio.secret-key", "");
    if (DEFAULT_MINIO_CREDENTIAL.equals(accessKey) || DEFAULT_MINIO_CREDENTIAL.equals(secretKey)) {
      log.warn("MinIO credentials are still using default values; set MINIO_ACCESS_KEY and MINIO_SECRET_KEY");
    }
  }

  private void warnIfSchemaManagementIsSplit() {
    boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class, false);
    String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "");
    if (flywayEnabled && "update".equalsIgnoreCase(ddlAuto)) {
      log.warn("Flyway is enabled while hibernate ddl-auto=update is still active; schema ownership remains split");
    }
  }
}
