package com.tap.backend.api;

import com.tap.common.api.ProblemResponse;
import com.tap.backend.quota.QuotaExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ProblemResponse badRequest(IllegalArgumentException e) {
    return ProblemResponse.of("BAD_REQUEST", e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ProblemResponse serverError(Exception e) {
    return ProblemResponse.of("INTERNAL_ERROR", e.getMessage());
  }

  @ExceptionHandler(QuotaExceededException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ProblemResponse quota(QuotaExceededException e) {
    return ProblemResponse.of("QUOTA_EXCEEDED", e.getMessage());
  }
}
