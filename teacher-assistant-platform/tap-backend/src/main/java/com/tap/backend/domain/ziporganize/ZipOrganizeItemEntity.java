package com.tap.backend.domain.ziporganize;

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
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "zip_organize_item")
public class ZipOrganizeItemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_id", nullable = false)
  private ZipOrganizeJobEntity job;

  @Column(name = "job_id", insertable = false, updatable = false)
  private Long jobId;

  @Column(name = "original_path", nullable = false, columnDefinition = "text")
  private String originalPath;

  @Column(name = "filename", nullable = false, length = 512)
  private String filename;

  @Column(name = "content_type", nullable = false, length = 128)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "sha256", nullable = false, length = 64)
  private String sha256;

  @Column(name = "object_key", nullable = false, columnDefinition = "text")
  private String objectKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "extract_status", nullable = false, length = 16)
  private ZipOrganizeExtractStatus extractStatus;

  @Column(name = "extracted_text_preview", columnDefinition = "text")
  private String extractedTextPreview;

  @Column(name = "extracted_text_key", columnDefinition = "text")
  private String extractedTextKey;

  @Column(name = "doc_type", length = 32)
  private String docType;

  @Column(name = "paper_category", length = 64)
  private String paperCategory;

  @Column(name = "paper_subtype", length = 64)
  private String paperSubtype;

  @Column(name = "title_guess", length = 512)
  private String titleGuess;

  @Column(name = "author_guess", length = 256)
  private String authorGuess;

  @Column(name = "year_guess")
  private Integer yearGuess;

  @Column(name = "keywords_json", columnDefinition = "json")
  @JdbcTypeCode(SqlTypes.JSON)
  private String keywordsJson;

  @Column(name = "summary_zh", columnDefinition = "text")
  private String summaryZh;

  @Column(name = "suggested_folder", length = 512)
  private String suggestedFolder;

  @Column(name = "suggested_filename", length = 512)
  private String suggestedFilename;

  @Column(name = "final_path", columnDefinition = "text")
  private String finalPath;

  @Column(name = "confidence")
  private Double confidence;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (extractStatus == null) extractStatus = ZipOrganizeExtractStatus.PENDING;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public ZipOrganizeJobEntity getJob() {
    return job;
  }

  public void setJob(ZipOrganizeJobEntity job) {
    this.job = job;
  }

  public Long getJobId() {
    return jobId;
  }

  public String getOriginalPath() {
    return originalPath;
  }

  public void setOriginalPath(String originalPath) {
    this.originalPath = originalPath;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public ZipOrganizeExtractStatus getExtractStatus() {
    return extractStatus;
  }

  public void setExtractStatus(ZipOrganizeExtractStatus extractStatus) {
    this.extractStatus = extractStatus;
  }

  public String getExtractedTextPreview() {
    return extractedTextPreview;
  }

  public void setExtractedTextPreview(String extractedTextPreview) {
    this.extractedTextPreview = extractedTextPreview;
  }

  public String getExtractedTextKey() {
    return extractedTextKey;
  }

  public void setExtractedTextKey(String extractedTextKey) {
    this.extractedTextKey = extractedTextKey;
  }

  public String getDocType() {
    return docType;
  }

  public void setDocType(String docType) {
    this.docType = docType;
  }

  public String getPaperCategory() {
    return paperCategory;
  }

  public void setPaperCategory(String paperCategory) {
    this.paperCategory = paperCategory;
  }

  public String getPaperSubtype() {
    return paperSubtype;
  }

  public void setPaperSubtype(String paperSubtype) {
    this.paperSubtype = paperSubtype;
  }

  public String getTitleGuess() {
    return titleGuess;
  }

  public void setTitleGuess(String titleGuess) {
    this.titleGuess = titleGuess;
  }

  public String getAuthorGuess() {
    return authorGuess;
  }

  public void setAuthorGuess(String authorGuess) {
    this.authorGuess = authorGuess;
  }

  public Integer getYearGuess() {
    return yearGuess;
  }

  public void setYearGuess(Integer yearGuess) {
    this.yearGuess = yearGuess;
  }

  public String getKeywordsJson() {
    return keywordsJson;
  }

  public void setKeywordsJson(String keywordsJson) {
    this.keywordsJson = keywordsJson;
  }

  public String getSummaryZh() {
    return summaryZh;
  }

  public void setSummaryZh(String summaryZh) {
    this.summaryZh = summaryZh;
  }

  public String getSuggestedFolder() {
    return suggestedFolder;
  }

  public void setSuggestedFolder(String suggestedFolder) {
    this.suggestedFolder = suggestedFolder;
  }

  public String getSuggestedFilename() {
    return suggestedFilename;
  }

  public void setSuggestedFilename(String suggestedFilename) {
    this.suggestedFilename = suggestedFilename;
  }

  public String getFinalPath() {
    return finalPath;
  }

  public void setFinalPath(String finalPath) {
    this.finalPath = finalPath;
  }

  public Double getConfidence() {
    return confidence;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
