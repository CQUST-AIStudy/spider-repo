$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'ptadatabase'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '123456'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:AI_PROVIDER = 'mock'

$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
Set-Location (Join-Path $projectRoot 'AI_Ds')
& mvn.cmd -q -DskipTests spring-boot:run
