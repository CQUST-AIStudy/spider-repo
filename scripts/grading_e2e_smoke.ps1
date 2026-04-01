param(
    [string]$BackendBaseUrl = "http://localhost:8081",
    [string]$Username = $env:TAP_SMOKE_USERNAME,
    [string]$Password = $env:TAP_SMOKE_PASSWORD,
    [Parameter(Mandatory = $true)]
    [string]$TemplateFile,
    [Parameter(Mandatory = $true)]
    [string[]]$StudentFiles,
    [string]$Subject = "",
    [string]$RubricName = "",
    [string]$OutputDir = "",
    [int]$TimeoutSeconds = 900,
    [int]$PollIntervalSeconds = 8,
    [string]$ExperimentId = "",
    [string]$ClassId = "",
    [string]$ScoreRangeMin = "",
    [string]$ScoreRangeMax = "",
    [switch]$ExportZip
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

function Resolve-FullPath {
    param([Parameter(Mandatory = $true)][string]$PathValue)
    $resolved = Resolve-Path -LiteralPath $PathValue
    return $resolved.Path
}

function New-HttpClient {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(180)
    return $client
}

function Add-AuthHeader {
    param(
        [Parameter(Mandatory = $true)]$Headers,
        [string]$Token
    )
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
}

function Read-ApiJson {
    param([string]$BodyText)
    if ([string]::IsNullOrWhiteSpace($BodyText)) {
        return $null
    }
    return $BodyText | ConvertFrom-Json -Depth 100
}

function Get-ApiData {
    param([object]$Payload)
    if ($null -eq $Payload) {
        return $null
    }
    if ($Payload.PSObject.Properties.Name -contains "data") {
        return $Payload.data
    }
    return $Payload
}

function Format-ErrorMessage {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [string]$BodyText
    )
    try {
        $payload = Read-ApiJson $BodyText
        if ($payload -and ($payload.PSObject.Properties.Name -contains "message") -and $payload.message) {
            return "$([int]$Response.StatusCode) $($payload.message)"
        }
    } catch {
    }
    if (-not [string]::IsNullOrWhiteSpace($BodyText)) {
        return "$([int]$Response.StatusCode) $BodyText"
    }
    return "$([int]$Response.StatusCode) $($Response.ReasonPhrase)"
}

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PUT", "DELETE")] [string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$Token,
        [object]$Body = $null
    )

    $client = New-HttpClient
    try {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::$Method, $Url)
        Add-AuthHeader -Headers $request.Headers -Token $Token
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 100
            $request.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
        }

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $bodyText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw (Format-ErrorMessage -Response $response -BodyText $bodyText)
        }
        return Read-ApiJson $bodyText
    } finally {
        $client.Dispose()
    }
}

function New-FileContent {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [string]$ContentType = "application/octet-stream"
    )

    $bytes = [System.IO.File]::ReadAllBytes($PathValue)
    $content = New-Object System.Net.Http.ByteArrayContent($bytes)
    $content.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue($ContentType)
    return $content
}

function Invoke-MultipartRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$Token,
        [hashtable]$Fields = @{},
        [array]$Files = @()
    )

    $client = New-HttpClient
    $multipart = New-Object System.Net.Http.MultipartFormDataContent
    try {
        foreach ($key in $Fields.Keys) {
            $value = $Fields[$key]
            if ($null -eq $value) {
                continue
            }
            $stringValue = [string]$value
            if ([string]::IsNullOrWhiteSpace($stringValue)) {
                continue
            }
            $multipart.Add((New-Object System.Net.Http.StringContent($stringValue, [System.Text.Encoding]::UTF8)), $key)
        }

        foreach ($file in $Files) {
            $fileContent = New-FileContent -PathValue $file.Path -ContentType $file.ContentType
            $multipart.Add($fileContent, $file.FieldName, [System.IO.Path]::GetFileName($file.Path))
        }

        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $Url)
        Add-AuthHeader -Headers $request.Headers -Token $Token
        $request.Content = $multipart

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $bodyText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw (Format-ErrorMessage -Response $response -BodyText $bodyText)
        }
        return Read-ApiJson $bodyText
    } finally {
        $multipart.Dispose()
        $client.Dispose()
    }
}

