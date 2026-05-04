# JobLens - Architecture & Design Document

## 🏗️ System Architecture

```
┌─────────────────┐
│   Gmail API     │
│   (OAuth2)      │
└────────┬────────┘
         │
         ▼
    ┌─────────────────────────────┐
    │   GmailService              │
    │ - Fetch emails (last 24h)   │
    │ - Extract message body      │
    │ - Handle OAuth tokens       │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │   JobExtractionService      │
    │ - Parse HTML email          │
    │ - Extract job details       │
    │ - Parse apply links         │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │   ExperienceExtractionUtil  │
    │ - Regex patterns            │
    │ - Min/Max experience        │
    │ - Experience matching       │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │   JobFilterService          │
    │ - Skill filtering           │
    │ - Experience filtering      │
    │ - Deduplication            │
    │ - Top N results             │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │   EmailService              │
    │ - Build HTML template       │
    │ - Send SMTP email           │
    │ - Format results            │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │   User Email Inbox          │
    │   (Job Summary)             │
    └─────────────────────────────┘
```

## 📊 Data Flow

```
1. FETCH PHASE
   ├─ Query Gmail API for emails from last 24 hours
   ├─ Filter subjects: "jobs", "recommended", "opportunities"
   └─ Return Message objects

2. EXTRACTION PHASE
   ├─ Parse HTML content with JSoup
   ├─ Extract:
   │  ├─ Job title (from subject or HTML)
   │  ├─ Company (from sender or document)
   │  ├─ Apply link (from <a> tags)
   │  ├─ Full description (from <p> tags)
   │  └─ Raw content (full email text)
   └─ Return JobDTO objects

3. EXPERIENCE EXTRACTION PHASE
   ├─ Apply regex patterns to job description
   ├─ Extract min/max experience
   ├─ Handle patterns:
   │  ├─ "X-Y years" → min=X, max=Y
   │  ├─ "X+ years" → min=X, max=null
   │  ├─ "minimum X years" → min=X, max=null
   │  └─ "X years" → min=X, max=X
   └─ Update JobDTO

4. FILTERING PHASE
   ├─ Remove duplicates by link
   ├─ Skill filtering:
   │  └─ Include if ANY keyword in (title + description)
   ├─ Experience filtering:
   │  └─ Include if job matches user experience range
   └─ Limit to top N results

5. EMAIL PHASE
   ├─ Generate HTML email template
   ├─ Include job details with styling
   ├─ Add apply links
   └─ Send via SMTP
```

## 🔄 Component Interactions

### GmailService
**Responsibilities:**
- OAuth2 authentication and token management
- Fetch emails from Gmail API
- Extract message content (body, headers)
- Base64 decoding of email content

**Key Methods:**
- `getGmailService()` - Initialize authenticated Gmail service
- `fetchRecentJobEmails()` - Query emails from last 24h
- `getFullMessage(messageId)` - Get complete message
- `extractMessageBody(message)` - Parse email body
- `getHeaderValue(message, name)` - Extract header

### JobExtractionService
**Responsibilities:**
- Parse HTML email content
- Extract structured job information
- Identify job titles, companies, links
- Build JobDTO objects

**Key Methods:**
- `extractJobFromEmail(message)` - Extract single job
- `extractJobsFromMessages(messages)` - Batch extraction
- `extractJobTitle()` - Get job title from subject/HTML
- `extractCompanyName()` - Parse company
- `extractApplyLink()` - Find career link
- `extractDescription()` - Get job description

### ExperienceExtractionUtil
**Responsibilities:**
- Regex pattern matching for experience
- Handle various text formats
- Normalize experience ranges
- Match user criteria to job requirements

**Key Methods:**
- `extractExperience(text)` - Extract from text
- `isExperienceMatch(jobMin, jobMax, userMin, userMax)` - Check match

