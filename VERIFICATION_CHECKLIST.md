# JobLens - Pre-Launch Verification Checklist

Use this checklist before launching JobLens in production.

## ✅ Step 1: Project Setup Verification

- [ ] Navigate to: `d:\Personal WorkSpace\JobLens`
- [ ] All required files exist:
  - [ ] `pom.xml`
  - [ ] `README.md`
  - [ ] `QUICK_START.md`
  - [ ] `GMAIL_SETUP_GUIDE.md`
  - [ ] `ARCHITECTURE.md`
  - [ ] `PROJECT_SUMMARY.md`
  - [ ] `.env.example`
  - [ ] `.gitignore`
  - [ ] `Dockerfile`
  - [ ] `docker-compose.yml`

- [ ] Source code structure:
  ```
  src/main/java/com/joblens/
  ├── JobLensApplication.java
  ├── dto/JobDTO.java
  ├── service/ (4 services)
  ├── util/ (2 utilities)
  └── resources/application.yml
  ```

## ✅ Step 2: Java & Maven Verification

```bash
# Check Java version
java -version
# Should show: version "21" or higher

# Check Maven version
mvn -version
# Should show: version 3.8 or higher
```

- [ ] Java 21+ installed
- [ ] Maven 3.8+ installed

## ✅ Step 3: Gmail Account Preparation

