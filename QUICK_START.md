# 🚀 JobLens Quick Start Guide

Get JobLens running in under 30 minutes!

## Prerequisites
- ✅ Java 21+ installed
- ✅ Maven 3.8+ installed
- ✅ Gmail account
- ✅ Google Cloud account

## ⏱️ 5-Minute Setup

### Step 1: Clone/Download Project
```bash
cd JobLens
```

### Step 2: Setup Gmail OAuth (15 minutes)

**Follow the detailed guide:**
```bash
cat GMAIL_SETUP_GUIDE.md
```

**Quick checklist:**
- [ ] Google Cloud Project created
- [ ] Gmail API enabled
- [ ] OAuth2 credentials created (save Client ID & Secret)
- [ ] Redirect URI set to `http://localhost:8888/callback`
- [ ] 2FA enabled on Gmail account
- [ ] App Password generated

### Step 3: Configure Environment

Copy the example file:
```bash
cp .env.example .env
```

Edit `.env` with your values:
```bash
GMAIL_CLIENT_ID=your_client_id
GMAIL_CLIENT_SECRET=your_client_secret
GMAIL_USER_EMAIL=your.email@gmail.com
GMAIL_APP_PASSWORD=your_app_password
RECIPIENT_EMAIL=where@to.send
```

### Step 4: Build & Run

```bash
# Build
mvn clean package

# Run
java -jar target/job-email-filter-1.0.0.jar
```

**First time?** A browser will open for OAuth authentication. Click "Allow".

### Step 5: Wait for Results

- Application processes emails every hour
- Manually test by sending an email with subject: `[JOBS] Senior Java Developer`
- Summary email arrives at your RECIPIENT_EMAIL

---

## 🧪 Quick Test

### Test Experience Extraction

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="test-extraction"
```

Output shows regex patterns working:
```
Input:  3-5 years of experience required
Output: min=3, max=5
Range:  3-5 years
```

### Test Full Flow (Manual)

1. Send email to `GMAIL_USER_EMAIL` with:
   - Subject: `[JOBS] Senior Spring Boot Developer`
   - Body: `"We're looking for 2-4 years of backend experience"`

2. In project root, run manually:
   ```bash
   mvn spring-boot:run
   ```

3. Watch logs:
   ```
   INFO: Step 1: Fetching emails from Gmail...
   INFO: Step 2: Extracting job details from X emails...
   INFO: Step 4: Applying skill and experience filters...
   INFO: Step 5: Sending summary email...
   ```

4. Check your RECIPIENT_EMAIL for results

---

## 📊 Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
job:
  keywords:
    - java
    - spring
    - backend
    - rest
  
  experience:
    min: 1
    max: 3
  
  max-results: 10

scheduling:
  email-fetcher:
    cron: "0 0 * * * *"  # Every hour (change for testing)
```

**For testing every 5 minutes:**
```yaml
cron: "0 */5 * * * *"
```

---

## 🐛 Troubleshooting

### Issue: Emails not found
- Check subject contains: "jobs", "recommended", "opportunities"
- Verify email timestamp is within last 24 hours

### Issue: OAuth authorization fails
- Browser doesn't open? Manually visit: `http://localhost:8888/callback` after starting
- Check `GMAIL_CLIENT_ID` and `GMAIL_CLIENT_SECRET` are correct

### Issue: SMTP authentication fails
- Did you create an **App Password**? (not regular Gmail password)
- Is 2FA enabled? (required for App Passwords)
- No spaces in `GMAIL_APP_PASSWORD`?

### Issue: No experience extracted
- Patterns need exact text format (see regex in `ExperienceExtractionUtil.java`)
- Supported: "3-5 years", "2+ years", "minimum 1 year"
- Add new patterns if needed

---

## 📁 Project Structure

```
JobLens/
├── pom.xml                           # Maven configuration
├── README.md                         # Full documentation
├── GMAIL_SETUP_GUIDE.md             # Detailed Gmail setup
├── QUICK_START.md                   # This file
├── .env.example                     # Configuration template
├── docker-compose.yml               # Docker setup
├── Dockerfile                       # Container image
├── src/main/
│   ├── java/com/joblens/
│   │   ├── JobLensApplication.java  # Main app with scheduler
│   │   ├── dto/JobDTO.java          # Job data model
│   │   ├── service/
│   │   │   ├── GmailService.java    # Gmail API
│   │   │   ├── JobExtractionService.java
│   │   │   ├── JobFilterService.java
│   │   │   └── EmailService.java
│   │   └── util/
│   │       └── ExperienceExtractionUtil.java  # Regex patterns
│   └── resources/
│       └── application.yml          # Spring Boot config
└── tokens/                          # OAuth tokens (created on first run)
```

---

## 🚢 Docker Deployment

No Java? Use Docker:

```bash
# Build image
docker build -t joblens .

# Run with compose
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

---

## 📈 How It Works

1. **Fetch** emails from last 24 hours with job keywords
2. **Extract** job details using JSoup HTML parser
3. **Analyze** experience requirements with regex patterns
4. **Filter** by keywords AND experience range
5. **Deduplicate** by apply link
6. **Send** HTML summary email with top 10 jobs

---

## 🔗 Important Links

- [Gmail API Docs](https://developers.google.com/gmail/api)
- [OAuth2 Guide](https://developers.google.com/identity/protocols/oauth2)
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [JSoup Parser](https://jsoup.org/)

---

## 💡 Pro Tips

1. **Test first with manual trigger:**
   ```bash
   mvn spring-boot:run
   ```

2. **Watch logs in real-time:**
   ```bash
   tail -f spring-boot-app.log
   ```

3. **Use environment variables for security:**
   ```bash
   export GMAIL_CLIENT_ID=...
   mvn spring-boot:run
   ```

4. **Schedule with `cron` for production:**
   - Every hour: `"0 0 * * * *"`
   - Every 6 hours: `"0 0 */6 * * *"`
   - Daily at 9 AM: `"0 0 9 * * *"`

5. **Monitor from logs:**
   - Debug: `LOGGING_LEVEL_COM_JOBLENS=DEBUG`
   - Info: `LOGGING_LEVEL_COM_JOBLENS=INFO`

---

## 🤝 Need Help?

1. Check **GMAIL_SETUP_GUIDE.md** for authentication issues
2. Check **README.md** for full documentation
3. Review **application.yml** for configuration options
4. Check application logs for error details

---

## ✅ Ready?

```bash
# 1. Configure .env
cp .env.example .env
# → Edit .env with your values

# 2. Build & run
mvn clean package
java -jar target/job-email-filter-1.0.0.jar

# 3. Wait for first run (should process automatically)

# 4. Check your email!
```

**That's it! 🎉**

The app will run automatically every hour and send you job summaries.
