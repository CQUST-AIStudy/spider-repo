package com.tap.backend.service.ziporganize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ZipOrganizeNaming {
  private ZipOrganizeNaming() {}

  static String normalizeZipEntryPath(String raw) {
    String path = raw == null ? "" : raw.replace('\\', '/').trim();
    while (path.startsWith("/")) path = path.substring(1);
    path = path.replaceAll("/+", "/");
    if (path.isBlank()) throw new IllegalArgumentException("zip entry path is blank");
    List<String> segments = new ArrayList<>();
    for (String part : path.split("/")) {
      if (part.isBlank() || ".".equals(part)) continue;
      if ("..".equals(part)) throw new IllegalArgumentException("zip entry path contains parent traversal");
      segments.add(part);
    }
    if (segments.isEmpty()) throw new IllegalArgumentException("zip entry path is blank");
    return String.join("/", segments);
  }

  static String filenameOf(String path) {
    String p = path == null ? "file" : path;
    int idx = p.lastIndexOf('/');
    return idx >= 0 ? p.substring(idx + 1) : p;
  }

  static String extensionOf(String filename) {
    String name = filename == null ? "" : filename;
    int idx = name.lastIndexOf('.');
    if (idx < 0 || idx == name.length() - 1) return "";
    return name.substring(idx + 1).toLowerCase(Locale.ROOT);
  }

  static String guessContentType(String filename) {
    String ext = extensionOf(filename);
    return switch (ext) {
      case "pdf" -> "application/pdf";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "doc" -> "application/msword";
      case "txt" -> "text/plain; charset=utf-8";
      default -> "application/octet-stream";
    };
  }

  static String sanitizeFilename(String raw, String fallbackExt) {
    String name = raw == null ? "" : raw.trim();
    name = name.replace('\\', '_').replace('/', '_').replace(':', '-');
    name = name.replaceAll("[\\r\\n\\t]+", " ");
    name = name.replaceAll("[<>\"|?*]+", "_");
    name = name.replaceAll("\\s+", " ").trim();
    if (name.isBlank()) name = "file";
    String ext = extensionOf(name);
    if (ext.isBlank() && fallbackExt != null && !fallbackExt.isBlank()) {
      name = name + "." + fallbackExt.toLowerCase(Locale.ROOT);
    }
    if (name.length() > 180) {
      String keepExt = extensionOf(name);
      if (!keepExt.isBlank()) {
        int baseMax = Math.max(1, 180 - keepExt.length() - 1);
        name = name.substring(0, Math.min(baseMax, name.length())) + "." + keepExt;
      } else {
        name = name.substring(0, 180);
      }
    }
    return name;
  }

  static String sanitizeFolderPath(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String path = raw.replace('\\', '/').trim();
    path = path.replaceAll("/+", "/");
    List<String> out = new ArrayList<>();
    for (String part : path.split("/")) {
      String s = part.trim();
      if (s.isBlank() || ".".equals(s) || "..".equals(s)) continue;
      s = s.replace(':', '-').replaceAll("[<>\"|?*]+", "_");
      s = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
      if (!s.isBlank()) out.add(s);
    }
    return String.join("/", out);
  }

  static String defaultFolder(String docType, String paperCategory, String paperSubtype) {
    String type = lower(docType);
    if ("paper".equals(type)) {
      String category = paperCategory == null || paperCategory.isBlank() ? "Other" : sanitizeFolderPath(paperCategory);
      String subtype = paperSubtype == null || paperSubtype.isBlank() ? "general" : sanitizeFolderPath(paperSubtype);
      return category + "/" + subtype;
    }
    if ("slides".equals(type) || "courseware".equals(type)) return "Courseware";
    if ("assignment".equals(type)) return "Assignments";
    if ("report".equals(type)) return "Reports";
    if ("reference".equals(type)) return "References";
    return "Review_Required";
  }

  static String joinPath(String folder, String filename) {
    String cleanName = sanitizeFilename(filename, extensionOf(filename));
    String cleanFolder = sanitizeFolderPath(folder);
    return cleanFolder.isBlank() ? cleanName : cleanFolder + "/" + cleanName;
  }

  static String ensureUniquePath(String candidate, java.util.Set<String> used) {
    String normalized = normalizeOutputPath(candidate);
    if (used.add(normalized)) return normalized;

    String filename = filenameOf(normalized);
    String ext = extensionOf(filename);
    String folder = normalized.contains("/") ? normalized.substring(0, normalized.lastIndexOf('/')) : "";
    String stem = ext.isBlank() ? filename : filename.substring(0, filename.length() - ext.length() - 1);
    int index = 2;
    while (true) {
      String altName = ext.isBlank() ? stem + "-" + index : stem + "-" + index + "." + ext;
      String next = folder.isBlank() ? altName : folder + "/" + altName;
      if (used.add(next)) return next;
      index++;
    }
  }

  static String normalizeOutputPath(String candidate) {
    String path = candidate == null ? "Review_Required/file" : candidate.replace('\\', '/').trim();
    while (path.startsWith("/")) path = path.substring(1);
    path = path.replaceAll("/+", "/");
    if (path.isBlank()) return "Review_Required/file";
    return path;
  }

  private static String lower(String s) {
    return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
  }
}
