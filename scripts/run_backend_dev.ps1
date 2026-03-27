$env:SPRING_PROFILES_ACTIVE = 'dev'
Set-Location 'g:\myapps\AI_Ds'
& 'G:\downloads\apache-maven-3.9.9\bin\mvn.cmd' -q -DskipTests spring-boot:run