function Download-File {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$Token,
        [Parameter(Mandatory = $true)][string]$TargetPath
    )

    $client = New-HttpClient
    try {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $Url)
        Add-AuthHeader -Headers $request.Headers -Token $Token
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $bodyText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            throw (Format-ErrorMessage -Response $response -BodyText $bodyText)
        }
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        [System.IO.File]::WriteAllBytes($TargetPath, $bytes)
        return $response
    } finally {
        $client.Dispose()
    }
}

function Save-JsonFile {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][object]$Data
    )
    $json = $Data | ConvertTo-Json -Depth 100
    Set-Content -LiteralPath $PathValue -Value $json -Encoding UTF8
}

function Get-SubmissionSummary {
    param([array]$Submissions)
    $groups = @{}
    foreach ($submission in $Submissions) {
        $status = [string]$submission.status
        if (-not $groups.ContainsKey($status)) {
            $groups[$status] = 0
        }
        $groups[$status]++
    }
    $parts = @()
    foreach ($key in ($groups.Keys | Sort-Object)) {
        $parts += "${key}=$($groups[$key])"
    }
    return ($parts -join ", ")
}

function Test-TaskFinished {
    param([array]$Submissions)
    foreach ($submission in $Submissions) {
        if ($submission.status -in @("PENDING", "PROCESSING")) {
            return $false
        }
    }
    return $true
}

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "Username and Password are required. Pass -Username/-Password or set TAP_SMOKE_USERNAME/TAP_SMOKE_PASSWORD."
}

$templatePath = Resolve-FullPath -PathValue $TemplateFile
$studentPaths = @()
foreach ($studentFile in $StudentFiles) {
    $studentPaths += Resolve-FullPath -PathValue $studentFile
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $PSScriptRoot "..\\smoke-output\\grading"
}
$outputPath = [System.IO.Path]::GetFullPath($OutputDir)
if (-not (Test-Path -LiteralPath $outputPath)) {
    New-Item -ItemType Directory -Path $outputPath | Out-Null
}

$baseUrl = $BackendBaseUrl.TrimEnd("/")

Write-Host "Logging in: $Username" -ForegroundColor Cyan
$loginPayload = Invoke-JsonRequest -Method POST -Url "$baseUrl/api/auth/login" -Body @{
    username = $Username
    password = $Password
}
$auth = Get-ApiData $loginPayload
$token = [string]$auth.accessToken
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login succeeded but accessToken is missing."
}

$draftName = if ([string]::IsNullOrWhiteSpace($RubricName)) { "Auto Draft $(Get-Date -Format 'yyyyMMdd-HHmmss')" } else { $RubricName }

Write-Host "Generating rubric draft from template..." -ForegroundColor Cyan
$draftPayload = Invoke-MultipartRequest -Url "$baseUrl/api/grading/rubrics/draft" -Token $token -Fields @{
    subject = $Subject
    name = $draftName
} -Files @(
    @{
        FieldName = "templateFile"
        Path = $templatePath
        ContentType = "application/octet-stream"
    }
)
$draft = Get-ApiData $draftPayload
Save-JsonFile -PathValue (Join-Path $outputPath "rubric-draft.json") -Data $draft

$rubricBody = @{
    name = if ($draft.name) { $draft.name } else { $draftName }
    subject = if ($draft.subject) { $draft.subject } else { $Subject }
    description = if ($draft.description) { $draft.description } else { "" }
    customPrompt = if ($draft.customPrompt) { $draft.customPrompt } else { "" }
    dimensions = @()
}

foreach ($dimension in $draft.dimensions) {
    $rubricBody.dimensions += @{
        name = [string]$dimension.name
        description = [string]$dimension.description
        maxScore = [decimal]$dimension.maxScore
        weight = [int]$dimension.weight
    }
}

Write-Host "Creating rubric..." -ForegroundColor Cyan
$rubricPayload = Invoke-JsonRequest -Method POST -Url "$baseUrl/api/grading/rubrics" -Token $token -Body $rubricBody
$rubric = Get-ApiData $rubricPayload
Save-JsonFile -PathValue (Join-Path $outputPath "rubric-created.json") -Data $rubric

