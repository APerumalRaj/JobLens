#!/bin/bash
# JobLens Setup Script
# Automates environment setup and project build

set -e  # Exit on any error

echo "========================================"
echo "🚀 JobLens Setup Script"
echo "========================================"
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 21+"
    exit 1
fi
java_version=$(java -version 2>&1 | grep version | awk '{print $3}' | tr -d '"')
echo "✅ Java $java_version found"

if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven 3.8+"
    exit 1
fi
mvn_version=$(mvn -version 2>&1 | grep "Apache Maven" | awk '{print $3}')
echo "✅ Maven $mvn_version found"

echo ""
echo "📁 Setting up project structure..."

# Create .env file if doesn't exist
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✅ Created .env file from template"
    echo ""
    echo "⚠️  IMPORTANT: Edit .env with your Gmail credentials"
    echo "    - GMAIL_CLIENT_ID"
    echo "    - GMAIL_CLIENT_SECRET"
    echo "    - GMAIL_USER_EMAIL"
    echo "    - GMAIL_APP_PASSWORD"
    echo "    - RECIPIENT_EMAIL"
    echo ""
    read -p "Press Enter after updating .env file: "
else
    echo "✅ .env file already exists"
fi

echo ""
echo "🔨 Building project..."

# Load environment variables
export $(cat .env | xargs)

# Build project
mvn clean package -DskipTests

if [ -f target/job-email-filter-1.0.0.jar ]; then
    echo "✅ Build successful!"
    echo ""
    echo "========================================"
    echo "✅ Setup Complete!"
    echo "========================================"
    echo ""
    echo "To start JobLens, run:"
    echo "  java -jar target/job-email-filter-1.0.0.jar"
    echo ""
    echo "Or use Maven:"
    echo "  mvn spring-boot:run"
    echo ""
    echo "Or use Docker:"
    echo "  docker-compose up -d"
    echo ""
    echo "For more help, see:"
    echo "  - QUICK_START.md (5-minute setup)"
    echo "  - GMAIL_SETUP_GUIDE.md (OAuth setup)"
    echo ""
else
    echo "❌ Build failed. Check logs above."
    exit 1
fi
