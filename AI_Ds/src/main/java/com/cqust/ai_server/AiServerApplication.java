package com.cqust.ai_server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.cqust.ai_server", "com.tap.backend"})
@MapperScan(basePackages = "com.cqust.ai_server.dao")
@ConfigurationPropertiesScan(basePackages = {"com.cqust.ai_server", "com.tap.backend"})
@EnableJpaRepositories(basePackages = "com.tap.backend")
@EntityScan(basePackages = "com.tap.backend")
@EnableScheduling
public class AiServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiServerApplication.class, args);
	}

}
