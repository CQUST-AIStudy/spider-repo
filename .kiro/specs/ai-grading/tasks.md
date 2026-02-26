# Implementation Plan: AI Grading Module

## Overview

Implement the AI grading module across three layers: database schema (Flyway migration), Spring Boot backend (entities, repositories, services, controllers), and Python Worker (FastAPI + Celery pipeline). Vue frontend pages are added last to wire everything together. Java uses existing project patterns (JPA entities, ApiResponse, JWT auth). Python Worker is a new standalone service.

## Tasks

- [x] 1. Database schema and Spring Boot entities
  - [x] 1.1 Create Flyway migration V2__grading_init.sql
    - Add all grading tables: grading_rubric, rubric_dimension, grading_task, grading_submission, evidence_block, score_item, score_override, grading_trace, report_file
    - Follow existing migration pattern from V1__tap_init.sql
    - Include all indexes, foreign keys, and check constraints from the design document
    - _Requirements: 1.1, 1.2, 3.1, 4.1, 8.1, 9.2, 11.1, 10.3_

  - [x] 1.2 Create JPA entities in com.tap.backend.domain.grading
    - Create GradingRubricEntity, RubricDimensionEntity, GradingTaskEntity, GradingSubmissionEntity, EvidenceBlockEntity, ScoreItemEntity, ScoreOverrideEntity, GradingTraceEntity, ReportFileEntity
    - Follow DocumentEntity pattern: @Entity, @Table, @Id with IDENTITY, @ManyToOne(LAZY), @PrePersist for createdAt
    - Use @Column with explicit names matching SQL schema
    - Add status enums: GradingTaskStatus, SubmissionStatus, ScoreItemStatus, EvidenceKind
    - _Requirements: 1.1, 3.1, 8.1, 9.2, 11.1_

  - [x] 1.3 Create JPA repositories in com.tap.backend.repo
    - GradingRubricRepository, RubricDimensionRepository, GradingTaskRepository, GradingSubmissionRepository, EvidenceBlockRepository, ScoreItemRepository, ScoreOverrideRepository, GradingTraceRepository, ReportFileRepository
    - Add custom query methods: findAllByTeacherId, findAllByTaskId, findAllBySubmissionId, findByTaskIdAndStatus
    - _Requirements: 2.1, 2.2, 3.4, 9.1_

