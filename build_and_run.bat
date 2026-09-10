@echo off
start "" /B powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0bring-to-front.ps1"
call "%~dp0gradlew.bat" build run
if errorlevel 1 (
    echo.
    echo Build or run failed. See errors above.
    pause
)
