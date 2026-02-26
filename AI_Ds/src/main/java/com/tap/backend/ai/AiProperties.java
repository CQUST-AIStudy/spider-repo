package com.tap.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tap.ai")
public record AiProperties(
    String provider,
    OpenAi openai
) {
  public record OpenAi(
      String baseUrl,
      String apiKey,
      String model
  ) {}
}