### 2FA Setup
- [ ] Go to [Google Account Security](https://myaccount.google.com/security)
- [ ] Enable 2-Step Verification
- [ ] Verify with phone number

### App Password Generation
- [ ] Go to [App Passwords](https://myaccount.google.com/apppasswords)
- [ ] Generate app password for "Mail" on "Windows"
- [ ] Copy 16-character password (without spaces)

### Google Cloud Project
- [ ] Create project: [Google Cloud Console](https://console.cloud.google.com/)
- [ ] Enable Gmail API
- [ ] Create OAuth 2.0 credentials (Desktop app)
- [ ] Download JSON credentials
- [ ] Note Client ID and Client Secret
- [ ] Set Redirect URI: `http://localhost:8888/callback`

## ✅ Step 4: Environment Configuration

```bash
# Create .env file from template
cp .env.example .env

# Edit .env with your values
```

Verify `.env` contains:
- [ ] `GMAIL_CLIENT_ID=...` (from Google Cloud)
- [ ] `GMAIL_CLIENT_SECRET=...` (from Google Cloud)
- [ ] `GMAIL_USER_EMAIL=your.email@gmail.com`
- [ ] `GMAIL_APP_PASSWORD=abcdefghijklmnop` (from App Passwords, no spaces)
- [ ] `RECIPIENT_EMAIL=where.to.send@gmail.com`

**IMPORTANT**: Never commit `.env` file!
- [ ] `.env` added to `.gitignore`
- [ ] `.env` NOT in Git history

## ✅ Step 5: Project Build Verification

```bash
# Navigate to project
cd d:\Personal\ WorkSpace\JobLens

# Clean and build
mvn clean package
```

Build must succeed:
- [ ] No compilation errors
- [ ] No test failures
- [ ] JAR created: `target/job-email-filter-1.0.0.jar`

## ✅ Step 6: First-Time OAuth Authentication

```bash
# Start the application
java -jar target/job-email-filter-1.0.0.jar
```

- [ ] Application starts successfully
- [ ] Browser opens (or manually visit `http://localhost:8888/`)
- [ ] See Google OAuth consent screen
- [ ] Click "Allow" to grant permissions
- [ ] Redirected to `localhost:8888/callback`
- [ ] Browser shows success/redirect
- [ ] File created: `tokens/StoredCredential`

## ✅ Step 7: Email Parsing Verification

### Send Test Email
1. Send email to `GMAIL_USER_EMAIL` with:
   - Subject: `[JOBS] Senior Java Developer at TechCorp`
   - Body: Include text like "3-5 years of Spring Boot experience"

2. Wait for next scheduled run (or restart app)

### Check Logs
```
INFO: === Starting scheduled job processing ===
INFO: Step 1: Fetching emails from Gmail...
INFO: Step 2: Extracting job details from X emails...
INFO: Step 4: Applying skill and experience filters...
INFO: Step 5: Sending summary email...
INFO: === Job processing completed successfully ===
```

- [ ] No errors in logs
- [ ] Email fetch successful
- [ ] Jobs extracted correctly
- [ ] Filtering applied
- [ ] Email sent without errors

## ✅ Step 8: Email Receipt Verification

Check `RECIPIENT_EMAIL`:
- [ ] Email received from `GMAIL_USER_EMAIL`
- [ ] Subject contains: "JobLens - Top 10 Opportunities"
- [ ] HTML formatted (colored, styled)
- [ ] Job cards visible
- [ ] Job titles displayed
- [ ] Company names visible
- [ ] Experience badges shown
- [ ] Apply links are clickable

## ✅ Step 9: Experience Extraction Verification

Test the regex patterns:

```bash
# Stop the running app first
# Then run pattern test
mvn spring-boot:run -Dspring-boot.run.arguments="test-extraction"
```

Verify output shows all patterns:
- [ ] "3-5 years" → min=3, max=5
- [ ] "2+ years" → min=2, max=null
- [ ] "minimum 1 year" → min=1, max=null
- [ ] "5 years" → min=5, max=5
- [ ] No pattern → N/A, N/A

## ✅ Step 10: Configuration Verification

### application.yml
- [ ] Keywords configured: java, spring, backend, etc.
- [ ] Experience: min=1, max=3
- [ ] Max results: 10
- [ ] Schedule: "0 0 * * * *" (hourly)
- [ ] SMTP: smtp.gmail.com:587
- [ ] Logging: DEBUG for com.joblens

### Can customize by editing:
```bash
vim src/main/resources/application.yml
mvn clean package
java -jar target/job-email-filter-1.0.0.jar
```

- [ ] Keywords updated (optional)
- [ ] Experience range updated (optional)
- [ ] Cron expression updated for testing (optional)

## ✅ Step 11: Docker Verification (Optional)

```bash
# Build Docker image
docker build -t joblens .

# Run with compose
docker-compose up -d

# Check logs
docker-compose logs -f
```

- [ ] Docker image builds successfully
- [ ] Container starts without errors
- [ ] Logs show processing
- [ ] Container can be stopped: `docker-compose down`

## ✅ Step 12: Logging Verification

Check logs for proper format:
```
DEBUG: "Found range pattern: 3-5"
INFO: "Fetched X emails from Gmail"
INFO: "Extracted X jobs from Y messages"
INFO: "Email sent successfully with X jobs"
```

- [ ] Log timestamps present
- [ ] Log levels correct (DEBUG, INFO, etc.)
- [ ] No ERROR messages during success case

## ✅ Step 13: Error Handling Verification

### Test with invalid config
1. Intentionally break a setting (e.g., wrong client ID)
2. Restart app and check logs
- [ ] Error logged gracefully
- [ ] Application doesn't crash
- [ ] Next iteration can recover

### Test empty results
1. Use keyword that won't match (e.g., "kubernetes")
2. Check logs and email
- [ ] "No jobs found" email sent
- [ ] No errors in console
- [ ] Proper notification email received

## ✅ Step 14: Security Verification

- [ ] `.env` file NOT readable by others (Unix: `chmod 600 .env`)
- [ ] No secrets in logs (check `DEBUG` output)
- [ ] No passwords in `application.yml`
- [ ] All env vars from `.env` file
- [ ] OAuth token stored (not in public directory)
- [ ] `tokens/` not in Git repository

```bash
# Verify .env is ignored
git status
# Should NOT show: .env
```

- [ ] `.env` in `.gitignore`
- [ ] `tokens/` in `.gitignore`
- [ ] `target/` in `.gitignore`

## ✅ Step 15: Scheduling Verification

Default cron: `"0 0 * * * *"` (every hour at :00)

Test with frequent schedule:
```yaml
scheduling:
  email-fetcher:
    cron: "0 */5 * * * *"  # Every 5 minutes for testing
```

- [ ] Application starts
- [ ] Processing runs at scheduled times
- [ ] No duplicate emails received
- [ ] Logs show scheduled execution

## ✅ Step 16: Documentation Verification

- [ ] README.md is comprehensive (3000+ words)
- [ ] QUICK_START.md provides 30-minute setup
- [ ] GMAIL_SETUP_GUIDE.md has step-by-step instructions
- [ ] ARCHITECTURE.md explains system design
- [ ] All code is well-commented
- [ ] Example config files provided
- [ ] Troubleshooting section complete

## ✅ Step 17: Performance Verification

Check processing time in logs:
```
INFO: === Job processing completed successfully ===
```

Verify performance metrics:
- [ ] Fetch: < 1 second
- [ ] Extraction: < 100ms per email
- [ ] Filtering: < 100ms total
- [ ] Email sending: < 1 second
- [ ] **Total**: < 3 seconds

## ✅ Step 18: Production Checklist

### Before Going Live
- [ ] All tests pass
- [ ] No WARN or ERROR in logs
- [ ] Email formatting verified
- [ ] OAuth token persists after restart
- [ ] Logs are comprehensive and readable
- [ ] Configuration is secure (no hardcoded secrets)
- [ ] Documentation is complete
- [ ] Team has access to setup guide

### Long-term Maintenance
- [ ] Logs monitored for errors
- [ ] OAuth tokens refreshed automatically
- [ ] Email quota not exceeded
- [ ] Keywords reviewed monthly
- [ ] Dependencies updated quarterly
- [ ] Security best practices followed

## 🎯 Summary

| Verification | Status | Notes |
|--------------|--------|-------|
| Project Structure | ✅ | All files present |
| Java/Maven | ✅ | Version 21+, 3.8+ |
| Gmail Setup | ✅ | OAuth2, App Password |
| Configuration | ✅ | .env file created |
| Build | ✅ | JAR compiled |
| OAuth | ✅ | Token obtained |
| Email Parsing | ✅ | Jobs extracted |
| Filtering | ✅ | Skills & experience matched |
| Email Sending | ✅ | HTML email received |
| Regex | ✅ | Patterns working |
| Docker | ✅ | Container built |
| Logging | ✅ | Comprehensive logs |
| Error Handling | ✅ | Graceful failures |
| Security | ✅ | Secrets in .env |
| Scheduling | ✅ | Cron working |
| Documentation | ✅ | Complete |
| Performance | ✅ | < 3 seconds |

## ✨ Ready for Production!

If all items above are checked ✅, your JobLens application is ready for production deployment.

### Next Steps:
1. For **local development**: Keep running with `mvn spring-boot:run`
2. For **remote server**: Deploy JAR file
3. For **cloud deployment**: Use Docker image or cloud-specific setup
4. For **monitoring**: Set up log aggregation and alerts

### Support:
- Issues? Check: QUICK_START.md
- Setup help? Check: GMAIL_SETUP_GUIDE.md
- Architecture questions? Check: ARCHITECTURE.md
- General info? Check: README.md

---

**Verification Date**: _______________
**Status**: ✅ READY FOR PRODUCTION
**Next Review**: In 1 month

