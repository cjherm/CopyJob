@echo off
start "" /B powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0bring-to-front.ps1"
call "%~dp0gradlew.bat" run
if errorlevel 1 (
    echo.
    echo Run failed. See errors above.
    pause
)
