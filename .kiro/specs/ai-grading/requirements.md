# Requirements Document

## Introduction

智能教辅系统-教师端作业批改模块 (AI Grading Module): An AI-powered batch grading system for student experiment reports. Teachers upload batches of student PDF submissions (containing code screenshots, terminal logs, diagrams, and plots). A Python Worker pipeline extracts evidence via OCR/VLM, compresses it into per-question evidence packs, and uses DeepSeek LLM to score each dimension against a configurable rubric. Teachers can review, override scores, and export PDF/ZIP reports.

## Glossary

- **Grading_Task**: A batch grading job created by a teacher, containing multiple student PDF submissions linked to a specific experiment and rubric.
- **Submission**: A single student's PDF experiment report within a Grading_Task, processed through the AI pipeline.
- **Rubric**: A configurable scoring template defining dimensions (e.g., code correctness, report quality) with weights and max scores per dimension.
- **Rubric_Dimension**: A single scoring axis within a Rubric (e.g., "代码正确性", "实验分析"), with name, description, max score, and weight.
- **Evidence_Block**: A discrete piece of extracted evidence from a PDF page (text, OCR result, or VLM description) with page number, bounding box, content, and confidence.
- **Evidence_Pack**: A compressed collection of 3-8 Evidence_Blocks relevant to a specific rubric dimension, used as input to the LLM scorer.
- **Score_Item**: The AI-generated score for one Rubric_Dimension of one Submission, including numeric score, comment, and references to Evidence_Block IDs.
- **Score_Override**: A teacher's manual correction to a Score_Item, recording old/new values and reason.
- **Grading_Trace**: A log entry recording one pipeline step's execution (timing, model used, tokens consumed, errors).
- **Python_Worker**: A FastAPI + Celery service that executes the AI inference pipeline (PDF parsing, OCR, VLM, evidence building, LLM scoring).
- **Spring_Backend**: The Spring Boot application handling business logic, CRUD, task orchestration, and API endpoints.
- **Redis_Queue**: Redis-based message queue for communication between Spring_Backend and Python_Worker.
- **DeepSeek_Scorer**: The LLM scoring component that evaluates evidence against rubric dimensions and produces structured JSON scores.
- **VLM**: Vision-Language Model used to describe diagram/plot images as structured JSON.
- **PaddleOCR**: OCR engine used to extract text from code screenshots and terminal log images.
- **Image_Classifier**: A component that categorizes extracted PDF images into types: code_screenshot, terminal_log, diagram, plot, or other.

## Requirements

### Requirement 1: Batch PDF Upload and Task Creation

**User Story:** As a teacher, I want to upload a batch of student PDF experiment reports and create a grading task, so that the system can process them automatically using AI.

#### Acceptance Criteria

1. WHEN a teacher selects a course, experiment, and class and uploads 1-200 PDF files, THE Spring_Backend SHALL create a Grading_Task with status PENDING and one Submission per PDF.
2. WHEN a Grading_Task is created, THE Spring_Backend SHALL store each uploaded PDF in MinIO and record the object key in the corresponding Submission.
3. WHEN a Grading_Task is created, THE Spring_Backend SHALL publish a task message to the Redis_Queue for the Python_Worker to consume.
4. IF a teacher uploads a file that is not a valid PDF, THEN THE Spring_Backend SHALL reject that file with a descriptive error and continue processing remaining valid files.
5. IF a teacher uploads more than 200 files in a single batch, THEN THE Spring_Backend SHALL reject the entire request with an error indicating the maximum batch size.
6. WHEN a Grading_Task is created, THE Spring_Backend SHALL associate the task with the selected Rubric.

### Requirement 2: Task Monitoring

**User Story:** As a teacher, I want to monitor the progress and status of my grading tasks, so that I can track completion and identify failures.

#### Acceptance Criteria

1. WHEN a teacher requests the task list, THE Spring_Backend SHALL return all Grading_Tasks for that teacher with status, total count, completed count, and failed count.
2. WHEN a teacher requests task detail, THE Spring_Backend SHALL return the Grading_Task with all its Submissions and their individual statuses.
3. WHILE a Grading_Task is in PROCESSING status, THE Spring_Backend SHALL update completed_count and failed_count as Submissions finish.
4. WHEN a Submission fails processing, THE Spring_Backend SHALL record the error in the Submission status and increment the task's failed_count.
5. WHEN a teacher requests retry for failed Submissions, THE Spring_Backend SHALL re-queue only the failed Submissions to the Redis_Queue and reset their status to PENDING.

