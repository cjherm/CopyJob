@echo off
call "%~dp0gradlew.bat" run
if errorlevel 1 (
    echo.
    echo Run failed. See errors above.
    pause
)