**Regex Patterns:**
```
1. Range: "\b(\d{1,2})\s*[-–]\s*(\d{1,2})\s*(?:years?|yrs?|y\.?)"
   Example: "3-5 years"

2. Plus: "(\d{1,2})\s*\+\s*(?:years?|yrs?|y\.?)"
   Example: "2+ years"

3. Minimum: "(?:minimum|min|at\s+least)\s+(?:of\s+)?(\d{1,2})\s*(?:years?|yrs?|y\.?)"
   Example: "minimum 1 year"

4. Single: "(?:^|\\s)(\d{1,2})\s*(?:years?|yrs?|y\.?)(?:\\s|$|[.,;])"
   Example: "5 years"

5. Preferred: "(?:preferred|nice\s+to\s+have|ideal).*?(\d{1,2})\s*(?:years?|yrs?|y\.?)"
   Example: "preferred: 3 years"
```

### JobFilterService
**Responsibilities:**
- Apply skill matching
- Apply experience filtering
- Remove duplicate jobs
- Limit results to top N

**Key Methods:**
- `filterJobs(jobs)` - Apply all filters
- `removeDuplicates(jobs)` - Dedup by link
- `filterAndDeduplicate(jobs)` - Combined operation

**Filtering Logic:**
```
SKILL FILTER:
  Include if: ANY keyword in (title.lower() + description.lower())

EXPERIENCE FILTER:
  Include if:
    - job.minExperience != null AND
    - (job.maxExperience == null OR job.maxExperience >= userMinExp) AND
    - job.minExperience <= userMaxExp

DEDUPLICATION:
  Remove if: link already seen
```

### EmailService
**Responsibilities:**
- Build HTML email template
- Handle SMTP sending
- Format job data for display
- Handle error cases

**Key Methods:**
- `sendJobsEmail(jobs)` - Send job summary
- `sendNoJobsEmail()` - Send empty result notification
- `buildHtmlContent(jobs)` - Generate HTML
- `escapeHtml(text)` - Prevent injection

**Email Format:**
```
📧 HTML Email Structure
├─ Header
│  ├─ Title: "JobLens - Top Job Opportunities"
│  └─ Summary: "Found X opportunities"
├─ Job Cards (repeated)
│  ├─ Title (with number)
│  ├─ Company
│  ├─ Experience Badge
│  ├─ Description snippet
│  └─ Apply Button (link)
└─ Footer
   └─ Metadata
```

## 🎯 Key Design Decisions

### 1. **No Database**
- **Why**: Job opportunities are time-sensitive, emails are one-time
- **Tradeoff**: Can't track historical jobs or learning
- **Solution**: Email provides audit trail

### 2. **Regex-Only Experience Extraction**
- **Why**: Simple, no external dependencies, fast
- **Tradeoff**: Won't catch 100% of patterns
- **Solution**: New patterns added as needed

### 3. **Email Source as Data Source**
- **Why**: Jobs are already in email format, no UI needed
- **Tradeoff**: Only works for email-based job sources
- **Solution**: Can add other sources (APIs, feeds) later

### 4. **In-Memory Processing**
- **Why**: Simple, fast, no persistence overhead
- **Tradeoff**: Can't resume on failure
- **Solution**: Idempotent operations (same result each run)

### 5. **Hourly Scheduling**
- **Why**: Good balance between freshness and API limits
- **Tradeoff**: May miss opportunities posted between checks
- **Solution**: Can change cron to any frequency

## 📦 Data Models

### JobDTO
```java
class JobDTO {
    String title;              // "Senior Java Developer"
    String company;            // "Acme Corp"
    String link;               // "https://careers..."
    String description;        // Job requirements/description
    Integer minExperience;     // Min years (null if not specified)
    Integer maxExperience;     // Max years (null if open-ended)
    String source;             // Email subject
    String rawContent;         // Full email content
}
```

### Email Query Structure
```
Query: "after:TIMESTAMP (subject:jobs OR subject:recommended OR subject:opportunities)"
TimeRange: Last 24 hours
Limit: 20 emails per query
Format: Full message with body
```

