package com.tap.backend.domain.ziporganize;

import com.tap.backend.domain.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "zip_organize_job")
public class ZipOrganizeJobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "user_id", insertable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ZipOrganizeJobStatus status;

  @Column(name = "original_filename", nullable = false, length = 512)
  private String originalFilename;

  @Column(name = "input_object_key", nullable = false, columnDefinition = "text")
  private String inputObjectKey;

  @Column(name = "output_object_key", columnDefinition = "text")
  private String outputObjectKey;

  @Column(name = "report_object_key", columnDefinition = "text")
  private String reportObjectKey;

  @Column(name = "provider", nullable = false, length = 32)
  private String provider;

  @Column(name = "model", nullable = false, length = 64)
  private String model;

  @Column(name = "total_items", nullable = false)
  private int totalItems;

  @Column(name = "processed_items", nullable = false)
  private int processedItems;

  @Column(name = "success_items", nullable = false)
  private int successItems;

  @Column(name = "failed_items", nullable = false)
  private int failedItems;

  @Column(name = "progress", nullable = false)
  private int progress;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (status == null) status = ZipOrganizeJobStatus.PENDING;
    if (provider == null) provider = "";
    if (model == null) model = "";
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public UserEntity getUser() {
    return user;
  }

  public void setUser(UserEntity user) {
    this.user = user;
  }

  public Long getUserId() {
    return userId;
  }

  public ZipOrganizeJobStatus getStatus() {
    return status;
  }

  public void setStatus(ZipOrganizeJobStatus status) {
    this.status = status;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getInputObjectKey() {
    return inputObjectKey;
  }

  public void setInputObjectKey(String inputObjectKey) {
    this.inputObjectKey = inputObjectKey;
  }

  public String getOutputObjectKey() {
    return outputObjectKey;
  }

  public void setOutputObjectKey(String outputObjectKey) {
    this.outputObjectKey = outputObjectKey;
  }

  public String getReportObjectKey() {
    return reportObjectKey;
  }

  public void setReportObjectKey(String reportObjectKey) {
    this.reportObjectKey = reportObjectKey;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(int totalItems) {
    this.totalItems = totalItems;
  }

  public int getProcessedItems() {
    return processedItems;
  }

  public void setProcessedItems(int processedItems) {
    this.processedItems = processedItems;
  }

  public int getSuccessItems() {
    return successItems;
  }

  public void setSuccessItems(int successItems) {
    this.successItems = successItems;
  }

  public int getFailedItems() {
    return failedItems;
  }

  public void setFailedItems(int failedItems) {
    this.failedItems = failedItems;
  }

  public int getProgress() {
    return progress;
  }

  public void setProgress(int progress) {
    this.progress = progress;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }
}
