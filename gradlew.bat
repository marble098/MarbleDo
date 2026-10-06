@echo off
setlocal EnableExtensions
set ROOT=%~dp0
set PROPS=%ROOT%gradle\wrapper\gradle-wrapper.properties
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPS%"') do set URL=%%B
set URL=%URL:\:=:%
for %%F in ("%URL%") do set ZIP=%%~nxF
set VERSION=%ZIP:gradle-=%
set VERSION=%VERSION:-bin.zip=%
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set INSTALL=%GRADLE_USER_HOME%\wrapper\dists\marbledo-%VERSION%
set GRADLE=%INSTALL%\gradle-%VERSION%\bin\gradle.bat
if exist "%GRADLE%" goto run
where curl.exe >nul 2>nul || (echo curl.exe is required to bootstrap Gradle.& exit /b 2)
where powershell.exe >nul 2>nul || (echo PowerShell is required to verify and extract Gradle.& exit /b 2)
if not exist "%INSTALL%" mkdir "%INSTALL%"
set TMP=%INSTALL%\.download-%RANDOM%
mkdir "%TMP%"
curl.exe --fail --location --retry 3 --output "%TMP%\%ZIP%" "%URL%"
if errorlevel 1 exit /b 3
curl.exe --fail --location --retry 3 --output "%TMP%\%ZIP%.sha256" "%URL%.sha256"
if errorlevel 1 exit /b 3
powershell.exe -NoProfile -Command "$expected=(Get-Content -Raw '%TMP%\%ZIP%.sha256').Trim(); $actual=(Get-FileHash -Algorithm SHA256 '%TMP%\%ZIP%').Hash.ToLower(); if ($expected -ne $actual) { throw 'Gradle checksum mismatch' }; Expand-Archive -LiteralPath '%TMP%\%ZIP%' -DestinationPath '%TMP%'"
if errorlevel 1 exit /b 4
move "%TMP%\gradle-%VERSION%" "%INSTALL%\gradle-%VERSION%" >nul
rmdir /s /q "%TMP%"
:run
"%GRADLE%" -p "%ROOT%" %*
exit /b %ERRORLEVEL%
