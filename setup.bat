@echo off
REM JobLens Setup Script (Windows)
REM Automates environment setup and project build

setlocal enabledelayedexpansion

echo.
echo ========================================
echo 🚀 JobLens Setup Script
echo ========================================
echo.

REM Check Java
echo 📋 Checking prerequisites...

where java >nul 2>nul
if errorlevel 1 (
    echo ❌ Java not found. Please install Java 21+
    exit /b 1
)
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /r "version"') do (
    set JAVA_VERSION=%%g
)
echo ✅ Java %JAVA_VERSION% found

REM Check Maven
where mvn >nul 2>nul
if errorlevel 1 (
    echo ❌ Maven not found. Please install Maven 3.8+
    exit /b 1
)
echo ✅ Maven found

echo.
echo 📁 Setting up project structure...

REM Create .env file if doesn't exist
if not exist .env (
    copy .env.example .env
    echo ✅ Created .env file from template
    echo.
    echo ⚠️  IMPORTANT: Edit .env with your Gmail credentials
    echo    - GMAIL_CLIENT_ID
    echo    - GMAIL_CLIENT_SECRET
    echo    - GMAIL_USER_EMAIL
    echo    - GMAIL_APP_PASSWORD
    echo    - RECIPIENT_EMAIL
    echo.
    echo Press any key after updating .env file...
    pause >nul
) else (
    echo ✅ .env file already exists
)

echo.
echo 🔨 Building project...

REM Build project
call mvn clean package -DskipTests

if exist target\job-email-filter-1.0.0.jar (
    echo ✅ Build successful!
    echo.
    echo ========================================
    echo ✅ Setup Complete!
    echo ========================================
    echo.
    echo To start JobLens, run one of:
    echo   java -jar target/job-email-filter-1.0.0.jar
    echo   mvn spring-boot:run
    echo   docker-compose up -d
    echo.
    echo For more help, see:
    echo   - QUICK_START.md (5-minute setup^)
    echo   - GMAIL_SETUP_GUIDE.md (OAuth setup^)
    echo.
) else (
    echo ❌ Build failed. Check logs above.
    exit /b 1
)

endlocal
