package com.tap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tap.backend.domain.document.DocumentEntity;
import com.tap.backend.domain.rag.CourseSpaceDocumentEntity;
import com.tap.backend.domain.rag.CourseSpaceEntity;
import com.tap.backend.domain.upload.UploadFolderEntity;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.CourseSpaceDocumentRepository;
import com.tap.backend.repo.CourseSpaceRepository;
import com.tap.backend.repo.DocumentRepository;
import com.tap.backend.repo.UploadFolderRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseSpaceService {

    private static final Logger log = LoggerFactory.getLogger(CourseSpaceService.class);
    private static final String RAG_TASKS_KEY = "rag:tasks";

    private final CourseSpaceRepository courseSpaceRepo;
    private final CourseSpaceDocumentRepository courseSpaceDocRepo;
    private final DocumentRepository documentRepo;
    private final DocumentIngestService documentIngestService;
    private final UploadFolderRepository uploadFolderRepo;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CourseSpaceService(CourseSpaceRepository courseSpaceRepo,
                              CourseSpaceDocumentRepository courseSpaceDocRepo,
                              DocumentRepository documentRepo,
                              DocumentIngestService documentIngestService,
                              UploadFolderRepository uploadFolderRepo,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.courseSpaceRepo = courseSpaceRepo;
        this.courseSpaceDocRepo = courseSpaceDocRepo;
        this.documentRepo = documentRepo;
        this.documentIngestService = documentIngestService;
        this.uploadFolderRepo = uploadFolderRepo;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseSpaceEntity createSpace(UserEntity teacher, String name, String term,
                                         String courseName, String description) {
        CourseSpaceEntity cs = new CourseSpaceEntity();
        cs.setTeacher(teacher);
        cs.setName(name);
        cs.setTerm(term);
        cs.setCourseName(courseName);
        cs.setDescription(description);
        return courseSpaceRepo.save(cs);
    }

    @Transactional(readOnly = true)
    public List<CourseSpaceEntity> listSpaces(Long teacherId) {
        return courseSpaceRepo.findAllByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public CourseSpaceEntity getSpace(Long id) {
        return courseSpaceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("course space not found"));
    }

    @Transactional(readOnly = true)
    public CourseSpaceEntity requireOwnedSpace(Long id, Long teacherId) {
        CourseSpaceEntity cs = getSpace(id);
        if (!cs.getTeacherId().equals(teacherId)) {
            throw new IllegalArgumentException("not your course space");
        }
        return cs;
    }

    @Transactional
    public CourseSpaceEntity updateSpace(Long id, Long teacherId, String name, String term,
                                          String courseName, String description,
                                          String defaultMode, Boolean allowWebSearch,
                                          Boolean requireCitation, String docVisibility) {
        CourseSpaceEntity cs = courseSpaceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("course space not found"));
        if (!cs.getTeacherId().equals(teacherId)) {
            throw new IllegalArgumentException("not your course space");
        }
        if (name != null) cs.setName(name);
        if (term != null) cs.setTerm(term);
        if (courseName != null) cs.setCourseName(courseName);
        if (description != null) cs.setDescription(description);
        if (defaultMode != null) cs.setDefaultMode(defaultMode);
        if (allowWebSearch != null) cs.setAllowWebSearch(allowWebSearch);
        if (requireCitation != null) cs.setRequireCitation(requireCitation);
        if (docVisibility != null) cs.setDocVisibility(docVisibility);
        return courseSpaceRepo.save(cs);
    }

    @Transactional
    public void deleteSpace(Long id, Long teacherId) {
        CourseSpaceEntity cs = courseSpaceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("course space not found"));
        if (!cs.getTeacherId().equals(teacherId)) {
            throw new IllegalArgumentException("not your course space");
        }
        courseSpaceRepo.delete(cs);
    }

    @Transactional
    public CourseSpaceDocumentEntity uploadDocument(Long courseSpaceId, UserEntity teacher,
                                                     MultipartFile file, String docType) throws Exception {
        CourseSpaceEntity cs = courseSpaceRepo.findById(courseSpaceId)
                .orElseThrow(() -> new IllegalArgumentException("course space not found"));
        if (!cs.getTeacherId().equals(teacher.getId())) {
            throw new IllegalArgumentException("not your course space");
        }

        // Create or reuse an upload folder for this course space
        String folderName = "rag-cs-" + courseSpaceId;
        UploadFolderEntity folder = uploadFolderRepo.findAllByUser_Id(teacher.getId()).stream()
                .filter(f -> folderName.equals(f.getFolderName()))
                .findFirst()
                .orElseGet(() -> {
                    UploadFolderEntity nf = new UploadFolderEntity();
                    nf.setUser(teacher);
                    nf.setFolderName(folderName);
                    return uploadFolderRepo.save(nf);
                });

        // Ingest the file using existing DocumentIngestService
        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        List<DocumentIngestService.StoredDocument> stored =
                documentIngestService.ingestMultipartFiles(teacher, folder, List.of(file), List.of(originalFilename));

        if (stored.isEmpty()) {
            throw new IllegalArgumentException("file is empty or could not be ingested");
        }

        DocumentIngestService.StoredDocument sd = stored.get(0);

        // Load the persisted DocumentEntity
        DocumentEntity docEntity = documentRepo.findById(sd.id())
                .orElseThrow(() -> new IllegalStateException("ingested document not found"));

        // Create course_space_document record
        CourseSpaceDocumentEntity csDoc = new CourseSpaceDocumentEntity();
        csDoc.setCourseSpace(cs);
        csDoc.setDocument(docEntity);
        csDoc.setDocType(docType == null ? "textbook" : docType);
        csDoc.setStatus("PENDING");
        csDoc.setChunkCount(0);
        csDoc = courseSpaceDocRepo.save(csDoc);

        log.info("Uploaded document {} to course space {}, csDocId={}", sd.id(), courseSpaceId, csDoc.getId());
        return csDoc;
    }

    @Transactional(readOnly = true)
    public List<CourseSpaceDocumentEntity> listDocuments(Long courseSpaceId) {
        return courseSpaceDocRepo.findAllByCourseSpaceId(courseSpaceId);
    }

    private void pushRagTask(Long courseSpaceDocId, Long courseSpaceId, long documentId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "courseSpaceDocId", courseSpaceDocId,
                    "courseSpaceId", courseSpaceId,
                    "documentId", documentId
            ));
            redisTemplate.opsForList().leftPush(RAG_TASKS_KEY, json);
            log.info("Pushed rag task: {}", json);
        } catch (Exception e) {
            log.error("Failed to push rag task to Redis", e);
        }
    }
}
