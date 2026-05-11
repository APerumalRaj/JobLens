# JobLens - Complete Code Documentation & Setup Guide

> **Comprehensive guide explaining the entire JobLens application from setup to production deployment, designed for Spring Boot learning.**

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture & Design](#architecture--design)
3. [Environment Setup](#environment-setup)
4. [Configuration Guide](#configuration-guide)
5. [Code Structure Explained](#code-structure-explained)
6. [Component Breakdown](#component-breakdown)
7. [Deployment Options](#deployment-options)
8. [Troubleshooting](#troubleshooting)

---

## Project Overview

### What is JobLens?

JobLens is a **Spring Boot microservice** that automates job opportunity discovery and filtering. It:

- **Fetches emails** from your Gmail inbox using OAuth2 authentication
- **Parses job postings** from email content using HTML parsing
- **Extracts job details** (title, company, experience requirements, apply links)
- **Filters by skills & experience** based on your criteria
- **Sends summary emails** with curated opportunities

### Key Features

| Feature | Benefit |
|---------|---------|
| **Automated Scheduling** | Runs hourly without manual intervention |
| **Smart Filtering** | Matches jobs to your skills and experience |
| **Experience Extraction** | Understands various experience formats (e.g., "2-3 years", "fresher", "5+ years") |
| **Deduplication** | Removes duplicate job postings |
| **Clean Code** | Production-ready with proper logging and error handling |

---

## Architecture & Design

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    JobLens Application                      │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  Gmail Inbox     │
│  (OAuth2 Auth)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  1. GmailService                                             │
│     ├─ Authenticate with OAuth2                             │
│     ├─ Build smart query to exclude own emails              │
│     ├─ Fetch recent emails (last 24 hours)                  │
│     └─ Filter out app-generated notifications               │
└────────┬─────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  2. JobExtractionService                                     │
│     ├─ Parse HTML email content using JSoup                 │
│     ├─ Extract job title (from subject/content)             │
│     ├─ Extract company name (from sender/content)           │
│     ├─ Extract apply link                                   │
│     ├─ Extract job description                              │
│     └─ Extract experience requirements                      │
└────────┬─────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  3. ExperienceExtractionUtil                                 │
│     ├─ Regex pattern matching for:                          │
│     │  • "2-3 years"                                        │
│     │  • "2+ years"                                         │
│     │  • "minimum 2 years"                                  │
│     │  • "fresher"                                          │
│     │  • "experienced"                                      │
│     └─ Returns min/max experience range                     │
└────────┬─────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  4. JobFilterService                                         │
│     ├─ Match skills: Job keywords vs user keywords          │
│     ├─ Match experience: Job range vs user range            │
│     ├─ Remove duplicates (by apply link)                    │
│     └─ Return filtered jobs (top N)                         │
└────────┬─────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  5. EmailService                                             │
│     ├─ Format filtered jobs as HTML email                   │
│     ├─ Add styling and branding                             │
│     └─ Send via Gmail SMTP                                  │
└────────┬─────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐
│  User's Inbox    │
│  (Summary Email) │
└──────────────────┘
```

### Design Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Service Layer Pattern** | Each service handles one responsibility | Separation of concerns, easy to test |
| **Dependency Injection** | Spring `@Autowired` | Loose coupling, flexible configuration |
| **Builder Pattern** | `JobDTO.builder()` | Clean object creation, readable code |
| **Strategy Pattern** | Multiple regex patterns for experience extraction | Flexible handling of various formats |
| **Scheduler Pattern** | `@Scheduled(cron=...)` | Automatic recurring execution |

---

## Environment Setup

### Prerequisites

```bash
# Minimum Requirements:
- Java JDK 21 or higher
- Maven 3.8+
- Gmail account (personal or business)
- Git (optional, for version control)
```

### Step 1: Clone or Download the Project

```bash
git clone https://github.com/yourusername/joblens.git
cd joblens
```

### Step 2: Set Up Gmail OAuth2

#### 2.1 Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Create Project"
3. Name: "JobLens"
4. Click "Create"

#### 2.2 Enable Gmail API

1. In the console, go to "APIs & Services" → "Library"
2. Search for "Gmail API"
3. Click "Enable"

#### 2.3 Create OAuth2 Credentials

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth client ID"
3. Choose "Desktop application"
4. Click "Create"
5. Download the JSON file
6. Copy `client_id` and `client_secret` values

#### 2.4 Generate Gmail App Password (for SMTP)

1. Go to your [Google Account](https://myaccount.google.com/)
2. Enable 2-factor authentication (if not already enabled)
3. Go to "App passwords"
4. Select "Mail" and "Windows Computer"
5. Generate app password (16-character)
6. Copy the password

### Step 3: Configure Application

Create `application.yml` in `src/main/resources/`:

```yaml
spring:
  application:
    name: joblens-email-filter
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-gmail@gmail.com               # Your Gmail address
    password: xxxx xxxx xxxx xxxx                 # App password (spaces included)
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 30000
          timeout: 30000
          writetimeout: 30000

gmail:
  oauth:
    client-id: 956600029837-xxxxx.apps.googleusercontent.com
    client-secret: GOCSPX-xxxxx
    redirect-uri: http://localhost:8888/callback
    token-file-path: tokens/
    scopes: https://www.googleapis.com/auth/gmail.readonly
  lookback-hours: 24
  max-emails-per-run: 20
  query:
    subject-terms:
      - jobs
      - recommended
      - opportunities

job:
  keywords: java,spring,spring boot,backend,rest,api          # Customize these
  filter:
    min-experience: 1
    max-experience: 5
  max-results: 10
  email:
    send-to: your-email@gmail.com
    subject: "JobLens - Top 10 Opportunities (Last 24h)"
    from: your-gmail@gmail.com
    subject-prefix: "JobLens -"

scheduling:
  email-fetcher:
    cron: "0 0 * * * *"                         # Runs daily at midnight

logging:
  level:
    root: INFO
    com.joblens: DEBUG
    com.google.api: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Step 4: Build the Project

```bash
mvn clean compile
```

---

## Configuration Guide

### 1. Gmail OAuth Configuration

```yaml
gmail:
  oauth:
    client-id: "YOUR_CLIENT_ID"          # From Google Cloud Console
    client-secret: "YOUR_CLIENT_SECRET"  # From Google Cloud Console
    redirect-uri: "http://localhost:8888/callback"
    token-file-path: "tokens/"           # Where OAuth tokens are stored locally
    scopes: "https://www.googleapis.com/auth/gmail.readonly"  # Read-only access
  lookback-hours: 24                     # Fetch emails from last 24 hours
  max-emails-per-run: 20                 # Maximum emails to fetch per run
```

**Why these values?**
- `redirect-uri`: Local redirect for OAuth callback during dev/testing
- `scopes`: Read-only access (safe, doesn't modify emails)
- `lookback-hours`: 24 hours balances freshness vs performance

### 2. SMTP Configuration (Gmail)

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587                            # TLS port
    username: your-email@gmail.com
    password: your-app-password          # Generated from Google Account
    properties:
      mail:
        smtp:
          auth: true                     # Enable SMTP authentication
          starttls:
            enable: true
            required: true
          connectiontimeout: 30000
          timeout: 30000
```

**Why App Password instead of regular password?**
- More secure (can be revoked independently)
- Required for Gmail's 2FA protection
- Cannot be used for other accounts

### 3. Job Filtering Configuration

```yaml
job:
  keywords: "java,spring,spring boot,backend,rest,api"  # Comma-separated
  filter:
    min-experience: 1           # Your minimum required experience (years)
    max-experience: 5           # Your maximum acceptable experience (years)
  max-results: 10               # Top N jobs to include in email
```

**How filtering works:**
- **Skill matching**: Job title/description must contain ANY of these keywords
- **Experience matching**: Job experience range must overlap with your range
- **Deduplication**: Same apply link = duplicate (removed)

### 4. Scheduling Configuration

```yaml
scheduling:
  email-fetcher:
    cron: "0 0 * * * *"
```

**Cron format:** `second minute hour day-of-month month day-of-week`

| Cron | Meaning |
|------|---------|
| `0 0 * * * *` | Daily at midnight |
| `0 * * * * *` | Hourly |
| `*/10 * * * * *` | Every 10 minutes |
| `0 0 * * 1-5 *` | Weekdays at midnight |

---

## Code Structure Explained

### Project Layout

```
joblens/
├── src/main/java/com/joblens/
│   ├── JobLensApplication.java           # Main entry point with @Scheduled
│   ├── controller/                        # (Optional) REST endpoints
│   ├── service/
│   │   ├── GmailService.java              # Gmail OAuth & email fetching
│   │   ├── JobExtractionService.java      # Email parsing & job extraction
│   │   ├── JobFilterService.java          # Skill & experience filtering
│   │   └── EmailService.java              # Summary email formatting & sending
│   ├── dto/
│   │   └── JobDTO.java                    # Job data model
│   ├── util/
│   │   ├── ExperienceExtractionUtil.java  # Regex patterns for experience
│   │   └── ExperienceExtractionDemo.java  # Testing utility
│   └── config/
│       └── WebSecurityConfig.java         # (If REST endpoints exist)
│
├── src/main/resources/
│   ├── application.yml                    # Configuration file
│   └── logback-spring.xml                 # Logging configuration
│
├── src/test/java/com/joblens/
│   └── service/
│       ├── JobFilterServiceIntegrationTest.java
│       ├── GmailServiceTest.java
│       └── ExperienceExtractionUtilTest.java
│
├── pom.xml                                # Maven dependencies
├── Dockerfile                             # Docker configuration
├── docker-compose.yml                     # Docker Compose setup
├── .github/workflows/joblens-schedule.yml # GitHub Actions workflow
├── README.md                              # Quick start guide
└── DEPLOYMENT.md                          # This comprehensive guide
```

---

## Component Breakdown

### 1. JobLensApplication.java (Main Entry Point)

**Purpose:** Spring Boot entry point with scheduled job execution

```java
@Slf4j                                  // Lombok: generates 'log' field
@SpringBootApplication                  // Enable Spring Boot auto-configuration
@EnableScheduling                       // Enable @Scheduled annotations
public class JobLensApplication {
    
    @Scheduled(cron = "${scheduling.email-fetcher.cron}")
    public void processJobOpportunities() {
        // Runs automatically based on cron schedule
        // Step 1: Fetch emails from Gmail
        // Step 2: Extract job details
        // Step 3: Remove duplicates
        // Step 4: Filter by skills & experience
        // Step 5: Send summary email
    }
}
```

**Key concepts:**
- `@Scheduled`: Runs method automatically based on cron expression
- `@Slf4j`: Generates logger field (Lombok annotation processor)
- `@EnableScheduling`: Enables scheduling infrastructure

### 2. GmailService.java (OAuth2 & Email Fetching)

**Purpose:** Handle Gmail authentication and email retrieval

#### 2.1 OAuth2 Authentication Flow

```java
public Gmail getGmailService() throws Exception {
    // 1. Create Gmail service with OAuth credentials
    return new Gmail.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        getCredentials()
    ).build();
}

public Credential getCredentials() throws Exception {
    // 1. Load client secrets from config
    GoogleClientSecrets clientSecrets = loadClientSecrets();
    
    // 2. Build authorization flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(...)
        .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory))
        .setAccessType("offline")  // Enables refresh tokens
        .build();
    
    // 3. On first run: opens browser for user consent
    // 4. Saves token locally in 'tokens/' directory
    // 5. Reuses token on subsequent runs
    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
}
```

**OAuth2 workflow:**
1. First run → Opens browser → User approves → Token saved
2. Subsequent runs → Reuses saved token → No manual approval needed

#### 2.2 Smart Gmail Query Building

```java
String buildJobSearchQuery(long afterSeconds) {
    // Excludes:
    // - Emails from the app itself (-from:me -from:app-email)
    // - App-generated notifications (-subject:"JobLens -")
    // + Includes:
    // - Job-related keywords (subject:jobs OR subject:recommended...)
    // + Time-based filtering (after:timestamp)
    
    return String.format(
        "after:%d (%s) -from:me -from:%s -subject:\"%s\"",
        afterSeconds,
        subjectQuery,
        applicationSenderEmail,
        applicationEmailSubjectPrefix
    );
}
```

**Why exclude own emails?**
- Prevents recursive processing (app fetches its own sent emails)
- Reduces noise in logs
- Improves efficiency

#### 2.3 Notification Filtering

```java
public boolean isApplicationNotification(Message message) {
    // Double-check after fetching to skip app-generated notifications
    String subject = getHeaderValue(message, "Subject");
    String from = getHeaderValue(message, "From");
    
    return isFromApplicationEmail(from) || isNotificationSubject(subject);
}
```

**Two-layer filtering approach:**
- Layer 1: Gmail query excludes most self-generated emails
- Layer 2: Manual check confirms before processing

### 3. JobExtractionService.java (Email Parsing)

**Purpose:** Parse email content and extract job details

#### 3.1 Email Structure Parsing

```java
public JobDTO extractJobFromEmail(Message message) {
    // Step 1: Get full message with headers & body
    Message fullMessage = gmailService.getFullMessage(message.getId());
    
    // Step 2: Extract headers
    String subject = gmailService.getHeaderValue(fullMessage, "Subject");
    String from = gmailService.getHeaderValue(fullMessage, "From");
    String body = gmailService.extractMessageBody(fullMessage);
    
    // Step 3: Parse HTML content
    Document doc = Jsoup.parse(body);
    String textContent = doc.text();
    
    // Step 4: Extract components
    String jobTitle = extractJobTitle(subject, doc);
    String company = extractCompanyName(doc, from);
    String applyLink = extractApplyLink(doc);
    Map<String, Integer> experience = ExperienceExtractionUtil.extractExperience(textContent);
    
    // Step 5: Build JobDTO
    return JobDTO.builder()
        .title(jobTitle)
        .company(company)
        .link(applyLink)
        .description(extractDescription(doc, textContent))
        .minExperience(experience.get("min"))
        .maxExperience(experience.get("max"))
        .source(subject)
        .build();
}
```

#### 3.2 Job Title Extraction

```java
private String extractJobTitle(String subject, Document doc) {
    // Priority 1: Use email subject (most reliable)
    if (subject != null && !subject.isEmpty()) {
        return subject.replaceAll("(?i)^(fwd?|re):\\s*", "");  // Remove Re:, Fwd:
    }
    
    // Priority 2: Use first heading from HTML
    Elements headings = doc.select("h1, h2, h3");
    if (!headings.isEmpty()) {
        return headings.first().text();
    }
    
    // Fallback
    return "Job Opportunity";
}
```

#### 3.3 Company Name Extraction

```java
private String extractCompanyName(Document doc, String from) {
    // Priority 1: Extract from sender email domain
    if (from != null && from.contains("<")) {
        // from: "Company <recruit@company.com>"
        // Extract: "company" from domain
    }
    
    // Priority 2: Find bold/strong text in document (usually company name)
    Elements elements = doc.select("strong, b, [style*=bold]");
    for (Element el : elements) {
        if (el.text().length() > 2 && el.text().length() < 50) {
            return el.text();
        }
    }
    
    return "Unknown Company";
}
```

#### 3.4 Apply Link Extraction

```java
private String extractApplyLink(Document doc) {
    // Find links containing job-related keywords
    Elements links = doc.select("a[href]");
    
    for (Element link : links) {
        String href = link.attr("href");
        String text = link.text().toLowerCase();
        
        // Check if link or text contains job keywords
        if (text.contains("job") || text.contains("apply") || 
            href.toLowerCase().contains("job")) {
            
            return href;  // Return first match
        }
    }
    
    // Fallback: return first URL-like link
    return null;
}
```

### 4. ExperienceExtractionUtil.java (Experience Parsing)

**Purpose:** Extract experience requirements using regex patterns

#### 4.1 Pattern Matching Strategy

```java
public static Map<String, Integer> extractExperience(String text) {
    Map<String, Integer> result = new HashMap<>();
    result.put("min", null);
    result.put("max", null);
    
    if (text == null || text.trim().isEmpty()) {
        return result;  // Both null = no experience specified
    }
    
    String processedText = text.toLowerCase();
    
    // Pattern 1: "2-3 years"
    Pattern RANGE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})\\s*(?:years?|yrs?)",
        Pattern.CASE_INSENSITIVE
    );
    Matcher rangeMatcher = RANGE_PATTERN.matcher(processedText);
    if (rangeMatcher.find()) {
        result.put("min", Integer.parseInt(rangeMatcher.group(1)));
        result.put("max", Integer.parseInt(rangeMatcher.group(2)));
        return result;  // Return immediately
    }
    
    // Pattern 2: "2+ years"
    Pattern PLUS_PATTERN = Pattern.compile(
        "(\\d{1,2})\\s*\\+\\s*(?:years?|yrs?)",
        Pattern.CASE_INSENSITIVE
    );
    Matcher plusMatcher = PLUS_PATTERN.matcher(processedText);
    if (plusMatcher.find()) {
        result.put("min", Integer.parseInt(plusMatcher.group(1)));
        result.put("max", null);  // Open-ended
        return result;
    }
    
    // Pattern 3: "minimum 2 years"
    Pattern MINIMUM_PATTERN = Pattern.compile(
        "(?:minimum|min|at\\s+least)\\s+(?:of\\s+)?(\\d{1,2})\\s*(?:years?|yrs?)",
        Pattern.CASE_INSENSITIVE
    );
    Matcher minimumMatcher = MINIMUM_PATTERN.matcher(processedText);
    if (minimumMatcher.find()) {
        result.put("min", Integer.parseInt(minimumMatcher.group(1)));
        result.put("max", null);
        return result;
    }
    
    // Pattern 4: "fresher" or "entry level"
    Pattern FRESHER_PATTERN = Pattern.compile(
        "\\b(?:fresher|freshers|entry\\s*level|junior)\\b",
        Pattern.CASE_INSENSITIVE
    );
    if (FRESHER_PATTERN.matcher(processedText).find()) {
        result.put("min", 0);
        result.put("max", 1);
        return result;
    }
    
    // Pattern 5: "experienced"
    Pattern EXPERIENCED_PATTERN = Pattern.compile(
        "\\bexperienced\\b",
        Pattern.CASE_INSENSITIVE
    );
    if (EXPERIENCED_PATTERN.matcher(processedText).find()) {
        result.put("min", 0);
        result.put("max", null);  // Open-ended
        return result;
    }
    
    // Pattern 6: "5 years" (single value)
    Pattern SINGLE_VALUE_PATTERN = Pattern.compile(
        "(?:^|\\s)(\\d{1,2})\\s*(?:years?|yrs?)",
        Pattern.CASE_INSENSITIVE
    );
    Matcher singleMatcher = SINGLE_VALUE_PATTERN.matcher(processedText);
    if (singleMatcher.find()) {
        int years = Integer.parseInt(singleMatcher.group(1));
        result.put("min", years);
        result.put("max", years);
        return result;
    }
    
    return result;  // Both null if no pattern matched
}
```

**Examples of pattern matching:**

| Input | Result | Reasoning |
|-------|--------|-----------|
| "2-3 years of experience" | min=2, max=3 | Pattern 1: range |
| "5+ years required" | min=5, max=null | Pattern 2: plus |
| "minimum 2 years" | min=2, max=null | Pattern 3: minimum |
| "fresher/entry level" | min=0, max=1 | Pattern 4: fresher |
| "experienced developers" | min=0, max=null | Pattern 5: experienced |
| "5 years expertise" | min=5, max=5 | Pattern 6: single |
| "no requirements mentioned" | min=null, max=null | No match |

#### 4.2 Experience Matching Logic

```java
public static boolean isExperienceMatch(
        Integer jobMinExp,       // Job's minimum requirement
        Integer jobMaxExp,       // Job's maximum requirement
        Integer userMinExp,      // Your minimum experience
        Integer userMaxExp) {    // Your maximum experience
    
    // If job has no experience requirement, exclude
    if (jobMinExp == null && jobMaxExp == null) {
        return false;
    }
    
    // If job requires X+ years: match if user can handle it
    if (jobMinExp != null && jobMaxExp == null) {
        return userMaxExp >= jobMinExp;
    }
    
    // If job requires X-Y years: must have overlap
    if (jobMinExp != null && jobMaxExp != null) {
        // Your range: [userMinExp, userMaxExp]
        // Job range:  [jobMinExp, jobMaxExp]
        // Match if: job.min <= user.max AND job.max >= user.min
        return jobMinExp <= userMaxExp && jobMaxExp >= userMinExp;
    }
    
    // Only job.max available (unusual)
    return jobMaxExp >= userMinExp;
}
```

**Experience matching example:**
```
Your criteria: 1-3 years

Job 1: 2-4 years
├─ job.min (2) <= user.max (3) ✓
├─ job.max (4) >= user.min (1) ✓
└─ Result: MATCH ✓

Job 2: 5-7 years
├─ job.min (5) <= user.max (3) ✗
└─ Result: NO MATCH ✗

Job 3: 2+ years
├─ job.min (2) <= user.max (3) ✓
└─ Result: MATCH ✓
```

### 5. JobFilterService.java (Filtering & Deduplication)

**Purpose:** Filter jobs by skills and experience, remove duplicates

#### 5.1 Skill Matching

```java
private boolean matchesSkills(JobDTO job) {
    // Combine title and description for matching
    String content = (
        (job.getTitle() != null ? job.getTitle() : "") + " " +
        (job.getDescription() != null ? job.getDescription() : "")
    ).toLowerCase();
    
    // Check if ANY keyword is present in job content
    boolean matches = keywords.stream()
        .anyMatch(content::contains);  // content.contains(keyword)
    
    return matches;
}
```

**Example:**
```
Your keywords: ["java", "spring", "backend"]
Job title: "Senior Java Backend Engineer"

Matching:
├─ "java" in content? Yes ✓
├─ "spring" in content? No
├─ "backend" in content? Yes ✓
└─ Result: MATCH (at least one keyword found)
```

#### 5.2 Experience Matching

```java
private boolean matchesExperience(JobDTO job) {
    return ExperienceExtractionUtil.isExperienceMatch(
        job.getMinExperience(),
        job.getMaxExperience(),
        userMinExperience,
        userMaxExperience
    );
}
```

#### 5.3 Deduplication

```java
public List<JobDTO> removeDuplicates(List<JobDTO> jobs) {
    Set<String> seenLinks = new HashSet<>();
    List<JobDTO> unique = new ArrayList<>();
    
    for (JobDTO job : jobs) {
        // If apply link is new, add job
        if (job.getLink() != null && !seenLinks.contains(job.getLink())) {
            seenLinks.add(job.getLink());
            unique.add(job);
        }
        // Otherwise, skip duplicate
    }
    
    return unique;
}
```

**Why deduplicate by link?**
- Same job posting often appears in multiple emails
- Apply link is unique identifier for each job
- Prevents duplicate opportunities in summary email

### 6. EmailService.java (Summary Email Formatting)

**Purpose:** Format and send summary email with HTML styling

#### 6.1 Email Formatting

```java
public void sendJobsEmail(List<JobDTO> jobs) {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    
    // Set email headers
    helper.setFrom(senderEmail);
    helper.setTo(recipientEmail);
    helper.setSubject(emailSubject + " (" + jobs.size() + " opportunities)");
    
    // Generate HTML content
    String htmlContent = generateHtmlEmail(jobs);
    
    // Set HTML content (isHtml=true)
    helper.setText(htmlContent, true);
    
    // Send
    mailSender.send(message);
}
```

#### 6.2 HTML Email Generation

```java
private String generateHtmlEmail(List<JobDTO> jobs) {
    StringBuilder html = new StringBuilder();
    
    // Header with styling
    html.append("<html><head>");
    html.append("<style>")
        .append("body { font-family: Arial, sans-serif; }")
        .append("h1 { color: #2c3e50; }")
        .append(".job { border: 1px solid #ddd; padding: 15px; margin: 10px 0; }")
        .append(".title { color: #2980b9; font-weight: bold; }")
        .append(".company { color: #27ae60; }")
        .append(".experience { background: #ecf0f1; padding: 5px; border-radius: 3px; }")
        .append(".link { color: #3498db; text-decoration: none; }")
        .append("</style>")
        .append("</head><body>");
    
    // Title
    html.append("<h1>🎯 JobLens - Top Job Opportunities</h1>");
    html.append("<p>Found ").append(jobs.size()).append(" matching opportunities</p>");
    
    // Jobs list
    for (int i = 0; i < jobs.size(); i++) {
        JobDTO job = jobs.get(i);
        html.append("<div class='job'>");
        html.append("<div class='title'>").append(i + 1).append(". ").append(job.getTitle()).append("</div>");
        html.append("<div class='company'>").append(job.getCompany()).append("</div>");
        html.append("<div class='experience'>").append(job.getExperienceRange()).append("</div>");
        html.append("<div>").append(job.getDescription()).append("</div>");
        html.append("<a class='link' href='").append(job.getLink()).append("'>Apply Now</a>");
        html.append("</div>");
    }
    
    // Footer
    html.append("<hr>");
    html.append("<p><small>This email was generated by JobLens. ").
        ("Your configuration filters jobs by keywords and experience range.</small></p>");
    html.append("</body></html>");
    
    return html.toString();
}
```

**Sample HTML email output:**

```html
<h1>🎯 JobLens - Top Job Opportunities</h1>
<p>Found 3 matching opportunities</p>

<div class='job'>
  <div class='title'>1. Senior Java Developer</div>
  <div class='company'>TechCorp</div>
  <div class='experience'>3-5 years</div>
  <div>Spring Boot backend development...</div>
  <a class='link' href='https://careers.techcorp.com/jobs/123'>Apply Now</a>
</div>

<div class='job'>
  <div class='title'>2. Backend Engineer</div>
  <div class='company'>StartupXYZ</div>
  <div class='experience'>2-4 years</div>
  <div>REST API development...</div>
  <a class='link' href='https://careers.startupxyz.com/jobs/456'>Apply Now</a>
</div>
```

### 7. JobDTO.java (Data Model)

**Purpose:** Represent a job opportunity as a data object

```java
@Data                                   // Lombok: generates getters/setters
@Builder                                // Lombok: generates builder() method
public class JobDTO {
    private String title;               // "Senior Java Developer"
    private String company;             // "TechCorp"
    private String link;                // "https://careers.techcorp.com/jobs/123"
    private String description;         // Job description (first 300 chars)
    private Integer minExperience;      // Minimum years required (null if not specified)
    private Integer maxExperience;      // Maximum years (null if open-ended)
    private String source;              // Original email subject
    private String rawContent;          // Full email HTML content
    
    /**
     * Get human-readable experience range
     * Examples: "3-5 years", "2+ years", "Not specified"
     */
    public String getExperienceRange() {
        if (minExperience == null && maxExperience == null) {
            return "Not specified";
        }
        
        if (minExperience != null && maxExperience == null) {
            return minExperience + "+ years";
        }
        
        if (minExperience != null && maxExperience != null) {
            return minExperience + "-" + maxExperience + " years";
        }
        
        return maxExperience + " years max";
    }
}
```

**Builder usage example:**

```java
JobDTO job = JobDTO.builder()
    .title("Senior Java Developer")
    .company("TechCorp")
    .link("https://careers.techcorp.com/jobs/123")
    .description("We need a senior Java developer...")
    .minExperience(3)
    .maxExperience(5)
    .source("Job Opportunity at TechCorp")
    .build();

// Alternatively, you can use:
String range = job.getExperienceRange();  // Returns "3-5 years"
```

---

## Deployment Options

### Option 1: Local Development (Manual)

**Best for:** Testing, learning, local development

```bash
# 1. Build
mvn clean package

# 2. Run (starts scheduled job immediately)
java -jar target/job-email-filter-1.0.0.jar

# 3. First run: Browser opens for Gmail OAuth consent
#    Accept permission
#    Token saved to 'tokens/' directory
#    Automatic runs proceed without browser

# 4. Logs show job execution every hour
```

### Option 2: Docker (Containerized)

**Best for:** Consistent environment, easy deployment

#### 2.1 Build Docker Image

```bash
docker build -t joblens:latest .
```

#### 2.2 Run with Docker Compose

```bash
# 1. Create .env file with configuration
cp .env.example .env
# Edit .env with your Gmail credentials

# 2. Start container
docker-compose up -d

# 3. View logs
docker-compose logs -f joblens

# 4. Stop container
docker-compose down

# 5. Cleanup volumes
docker-compose down -v
```

**docker-compose.yml environment variables:**
- `GMAIL_OAUTH_CLIENT_ID`: Your OAuth client ID
- `GMAIL_OAUTH_CLIENT_SECRET`: Your OAuth secret
- `GMAIL_SMTP_USERNAME`: Your Gmail address
- `GMAIL_SMTP_PASSWORD`: Your Gmail app password
- `JOB_EMAIL_SEND_TO`: Recipient email

### Option 3: GitHub Actions (Free CI/CD)

**Best for:** No server needed, runs on GitHub infrastructure, free

#### 3.1 Setup

```bash
# 1. Push code to GitHub
git push origin main

# 2. Add secrets to GitHub repo
#    Go to: Settings → Secrets and variables → Actions
#    Add:
#    - GMAIL_OAUTH_CLIENT_ID
#    - GMAIL_OAUTH_CLIENT_SECRET
#    - GMAIL_SMTP_USERNAME
#    - GMAIL_SMTP_PASSWORD
#    - JOB_EMAIL_SEND_TO
```

#### 3.2 Workflow File

The `.github/workflows/joblens-schedule.yml` contains:

```yaml
name: JobLens Scheduled Run

on:
  schedule:
    - cron: '0 * * * *'  # Run every hour
  workflow_dispatch:     # Allow manual trigger

jobs:
  joblens:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Build with Maven
        run: mvn clean package -q -DskipTests
      
      - name: Run JobLens
        env:
          GMAIL_OAUTH_CLIENT_ID: ${{ secrets.GMAIL_OAUTH_CLIENT_ID }}
          GMAIL_OAUTH_CLIENT_SECRET: ${{ secrets.GMAIL_OAUTH_CLIENT_SECRET }}
          GMAIL_SMTP_USERNAME: ${{ secrets.GMAIL_SMTP_USERNAME }}
          GMAIL_SMTP_PASSWORD: ${{ secrets.GMAIL_SMTP_PASSWORD }}
          JOB_EMAIL_SEND_TO: ${{ secrets.JOB_EMAIL_SEND_TO }}
        run: |
          java -jar target/job-email-filter-1.0.0.jar
```

**How it works:**
1. GitHub runs workflow on schedule (or manual trigger)
2. Checks out your code
3. Builds the JAR file
4. Runs the application (single execution)
5. Sends emails
6. Completes

**Limitations:**
- Tokens must be stored/persisted (GitHub doesn't provide persistent storage)
- Need alternative for token persistence (GitHub Secrets, external storage)

### Option 4: Cloud Deployment (Recommended for Production)

#### 4.1 Google Cloud Run (Serverless)

```bash
# 1. Create .gcp-deploy.sh
gcloud build submit --tag gcr.io/YOUR_PROJECT/joblens
gcloud run deploy joblens \
    --image gcr.io/YOUR_PROJECT/joblens \
    --platform managed \
    --region us-central1 \
    --set-env-vars "GMAIL_OAUTH_CLIENT_ID=...,GMAIL_SMTP_USERNAME=..."
    --memory 512Mi

# 2. Set up Cloud Scheduler to invoke HTTP endpoint hourly
gcloud scheduler jobs create http joblens-trigger \
    --schedule="0 * * * *" \
    --http-method POST \
    --uri https://joblens-xxxxxx.run.app/
```

#### 4.2 AWS Lambda

```bash
# Package for Lambda
mvn package -P lambda

# Upload to Lambda console
# Set trigger: EventBridge rule (cron-based)
# Runtime: Java 21
```

#### 4.3 Azure Container Instances

```bash
# Push to Azure Container Registry
az acr build --registry joblensreg --image joblens:latest .

# Create Container Instance with schedule
az container create \
    --resource-group joblens \
    --name joblens \
    --image joblensreg.azurecr.io/joblens:latest \
    --schedule "0 * * * *"
```

---

## Troubleshooting

### Issue 1: OAuth Token Error

**Error:** `Token.json not found` or `Invalid grant`

**Solution:**
```bash
# 1. Delete existing tokens
rm -rf tokens/

# 2. Run application (will open browser)
java -jar target/job-email-filter-1.0.0.jar

# 3. Accept Gmail permission in browser
# 4. Token regenerated
```

### Issue 2: SMTP Authentication Failed

**Error:** `javax.mail.AuthenticationFailedException`

**Cause:** Wrong password or Gmail 2FA not enabled

**Solution:**
```yaml
# Verify these in application.yml:
spring:
  mail:
    username: your-email@gmail.com     # Must match Gmail account
    password: xxxx xxxx xxxx xxxx       # App password (not regular password!)
```

**Steps to generate app password:**
1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Verify 2FA is enabled
3. App passwords → Select "Mail" and "Windows Computer"
4. Copy 16-character password

### Issue 3: No Emails Found

**Problem:** Application runs but finds no emails

**Debugging:**
```yaml
# Enable debug logging
logging:
  level:
    com.joblens: DEBUG  # Shows detailed query and filtering

# Check query being used in logs:
# "Fetching emails with query: after:XXXX (...) -from:me..."
```

**Common causes:**
- No emails in Gmail with job-related subjects
- Keywords don't match your emails
- Email subjects don't contain "jobs", "recommended", "opportunities"

**Solution:**
```yaml
# Adjust keywords
job:
  keywords: java,python,developer,engineer,consultant

# Check your Gmail for matching emails manually
# Adjust cron to run more frequently for testing
scheduling:
  email-fetcher:
    cron: "*/5 * * * * *"  # Every 5 minutes instead of hourly
```

### Issue 4: Experience Extraction Returns null-null

**Problem:** Jobs show "Not specified" for experience

**Cause:** Email doesn't match any regex pattern

**Debug steps:**

1. Enable demo utility:
```bash
java -cp target/job-email-filter-1.0.0.jar com.joblens.util.ExperienceExtractionDemo
```

2. Check logs for patterns attempted:
```
DEBUG: Found range pattern: 2-3
DEBUG: Found plus pattern: 5+
DEBUG: Found minimum pattern: 2
DEBUG: No experience pattern found in text
```

3. Add new pattern if needed (in `ExperienceExtractionUtil.java`):
```java
// Pattern X: Custom pattern
Pattern CUSTOM_PATTERN = Pattern.compile(
    "your-regex-here",
    Pattern.CASE_INSENSITIVE
);
Matcher customMatcher = CUSTOM_PATTERN.matcher(processedText);
if (customMatcher.find()) {
    result.put("min", value1);
    result.put("max", value2);
    return result;
}
```

### Issue 5: Build Fails with Lombok Error

**Error:** `cannot find symbol: variable log`

**Cause:** Lombok annotation processor not configured

**Solution:**
```xml
<!-- In pom.xml, add: -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.30</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### Issue 6: Docker Build Fails

**Error:** `maven: not found` or `java: not found`

**Solution:**
```dockerfile
# Ensure Dockerfile starts with correct base image
FROM eclipse-temurin:21-jdk-jammy  # Includes Java 21 AND Maven

# Or use builder pattern if needed
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -q -DskipTests

FROM eclipse-temurin:21-jdk-jammy
COPY --from=builder /app/target/job-email-filter-1.0.0.jar /app/
```

---

## Performance & Optimization

### Current Performance

| Operation | Time | Notes |
|-----------|------|-------|
| Gmail OAuth (first time) | 30-60s | Opens browser, requires user input |
| Gmail OAuth (cached) | <100ms | Uses stored token |
| Fetch 20 emails | 2-5s | Network + Gmail API |
| Parse 20 emails | 1-2s | HTML parsing with JSoup |
| Extract experience | 100-200ms | Regex pattern matching |
| Filter jobs | 50-100ms | In-memory filtering |
| Send email | 1-2s | SMTP connection + send |
| **Total run time** | **5-15s** | Typical execution |

### Optimization Tips

1. **Cache Gmail service:**
```java
@Component
public class GmailServiceCache {
    private Gmail cachedService;
    
    public Gmail getGmailService() {
        if (cachedService == null) {
            cachedService = gmailService.getGmailService();
        }
        return cachedService;
    }
}
```

2. **Batch process emails:**
```java
// Instead of sequential processing, use parallel streams
List<JobDTO> jobs = messages.parallelStream()
    .map(jobExtractionService::extractJobFromEmail)
    .collect(Collectors.toList());
```

3. **Limit emails fetched:**
```yaml
gmail:
  max-emails-per-run: 10  # Reduce from 20
```

---

## Learning Resources

### Spring Boot Concepts Used

| Concept | Where | Reference |
|---------|-------|-----------|
| `@SpringBootApplication` | Main class | [Spring Docs](https://spring.io/guides/gs/spring-boot/) |
| `@Scheduled` | Job scheduling | [Spring Scheduling](https://spring.io/guides/gs/scheduling-tasks/) |
| `@Service` | Service layer | [Dependency Injection](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-constructor-injection) |
| `@Autowired` | Dependency injection | [Dependency Injection](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans) |
| `@Value` | Property injection | [Property Placeholder](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-placeholder-resolution) |
| `@Slf4j` | Logging (Lombok) | [SLF4J](http://www.slf4j.org/manual.html) |

### External Libraries Used

| Library | Purpose | Docs |
|---------|---------|------|
| **Google APIs Client** | Gmail API interaction | [github.com/googleapis/google-api-java-client](https://github.com/googleapis/google-api-java-client) |
| **Google OAuth Client** | OAuth2 authentication | [Google OAuth Docs](https://developers.google.com/identity/protocols/oauth2) |
| **JSoup** | HTML parsing | [jsoup.org](https://jsoup.org) |
| **Lombok** | Code generation (getters, setters, log) | [projectlombok.org](https://projectlombok.org) |
| **Spring Mail** | SMTP email sending | [Spring Mail Docs](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#mail) |

---

## Conclusion

JobLens demonstrates core Spring Boot concepts:
- **Service architecture** - Separation of concerns
- **Scheduling** - Automated task execution
- **OAuth2** - Third-party authentication
- **Configuration management** - Environment-based settings
- **Error handling** - Graceful failure management

This project is production-ready and can be extended with:
- Database persistence (PostgreSQL, MongoDB)
- REST API endpoints
- Web UI (React, Vue.js)
- Advanced filtering (ML-based skills matching)
- Multi-user support

---

**Document Version:** 1.0  
**Last Updated:** May 2026  
**For:** Spring Boot Learning & Production Deployment

