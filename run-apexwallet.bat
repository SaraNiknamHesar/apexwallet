@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.3"
set "MVN=C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd"
set "NODE_DIR=C:\Program Files\nodejs"

REM High, unlikely-to-be-taken ports so this demo can run beside other projects.
set "API_PORT=37917"
set "ASSIST_PORT=37919"
set "WEB_PORT=37918"

echo [1/5] freeing ports %API_PORT%, %ASSIST_PORT% and %WEB_PORT% if something else holds them
for %%P in (%API_PORT% %ASSIST_PORT% %WEB_PORT%) do (
  for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:":%%P " ^| findstr LISTENING') do taskkill /PID %%p /F >nul 2>&1
)

echo [2/5] loading model keys from .env
if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)
if defined APEX_MODEL_URL echo   model url: %APEX_MODEL_URL%
if not defined APEX_MODEL_URL echo   WARNING: no APEX_MODEL_URL, the Assistant page will say "the hosted model is not connected"
if not defined APEX_MODEL_TOKEN echo   WARNING: APEX_MODEL_TOKEN is empty, the Assistant will refuse to answer

echo [3/5] building (tests skipped)
set "BUILD=0"
if /i "%~1"=="--build" set "BUILD=1"
if not exist apexwallet-app\target\apexwallet-app-0.8.0-SNAPSHOT.jar set "BUILD=1"
if not exist apexwallet-assistant\target\apexwallet-assistant-0.8.0-SNAPSHOT.jar set "BUILD=1"
if "%BUILD%"=="0" (
  echo   jars already present, skipping Maven - run "run-apexwallet.bat --build" to rebuild
  goto startall
)
call "%MVN%" -B -DskipTests package
if errorlevel 1 (
  echo BUILD FAILED - see the Maven output above.
  pause
  exit /b 1
)

:startall

echo [4/5] staging jars off the slow drive and starting assistant (%ASSIST_PORT%), API (%API_PORT%) and UI (%WEB_PORT%)
set "RUN_DIR=%LOCALAPPDATA%\ApexWallet\run"
if not exist "%RUN_DIR%\app" mkdir "%RUN_DIR%\app"
if not exist "%RUN_DIR%\assistant" mkdir "%RUN_DIR%\assistant"
REM G: reads at ~2 MB/s, so the 105 MB of jars boot 6x faster from C:. /XO copies only when the source is newer.
robocopy "apexwallet-app\target" "%RUN_DIR%\app" "apexwallet-app-0.8.0-SNAPSHOT.jar" /XO /NJH /NJS /NDL /NC /NS >nul
robocopy "apexwallet-assistant\target" "%RUN_DIR%\assistant" "apexwallet-assistant-0.8.0-SNAPSHOT.jar" /XO /NJH /NJS /NDL /NC /NS >nul
if errorlevel 8 echo   WARNING: could not stage the jars into %RUN_DIR%
start "ApexWallet Assistant %ASSIST_PORT%" /min /d "%~dp0" "%JAVA_HOME%\bin\java.exe" -jar "%RUN_DIR%\assistant\apexwallet-assistant-0.8.0-SNAPSHOT.jar" --server.port=%ASSIST_PORT%
start "ApexWallet API %API_PORT%" /min /d "%~dp0" "%JAVA_HOME%\bin\java.exe" -jar "%RUN_DIR%\app\apexwallet-app-0.8.0-SNAPSHOT.jar" --spring.profiles.active=local --server.port=%API_PORT% --apex.assistant-url=http://127.0.0.1:%ASSIST_PORT% --apex.cors-allowed-origins=http://localhost:%WEB_PORT%
set "APEX_API_URL=http://localhost:%API_PORT%"
start "ApexWallet UI %WEB_PORT%" /min /d "%~dp0web" "%NODE_DIR%\npm.cmd" run dev

echo [5/5] waiting for all three to answer, then opening the browser
set /a tries=0
:wait
set /a tries+=1
curl -s -o nul http://localhost:%API_PORT%/actuator/health && curl -s -o nul http://127.0.0.1:%ASSIST_PORT%/health && curl -s -o nul http://localhost:%WEB_PORT%/ && goto ready
if %tries% GEQ 90 goto timeout
"%SystemRoot%\System32\timeout.exe" /t 2 /nobreak >nul
goto wait

:timeout
echo.
echo Something is not answering yet. Check the three minimised windows.
echo   API http://localhost:%API_PORT%/actuator/health  ^| Assistant http://127.0.0.1:%ASSIST_PORT%/health  ^| UI http://localhost:%WEB_PORT%
start "" http://localhost:%WEB_PORT%
pause
exit /b 0

:ready
echo.
echo ApexWallet is up.
echo   API        http://localhost:%API_PORT%/actuator/health
echo   Docs       http://localhost:%API_PORT%/swagger-ui.html
echo   Assistant  http://127.0.0.1:%ASSIST_PORT%/health  (Tools page chats through it, model=%APEX_MODEL_URL%)
echo   UI         http://localhost:%WEB_PORT%         demo login: alice / alice-pass-1
echo.
echo Close this window when you are done; the app keeps running.
echo Stop it later with:  stop-apexwallet.bat
start "" http://localhost:%WEB_PORT%
pause
exit /b 0
