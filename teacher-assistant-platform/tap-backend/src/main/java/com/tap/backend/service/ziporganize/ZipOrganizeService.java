package com.tap.backend.service.ziporganize;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobStatus;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.ZipOrganizeItemRepository;
import com.tap.backend.repo.ZipOrganizeJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZipOrganizeService {
  private final ZipOrganizeJobRepository jobRepository;
  private final ZipOrganizeItemRepository itemRepository;
  private final ObjectStorageService objectStorageService;
  private final ZipOrganizeProperties props;
  private final ZipOrganizeAiService aiService;

  public ZipOrganizeService(ZipOrganizeJobRepository jobRepository,
      ZipOrganizeItemRepository itemRepository,
      ObjectStorageService objectStorageService,
      ZipOrganizeProperties props,
      ZipOrganizeAiService aiService) {
    this.jobRepository = jobRepository;
    this.itemRepository = itemRepository;
    this.objectStorageService = objectStorageService;
    this.props = props;
    this.aiService = aiService;
  }

  @Transactional
  public ZipOrganizeJobEntity createJob(UserEntity user, String originalFilename, byte[] zipBytes) {
    if (zipBytes == null || zipBytes.length == 0) throw new IllegalArgumentException("zip file is empty");
    long maxZipBytes = props.maxZipBytes() <= 0 ? 50L * 1024 * 1024 : props.maxZipBytes();
    if (zipBytes.length > maxZipBytes) {
      throw new IllegalArgumentException("zip file is too large");
    }
    ZipOrganizeJobEntity job = new ZipOrganizeJobEntity();
    job.setUser(user);
    job.setStatus(ZipOrganizeJobStatus.PENDING);
    job.setOriginalFilename(ZipOrganizeNaming.sanitizeFilename(originalFilename, "zip"));
    job.setInputObjectKey("pending");
    job.setProgress(0);
    job.setProvider(aiService.provider());
    job.setModel(aiService.model());
    job = jobRepository.save(job);

    String key = "zip-organize/%d/input/%s".formatted(job.getId(), job.getOriginalFilename());
    objectStorageService.putBytes(key, zipBytes, "application/zip");
    job.setInputObjectKey(key);
    return jobRepository.save(job);
  }

  @Transactional
  public ZipOrganizeJobEntity retryOwnedJob(ZipOrganizeJobEntity job) {
    if (job.getStatus() == ZipOrganizeJobStatus.RUNNING) {
      throw new IllegalArgumentException("job is running");
    }
    int maxRetries = props.maxRetries() <= 0 ? 2 : props.maxRetries();
    if (job.getRetryCount() >= maxRetries) {
      throw new IllegalArgumentException("job retry limit reached");
    }
    itemRepository.deleteAllByJob_Id(job.getId());
    job.setRetryCount(job.getRetryCount() + 1);
    job.setStatus(ZipOrganizeJobStatus.PENDING);
    job.setProgress(0);
    job.setTotalItems(0);
    job.setProcessedItems(0);
    job.setSuccessItems(0);
    job.setFailedItems(0);
    job.setErrorMessage(null);
    job.setOutputObjectKey(null);
    job.setReportObjectKey(null);
    job.setStartedAt(null);
    job.setFinishedAt(null);
    return jobRepository.save(job);
  }

  @Transactional
  public void markCancelled(ZipOrganizeJobEntity job) {
    if (job.getStatus() == ZipOrganizeJobStatus.SUCCEEDED || job.getStatus() == ZipOrganizeJobStatus.FAILED) {
      throw new IllegalArgumentException("job already finished");
    }
    job.setStatus(ZipOrganizeJobStatus.CANCELLED);
    job.setFinishedAt(Instant.now());
    jobRepository.save(job);
  }
}