- [x] 2. Rubric management (Spring Boot service + controller)
  - [x] 2.1 Implement RubricService
    - create(teacherId, rubricDto): validate weights sum to 100, validate each dimension (non-empty name, max_score > 0, weight > 0), persist rubric + dimensions
    - update(rubricId, rubricDto): check no PROCESSING tasks reference it, validate, persist
    - listByTeacher(teacherId, subject): return filtered list
    - getDetail(rubricId): return rubric with dimensions
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 2.2 Write property tests for RubricService (jqwik)
    - **Property 5: Rubric validation — weights and dimensions**
    - **Validates: Requirements 3.2, 3.5**
    - **Property 22: Rubric round-trip persistence**
    - **Validates: Requirements 3.1**

  - [x] 2.3 Implement RubricController
    - POST /api/grading/rubrics, GET /api/grading/rubrics, GET /api/grading/rubrics/{id}, PUT /api/grading/rubrics/{id}
    - Use ApiResponse wrapper, JWT auth via existing SecurityConfig
    - Add /api/grading/** to tapSecurityFilterChain matcher
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 2.4 Write unit tests for RubricController
    - Test CRUD endpoints with MockMvc
    - Test validation errors (weights != 100, empty name, zero max_score)
    - Test rubric update rejection when referenced by active task
    - _Requirements: 3.2, 3.3, 3.5_

- [x] 3. Grading task creation and monitoring (Spring Boot)
  - [x] 3.1 Implement GradingTaskService
    - createTask(teacherId, experimentId, classId, rubricId, pdfFiles): validate batch size ≤ 200, filter invalid PDFs, store valid PDFs in MinIO, create task + submissions, publish to Redis queue
    - getTaskList(teacherId, status, pageable): return paginated task list
    - getTaskDetail(taskId): return task with all submissions
    - retryFailed(taskId): find FAILED submissions, reset to PENDING, re-queue
    - onSubmissionComplete(submissionId, status, totalScore): update submission, increment task counters
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 3.2 Write property tests for GradingTaskService (jqwik)
    - **Property 1: Task creation produces correct submissions**
    - **Validates: Requirements 1.1, 1.2, 1.6**
    - **Property 3: Task counter invariant**
    - **Validates: Requirements 2.3, 2.4**
    - **Property 4: Retry targets only failed submissions**
    - **Validates: Requirements 2.5**

  - [x] 3.3 Implement Redis queue publisher and listener
    - RedisGradingPublisher: publish task messages to grading:tasks list as JSON
    - RedisGradingListener: subscribe to grading:results channel, parse notification, delegate to GradingTaskService.onSubmissionComplete()
    - Message format: {taskId, submissionId, pdfObjectKey, rubricId} for tasks, {submissionId, status, totalScore} for results
    - _Requirements: 1.3, 2.3_

  - [x] 3.4 Implement GradingTaskController
    - POST /api/grading/tasks (multipart), GET /api/grading/tasks, GET /api/grading/tasks/{id}, POST /api/grading/tasks/{id}/retry
    - Handle multipart file upload with PDF validation (check content type and magic bytes)
    - _Requirements: 1.1, 1.4, 1.5, 2.1, 2.2, 2.5_

  - [ ]* 3.5 Write unit tests for GradingTaskController
    - Test batch upload with valid PDFs
    - Test rejection of non-PDF files
    - Test batch size limit (201 files)
    - Test retry endpoint
    - _Requirements: 1.1, 1.4, 1.5, 2.5_

- [x] 4. Checkpoint — Verify Spring Boot compilation and tests
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Python Worker project setup
  - [x] 5.1 Initialize Python Worker project structure
    - Create grading_worker/ directory with: celery_app.py, config.py, main.py (FastAPI), requirements.txt
    - Create grading_worker/pipeline/ package with __init__.py
    - Create grading_worker/models/ package for Pydantic models
    - Configure Celery with Redis broker, MySQL via SQLAlchemy for direct writes
    - Configure FastAPI health endpoint
    - requirements.txt: fastapi, celery[redis], pymupdf, paddleocr, langchain, langchain-community, weasyprint, sqlalchemy, pymysql, redis, pydantic, httpx
    - _Requirements: 12.1_

  - [x] 5.2 Create Pydantic models for pipeline data
    - ParsedPage, ParsedDocument, ImageInfo, OcrResult, VlmResult, EvidenceBlock, EvidencePack, ScoreResult, TraceRecord
    - TaskMessage model matching Redis queue message format
    - _Requirements: 4.1, 5.1, 7.1, 8.1, 11.1_

  - [x] 5.3 Create database models (SQLAlchemy) for direct MySQL writes
    - Mirror the JPA entities: GradingSubmission, EvidenceBlock, ScoreItem, GradingTrace, ReportFile
    - Read-only models: GradingRubric, RubricDimension, GradingTask
    - Configure connection pool with environment variables
    - _Requirements: 8.5, 11.1_

- [x] 6. Python Worker pipeline — PDF parsing and image classification
  - [x] 6.1 Implement PdfParser (pipeline/pdf_parser.py)
    - Use PyMuPDF (fitz) to extract text per page and embedded images with bounding boxes
    - Return ParsedDocument with pages containing text and image list
    - Handle corrupted PDFs: catch exceptions, return error
    - _Requirements: 4.1, 4.2, 4.4_

  - [x] 6.2 Implement ImageClassifier (pipeline/image_classifier.py)
    - Rule-based classification using aspect ratio and color histogram analysis
    - Categories: code_screenshot (wide, low color variance), terminal_log (dark background, monospace-like), diagram (high color variance, geometric), plot (axes detection), other
    - Return exactly one label from the valid set
    - _Requirements: 4.3_

  - [ ]* 6.3 Write property test for ImageClassifier (Hypothesis)
    - **Property 8: Image classifier output domain**
    - **Validates: Requirements 4.3**

- [x] 7. Python Worker pipeline — OCR and VLM processing
  - [x] 7.1 Implement OcrProcessor (pipeline/ocr_processor.py)
    - Initialize PaddleOCR with Chinese + English support
    - Post-processing: fullwidth→halfwidth conversion (U+FF01–U+FF5E → U+0021–U+007E), common character fixes
    - Preserve line structure from OCR bounding box positions
    - Return OcrResult with text, confidence, and line details
    - Handle OCR failures gracefully: log and return empty result
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 7.2 Write property test for OCR post-processor (Hypothesis)
    - **Property 10: OCR fullwidth-to-halfwidth conversion**
    - **Validates: Requirements 5.2**

  - [x] 7.3 Implement VlmClient (pipeline/vlm_client.py)
    - Compute SHA256 hash of image bytes for cache key
    - Check Redis cache before calling VLM API
    - Call VLM API via httpx with timeout
    - Cache result in Redis on success
    - On failure: log trace, return vlm_failed marker
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 7.4 Write property test for VLM hash determinism (Hypothesis)
    - **Property 11: VLM hash determinism**
    - **Validates: Requirements 6.2**

- [x] 8. Python Worker pipeline — Evidence building and LLM scoring
  - [x] 8.1 Implement EvidenceBuilder (pipeline/evidence_builder.py)
    - Convert all extracted content to LangChain Document objects
    - Use custom CodeLineSplitter for code content (split on line boundaries, preserve indentation)
    - Use BM25Retriever from langchain-community to rank evidence per dimension
    - Select top 3-8 evidence blocks per dimension
    - Return Dict[dimension_id, EvidencePack]
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 8.2 Write property tests for EvidenceBuilder (Hypothesis)
    - **Property 12: Evidence pack size bounds**
    - **Validates: Requirements 7.1, 7.3**
    - **Property 13: BM25 selection optimality**
    - **Validates: Requirements 7.2**

  - [x] 8.3 Implement DeepSeekScorer (pipeline/scorer.py)
    - Build prompt with rubric dimension description + evidence pack (not full PDF)
    - Parse structured JSON response: {dimension_id, score, max_score, comment, evidence_ids, status}
    - Handle NEED_MORE_EVIDENCE status
    - Retry up to 3 times on schema violation
    - Rate limiting via Redis token bucket
    - Record trace for each API call
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6_

  - [ ]* 8.4 Write property tests for DeepSeekScorer (Hypothesis)
    - **Property 14: DeepSeek scorer output schema conformance**
    - **Validates: Requirements 8.1**
    - **Property 20: Schema violation retry limit**
    - **Validates: Requirements 8.3**

  - [x] 8.5 Implement weighted total score calculation
    - Compute total_score = sum((score / max_score) * weight) across all dimensions
    - Update grading_submission record with total_score
    - Used both in Python Worker (after AI scoring) and Spring Boot (after teacher override)
    - _Requirements: 8.5_

  - [ ]* 8.6 Write property test for weighted total score calculation (Hypothesis)
    - **Property 15: Weighted total score calculation**
    - **Validates: Requirements 8.5, 9.3**

- [x] 9. Python Worker pipeline — Orchestration and trace logging
  - [x] 9.1 Implement TraceLogger (pipeline/trace_logger.py)
    - Context manager that records step name, start time, and writes GradingTrace record on exit
    - Capture duration_ms, status (SUCCESS/FAILED), error_message
    - For API calls: accept model_used, input_tokens, output_tokens
    - _Requirements: 11.1, 11.2, 11.4_

  - [x] 9.2 Implement Celery task process_submission
    - Consume from Redis queue, parse TaskMessage
    - Orchestrate pipeline: PDF parse → classify images → OCR/VLM → build evidence → score → write results
    - Write evidence_block, score_item records to MySQL
    - Compute and write total_score to grading_submission
    - Publish result notification to grading:results Redis channel
    - Handle transient errors with Celery retry (max 3, exponential backoff)
    - _Requirements: 1.3, 4.1, 5.1, 6.1, 7.1, 8.1, 8.5, 12.1, 12.5_

  - [ ]* 9.3 Write property test for trace record integrity (Hypothesis)
    - **Property 19: Trace record integrity**
    - **Validates: Requirements 11.1, 11.2, 11.4**

- [x] 10. Checkpoint — Verify Python Worker pipeline tests
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Score review and teacher override (Spring Boot)
  - [x] 11.1 Implement GradingSubmissionService
    - getDetail(submissionId): return submission with score items and evidence blocks
    - overrideScore(submissionId, dimensionId, newScore, newComment, reason, teacherId): create ScoreOverride, update ScoreItem, recalculate total_score, record audit event
    - Reuse weighted total score formula: sum((score / max_score) * weight)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 11.3_

  - [ ]* 11.2 Write property tests for GradingSubmissionService (jqwik)
    - **Property 16: Score override audit trail**
    - **Validates: Requirements 9.2, 9.4**
    - **Property 17: Submission detail completeness**
    - **Validates: Requirements 9.1**

  - [x] 11.3 Implement GradingSubmissionController
    - GET /api/grading/submissions/{id}, PUT /api/grading/submissions/{id}/scores, GET /api/grading/submissions/{id}/report
    - Validate override: score between 0 and max_score, non-empty reason
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ]* 11.4 Write unit tests for GradingSubmissionController
    - Test submission detail response structure
    - Test score override with valid/invalid inputs
    - Test override creates audit event
    - _Requirements: 9.1, 9.2, 9.3, 11.3_

- [x] 12. Report generation and export
  - [x] 12.1 Implement ReportBuilder in Python Worker (pipeline/report_builder.py)
    - Build HTML template with scores, comments, evidence references per dimension
    - Mark NEED_MORE_EVIDENCE dimensions clearly
    - Convert HTML to PDF using WeasyPrint
    - Return PDF bytes
    - _Requirements: 10.1, 10.4_

  - [x] 12.2 Implement export endpoints in Spring Boot
    - GradingExportService: trigger report generation for completed submissions, package into ZIP, store in MinIO
    - POST /api/grading/tasks/{id}/export: create export job, return export ID
    - GET /api/grading/submissions/{id}/report: download individual report from MinIO
    - _Requirements: 10.2, 10.3_

  - [ ]* 12.3 Write property test for batch export file count (jqwik)
    - **Property 18: Batch export file count**
    - **Validates: Requirements 10.2**

- [x] 13. Security configuration update
  - [x] 13.1 Update SecurityConfig to include grading API paths
    - Add "/api/grading/**" to tapSecurityFilterChain securityMatcher
    - Ensure all grading endpoints require authentication
    - Add ownership checks in service layer (teacher can only access own tasks/rubrics)
    - _Requirements: 9.1, 2.1_

- [x] 14. Vue frontend — Grading pages
  - [x] 14.1 Add grading API client functions to tap.js
    - createGradingTask, getGradingTasks, getGradingTaskDetail, retryGradingTask
    - getSubmissionDetail, overrideSubmissionScore, downloadSubmissionReport
    - exportGradingTask
    - getRubrics, createRubric, updateRubric, getRubricDetail
    - _Requirements: 1.1, 2.1, 3.1, 9.1, 10.1_

  - [x] 14.2 Implement RubricEditor.vue
    - Form for rubric name, subject, description
    - Dynamic dimension list with add/remove, name, description, maxScore, weight inputs
    - Real-time weight sum validation (must equal 100)
    - Save/update rubric via API
    - _Requirements: 3.1, 3.2, 3.5_

  - [x] 14.3 Implement GradingCenter.vue
    - Create task form: select experiment, class, rubric, upload PDFs (drag-and-drop)
    - Task list table with status, progress bar, counts, created time
    - Auto-refresh progress while tasks are PROCESSING
    - Retry button for failed tasks
    - Export button for completed tasks
    - _Requirements: 1.1, 2.1, 2.2, 2.5, 10.2_

  - [x] 14.4 Implement GradingDetail.vue
    - Task summary header with progress stats
    - Submission table with student name, status, total score, actions
    - Click submission to navigate to SubmissionReview
    - Filter by status (all, scored, failed, need_more_evidence)
    - _Requirements: 2.2, 9.1_

  - [x] 14.5 Implement SubmissionReview.vue
    - Score cards per dimension: dimension name, AI score, max score, weight, comment
    - Evidence panel: show evidence blocks with kind badge, page number, content preview, confidence
    - Override UI: click score to edit, enter new score + comment + reason, save
    - Download individual report button
    - Highlight NEED_MORE_EVIDENCE dimensions
    - _Requirements: 9.1, 9.2, 9.3, 10.1, 10.4_

  - [x] 14.6 Add grading routes to router/index.js
    - /teacher/grading → GradingCenter.vue
    - /teacher/grading/detail/:id → GradingDetail.vue
    - /teacher/grading/submission/:id → SubmissionReview.vue
    - /teacher/grading/rubrics → RubricEditor.vue
    - _Requirements: 1.1, 2.1, 3.1, 9.1_

- [x] 15. Final checkpoint — Full integration verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (Hypothesis for Python, jqwik for Java)
- Unit tests validate specific examples and edge cases
- The Python Worker is a separate service; its project lives in a new grading_worker/ directory at the repo root
