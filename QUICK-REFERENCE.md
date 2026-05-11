# JobLens - Quick Reference

> TL;DR: Setup and deployment quick start

---

## 5-Minute Local Setup

```bash
# 1. Clone project
git clone https://github.com/yourusername/joblens.git
cd joblens

# 2. Copy environment template
cp .env.example .env

# 3. Edit .env with your Gmail credentials
# (See COMPREHENSIVE-GUIDE.md for how to get these)
nano .env

# 4. Build
mvn clean package -DskipTests

# 5. Run
java -jar target/job-email-filter-1.0.0.jar

# First run: Browser opens for Gmail OAuth
# Accept permission
# Email sent successfully!
```

---

## 10-Minute Docker Setup

```bash
# 1. Prepare .env
cp .env.example .env
# Edit with your credentials

# 2. Build image
docker build -t joblens:latest .

# 3. Run
docker-compose up -d

# 4. Check logs
docker-compose logs -f joblens

# 5. Stop
docker-compose down
```

---

## 15-Minute GitHub Actions Setup

```bash
# 1. Push to GitHub
git remote add origin https://github.com/YOUR_USERNAME/joblens.git
git branch -M main
git push -u origin main

# 2. Add GitHub Secrets
# Go to: Settings → Secrets and variables → Actions
# Click "New repository secret"
# Add 5 secrets:
#   - GMAIL_OAUTH_CLIENT_ID
#   - GMAIL_OAUTH_CLIENT_SECRET
#   - GMAIL_SMTP_USERNAME
#   - GMAIL_SMTP_PASSWORD
#   - JOB_EMAIL_SEND_TO

# 3. Workflow runs automatically every hour!
# Check: Actions tab → JobLens Scheduled Run
```

---

## Configuration Quick Reference

### Cron Schedule

```yaml
scheduling:
  email-fetcher:
    cron: "0 0 * * * *"  # Daily at midnight
    
    # Common schedules:
    # "0 * * * * *"       Every hour
    # "*/30 * * * * *"    Every 30 minutes
    # "0 9 * * 1-5 *"     Weekdays at 9 AM
    # "0 0 * * 0 *"       Sundays at midnight
```

### Job Filtering

```yaml
job:
  keywords: java,spring,spring boot,backend,rest,api
  filter:
    min-experience: 1     # Your minimum (years)
    max-experience: 5     # Your maximum (years)
  max-results: 10         # Top N jobs in email
```

### Gmail Configuration

```yaml
gmail:
  lookback-hours: 24          # Fetch last 24 hours
  max-emails-per-run: 20      # Max emails to check
  query:
    subject-terms:
      - jobs
      - recommended
      - opportunities
```

---

## File Structure

```
joblens/
├── src/main/java/com/joblens/
│   ├── JobLensApplication.java       (Main + scheduler)
│   ├── service/
│   │   ├── GmailService.java         (OAuth + email fetch)
│   │   ├── JobExtractionService.java (HTML parsing)
│   │   ├── JobFilterService.java     (Filter + deduplicate)
│   │   └── EmailService.java         (Send summary)
│   ├── util/
│   │   └── ExperienceExtractionUtil.java (Experience regex)
│   └── dto/
│       └── JobDTO.java               (Job data model)
│
├── src/main/resources/
│   └── application.yml               (Configuration)
│
├── Dockerfile                        (Docker build)
├── docker-compose.yml                (Local Docker run)
├── pom.xml                           (Maven dependencies)
├── .github/workflows/                (GitHub Actions)
│
├── README.md                         (Quick start)
├── COMPREHENSIVE-GUIDE.md            (Full documentation)
├── DEPLOYMENT.md                     (Deployment steps)
└── QUICK-REFERENCE.md               (This file)
```

---

## Common Tasks

### Change Cron Schedule

```yaml
# Edit: src/main/resources/application.yml
scheduling:
  email-fetcher:
    cron: "0 0 * * * *"  # Change this
```

### Change Email Recipients

```yaml
# Edit: src/main/resources/application.yml
job:
  email:
    send-to: your-email@gmail.com
```

### Change Keywords

