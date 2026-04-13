package com.tap.backend.service.ziporganize;

import com.tap.backend.domain.ziporganize.ZipOrganizeItemEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ZipPackService {
  private final ObjectStorageService objectStorageService;

  public ZipPackService(ObjectStorageService objectStorageService) {
    this.objectStorageService = objectStorageService;
  }

  public byte[] buildOrganizedZip(List<ZipOrganizeItemEntity> items, String readmeText, byte[] reportJson) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
      for (ZipOrganizeItemEntity item : items) {
        ZipEntry entry = new ZipEntry(ZipOrganizeNaming.normalizeOutputPath(item.getFinalPath()));
        zos.putNextEntry(entry);
        zos.write(objectStorageService.getBytes(item.getObjectKey()));
        zos.closeEntry();
      }
      if (readmeText != null && !readmeText.isBlank()) {
        zos.putNextEntry(new ZipEntry("README.txt"));
        zos.write(readmeText.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
      if (reportJson != null && reportJson.length > 0) {
        zos.putNextEntry(new ZipEntry("report.json"));
        zos.write(reportJson);
        zos.closeEntry();
      }
    }
    return bos.toByteArray();
  }
}