## 🔐 Security Architecture

### OAuth2 Flow
```
1. User → Launch app
2. App → Open browser to Google OAuth consent
3. User → Clicks "Allow"
4. Google → Redirects to localhost:8888/callback
5. App → Exchanges auth code for access token
6. App → Stores token in tokens/StoredCredential
7. App → Uses token for future Gmail API calls
```

### Token Storage
- Location: `tokens/StoredCredential`
- Format: Binary (Google's format)
- Lifecycle: Refreshed automatically by Google client library
- Security: Local filesystem only (don't commit!)

### SMTP Authentication
- Protocol: TLS (port 587)
- Credentials: Gmail app password (not user password)
- Scopes: Limited to sending emails

## 📈 Performance Considerations

### API Call Frequency
- Gmail fetch: 1 per hour (configurable)
- Gmail quota: 15 GB/day + 500k API calls/day = plenty
- SMTP: 1 per run + no-results notification = 2 per hour max

### Memory Usage
- Per run: ~10 MB (email parsing + job objects)
- Token storage: < 1 MB
- No long-term accumulation

### Processing Time
- Email fetch: ~500ms
- HTML parsing: ~100ms per email
- Filtering: ~10ms per job
- Total: ~2-3 seconds per run

## 🧪 Testing Strategy

### Unit Tests
- Regex patterns in ExperienceExtractionUtil
- Filtering logic in JobFilterService
- HTML escaping in EmailService

### Integration Tests
- Full filter pipeline with test data
- Experience matching scenarios
- Deduplication logic

### Manual Testing
- Send test email with various job formats
- Verify extraction accuracy
- Check email formatting

## 🔧 Configuration Hierarchy

```
1. application.yml (checked in)
   ├─ Default values
   └─ Use ${VAR} for env substitution

2. Environment variables (.env file)
   ├─ Secrets (client ID, passwords)
   ├─ Runtime configuration
   └─ Overrides .yml values

3. application-local.yml (dev override)
   └─ Not checked in (in .gitignore)

4. System properties (-D flags)
   └─ Highest priority
```

## 📝 Logging Strategy

### Log Levels
- **DEBUG**: Job details, regex matches, filter decisions
- **INFO**: Start/stop, email sent, job count
- **WARN**: API warnings, missing fields
- **ERROR**: Failures, exceptions

### Key Log Points
1. Job processing start/end
2. Email fetch count
3. Extraction results
4. Filter application
5. Email sending success/failure

## 🚀 Deployment Options

### Local Development
```bash
mvn spring-boot:run
```

### Standalone JAR
```bash
java -jar job-email-filter-1.0.0.jar
```

### Docker Container
```bash
docker-compose up -d
```

### Cloud (Azure/AWS/GCP)
- Use managed SMTP (SendGrid, AWS SES)
- Cloud function/Lambda trigger
- Managed storage for tokens

## 📊 Future Enhancements

1. **Database Storage**
   - Track historical jobs
   - User preferences
   - Application tracking

2. **Machine Learning**
   - Job ranking by relevance
   - Salary prediction
   - Company culture matching

3. **Alternative Integrations**
   - API: LinkedIn, Indeed, etc.
   - RSS feeds from job boards
   - Slack/Teams notifications

4. **Advanced Filtering**
   - Location-based filtering
   - Salary range matching
   - Company size preference

5. **User Interface**
   - Web dashboard
   - Mobile app
   - Job tracking

## 📚 References

- [Spring Boot Reference](https://spring.io/projects/spring-boot)
- [Gmail API Guide](https://developers.google.com/gmail/api)
- [OAuth2 Specification](https://tools.ietf.org/html/rfc6749)
- [JSoup Documentation](https://jsoup.org/cookbook/)
- [Regex Pattern Guide](https://www.regular-expressions.info/)
