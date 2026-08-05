function Initialize-SpiderPort {
  param([string]$ProjectRoot)

  if (-not $env:SPIDER_PORT) {
    $envFile = Join-Path $ProjectRoot ".env"
    if (Test-Path $envFile) {
      foreach ($line in [System.IO.File]::ReadLines($envFile)) {
        if ($line -match '^\s*SPIDER_PORT\s*=\s*(.*?)\s*$') {
          $env:SPIDER_PORT = $Matches[1].Trim().Trim('"').Trim("'")
          break
        }
      }
    }
  }
  if (-not $env:SPIDER_PORT) {
    $env:SPIDER_PORT = "8100"
  }
  $parsedPort = 0
  if (-not [int]::TryParse($env:SPIDER_PORT, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
    throw "SPIDER_PORT must be an integer between 1 and 65535"
  }
  $env:SPIDER_PORT = $parsedPort.ToString()
}
