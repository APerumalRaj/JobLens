# JobLens - Deployment Guide

> Quick deployment steps for different platforms to run JobLens independently without manual intervention

---

## Table of Contents

1. [Before You Start](#before-you-start)
2. [Option 1: Docker (Recommended for Local)](#option-1-docker-recommended-for-local)
3. [Option 2: GitHub Actions (Free, No Server)](#option-2-github-actions-free-no-server)
4. [Option 3: Google Cloud Run (Serverless)](#option-3-google-cloud-run-serverless)
5. [Option 4: AWS Lambda](#option-4-aws-lambda)
6. [Token Persistence](#token-persistence)

---

## Before You Start

### Prerequisites

You need:
1. **Gmail account** with OAuth2 credentials (see [COMPREHENSIVE-GUIDE.md](COMPREHENSIVE-GUIDE.md#step-2-set-up-gmail-oauth2))
2. **Gmail App Password** for SMTP (see [COMPREHENSIVE-GUIDE.md](COMPREHENSIVE-GUIDE.md#step-2-set-up-gmail-oauth2))
3. **git** (for GitHub Actions)
4. **docker** (for Docker option)

### Prepare Configuration

Get these values ready:
- `GMAIL_OAUTH_CLIENT_ID` - From Google Cloud Console
- `GMAIL_OAUTH_CLIENT_SECRET` - From Google Cloud Console
- `GMAIL_SMTP_USERNAME` - Your Gmail address
- `GMAIL_SMTP_PASSWORD` - Your Gmail app password
- `JOB_EMAIL_SEND_TO` - Recipient email address

---

## Option 1: Docker (Recommended for Local)

**Cost:** Free (if you have Docker installed)  
**Effort:** 5 minutes  
**Best for:** Testing, local deployment

### Step 1: Create `.env` file

```bash
cp .env.example .env
```

Edit `.env`:

```bash
# .env
GMAIL_OAUTH_CLIENT_ID=956600029837-xxxxx.apps.googleusercontent.com
GMAIL_OAUTH_CLIENT_SECRET=GOCSPX-xxxxx
GMAIL_SMTP_USERNAME=your-email@gmail.com
GMAIL_SMTP_PASSWORD=xxxx xxxx xxxx xxxx
JOB_EMAIL_SEND_TO=recipient@gmail.com
```

### Step 2: Build and Run

```bash
# Build image
docker build -t joblens:latest .

# Run with docker-compose
docker-compose up -d

# View logs
docker-compose logs -f joblens

# Stop
docker-compose down
```

### Step 3: First Run - Gmail OAuth

On first run, the container will:
1. Try to open browser for Gmail OAuth (will fail in container)
2. Check logs for URL
3. Access the URL from your local browser
4. Grant permission
5. Container saves token

**Alternative for headless setup:**

```bash
# Extract token from running container
docker-compose up -d
docker exec joblens ls -la /app/tokens/

# If first run fails, manually authenticate:
docker run -it \
    -v joblens-tokens:/app/tokens \
    -e GMAIL_OAUTH_CLIENT_ID=... \
    -e GMAIL_OAUTH_CLIENT_SECRET=... \
    joblens:latest

# Then restart with docker-compose
docker-compose up -d
```

### Step 4: Verify It's Running

```bash
# Check container status
docker-compose ps

# View recent logs
docker-compose logs --tail=50 joblens

# Should see:
# "Starting scheduled job processing"
# "Fetching emails from Gmail..."
# "Sending summary email..."
```

---

## Option 2: GitHub Actions (Free, No Server)

**Cost:** Free (GitHub Actions included)  
**Effort:** 10 minutes  
**Best for:** Automated runs, no server needed

### Step 1: Push Code to GitHub

```bash
git remote add origin https://github.com/YOUR_USERNAME/joblens.git
git push origin main
```

### Step 2: Add GitHub Secrets

1. Go to your GitHub repo
2. Settings → Secrets and variables → Actions
3. Click "New repository secret"
4. Add these secrets:

```
GMAIL_OAUTH_CLIENT_ID        = 956600029837-xxxxx.apps.googleusercontent.com
GMAIL_OAUTH_CLIENT_SECRET    = GOCSPX-xxxxx
GMAIL_SMTP_USERNAME          = your-email@gmail.com
GMAIL_SMTP_PASSWORD          = xxxx xxxx xxxx xxxx
JOB_EMAIL_SEND_TO            = recipient@gmail.com
```

### Step 3: Workflow Already Configured

The `.github/workflows/joblens-schedule.yml` is already set up to:
- Run every hour automatically
- Build your code
- Execute JobLens
- Send emails

### Step 4: Test Workflow

1. Go to your repo → "Actions" tab
2. Click "JobLens Scheduled Run"
3. Click "Run workflow" → "Run workflow"
4. Wait 2-3 minutes
5. Check if email arrives

### Step 5: Verify Automatic Scheduling

```bash
# The workflow runs automatically at:
# Every hour at minute 0 (00:00, 01:00, 02:00, etc.)

# To change schedule, edit:
# .github/workflows/joblens-schedule.yml

on:
  schedule:
    - cron: '0 * * * *'  # Change this line
```

**Common cron patterns:**
```
'0 * * * *'     # Every hour
'0 0 * * *'     # Daily at midnight
'0 9 * * 1-5'   # Weekdays at 9 AM
'*/30 * * * *'  # Every 30 minutes
```

### Limitations of GitHub Actions

- **No persistent storage** for Gmail OAuth tokens
- First run requires token setup
- Each workflow run is independent

**Solution:** Store tokens in repository (⚠️ security risk) or use external storage

---

## Option 3: Google Cloud Run (Serverless)

**Cost:** Free tier (2 million requests/month)  
**Effort:** 20 minutes  
**Best for:** Production, scalable, no server management

### Step 1: Setup Google Cloud

```bash
# Install Google Cloud CLI
# https://cloud.google.com/sdk/docs/install

# Login
gcloud auth login

# Create project
gcloud projects create joblens-project
gcloud config set project joblens-project

# Enable required APIs
gcloud services enable run.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable cloudscheduler.googleapis.com
```

### Step 2: Build and Deploy

```bash
# Build container image
gcloud builds submit --tag gcr.io/joblens-project/joblens

# Deploy to Cloud Run
gcloud run deploy joblens \
    --image gcr.io/joblens-project/joblens \
    --platform managed \
    --region us-central1 \
    --memory 512Mi \
    --timeout 300 \
    --set-env-vars \
    "GMAIL_OAUTH_CLIENT_ID=956600029837-xxxxx.apps.googleusercontent.com,\
GMAIL_OAUTH_CLIENT_SECRET=GOCSPX-xxxxx,\
GMAIL_SMTP_USERNAME=your-email@gmail.com,\
GMAIL_SMTP_PASSWORD=xxxx xxxx xxxx xxxx,\
JOB_EMAIL_SEND_TO=recipient@gmail.com"

# Note the service URL:
# https://joblens-xxxxxx.run.app
```

### Step 3: Schedule Execution

```bash
# Create Cloud Scheduler job (runs every hour)
gcloud scheduler jobs create http joblens-trigger \
    --schedule="0 * * * *" \
    --http-method POST \
    --uri https://joblens-xxxxxx.run.app/run \
    --location us-central1

# Test trigger
gcloud scheduler jobs run joblens-trigger --location us-central1

# Check logs
gcloud run services describe joblens --platform managed --region us-central1
gcloud logging read "resource.type=cloud_run_revision" --limit 50 --format json
```

### Step 4: Monitor

```bash
# View real-time logs
gcloud run services describe joblens --platform managed --region us-central1

# Or via Cloud Console:
# https://console.cloud.google.com/run
```

---

## Option 4: AWS Lambda

**Cost:** Free tier (1 million requests/month)  
**Effort:** 30 minutes  
**Best for:** AWS ecosystem integration

### Step 1: Prepare for Lambda

```bash
# Create Lambda-specific build
mvn clean package -DskipTests -P lambda

# This creates a smaller JAR optimized for Lambda
```

### Step 2: Create AWS Lambda Function

```bash
# Using AWS CLI
aws lambda create-function \
    --function-name joblens \
    --runtime java21 \
    --role arn:aws:iam::ACCOUNT_ID:role/lambda-role \
    --handler com.joblens.LambdaHandler \
    --zip-file fileb://target/job-email-filter-1.0.0-lambda.jar \
    --timeout 300 \
    --memory-size 512 \
    --environment Variables="{\
GMAIL_OAUTH_CLIENT_ID=956600029837-xxxxx.apps.googleusercontent.com,\
GMAIL_OAUTH_CLIENT_SECRET=GOCSPX-xxxxx,\
GMAIL_SMTP_USERNAME=your-email@gmail.com,\
GMAIL_SMTP_PASSWORD=xxxx xxxx xxxx xxxx,\
JOB_EMAIL_SEND_TO=recipient@gmail.com\
}"
```

### Step 3: Set EventBridge Trigger

```bash
# Create hourly schedule
aws events put-rule \
    --name joblens-schedule \
    --schedule-expression "rate(1 hour)"

# Add Lambda as target
aws events put-targets \
    --rule joblens-schedule \
    --targets "Id"="1","Arn"="arn:aws:lambda:region:account:function:joblens"

# Grant EventBridge permission to invoke Lambda
aws lambda add-permission \
    --function-name joblens \
    --statement-id AllowEventBridgeInvoke \
    --action lambda:InvokeFunction \
    --principal events.amazonaws.com \
    --source-arn arn:aws:events:region:account:rule/joblens-schedule
```

---

## Token Persistence

### Problem

Gmail OAuth tokens expire. First-time authentication requires browser interaction. In serverless environments (GitHub Actions, Cloud Run, Lambda), this is problematic.

### Solution 1: Cloud Storage (Recommended)

Store tokens in cloud storage (S3, GCS, Azure Blob):

```java
// Modified GmailService.java
public Credential getCredentials() throws Exception {
    File tokensDirectory = new File(tokenFilePath);
    
    // Try to download tokens from S3 first
    downloadTokensFromS3(tokensDirectory);
    
    // ... rest of OAuth flow ...
    
    // After successful auth, upload token to S3
    uploadTokensToS3(tokensDirectory);
    
    return credential;
}

private void downloadTokensFromS3(File tokensDirectory) {
    // Use AWS SDK to download tokens/StoredCredential
}

private void uploadTokensToS3(File tokensDirectory) {
    // Use AWS SDK to upload tokens/StoredCredential
}
```

### Solution 2: GitHub Secrets (Not Recommended for Tokens)

Store serialized token in GitHub Secrets:

```bash
# Risky - tokens contain sensitive data
# Only do this for testing/development
```

### Solution 3: Pre-Generate Token

```bash
# Generate token locally once
java -jar target/job-email-filter-1.0.0.jar

# Accept Gmail permission in browser
# Copy tokens/StoredCredential to cloud

# In deployment, download tokens before running
```

---

## Quick Comparison

| Option | Cost | Setup Time | Reliability | Best For |
|--------|------|-----------|-------------|----------|
| **Docker** | Free | 5 min | High | Local/Testing |
| **GitHub Actions** | Free | 10 min | Medium | Learning |
| **Google Cloud Run** | Free tier | 20 min | High | Production |
| **AWS Lambda** | Free tier | 30 min | High | AWS users |

---

## Troubleshooting Deployments

### Docker Won't Start

```bash
# Check logs
docker-compose logs joblens

# Rebuild image (clean cache)
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

### GitHub Actions Workflow Fails

```bash
# Check workflow logs:
# GitHub Repo → Actions → Job → Step logs

# Common issues:
# - Secrets not set (workflow won't run with missing secrets)
# - Java/Maven cache issues
# - Timeout (increase timeout in workflow)
```

### Cloud Run Deployment Fails

```bash
# Check build logs
gcloud builds log LAST

# Check runtime logs
gcloud logging read "resource.type=cloud_run_revision" --limit 50

# Verify environment variables
gcloud run services describe joblens --platform managed --region us-central1
```

---

## Final Checklist

Before deploying:

- [ ] Gmail OAuth credentials obtained
- [ ] Gmail app password generated
- [ ] `.env` file created with credentials
- [ ] `docker-compose.yml` configured (if using Docker)
- [ ] GitHub secrets added (if using GitHub Actions)
- [ ] Cloud project created (if using Cloud Run/Lambda)
- [ ] Cron schedule reviewed
- [ ] Email recipients verified
- [ ] Job keywords customized
- [ ] First manual test successful

---

**Next Steps:**
1. Choose deployment option
2. Follow setup steps
3. Test workflow
4. Monitor execution

For detailed code explanation, see [COMPREHENSIVE-GUIDE.md](COMPREHENSIVE-GUIDE.md).