$taskFields = @{
    rubricId = [string]$rubric.id
    experimentId = $ExperimentId
    classId = $ClassId
    scoreRangeMin = $ScoreRangeMin
    scoreRangeMax = $ScoreRangeMax
}
$taskFiles = @()
foreach ($studentPath in $studentPaths) {
    $taskFiles += @{
        FieldName = "files"
        Path = $studentPath
        ContentType = "application/octet-stream"
    }
}

Write-Host "Creating grading task for $($studentPaths.Count) student file(s)..." -ForegroundColor Cyan
$taskPayload = Invoke-MultipartRequest -Url "$baseUrl/api/grading/tasks" -Token $token -Fields $taskFields -Files $taskFiles
$task = Get-ApiData $taskPayload
Save-JsonFile -PathValue (Join-Path $outputPath "task-created.json") -Data $task
$taskId = [int64]$task.taskId

Write-Host "Polling task $taskId ..." -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$latestTaskDetail = $null
do {
    Start-Sleep -Seconds $PollIntervalSeconds
    $taskDetailPayload = Invoke-JsonRequest -Method GET -Url "$baseUrl/api/grading/tasks/$taskId" -Token $token
    $latestTaskDetail = Get-ApiData $taskDetailPayload
    $submissionSummary = Get-SubmissionSummary -Submissions $latestTaskDetail.submissions
    Write-Host ("[{0}] taskStatus={1}; submissions: {2}" -f (Get-Date -Format "HH:mm:ss"), $latestTaskDetail.status, $submissionSummary)
    if (Test-TaskFinished -Submissions $latestTaskDetail.submissions) {
        break
    }
} while ((Get-Date) -lt $deadline)

if ($null -eq $latestTaskDetail) {
    throw "Task detail polling did not return any payload."
}

Save-JsonFile -PathValue (Join-Path $outputPath "task-final.json") -Data $latestTaskDetail

$downloadDir = Join-Path $outputPath "reports"
if (-not (Test-Path -LiteralPath $downloadDir)) {
    New-Item -ItemType Directory -Path $downloadDir | Out-Null
}

$downloaded = @()
foreach ($submission in $latestTaskDetail.submissions) {
    if (-not $submission.hasDownloadableReport) {
        continue
    }
    $baseName = if ([string]::IsNullOrWhiteSpace([string]$submission.studentName)) {
        "submission-$($submission.submissionId)"
    } else {
        ([string]$submission.studentName) -replace '[\\/:*?""<>|]', '_'
    }
    $extension = if ($submission.preferredReportFileType -eq "annodoc") { ".docx" } else { ".pdf" }
    $targetPath = Join-Path $downloadDir ($baseName + $extension)
    Write-Host "Downloading report for submission $($submission.submissionId) -> $targetPath" -ForegroundColor Green
    Download-File -Url "$baseUrl/api/grading/reports/$($submission.submissionId)" -Token $token -TargetPath $targetPath | Out-Null
    $downloaded += $targetPath
}

$zipPath = $null
if ($ExportZip) {
    $zipPath = Join-Path $outputPath ("grading-export-task-" + $taskId + ".zip")
    Write-Host "Downloading batch export zip..." -ForegroundColor Cyan
    Download-File -Url "$baseUrl/api/grading/tasks/$taskId/export" -Token $token -TargetPath $zipPath | Out-Null
}

$summary = [ordered]@{
    backendBaseUrl = $baseUrl
    taskId = $taskId
    rubricId = $rubric.id
    outputDir = $outputPath
    downloadedReports = $downloaded
    batchZip = $zipPath
    submissions = $latestTaskDetail.submissions
}
Save-JsonFile -PathValue (Join-Path $outputPath "smoke-summary.json") -Data $summary

$successCount = @($downloaded).Count
$terminalCount = @($latestTaskDetail.submissions).Count
Write-Host ""
Write-Host "Smoke test completed." -ForegroundColor Green
Write-Host "Task ID: $taskId"
Write-Host "Rubric ID: $($rubric.id)"
Write-Host "Terminal submissions: $terminalCount"
Write-Host "Downloaded reports: $successCount"
Write-Host "Artifacts: $outputPath"

if (-not (Test-TaskFinished -Submissions $latestTaskDetail.submissions)) {
    throw "Task did not finish within timeout. Check backend logs, Redis, and grading worker."
}
