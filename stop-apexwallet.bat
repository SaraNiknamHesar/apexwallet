@echo off
setlocal
cd /d "%~dp0"

echo stopping ApexWallet (ports 37917, 37919 and 37918 only)
for %%P in (37917 37919 37918) do (
  for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:":%%P " ^| findstr LISTENING') do (
    taskkill /PID %%p /F >nul 2>&1 && echo   stopped PID %%p on port %%P
  )
)
echo done.
pause
