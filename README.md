# JobLens - Email-based Job Opportunity Finder

A production-ready Spring Boot application that reads job-related emails from Gmail, extracts job details, filters them based on skills and experience range, and sends a summarized email.

## 🚀 Features

- **Gmail Integration**: OAuth2 authentication with Gmail API
- **Smart Email Parsing**: Extracts job titles, companies, and apply links from HTML emails
- **Experience Extraction**: Uses regex patterns to extract experience requirements
- **Skill Filtering**: Filters jobs based on configurable keywords
- **Experience Filtering**: Matches jobs to your experience range
- **HTML Email Reports**: Beautiful formatted email summaries
- **Scheduled Processing**: Runs automatically every hour
- **Production-Ready**: Clean code, logging, error handling

## 📋 Requirements

- Java 21+
- Maven 3.8+
- Spring Boot 3.2
- Gmail account with API access
- SMTP credentials for email sending

## 🔧 Setup Instructions

### 1. Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (e.g., "JobLens")
3. Enable the **Gmail API**:
   - Go to "APIs & Services" → "Library"
   - Search for "Gmail API"
   - Click "Enable"

### 2. Create OAuth2 Credentials

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth client ID"
3. Choose "Desktop application"
4. Download the JSON file
5. Copy the `client_id` and `client_secret` values

### 3. Configure Environment Variables

Create a `.env` file or set environment variables:

```bash
# Gmail OAuth
GMAIL_CLIENT_ID=your_client_id.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=your_client_secret
GMAIL_USER_EMAIL=your.email@gmail.com

# SMTP (for sending summary email)
GMAIL_APP_PASSWORD=your_app_password

# Email recipient
RECIPIENT_EMAIL=recipient@gmail.com
```

**Note**: For Gmail, use an [App Password](https://myaccount.google.com/apppasswords) instead of your regular password.

### 4. Build and Run

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/job-email-filter-1.0.0.jar
```

Or using Maven:
```bash
mvn spring-boot:run
```

The application will start and run the scheduled job every hour.

## 📁 Project Structure

```
src/main/
├── java/com/joblens/
│   ├── JobLensApplication.java          # Main app with @Scheduled
│   ├── dto/
│   │   └── JobDTO.java                  # Job data model
│   ├── service/
│   │   ├── GmailService.java            # Gmail API integration
│   │   ├── JobExtractionService.java    # Email parsing & extraction
│   │   ├── JobFilterService.java        # Skill & experience filtering
│   │   └── EmailService.java            # Summary email sending
│   └── util/
│       └── ExperienceExtractionUtil.java # Regex-based experience extraction
└── resources/
    └── application.yml                   # Configuration
```

## ⚙️ Configuration (application.yml)

```yaml
job:
  keywords:
    - java
    - spring
    - spring boot
    - backend
    - rest
    - oracle

  experience:
    min: 1
    max: 3

  max-results: 10

scheduling:
  email-fetcher:
    cron: "0 0 * * * *"  # Every hour
```

## 🎯 How It Works

### 1. Email Fetching
- Queries Gmail API for emails from last 24 hours
- Filters by subject: "jobs", "recommended", "opportunities"

### 2. Email Parsing
- Extracts HTML content
- Parses using JSoup
- Identifies job title, company, apply link, and description

### 3. Experience Extraction
Regex patterns handle:
- `"1-3 years"` → min=1, max=3
- `"2+ years"` → min=2, max=null
- `"0-2 yrs"` → min=0, max=2
- `"minimum 1 year"` → min=1, max=null
- `"3 years experience"` → min=3, max=3

### 4. Filtering
**Skill Filter**: Includes if ANY keyword matches in title + description

**Experience Filter**: Includes if:
- `job.minExperience <= userMaxExperience` AND
- `(job.maxExperience == null OR job.maxExperience >= userMinExperience)`

**Deduplication**: Removes duplicates by apply link

### 5. Email Report
Sends HTML email with:
- Top 10 matching opportunities
- Job title, company, experience range
- Direct apply links

## 🧪 Testing the Experience Extraction

The `ExperienceExtractionUtil` handles various patterns:

```java
Map<String, Integer> exp = ExperienceExtractionUtil.extractExperience("We need someone with 3-5 years of experience");
// Returns: min=3, max=5

exp = ExperienceExtractionUtil.extractExperience("Minimum 2 years required");
// Returns: min=2, max=null
```

## 📊 Logging

Application logs are configured with SLF4J:

```
DEBUG: Detailed information about jobs, matching logic
INFO: High-level processing steps, email sending
WARN: Gmail/API warnings
ERROR: Critical failures
```

Configure in `application.yml`:
```yaml
logging:
  level:
    com.joblens: DEBUG
```

## 🚨 Troubleshooting

### Gmail OAuth Issues
- Ensure Gmail API is enabled in Google Cloud Console
- Check `tokens/` directory has valid authentication files
- Verify OAuth redirect URI matches configuration

### Email Not Sending
- Check SMTP credentials
- Verify app password is used (not regular password)
- Check firewall/network allows SMTP

### No Jobs Found
- Check email subject contains: "jobs", "recommended", "opportunities"
- Verify keyword configuration matches job postings
- Check email timestamps (only last 24 hours)

### Experience Extraction Not Working
- Ensure text format matches supported patterns
- Check logs for regex match attempts
- Add new patterns to `ExperienceExtractionUtil` as needed

## 📝 Example JobDTO Output

```json
{
  "title": "Senior Java Developer",
  "company": "Acme Corp",
  "link": "https://careers.example.com/jobs/123",
  "description": "Looking for a senior Java developer with Spring Boot experience...",
  "minExperience": 3,
  "maxExperience": 6,
  "experienceRange": "3-6 years",
  "source": "Job Opportunity at Acme Corp"
}
```

## 🔒 Security Notes

- OAuth tokens are stored locally in `tokens/` directory
- Never commit `.env` file or token files to version control
- Use environment variables for sensitive data
- App passwords are safer than regular passwords for API access

## 📈 Future Enhancements

- Database storage for historical jobs
- Job ranking/scoring algorithm
- Multiple recipient emails
- Slack/Teams integration
- Job change notifications
- Preference learning

## 📄 License

MIT License - See LICENSE file for details

## 🤝 Support

For issues or questions, refer to:
- [Gmail API Documentation](https://developers.google.com/gmail/api)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [JSoup HTML Parser](https://jsoup.org/)
