@echo off
call "%~dp0gradlew.bat" build run
if errorlevel 1 (
    echo.
    echo Build or run failed. See errors above.
    pause
)