### Requirement 3: Rubric Management

**User Story:** As a teacher, I want to create and manage scoring rubrics with configurable dimensions, so that I can apply different grading criteria to different experiments.

#### Acceptance Criteria

1. WHEN a teacher creates a Rubric, THE Spring_Backend SHALL store the Rubric with name, subject, description, and a list of Rubric_Dimensions.
2. THE Spring_Backend SHALL validate that the sum of all Rubric_Dimension weights in a Rubric equals 100.
3. WHEN a teacher updates a Rubric, THE Spring_Backend SHALL prevent modification if the Rubric is referenced by any Grading_Task with status PROCESSING.
4. WHEN a teacher lists Rubrics, THE Spring_Backend SHALL return all Rubrics created by that teacher, filterable by subject.
5. THE Spring_Backend SHALL validate that each Rubric_Dimension has a non-empty name, a max_score greater than zero, and a weight greater than zero.

### Requirement 4: AI Grading Pipeline - PDF Parsing and Image Extraction

**User Story:** As a system operator, I want the Python_Worker to parse PDF submissions and extract text and images with metadata, so that downstream pipeline steps have structured input.

#### Acceptance Criteria

1. WHEN the Python_Worker receives a Submission task, THE Python_Worker SHALL extract all text content from the PDF with page numbers.
2. WHEN the Python_Worker parses a PDF, THE Python_Worker SHALL extract all embedded images with page number and bounding box coordinates.
3. WHEN the Python_Worker extracts an image, THE Image_Classifier SHALL classify the image as one of: code_screenshot, terminal_log, diagram, plot, or other.
4. IF a PDF is corrupted or unreadable, THEN THE Python_Worker SHALL mark the Submission as FAILED with error message "PDF_PARSE_ERROR" and record the failure in Grading_Trace.

### Requirement 5: AI Grading Pipeline - OCR Processing

**User Story:** As a system operator, I want the Python_Worker to perform OCR on code screenshots and terminal logs, so that text content from images is available as evidence.

#### Acceptance Criteria

1. WHEN the Image_Classifier labels an image as code_screenshot or terminal_log, THE Python_Worker SHALL run PaddleOCR on that image.
2. WHEN PaddleOCR produces results, THE Python_Worker SHALL post-process the text to convert fullwidth characters to halfwidth and apply common character corrections.
3. WHEN PaddleOCR produces results, THE Python_Worker SHALL preserve indentation and line breaks in the extracted text.
4. WHEN an Evidence_Block is created from OCR, THE Python_Worker SHALL record the OCR confidence score in the Evidence_Block.
5. IF PaddleOCR fails on an image, THEN THE Python_Worker SHALL log the error in Grading_Trace and continue processing remaining images.

### Requirement 6: AI Grading Pipeline - VLM Processing

**User Story:** As a system operator, I want the Python_Worker to use a Vision-Language Model for diagram and plot images only, so that visual content is described as structured evidence without excessive API calls.

#### Acceptance Criteria

1. WHEN the Image_Classifier labels an image as diagram or plot, THE Python_Worker SHALL call the VLM API with the image.
2. THE Python_Worker SHALL compute a perceptual hash of each image before VLM processing and check the Redis cache for existing results.
3. WHEN a VLM cache hit occurs, THE Python_Worker SHALL use the cached result instead of calling the VLM API.
4. WHEN the VLM returns a result, THE Python_Worker SHALL cache the result in Redis keyed by the image hash.
5. THE VLM SHALL output a short structured JSON description of the image content.
6. IF the VLM API call fails, THEN THE Python_Worker SHALL log the error in Grading_Trace and create an Evidence_Block with kind "vlm_failed" and empty content.

### Requirement 7: AI Grading Pipeline - Evidence Compression and Selection

**User Story:** As a system operator, I want the Python_Worker to compress and select the most relevant evidence per rubric dimension, so that the LLM scorer receives focused input within token limits.

#### Acceptance Criteria

1. WHEN all Evidence_Blocks for a Submission are collected, THE Python_Worker SHALL build an Evidence_Pack of 3-8 Evidence_Blocks per Rubric_Dimension.
2. THE Python_Worker SHALL use BM25 retrieval (via LangChain Retriever) to rank Evidence_Blocks by relevance to each Rubric_Dimension description.
3. WHEN building an Evidence_Pack, THE Python_Worker SHALL include the evidence_id, kind, page, content snippet, and confidence for each selected block.
4. THE Python_Worker SHALL use LangChain Document schema for all Evidence_Blocks and a custom CodeLineSplitter for code content.

