@echo off
REM Build script for ASTM Interface Service

echo Building ASTM Interface Service...
echo.

REM Clean and build all modules
echo [1/3] Cleaning previous builds...
call mvn clean

echo.
echo [2/3] Building all modules...
call mvn install

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo [3/3] Build completed successfully!
echo.

echo Created artifacts:
echo - astm-server\target\astm-server-1.0.0.jar (Main server application)
echo - instrument-simulator\target\instrument-simulator-1.0.0-shaded.jar (Testing simulator)
echo.

echo To run the server: java -jar astm-server\target\astm-server-1.0.0.jar
echo To run the simulator: java -jar instrument-simulator\target\instrument-simulator-1.0.0-shaded.jar
echo.

pause
