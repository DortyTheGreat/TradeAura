@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem === Parse -dev / -release (default: -dev) ===
set BUILD_TYPE=dev
if /i "%~1"=="-release" set BUILD_TYPE=release
if /i "%~1"=="-dev" set BUILD_TYPE=dev

rem === Load machine-specific settings (not committed, see deploy-prism.local.bat.example) ===
if not exist "deploy-prism.local.bat" (
    echo [test-addon] Missing deploy-prism.local.bat - copy deploy-prism.local.bat.example to deploy-prism.local.bat and fill in your paths.
    exit /b 1
)
call deploy-prism.local.bat

if "%MODS_DIR%"=="" (
    echo [test-addon] MODS_DIR is not set in deploy-prism.local.bat
    exit /b 1
)

rem === Read version.properties ===
set VERSION_CODE=a
set DEV_NUMBER=0
if exist "version.properties" (
    for /f "usebackq tokens=1,2 delims==" %%a in ("version.properties") do (
        if "%%a"=="version_code" set VERSION_CODE=%%b
        if "%%a"=="dev_number" set DEV_NUMBER=%%b
    )
)

if "%BUILD_TYPE%"=="release" (
    echo.
    echo ================================================
    echo   RELEASE BUILD
    echo   Current version code: %VERSION_CODE%
    echo ================================================
    set /p NEWCODE="Confirm version code (Enter to keep '%VERSION_CODE%', or type a new one, e.g. g): "
    if not "!NEWCODE!"=="" set VERSION_CODE=!NEWCODE!

    echo.
    echo This will build TradeAura-*-!VERSION_CODE!.jar, archive it in releases\, deploy it and reset dev_number to 0.
    echo Don't forget to add an entry for it in CHANGELOG.md afterwards.
    set /p CONFIRM="Proceed? (y/N): "
    if /i not "!CONFIRM!"=="y" (
        echo [test-addon] Release cancelled.
        exit /b 1
    )

    set DEV_NUMBER=0
    > version.properties echo version_code=!VERSION_CODE!
    >> version.properties echo dev_number=0

    set GRADLE_ARGS=-PbuildType=release
) else (
    set /a DEV_NUMBER=%DEV_NUMBER%+1
    > version.properties echo version_code=%VERSION_CODE%
    >> version.properties echo dev_number=%DEV_NUMBER%

    set GRADLE_ARGS=-PbuildType=dev
)

echo [test-addon] Building (%BUILD_TYPE%)...
call gradlew.bat jar %GRADLE_ARGS%
if errorlevel 1 (
    echo [test-addon] Build failed, aborting.
    exit /b 1
)

rem === Find the freshly built jar (newest first, skip -sources jars) ===
set JAR=
if exist "build\libs\TradeAura-*.jar" (
    for /f "delims=" %%f in ('dir /b /o-d "build\libs\TradeAura-*.jar" ^| findstr /v /i "sources"') do (
        if "!JAR!"=="" set JAR=build\libs\%%f
    )
)
if "%JAR%"=="" (
    echo [test-addon] Could not find a built jar in build\libs
    exit /b 1
)
echo [test-addon] Built: %JAR%

rem === Release builds get archived - jars are small, keeping them in git is fine ===
if "%BUILD_TYPE%"=="release" (
    if not exist "releases" mkdir "releases"
    copy /y "%JAR%" "releases\" >nul
    echo [test-addon] Archived to releases\%JAR:build\libs\=%

    for /f "usebackq delims=" %%s in (`powershell -NoProfile -Command "$s = (Get-ChildItem 'releases' -File -ErrorAction SilentlyContinue ^| Measure-Object -Property Length -Sum).Sum; if (-not $s) { $s = 0 }; [int64]$s"`) do set RELEASES_BYTES=%%s
    if not "!RELEASES_BYTES!"=="" (
        if !RELEASES_BYTES! GTR 5242880 (
            set /a RELEASES_MB=!RELEASES_BYTES!/1048576
            echo [test-addon] WARNING: releases\ is now ~!RELEASES_MB! MB ^(over 5 MB^) - consider trimming old jars before committing.
        )
    )
)

rem === Stop the instance if it's currently running, so the new jar is actually picked up ===
rem PrismLauncher passes -Djava.library.path with forward slashes (.../instances/<name>/natives),
rem even on Windows - confirmed from a real running process's CommandLine, so match on / not \.
if not "%PRISM_INSTANCE%"=="" (
    echo [test-addon] Checking for a running instance...
    powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' or Name='java.exe'\" | Where-Object { $_.CommandLine -like '*instances/%PRISM_INSTANCE%/*' } | ForEach-Object { Write-Host '[test-addon] Stopping running instance (PID' $_.ProcessId ')'; Stop-Process -Id $_.ProcessId -Force; Start-Sleep -Seconds 1 }"
)

rem === Swap the jar into the mods folder ===
if not exist "%MODS_DIR%" mkdir "%MODS_DIR%"
del /q "%MODS_DIR%\TradeAura-*.jar" 2>nul
copy /y "%JAR%" "%MODS_DIR%\" >nul
echo [test-addon] Deployed to %MODS_DIR%

rem === Launch the instance ===
if not "%PRISM_EXE%"=="" if not "%PRISM_INSTANCE%"=="" (
    echo [test-addon] Launching %PRISM_INSTANCE%...
    start "" "%PRISM_EXE%" -l "%PRISM_INSTANCE%"
)

endlocal
