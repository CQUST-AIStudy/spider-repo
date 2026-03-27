$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '123456' }
Set-Location 'g:\myapps\AI_Ds'
& 'G:\downloads\apache-maven-3.9.9\bin\mvn.cmd' -q -DskipTests spring-boot:run
