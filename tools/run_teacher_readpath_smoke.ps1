$ErrorActionPreference = 'Stop'

function Start-Backend {
    param(
        [string]$Tag,
        [hashtable]$ExtraEnv = @{}
    )

    $spiderDir = Split-Path -Parent $PSScriptRoot
    $runtimeDir = if ($env:PTA_RUNTIME_DIR) { $env:PTA_RUNTIME_DIR } else { Join-Path $spiderDir "runtime" }
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
    $outLog = Join-Path $runtimeDir "backend-$Tag.out.log"
    $errLog = Join-Path $runtimeDir "backend-$Tag.err.log"
    if (Test-Path $outLog) { Remove-Item -LiteralPath $outLog -Force }
    if (Test-Path $errLog) { Remove-Item -LiteralPath $errLog -Force }

    $envLines = @(
        '$env:DB_HOST = ''127.0.0.1''',
        '$env:DB_PORT = ''3306''',
        '$env:DB_NAME = ''ptadatabase''',
        '$env:DB_USERNAME = ''root''',
        '$env:DB_PASSWORD = ''123456''',
        '$env:SPRING_PROFILES_ACTIVE = ''dev''',
        '$env:AI_PROVIDER = ''mock'''
    )

    foreach ($entry in $ExtraEnv.GetEnumerator()) {
        $envLines += ('$env:{0} = ''{1}''' -f $entry.Key, ($entry.Value -replace '''', ''''''))
    }

    $command = @(
        '& {',
        ($envLines -join '; '),
        ('; Set-Location ''{0}''' -f (Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'AI_Ds')),
        '; & mvn.cmd -q -DskipTests spring-boot:run',
        '}'
    ) -join ' '

    $proc = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

    $healthy = $false
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/actuator/health' -TimeoutSec 3
            if ($health.status -eq 'UP') {
                $healthy = $true
                break
            }
        } catch {
        }

        if ($proc.HasExited) {
            break
        }
    }

    if (-not $healthy) {
        $outTail = if (Test-Path $outLog) { Get-Content -Path $outLog -Tail 80 | Out-String } else { '' }
        $errTail = if (Test-Path $errLog) { Get-Content -Path $errLog -Tail 80 | Out-String } else { '' }
        if (-not $proc.HasExited) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        }
        throw "Backend failed to become healthy for tag=$Tag.`nSTDOUT:`n$outTail`nSTDERR:`n$errTail"
    }

    return [pscustomobject]@{
        Process = $proc
        OutLog = $outLog
        ErrLog = $errLog
    }
}

function Stop-Backend {
    param($Handle)

    if ($null -eq $Handle) {
        return
    }
    $proc = $Handle.Process
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
    }
}

function Login-Teacher {
    $candidates = @('password123', '123456', 'teacher123', 'admin123')
    foreach ($password in $candidates) {
        $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
        try {
            $response = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/login' `
                -Method Post `
                -WebSession $session `
                -ContentType 'application/json' `
                -Body (@{ username = 'teacher1'; password = $password } | ConvertTo-Json -Compress)
            if ($response.success) {
                return [pscustomobject]@{
                    Session = $session
                    Password = $password
                    User = $response.user
                }
            }
        } catch {
        }
    }

    throw 'Unable to login as teacher1 with known local password candidates.'
}

function Normalize-ExperimentsResponse {
    param($Response)

    if ($null -eq $Response) { return @() }
    if ($Response -is [System.Array]) { return $Response }
    if ($Response.data -is [System.Array]) { return $Response.data }
    return @()
}

function Normalize-AssignmentsResponse {
    param($Response)

    if ($null -eq $Response) { return @() }
    if ($Response -is [System.Array]) { return $Response }
    if ($Response.success -and $Response.data -is [System.Array]) { return $Response.data }
    if ($Response.data -is [System.Array]) { return $Response.data }
    return @()
}

function Invoke-TeacherReadPathSmoke {
    param(
        [string]$Label
    )

    $login = Login-Teacher
    $session = $login.Session
    $experimentsRaw = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/teacher/experiments' -WebSession $session -TimeoutSec 10
    $experiments = Normalize-ExperimentsResponse $experimentsRaw
    $assignmentsRaw = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/teacher/allStudentExperiments' -WebSession $session -TimeoutSec 15
    $assignments = Normalize-AssignmentsResponse $assignmentsRaw

    $sample = $assignments |
        Where-Object { $_.studentId -and $_.experimentId -and $_.status -ne 'not_started' } |
        Select-Object -First 1
    if ($null -eq $sample) {
        $sample = $assignments |
            Where-Object { $_.studentId -and $_.experimentId } |
            Select-Object -First 1
    }
    if ($null -eq $sample) {
        throw "No teacher assignment row available for $Label smoke."
    }

    $submissionId = '{0}-{1}' -f $sample.studentId, $sample.experimentId
    $detail = Invoke-RestMethod -Uri ("http://127.0.0.1:8081/api/submissions/{0}" -f $submissionId) -WebSession $session -TimeoutSec 15

    return [pscustomobject]@{
        label = $Label
        loginUser = $login.User.username
        loginPassword = $login.Password
        experimentCount = @($experiments).Count
        assignmentCount = @($assignments).Count
        firstExperiment = if (@($experiments).Count -gt 0) { $experiments[0] } else { $null }
        sampleSubmissionId = $submissionId
        sampleAssignment = $sample
        detail = [pscustomobject]@{
            success = $detail.success
            studentId = $detail.studentId
            experimentId = $detail.experimentId
            experimentName = $detail.experimentName
            status = $detail.status
            hasCode = -not [string]::IsNullOrWhiteSpace($detail.code)
            hasReport = -not [string]::IsNullOrWhiteSpace($detail.report)
            score = $detail.score
        }
    }
}

$result = [ordered]@{}
$normalHandle = $null
$rollbackHandle = $null

try {
    $normalHandle = Start-Backend -Tag 'normal'
    $result.normal = Invoke-TeacherReadPathSmoke -Label 'normal'
}
finally {
    Stop-Backend -Handle $normalHandle
}

try {
    $rollbackHandle = Start-Backend -Tag 'rollback' -ExtraEnv @{
        TAP_TEACHER_UNIFIED_EXPERIMENT_QUERIES_ENABLED = 'false'
        TAP_TEACHER_UNIFIED_SUBMISSION_DETAIL_ENABLED = 'false'
        TAP_TEACHER_SUBMISSION_DETAIL_LEGACY_CODE_FALLBACK_ENABLED = 'true'
        TAP_TEACHER_SUBMISSION_DETAIL_LEGACY_REPORT_FALLBACK_ENABLED = 'true'
        TAP_TEACHER_SUBMISSION_DETAIL_LEGACY_AI_REMARKS_FALLBACK_ENABLED = 'true'
    }
    $result.rollback = Invoke-TeacherReadPathSmoke -Label 'rollback'
}
finally {
    Stop-Backend -Handle $rollbackHandle
}

$resultJson = $result | ConvertTo-Json -Depth 8
Write-Output $resultJson