### Requirement 8: AI Grading Pipeline - LLM Scoring

**User Story:** As a system operator, I want the DeepSeek_Scorer to evaluate each rubric dimension using the evidence pack, so that each submission receives structured, traceable scores.

#### Acceptance Criteria

1. WHEN an Evidence_Pack is ready for a Rubric_Dimension, THE DeepSeek_Scorer SHALL produce a structured JSON output containing: dimension_id, score, max_score, comment, and a list of evidence_id references.
2. THE DeepSeek_Scorer SHALL return status "NEED_MORE_EVIDENCE" instead of a score when the Evidence_Pack contains insufficient information to make a judgment.
3. WHEN the DeepSeek_Scorer returns a response that does not conform to the expected JSON schema, THE Python_Worker SHALL retry the request up to 3 times.
4. THE Python_Worker SHALL record each DeepSeek API call in Grading_Trace with model name, input tokens, output tokens, and duration.
5. WHEN all dimensions for a Submission are scored, THE Python_Worker SHALL compute the total weighted score and update the Submission record.
6. THE DeepSeek_Scorer SHALL receive only the Evidence_Pack for the current dimension and the Rubric_Dimension description, not the full PDF content.

### Requirement 9: Score Review and Teacher Override

**User Story:** As a teacher, I want to review AI-generated scores with supporting evidence and override any score or comment, so that I maintain final authority over grading.

#### Acceptance Criteria

1. WHEN a teacher views a Submission, THE Spring_Backend SHALL return all Score_Items with their associated Evidence_Blocks.
2. WHEN a teacher overrides a Score_Item, THE Spring_Backend SHALL create a Score_Override record with old score, new score, old comment, new comment, and reason.
3. WHEN a teacher overrides a Score_Item, THE Spring_Backend SHALL update the Score_Item with the new values and recalculate the Submission total score.
4. THE Spring_Backend SHALL preserve the original AI-generated score in the Score_Override record for audit purposes.

### Requirement 10: Report Generation and Export

**User Story:** As a teacher, I want to generate individual PDF reports and batch export all reports as a ZIP, so that I can distribute grading results to students and keep records.

#### Acceptance Criteria

1. WHEN a teacher requests an individual report, THE Python_Worker SHALL generate an HTML-based PDF report containing scores, comments, and evidence references for that Submission.
2. WHEN a teacher requests a batch export, THE Spring_Backend SHALL trigger report generation for all completed Submissions in the task and package them into a ZIP file.
3. WHEN a report is generated, THE Spring_Backend SHALL store the report file in MinIO and record the object key in the report_file table.
4. IF a Submission has Score_Items with status NEED_MORE_EVIDENCE, THEN the report SHALL clearly indicate which dimensions lack sufficient evidence.

### Requirement 11: Traceability and Audit Logging

**User Story:** As a system administrator, I want every grading pipeline step to be logged with timing, model usage, and error details, so that the system is fully auditable and debuggable.

#### Acceptance Criteria

1. WHEN any pipeline step executes, THE Python_Worker SHALL create a Grading_Trace record with step name, status, duration in milliseconds, and any error message.
2. WHEN an LLM or VLM API call is made, THE Grading_Trace SHALL record the model name, input token count, and output token count.
3. WHEN a teacher overrides a score, THE Spring_Backend SHALL record an audit event using the existing AuditService.
4. THE Grading_Trace records SHALL include the submission_id for correlation with the specific Submission being processed.

### Requirement 12: Concurrent Processing and Rate Limiting

**User Story:** As a system operator, I want the system to handle batch processing of 50-200 PDFs concurrently with rate limiting, so that external API services are not overwhelmed.

#### Acceptance Criteria

1. THE Python_Worker SHALL use Celery with Redis broker to process Submissions concurrently with a configurable worker concurrency limit.
2. THE Python_Worker SHALL enforce a configurable rate limit on DeepSeek API calls to prevent exceeding API quotas.
3. THE Python_Worker SHALL enforce a configurable rate limit on VLM API calls independently from DeepSeek rate limits.
4. IF a rate limit is reached, THEN THE Python_Worker SHALL queue the request and retry after the rate limit window resets.
5. WHEN a Celery task fails with a transient error, THE Python_Worker SHALL retry the task up to 3 times with exponential backoff.
