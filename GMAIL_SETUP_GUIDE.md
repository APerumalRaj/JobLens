# Gmail Setup Guide for JobLens

Complete step-by-step instructions to set up Gmail OAuth2 and SMTP authentication for JobLens.

## Part 1: Google Cloud Project Setup

### Step 1.1: Create a Google Cloud Project

1. Visit [Google Cloud Console](https://console.cloud.google.com/)
2. Sign in with your Google account (or create one)
3. At the top, click the project dropdown
4. Click "NEW PROJECT"
5. Enter project name: `JobLens`
6. Click "CREATE"
7. Wait for the project to be created

### Step 1.2: Enable Gmail API

1. In the Google Cloud Console, go to **APIs & Services** > **Library**
2. Search for `Gmail API`
3. Click on "Gmail API" from results
4. Click the **ENABLE** button
5. Wait for it to be enabled

### Step 1.3: Create OAuth 2.0 Credentials

1. Go to **APIs & Services** > **Credentials**
2. Click **+ CREATE CREDENTIALS** button at the top
3. Select "OAuth client ID"
4. You'll see "You need to create an OAuth consent screen first"
5. Click "CONFIGURE CONSENT SCREEN"

### Step 1.4: Configure OAuth Consent Screen

1. Choose **External** user type
2. Click **CREATE**
3. Fill in the form:
   - App name: `JobLens`
   - User support email: Your Gmail address
   - Developer contact: Your Gmail address
4. Click **SAVE AND CONTINUE**
5. Skip "Scopes" (click **SAVE AND CONTINUE**)
6. Click **SAVE AND CONTINUE** on "Test users" page
7. Click **BACK TO DASHBOARD**

### Step 1.5: Create Desktop Application Credentials

1. Go back to **APIs & Services** > **Credentials**
2. Click **+ CREATE CREDENTIALS**
3. Select "OAuth client ID"
4. Choose application type: **Desktop application**
5. Name it: `JobLens Desktop`
6. Click **CREATE**
7. A popup shows your credentials:
   - Copy **Client ID**
   - Copy **Client Secret**
8. Click **Download JSON** button (save this file safely)
9. Click **OK**

### Step 1.6: Set OAuth Redirect URI

1. In Credentials page, find your OAuth 2.0 Client ID
2. Click on it to edit
3. Under "Authorized redirect URIs", add:
   ```
   http://localhost:8888/callback
   ```
4. Click **SAVE**

## Part 2: Gmail Account Configuration

### Step 2.1: Enable 2-Factor Authentication (Required for App Passwords)

1. Go to [Google Account Security](https://myaccount.google.com/security)
2. Sign in with your Gmail account
3. Look for "2-Step Verification"
4. Click it and follow the steps to enable it
5. You'll need a phone number to verify

### Step 2.2: Create an App Password

**App passwords only work for Gmail accounts with 2FA enabled**

1. Go to [Google Account App Passwords](https://myaccount.google.com/apppasswords)
2. Select "Mail" as the app
3. Select "Windows Computer" (or your OS)
4. Click **GENERATE**
5. A password will be shown (looks like: `abcd efgh ijkl mnop`)
6. Copy this password - you'll need it for SMTP

## Part 3: Environment Configuration

### Step 3.1: Create .env File

Create a `.env` file in the JobLens project root:

```bash
# From Step 1.5 (OAuth Credentials)
GMAIL_CLIENT_ID=YOUR_CLIENT_ID.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=YOUR_CLIENT_SECRET

# Your Gmail email address
GMAIL_USER_EMAIL=your.email@gmail.com

# From Step 2.2 (App Password - without spaces)
GMAIL_APP_PASSWORD=abcdefghijklmnop

# Email where you want to receive summaries
RECIPIENT_EMAIL=recipient@gmail.com
```

**Example values:**
```bash
GMAIL_CLIENT_ID=123456789-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=GOCSPX-AbCdEfGhIjKlMnOpQrStUvWxYz
GMAIL_USER_EMAIL=jobseeker@gmail.com
GMAIL_APP_PASSWORD=abcdefghijklmnop
RECIPIENT_EMAIL=notifications@gmail.com
```

### Step 3.2: Load Environment Variables

**On Linux/Mac:**
```bash
source .env
export $(cat .env | xargs)
```

**On Windows (PowerShell):**
```powershell
Get-Content .env | ForEach-Object {
    $key, $value = $_ -split '='
    [Environment]::SetEnvironmentVariable($key, $value)
}
```

**Or use IDE configuration:**
- In IntelliJ IDEA: Run → Edit Configurations → Environment variables
- In VS Code: Add to `.vscode/launch.json`

## Part 4: First-Time OAuth Authentication

### Step 4.1: Initial Authentication

When you run JobLens for the first time:

1. The application will start
2. A browser window should open automatically
3. Click **"Continue"** to authorize JobLens
4. Review permissions and click **"Allow"**
5. You'll be redirected to `localhost:8888/callback`
6. The token will be saved locally in `tokens/` directory

### Step 4.2: Verify Token Storage

Check that the token was saved:
```bash
ls tokens/
# You should see: StoredCredential
```

## Part 5: Configure application.yml

Edit `src/main/resources/application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${GMAIL_USER_EMAIL}
    password: ${GMAIL_APP_PASSWORD}

gmail:
  oauth:
    client-id: ${GMAIL_CLIENT_ID}
    client-secret: ${GMAIL_CLIENT_SECRET}
    redirect-uri: http://localhost:8888/callback

job:
  keywords:
    - java
    - spring
    - backend
  
  experience:
    min: 1
    max: 3
  
  email:
    send-to: ${RECIPIENT_EMAIL}
    from: ${GMAIL_USER_EMAIL}
```

## Part 6: Build and Run

### Step 6.1: Build the Project

```bash
mvn clean package
```

### Step 6.2: Run the Application

**Option 1: Maven**
```bash
mvn spring-boot:run
```

**Option 2: Java JAR**
```bash
java -jar target/job-email-filter-1.0.0.jar
```

**Option 3: IDE**
- Right-click `JobLensApplication.java`
- Select "Run" or "Debug"

### Step 6.3: Check Logs

Look for:
```
INFO  com.joblens.JobLensApplication - === Starting scheduled job processing ===
INFO  com.joblens.service.GmailService - Fetching emails with query: ...
INFO  com.joblens.service.EmailService - Email sent successfully with X jobs
```

## Part 7: Troubleshooting

### Issue: "OAuth token not found"

**Solution:**
1. Restart the application
2. Browser should open automatically
3. Complete the OAuth flow
4. Verify `tokens/StoredCredential` file exists

### Issue: "Gmail API not enabled"

**Solution:**
1. Go to Google Cloud Console
2. Go to **APIs & Services** > **Library**
3. Search for "Gmail API"
4. Make sure it shows "API ENABLED" (blue button with checkmark)

### Issue: "SMTP authentication failed"

**Solution:**
1. Verify you're using an App Password (not regular password)
2. Check `GMAIL_APP_PASSWORD` has no spaces
3. Ensure 2FA is enabled on the Gmail account
4. Verify SMTP settings in `application.yml`:
   - Host: `smtp.gmail.com`
   - Port: `587`
   - Auth: `true`
   - StartTLS: `true`

### Issue: "No emails found"

**Solution:**
1. Check emails have subjects with: "jobs", "recommended", "opportunities"
2. Verify emails are from last 24 hours
3. Check keyword configuration in `application.yml`

### Issue: "OAuth redirect URI mismatch"

**Solution:**
1. Go to Google Cloud Console Credentials
2. Edit your OAuth 2.0 Client ID
3. Verify Authorized redirect URIs includes: `http://localhost:8888/callback`
4. The port must be **8888** (as in the code)

### Issue: "Permission denied" for token file

**Solution:**
```bash
# On Linux/Mac
chmod 755 tokens/

# On Windows
# Right-click tokens folder > Properties > Security > Edit
```

## Part 8: Testing

### Test Email Parsing

1. Send a test email to your Gmail account with subject: `[JOBS] Senior Java Developer at TechCorp`
2. Include text like: "3-5 years of experience with Spring Boot"
3. Wait for next scheduled run (1 hour) or restart the app
4. Check your recipient email for the summary

### Test Configuration

Modify `application.yml` to run every 5 minutes for testing:
```yaml
scheduling:
  email-fetcher:
    cron: "0 */5 * * * *"  # Every 5 minutes
```

## Part 9: Security Best Practices

1. **Never commit `.env` file** - Add to `.gitignore`:
   ```
   .env
   tokens/
   *.json
   ```

2. **Use environment variables** - Don't hardcode credentials

3. **Rotate app passwords** regularly in Google Account

4. **Limit OAuth scopes** - Only `gmail.readonly` is needed

5. **Monitor Gmail activity** - Check [Google Account Security](https://myaccount.google.com/security) regularly

## Part 10: Production Deployment

### For Cloud Deployment (AWS, Azure, GCP)

1. Store environment variables in cloud secret manager
2. Use service accounts instead of personal OAuth
3. Enable HTTPS (required for redirect URI)
4. Store tokens in secure storage (not local filesystem)
5. Use managed email service (SendGrid, AWS SES) instead of Gmail SMTP

### Example: Google Cloud Run

```bash
# Build Docker image
docker build -t joblens .

# Push to GCR
docker tag joblens gcr.io/PROJECT_ID/joblens
docker push gcr.io/PROJECT_ID/joblens

# Deploy to Cloud Run
gcloud run deploy joblens \
  --image gcr.io/PROJECT_ID/joblens \
  --set-env-vars GMAIL_CLIENT_ID=... \
  --region us-central1
```

## Useful Links

- [Gmail API Documentation](https://developers.google.com/gmail/api)
- [OAuth 2.0 Guide](https://developers.google.com/identity/protocols/oauth2)
- [App Passwords Help](https://support.google.com/accounts/answer/185833)
- [Google Cloud Console](https://console.cloud.google.com/)

---

**Need Help?** Check the logs with:
```bash
# Tail logs
tail -f spring-boot-app.log

# Search for errors
grep ERROR spring-boot-app.log
```
