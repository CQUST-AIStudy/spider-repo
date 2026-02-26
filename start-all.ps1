# 一键启动所有服务
# 用法: 在 G:\myapps 目录下运行 powershell -ExecutionPolicy Bypass -File start-all.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  启动智能教学系统 - 所有服务" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. 启动 AI_Ds 后端 (端口 8081)
Write-Host "`n[1/3] 启动 AI_Ds 后端 (端口 8081)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\AI_Ds && mvnw.cmd spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 3

# 2. 启动 tap-backend (端口 8080)
Write-Host "[2/3] 启动 tap-backend (端口 8080)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\teacher-assistant-platform && mvn -q -pl :tap-common install && mvn -q -pl :tap-backend spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 3

# 3. 启动 Vue 前端
Write-Host "[3/3] 启动 Vue 前端..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\AI_Ds-vue && npm run serve" -WindowStyle Normal

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  所有服务已启动!" -ForegroundColor Green
Write-Host "  AI_Ds 后端:    http://localhost:8081" -ForegroundColor White
Write-Host "  tap-backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  Vue 前端:      http://localhost:8082 (或自动分配)" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