```yaml
# Edit: src/main/resources/application.yml
job:
  keywords: java,python,backend,microservices
```

### Change Experience Range

```yaml
# Edit: src/main/resources/application.yml
job:
  filter:
    min-experience: 2
    max-experience: 8
```

### View Logs

```bash
# Local run
# Logs print directly to console

# Docker
docker-compose logs -f joblens

# GitHub Actions
# Actions → JobLens Scheduled Run → View logs

# Cloud Run
gcloud logging read "resource.type=cloud_run_revision" --limit 50

# AWS Lambda
aws logs tail /aws/lambda/joblens --follow
```

---

## Deployment Decision Matrix

| Need | Solution |
|------|----------|
| **Learning** | Local + Manual run |
| **Testing** | Docker locally |
| **Free & simple** | GitHub Actions |
| **Production** | Google Cloud Run |
| **AWS ecosystem** | AWS Lambda |
| **On-premises** | Docker + Cron |

---

## Troubleshooting Quick Fixes

| Problem | Fix |
|---------|-----|
| "Token not found" | Delete `tokens/` directory, run again (browser opens) |
| "SMTP Auth failed" | Use Gmail app password (not regular password) |
| "No emails found" | Verify Gmail has emails with "jobs", "recommended", "opportunities" |
| "null-null experience" | Email doesn't match experience regex patterns |
| Maven build fails | `mvn clean compile -e` (show full errors) |
| Docker won't start | `docker-compose down -v && docker-compose up` |

---

## Environment Variables Needed

```bash
# Gmail OAuth (get from Google Cloud Console)
GMAIL_OAUTH_CLIENT_ID=956600029837-xxxxx.apps.googleusercontent.com
GMAIL_OAUTH_CLIENT_SECRET=GOCSPX-xxxxx

# Gmail SMTP (app password from Google Account)
GMAIL_SMTP_USERNAME=your-email@gmail.com
GMAIL_SMTP_PASSWORD=xxxx xxxx xxxx xxxx

# Email recipient
JOB_EMAIL_SEND_TO=recipient@gmail.com
```

---

## Important Concepts

### OAuth2 Token Flow

```
First run: User approves in browser → Token saved locally
Next runs: Use saved token → No approval needed
```

### Experience Matching

```
Your range: 2-4 years
Job: 3-5 years
Match? Yes (overlap: 3-4)

Job: 5-7 years
Match? No (no overlap)
```

### Skill Matching

```
Keywords: ["java", "spring"]
Job title: "Senior Java Developer"
Match? Yes (contains "java")

Job title: "Python Developer"
Match? No (doesn't contain keywords)
```

### Deduplication

```
Email 1: Job at Company A (link: company-a.com/job/123)
Email 2: Same job (link: company-a.com/job/123)
Result: Only 1 job in summary (duplicate removed)
```

---

## Useful Commands

```bash
# Build
mvn clean package -DskipTests

# Run locally
java -jar target/job-email-filter-1.0.0.jar

# Docker build
docker build -t joblens:latest .

# Docker run
docker-compose up -d

# Docker stop
docker-compose down

# View docker logs
docker-compose logs -f

# Clean build
mvn clean

# Run tests
mvn test

# Generate docs
mvn site
```

---

## External Links

- [Google Cloud Console](https://console.cloud.google.com/) - OAuth credentials
- [Gmail Account Settings](https://myaccount.google.com/) - App passwords
- [Spring Boot Documentation](https://spring.io/projects/spring-boot/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions/)
- [Google Cloud Run Documentation](https://cloud.google.com/run/docs/)
- [AWS Lambda Documentation](https://docs.aws.amazon.com/lambda/)

---

## Documentation Map

```
START HERE
    ↓
Quick Reference (you are here)
    ↓
One of:
├─ DEPLOYMENT.md (for setup/deployment)
├─ COMPREHENSIVE-GUIDE.md (for full code explanation)
└─ README.md (for overview)
```

---

## Next Steps

1. **First time?** → Go to COMPREHENSIVE-GUIDE.md
2. **Want to deploy?** → Go to DEPLOYMENT.md
3. **Just need commands?** → You're in the right place!

---

**Last Updated:** May 2026

