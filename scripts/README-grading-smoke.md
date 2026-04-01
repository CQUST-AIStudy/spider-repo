# Grading Smoke Test

This script exercises the teacher grading flow end-to-end:

1. Login as a teacher
2. Upload a blank experiment template and generate a rubric draft
3. Create the rubric
4. Upload one or more student reports
5. Poll the grading task until submissions leave `PENDING/PROCESSING`
6. Download generated annotated reports
7. Optionally download the batch export zip

## Prerequisites

- Backend is running at `http://localhost:8081`
- Redis, MySQL, and MinIO are reachable by the backend
- Grading worker is running:
  - `g:\myapps\grading_worker\start_worker.ps1`
- The teacher account exists and can call TAP grading APIs

For local dev seeding, the backend only seeds `teacher1` when:

- `SPRING_PROFILES_ACTIVE=dev`
- `TAP_DEV_SEED_USERS_ENABLED=true`
- `TAP_DEV_TEACHER_PASSWORD` is set to a non-default value

## Example

```powershell
powershell -ExecutionPolicy Bypass -File g:\myapps\scripts\grading_e2e_smoke.ps1 `
  -Username teacher1 `
  -Password "<your-password>" `
  -TemplateFile "g:\myapps\templates\blank-report.docx" `
  -StudentFiles "g:\myapps\samples\student-a.docx","g:\myapps\samples\student-b.pdf" `
  -Subject "Data Structure Lab" `
  -RubricName "Experiment 1 Draft" `
  -ExportZip
```

You can also use environment variables:

```powershell
$env:TAP_SMOKE_USERNAME = "teacher1"
$env:TAP_SMOKE_PASSWORD = "<your-password>"
```

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File g:\myapps\scripts\grading_e2e_smoke.ps1 `
  -TemplateFile "g:\myapps\templates\blank-report.docx" `
  -StudentFiles "g:\myapps\samples\student-a.docx"
```

## Output

The script writes artifacts under `g:\myapps\smoke-output\grading` by default:

- `rubric-draft.json`
- `rubric-created.json`
- `task-created.json`
- `task-final.json`
- `smoke-summary.json`
- `reports\...`
- `grading-export-task-<taskId>.zip` when `-ExportZip` is used

If the task times out, inspect backend logs and confirm the grading worker is consuming Redis tasks.
