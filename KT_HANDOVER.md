# JobLens - Complete Knowledge Transfer & Architecture Handover Document

**Status**: Production-Ready System  
**Last Updated**: May 2026  
**Audience**: Junior Engineers, Incoming Maintainers, System Architects  
**Purpose**: Complete system understanding, operational knowledge, and maintenance guidelines

---

## Table of Contents

1. [Executive Summary: What JobLens Does](#executive-summary-what-joblens-does)
2. [Architecture Evolution: V1 → V2](#architecture-evolution-v1--v2)
3. [Complete Data Flow Pipeline](#complete-data-flow-pipeline)
4. [Core Services Deep Dive](#core-services-deep-dive)
5. [Resume Matching & Filtering Strategy](#resume-matching--filtering-strategy)
6. [Operational Details & Edge Cases](#operational-details--edge-cases)
7. [Error Handling & Failure Scenarios](#error-handling--failure-scenarios)
8. [Performance, Scaling & Limitations](#performance-scaling--limitations)
9. [Maintenance, Monitoring & Troubleshooting](#maintenance-monitoring--troubleshooting)
10. [Technical Debt & Future Roadmap](#technical-debt--future-roadmap)

---

## Executive Summary: What JobLens Does

### The Problem It Solves

You receive job opportunity emails multiple times per day. You manually:
1. Read each email to find the job link
2. Click the link and visit the job website
3. Read full job details
4. Compare against your skills and experience
5. Decide whether to apply

**This is repetitive, time-consuming, and prone to missing opportunities.**

### The Solution

JobLens is a **Spring Boot microservice** that automates the entire flow:

```
Email Inbox
    ↓ (Gmail API)
Extract Email Content + Find Apply Links
    ↓
Visit Job Website (Playwright Browser)
    ↓
Scrape Full Job Details from Website
    ↓
Parse Structured Data (Skills, Experience, Location)
    ↓
Compare Against Your Resume Profile
    ↓
Score & Rank By Relevance
    ↓
Send Summary Email with Top Opportunities
    ↓
Your Inbox
```

### What You Get in Your Email

```
Subject: JobLens - Top 10 Opportunities (Last 24h)

1. [Score: 100/100] Senior Backend Engineer - TechCorp
   San Francisco, CA | 3-5 years | Full-Time
   Match: Strong skills overlap (Java, Spring, Docker)
   Missing: AWS, Kubernetes
   [Apply Now →]

2. [Score: 85/100] Backend Lead - StartupXYZ
   ... (and 8 more)
```

---

## Architecture Evolution: V1 → V2

### Previous Version (V1): Email-Only Processing

| Aspect | V1 | Status |
|--------|-----|--------|
| **Email Fetching** | ✅ Gmail API OAuth2 | Works |
| **Email Parsing** | ✅ JSoup HTML parsing | Works |
| **Job Extraction** | ❌ Direct from email only | Limited |
| **Link Crawling** | ❌ Not implemented | N/A |
| **Website Scraping** | ❌ Not implemented | N/A |
| **Resume Parsing** | ❌ Not implemented | N/A |
| **Semantic Matching** | ❌ Keyword-only | Limited |
| **Email Filtering** | ✅ Basic keyword matching | Works |
| **Experience Extraction** | ✅ Regex patterns | Works |

**Limitations**:
- Job details extracted only from email content (incomplete, unstructured)
- No way to get full job description from actual job board
- No profile-based matching (just keyword filtering)
- High false positives due to keyword-only filtering
- Missing skills information

---

### Current Version (V2): Website-First Processing

| Aspect | V2 | Status | Rationale |
|--------|-----|--------|-----------|
| **Email Fetching** | ✅ Gmail API OAuth2 | Works | No change needed |
| **Email Scanning** | ✅ Extract apply links | Works | **NEW**: Email is now a link source, not data source |
| **Link Crawling** | ✅ Playwright browser | Works | **NEW**: Visit actual job website |
| **Website Scraping** | ✅ JSoup parsing | Works | **NEW**: Extract from rendered page |
| **Semantic Parsing** | ✅ Optional AI (OpenAI) | Works | **NEW**: Structured job metadata extraction |
| **Resume Parsing** | ✅ Tika + AI/Regex | Works | **NEW**: Extract candidate profile |
| **Resume Matching** | ✅ Scoring algorithm | Works | **NEW**: Semantic job-to-resume matching |
| **Deduplication** | ✅ By apply link | Works | Improved: Works on deduplicated list |
| **Ranking** | ✅ Relevance scoring | Works | **NEW**: Based on resume profile, not just keywords |

**Benefits**:
- ✅ Accurate job data from source (job website)
- ✅ Full job descriptions available
- ✅ Structured data extraction (skills, seniority, location)
- ✅ Profile-aware matching (resume vs job)
- ✅ Better relevance scoring
- ✅ Lower false positives
- ✅ Extensible for future AI improvements

**Tradeoffs**:
- ⚠️ Slower: Must visit every job website (30-60s per job vs instant)
- ⚠️ More complex: Browser automation adds complexity
- ⚠️ Fragile: Depends on website HTML structure
- ⚠️ Rate limiting: Job sites may rate-limit/block automated access
- ⚠️ More failure points: Browser, network, parsing all can fail
- ⚠️ Resource intensive: Each job requires a browser instance

---

## Complete Data Flow Pipeline

### The End-to-End Journey: From Email to Filtered Recommendations

```
┌─────────────────────────────────────────────────────────────┐
│ PHASE 1: EMAIL INGESTION & LINK EXTRACTION                │
└─────────────────────────────────────────────────────────────┘

  Gmail Inbox (100+ emails)
        ↓
  GmailService.fetchRecentJobEmails()
  ├─ OAuth2 authenticate
  ├─ Query: last 24h, subject contains "job", "opportunity", "career"
  ├─ Exclude emails from: {app itself, notifications}
  └─ Return: List<Message> [typically 5-20 messages]
        ↓
  Filter Result: 10 job-related emails found
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 2: EMAIL PARSING & APPLY LINK EXTRACTION             │
└─────────────────────────────────────────────────────────────┘

  JobExtractionService.extractJobsFromMessages()
  For each of 10 emails:
    ├─ Extract from email:
    │  ├─ Subject → Job Title
    │  ├─ From header → Company name
    │  ├─ Body HTML → Scan for links
    │  └─ Experience text → Parse with regex
    ├─ Create preliminary JobDTO:
    │  └─ title, company, apply_link (MOST IMPORTANT), experience_min/max
    └─ Store in extractedJobs list
        ↓
  Result: 10 jobs extracted (many with incomplete info since email ≠ full job posting)
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 3: DEDUPLICATION (Prevent Duplicate Processing)      │
└─────────────────────────────────────────────────────────────┘

  JobFilterService.removeDuplicates()
  ├─ Track seen apply_link values
  ├─ Skip if link already processed
  └─ Keep only first occurrence
        ↓
  Result: 10 → 8 unique jobs (2 were duplicates from different emails)
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 4: WEBSITE CRAWLING & FULL JOB DETAILS EXTRACTION    │
│          (THIS IS THE KEY DIFFERENCE FROM V1!)              │
└─────────────────────────────────────────────────────────────┘

  FOR EACH of 8 unique jobs (sequential, not parallel):
    JobCrawlerService.fetchJobPage(apply_link)
    ├─ Launch Playwright Chromium (headless mode)
    ├─ Navigate to: https://careers.techcorp.com/jobs/123
    ├─ Wait for: LoadState.NETWORKIDLE (JS execution complete)
    ├─ Extract from rendered HTML:
    │  ├─ Page title → job.title (overwrite email title)
    │  ├─ Page structure → job.company, job.location
    │  ├─ Job description → job.description (from job board, not email!)
    │  ├─ Skills section → job.skills = ["Java", "Spring", "Docker"]
    │  ├─ Experience requirement → job.experienceText
    │  ├─ Seniority indicators → job.seniority
    │  └─ Employment type → job.employmentType
    ├─ Return: JobPage object with raw HTML + extracted data
    └─ On failure (timeout, 404, etc.):
       └─ Log warning + return original job as-is
        ↓
  Result: 8 jobs with FULL, ACCURATE details from job websites
  Timeline: ~5-15 seconds per job = 40-120 seconds total
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 5: SEMANTIC PARSING (Optional AI Enhancement)        │
└─────────────────────────────────────────────────────────────┘

  SemanticJobParserService.parseJob(jobPage)
  IF openai.api-key is configured:
    ├─ Build prompt: "Extract from this HTML..."
    ├─ Send to OpenAI Chat Completion API
    ├─ Parse JSON response
    └─ Return: JobDTO with AI-parsed fields
  ELSE:
    └─ Use fallback regex-based parsing
        ↓
  Result: 8 jobs with structured metadata
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 6: RESUME PROFILE LOADING & CANDIDATE MATCHING      │
└─────────────────────────────────────────────────────────────┘

  ResumeParserService.loadUserProfile()
  ├─ Check if ${resume.file-path} is configured
  ├─ IF yes, read resume file (PDF, DOCX, etc.)
  │  ├─ Apache Tika extracts text
  │  ├─ OpenAI or regex parses:
  │  │  ├─ skills: ["Java", "Spring", "Docker", "Azure"]
  │  │  ├─ experienceYears: 5
  │  │  ├─ preferredRoles: ["Backend", "Architect"]
  │  │  ├─ domains: ["Finance", "SaaS"]
  │  │  ├─ locations: ["SF", "Remote"]
  │  │  ├─ seniority: "Senior"
  │  │  └─ employmentType: "Full-Time"
  │  └─ Return: UserProfile object
  └─ IF no:
     └─ Return: null (no profile-based matching)
        ↓
  Result: UserProfile or null
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 7: SEMANTIC RANKING & SCORING                         │
└─────────────────────────────────────────────────────────────┘

  JobMatchingService.rankJobs(jobs, userProfile)
  FOR EACH of 8 jobs:
    ├─ Calculate relevance score (0-100):
    │  ├─ Skill overlap: normalize lists + find intersection = +40 max
    │  ├─ Experience fit: (candidate_years >= job_min) AND (candidate_years <= job_max) = +20
    │  ├─ Role match: "Backend" in job.title = +15
    │  ├─ Location match: "SF" in job.location = +10
    │  ├─ Domain match: "Finance" in job.description = +10
    │  └─ Seniority match: candidate.seniority == job.seniority = +10
    ├─ Total: 0-100 (normalized)
    ├─ Store in: job.relevanceScore
    └─ Store match reasons in: job.matchReasons
        ↓
  Sort by relevanceScore descending
  Select top job.max-results (typically 10)
        ↓
  Result: 8 jobs → top 10 (all pass) sorted by score
  Example:
    1. [100/100] TechCorp - Senior Backend Engineer
    2. [85/100] StartupXYZ - Backend Lead
    3. [70/100] BigBank - Java Developer
    ... (5 more below threshold or out of top 10)
        ↓

┌─────────────────────────────────────────────────────────────┐
│ PHASE 8: EMAIL DELIVERY                                     │
└─────────────────────────────────────────────────────────────┘

  EmailService.sendJobsEmail(rankedJobs)
  ├─ Format as HTML email:
  │  ├─ Header: "🎯 JobLens - Top 10 Opportunities"
  │  ├─ For each job:
  │  │  ├─ Title + Company
  │  │  ├─ Location + Experience
  │  │  ├─ Match Score + Reasons
  │  │  ├─ Missing Skills
  │  │  └─ [Apply Now →] link
  │  └─ Footer with timestamp
  ├─ Send via Gmail SMTP (spring.mail.*)
  └─ Log success
        ↓
  Result: Email delivered to recipient inbox
        ↓
  User sees: Top 10 relevant jobs, ranked by profile match
```

### Key Insight: Email ≠ Job Details Source

**This is the critical architectural change from V1 to V2:**

```
V1 (OLD):
Email → Extract all job info directly → Limited/Unstructured data → Filter by keywords

V2 (NEW):
Email → Extract LINK ONLY → Visit actual job website → Parse full details → Semantic matching
          ↑ Email is just a delivery mechanism
          ↑ Real job data comes from job board
```

---

## Core Services Deep Dive

### Service 1: GmailService (Email Ingestion)

**Responsibility**: Authenticate with Gmail and fetch job-related emails

#### Configuration

```yaml
gmail:
  oauth:
    client-id: "YOUR_CLIENT_ID"
    client-secret: "YOUR_CLIENT_SECRET"
    redirect-uri: "http://localhost:8888/callback"  # Local dev only
    token-file-path: "tokens/"
    scopes: "https://www.googleapis.com/auth/gmail.readonly"
  lookback-hours: 24           # Only fetch emails from last 24h
  max-emails-per-run: 20       # Limit to prevent timeout
```

#### Query Strategy

```
Gmail query: after:{timestamp} subject:(jobs OR opportunity OR career) -from:me -label:sent
```

**Why this specific query?**
- `after:{timestamp}`: Narrow time window (24h) = faster query
- `subject:(...)`: Only job-related subjects = high precision
- `-from:me`: Exclude self-generated emails (the app itself)
- `max-results: 20`: Prevent timeout (Gmail query can be slow with large mailbox)

#### OAuth Token Persistence

```
Directory: tokens/
File: StoredCredential (binary format from Google auth library)

Key point: Token is REUSED across runs
├─ First run: Browser opens → User accepts → Token saved
├─ Subsequent runs: Token loaded from file → No browser needed
└─ Token auto-refresh: Handles expiration transparently
```

**Gotcha**: If `StoredCredential` is deleted, user must re-authenticate on next run.

#### Error Handling

```java
// If Gmail API is unreachable
→ Throw exception, caught in main scheduler
→ Log error, send email notification (optional)
→ Next run will retry (scheduled)

// If query returns 0 results
→ Send "no jobs found" email (user knows system is working)

// If rate-limited by Gmail
→ Exception propagates, next run retries
→ Currently no backoff strategy (relies on 1-hour interval)
```

---

### Service 2: JobExtractionService (Email Parsing)

**Responsibility**: Parse email HTML and extract basic job data + apply links

#### Email Structure Assumptions

```
Email body can be:
├─ HTML (common): <p>, <div>, <a href="apply-link"> tags
├─ Multipart: HTML + text + attachments
├─ Plain text: Job details as text only
└─ Base64 encoded: Requires decoding before parsing
```

#### Extraction Logic

```java
// For each email:
1. Get full message content (not just snippet)
2. Decode if Base64 encoded
3. Parse with Jsoup
4. Apply extraction heuristics:
   ├─ Title: From email subject (or first <h1>/<h2>)
   ├─ Company: From sender domain OR email body
   ├─ Link: Find href containing "job", "apply", "career"
   ├─ Description: First 300 chars of <p> tags
   └─ Experience: Regex patterns ("2-5 years", "minimum 3", etc.)
5. Return: JobDTO or null if required fields missing
```

**Important**: This extraction is **greedy** — uses first match found. If email has multiple job postings, only first is extracted.

#### Failure Points

```
What can fail:
├─ Email not actually about a job → Returns null
├─ Email has no apply link → Link field empty
├─ HTML is malformed → Jsoup still parses but data might be incomplete
├─ Company name not detectable → Defaults to "Unknown Company"
└─ Experience regex doesn't match → Fields stay null (matched later on website)
```

---

### Service 3: JobCrawlerService (Website Scraping)

**Responsibility**: Visit job website and extract full job details

**THIS IS THE CRITICAL SERVICE** — Most complexity lives here.

#### Browser Lifecycle

```
For each job:
1. Start: Playwright.create() → Browser.launch() → Page.newPage()
2. Navigate: page.navigate(url, LoadState.NETWORKIDLE, timeout=30s)
3. Wait: page.waitForTimeout(2000) — extra safety wait
4. Extract: page.content() → full rendered HTML
5. Parse: Jsoup.parse(html)
6. Close: Browser closes (cleanup in try-with-resources)

Timeline: ~5-10 seconds per job (including network latency)
```

#### Why Playwright + JSoup Combination?

```
Playwright alone:  Can navigate & render, but hard to parse HTML structure
JSoup alone:       Can parse, but can't execute JavaScript
Together:          Playwright renders → JSoup parses
                   = Full dynamic page support + precise HTML parsing
```

#### Selectors & Heuristics

```java
// Website layouts vary wildly — try multiple selectors

Example: Extract job title
├─ Try: document.select("h1, h2, title") → First match
├─ If none: Use email-extracted title as fallback
└─ If still empty: "Job Opportunity"

Example: Extract skills
├─ Look for: <li>, <p>, <span> containing tech keywords
│  (java, spring, docker, kubernetes, aws, azure, etc.)
├─ Split by delimiters: comma, bullets, pipes
├─ Filter: Keep 3-30 char tokens
└─ Deduplicate and return

Example: Infer seniority
├─ Scan full text for keywords:
│  ├─ "senior" OR "sr." OR "lead" → "Senior"
│  ├─ "junior" OR "jr." OR "entry" → "Junior"
│  └─ Default: "Mid-level"
```

#### Browser Instance Management

**Current Strategy**: Create fresh browser per job
```
Pros:
✅ Simple, no state pollution
✅ Easier to debug
✅ Each job is independent

Cons:
❌ Slow: 30-60 seconds for 10 jobs
❌ Resource intensive: Multiple Chromium processes
❌ Could be optimized: Browser pool/reuse
```

**Potential Optimization**: Browser pool (not implemented yet)
```java
// Pseudocode for future enhancement
BrowserPool pool = new BrowserPool(maxBrowsers=3);
for (JobDTO job : jobs) {
    Page page = pool.getPage();  // Reuse from pool
    try {
        JobPage result = crawlPage(page, job.getLink());
    } finally {
        pool.returnPage(page);  // Reset & return to pool
    }
}
```

#### Timeout & Failure Handling

```
What can timeout:
├─ Network slow → page.navigate() waits up to 30s
├─ Website down → 30s timeout → Caught as exception
├─ Infinite JavaScript → 30s timeout → Returns whatever loaded
└─ Anti-bot blocking → Returns 403/429 → Treated as failure

Fallback: If crawling fails → Return original email-extracted job as-is
```

**Important**: System continues even if 1-2 jobs fail to crawl (fail-open strategy)

---

### Service 4: ResumeParserService (Candidate Profile)

**Responsibility**: Extract candidate skills/preferences from resume file

#### Two-Tier Parsing Strategy

```
Tier 1: If OpenAI API key configured
├─ Send resume text to OpenAI
├─ Prompt: "Extract: skills, experience, roles, domains, locations, seniority"
├─ Parse JSON response
└─ Return: UserProfile with structured data

Tier 2: If OpenAI unavailable OR fails
├─ Use regex + keyword matching
├─ Search for known skill lists
├─ Extract experience years with regex
├─ Match against predefined role/domain keywords
└─ Return: UserProfile with heuristic data
```

#### Text Extraction from Resume Files

```java
// Apache Tika handles:
✅ PDF (most common)
✅ DOCX (Microsoft Word)
✅ RTF (older format)
✅ ODT (OpenOffice)
✅ TXT (plain text)

Limitations:
❌ Images-only PDF (scanned resume) → Extraction fails silently
❌ Special encodings → May return gibberish
❌ Very large files → Tika might timeout
```

#### Resume Profile Structure

```java
UserProfile {
    List<String> skills;              // ["Java", "Spring", "Docker"]
    Integer experienceYears;           // 5
    List<String> preferredRoles;      // ["Backend", "Architect"]
    List<String> domains;              // ["Finance", "SaaS"]
    List<String> locations;            // ["SF", "Remote"]
    String seniority;                  // "Senior"
    String employmentType;             // "Full-Time"
}
```

#### Known Limitations

```
What's NOT handled:
├─ Salary expectations (not extracted)
├─ Visa sponsorship requirements (not extracted)
├─ Work authorization status (not extracted)
├─ Certifications (minimal support)
├─ Specific tool proficiency levels (binary: present or not)
└─ Education details (minimal support)

Why? These fields don't affect job matching logic currently.
Future: Extend profile if needed.
```

---

### Service 5: JobMatchingService (Semantic Ranking)

**Responsibility**: Score each job against candidate profile

#### Scoring Algorithm

```
Max Score: 100 (normalized)

1. Skill Matching (up to 40 points)
   Resume: ["Java", "Spring", "Docker", "Azure"]
   Job:    ["Java", "Spring", "Docker", "AWS", "Kubernetes"]
   
   Overlap: {"Java", "Spring", "Docker"} = 3 skills
   Points: min(3 * 15, 40) = 40
   
   Logic: More overlap = higher score, capped at 40

2. Experience Alignment (up to 20 points)
   Candidate: 5 years
   Job Requirement: 3-7 years
   
   Check 1: 5 >= 3? YES → +10 points
   Check 2: 5 <= 7? YES → +10 points
   Total: 20 points
   
   Logic: Must meet BOTH min and max to get full points

3. Role Matching (up to 15 points)
   Candidate Roles: ["Backend", "Architect"]
   Job Title: "Senior Backend Engineer"
   
   Match: "Backend" in title? YES → 15 points
   
   Logic: If ANY preferred role appears in title, award points

4. Location Matching (up to 10 points)
   Candidate: ["San Francisco", "Remote"]
   Job: "San Francisco, CA"
   
   Match: "San Francisco" in job location? YES → 10 points

5. Domain Matching (up to 10 points)
   Candidate: ["Finance", "SaaS"]
   Job Description: "Build fintech microservices..."
   
   Match: "Finance" (fintech) in description? YES → 10 points
   
   Logic: Keyword search in description

6. Seniority Matching (up to 10 points)
   Candidate: "Senior"
   Job: "Senior"
   
   Exact match? YES → 10 points

Total: 40 + 20 + 15 + 10 + 10 + 10 = 105 → Normalized to 100
```

#### Normalization & Ranking

```java
// After scoring all jobs:
List<JobDTO> sorted = jobs.stream()
    .filter(job -> job.relevanceScore > 0)  // Skip zero scores
    .sorted(comparingInt(JobDTO::getRelevanceScore).reversed())  // Highest first
    .limit(maxResults)  // Top N (typically 10)
    .collect(toList());
```

**Important Caveat**: If profile is null (no resume provided), jobs are returned unsorted (or in order received).

---

## Resume Matching & Filtering Strategy

### Current Implementation (V2)

```
Resume is OPTIONAL but STRONGLY RECOMMENDED
├─ If provided:
│  ├─ Load candidate profile
│  ├─ Score each job 0-100
│  └─ Rank by relevance score
└─ If not provided:
   ├─ Return jobs unsorted (or by extraction order)
   └─ User gets all jobs but no personalization
```

### How Resume-to-Job Matching Works

```
RESUME PROFILE                    JOB POSTING
──────────────────────────────    ──────────────────────────
Skills: 
["Java",                          Job Skills:
 "Spring",                        ["Java", "Spring", "Docker", "AWS"]
 "Docker",
 "Azure"]
                    ↓
                OVERLAP CHECK
                ├─ ["Java", "Spring", "Docker"] match
                └─ [Azure] missing, ["AWS"] extra
                    ↓
                SCORE: 40/40 pts

Experience: 5 years               Job Requirement: 3-7 years
                    ↓
                RANGE CHECK
                ├─ 5 >= 3? YES
                ├─ 5 <= 7? YES
                └─ SCORE: 20/20 pts

Preferred Role:                   Job Title:
["Backend"]                       "Senior Backend Engineer"
                    ↓
                TEXT MATCH
                ├─ "Backend" found in title
                └─ SCORE: 15/15 pts

... (location, domain, seniority)
                    ↓
TOTAL SCORE: 100/100
```

### Matching Edge Cases

```
Edge Case 1: Job has no experience requirement
├─ Job fields: minExperience=null, maxExperience=null
├─ Scoring: Baseline +5 points (no requirement = slightly favorable)
└─ Reasoning: Job is entry-level friendly

Edge Case 2: Candidate is overqualified
├─ Candidate: 10 years, Senior
├─ Job: 3-7 years, Junior
├─ Score: +10 (min met), +0 (exceeds max)
├─ Total: 10 points for experience only
└─ Problem: Currently no penalty for overqualification

Edge Case 3: Resume not provided
├─ userProfile = null
├─ All scores skipped
├─ Jobs returned unsorted
└─ User gets all matches without ranking

Edge Case 4: Resume but very different from job market
├─ Resume: Paleontology background
├─ Job Market: Tech jobs
├─ Skill overlap: Minimal
├─ Score: <20 points
└─ Likely filtered out or ranked low
```

### Planned vs Implemented Features

```
✅ IMPLEMENTED:
├─ Resume text extraction (Tika)
├─ Skill extraction (regex + AI)
├─ Experience years extraction (regex)
├─ Role/domain inference (keyword matching)
├─ Score-based ranking

❌ NOT IMPLEMENTED (but designed for):
├─ Embeddings/Vector search for semantic skill matching
├─ Resume skill leveling (Junior vs Senior Java)
├─ Skill importance weighting
├─ Learning from user click behavior
├─ Time-decay (newer jobs ranked higher)
├─ Negative resume signals (deal-breaker keywords)
└─ Candidate availability window

WHY NOT IMPLEMENTED:
└─ Scope/Time constraints, not architectural limitation
```

### Future Enhancement: Semantic Vector Matching

```
Planned architecture (when resources allow):

1. Generate embeddings during resume parsing
   ├─ Resume skills → Vector embeddings
   └─ Store in: userProfile.skillEmbeddings

2. Generate embeddings during job parsing
   ├─ Job skills → Vector embeddings
   └─ Store in: jobDTO.skillEmbeddings

3. Use cosine similarity for matching
   ├─ Current: Exact string matching {"Java" vs "java"}
   ├─ Future: Semantic {"Java" ≈ "JavaSDK" ≈ "JDK"}
   └─ Use: OpenAI Embeddings API or local sentence-transformers

4. Benefits:
   ├─ Handle synonyms (REST ≈ RESTful)
   ├─ Fuzzy matching (Pythno → Python)
   ├─ Semantic understanding (microservices ≈ distributed systems)
   └─ Better ranking
```

---

## Operational Details & Edge Cases

### Scheduler Configuration

```yaml
scheduling:
  email-fetcher:
    cron: "0 0 * * * *"  # Midnight UTC daily

Why midnight?
├─ Lowest traffic time (fewer users online)
├─ Less competition for job posting updates
├─ Users see fresh results when they wake up
└─ Reduces resource contention on job websites
```

### Email Query Behavior

```
Gmail API Query:
├─ Searches entire mailbox every run
├─ Returns emails from last 24h (configured via lookback-hours)
├─ Filters duplicates using apply_link
├─ Max 20 emails returned per run
└─ Time complexity: O(1) — Gmail indexes are fast

Risk: What if same job appears in multiple emails?
├─ Email 1: "TechCorp hiring for Backend Engineer"
├─ Email 2: Same job from different recruiter
├─ Deduplication by apply_link catches this
└─ Only 1 job processed (first occurrence)
```

### Duplicate Prevention

```
Strategy: Track by apply_link

Why not track by job title or company?
❌ "Backend Engineer" at TechCorp could be different level
❌ Same company might post same role multiple times

Why apply_link?
✅ Unique identifier for specific job posting
✅ Different roles = different links
✅ Reposted jobs = same link = detected as duplicate
```

### Rate Limiting & Anti-Bot Handling

```
Current Status: NOT IMPLEMENTED

What could cause issues:
├─ Job websites detect Playwright browser → Block request
├─ Rapid consecutive requests → IP-based rate limiting
├─ User-Agent missing/suspicious → 403 Forbidden
└─ Too many requests from same IP → Temporary block

Mitigation (not implemented):
├─ Add realistic User-Agent headers
├─ Implement exponential backoff on failure
├─ Rotate proxy IPs (if needed)
├─ Add delays between requests
└─ Respect robots.txt

Why not implemented?
├─ Assumption: Small-scale personal use
├─ Most job sites allow crawling (careers pages are public)
└─ Low frequency (1 run per day)

Risk: If job site rate-limits our traffic
├─ Crawler fails silently
├─ Jobs return with email-only data (fallback)
└─ Next run retries

Future: Add rate-limit detection & backoff if needed
```

### Timeout & Error Recovery

```
Timeouts in system:

1. Gmail API query: 30s (configured in Gmail client)
   ├─ If timeout: Exception logged, email not fetched
   ├─ Retry: Next scheduled run

2. Job page crawling: 30s per job
   ├─ If timeout: Fallback to email-extracted data
   ├─ Retry: Not retried (move to next job)
   └─ Total jobs affected: Partial failure acceptable

3. OpenAI API call: 30s (if enabled)
   ├─ If timeout: Fallback to regex parsing
   ├─ Retry: Not retried
   └─ Job still usable with lower quality data

4. Email SMTP send: 10s
   ├─ If timeout: Exception propagates
   ├─ Retry: Next scheduled run
   └─ User doesn't know if email sent
```

**Problem**: If email send fails, user doesn't know. Future: Log to database or Slack.

### Session & Cookie Handling

```
Current: NONE

Browser instances are stateless:
├─ Each new browser = fresh cookies
├─ No login required (jobs pages are public)
└─ No session state maintained across runs

Implication: Can't access protected jobs pages
├─ If job requires login to view full description
├─ System can't access it
├─ Falls back to email-extracted data

If needed in future:
├─ Add login flow to browser automation
├─ Store credentials securely
├─ Handle session expiration
└─ Complexity increases significantly
```

### Memory Considerations

```
Memory usage per job crawl:
├─ Browser instance: ~100-200 MB
├─ Rendered HTML in memory: 1-10 MB (typical)
├─ Parsed JobDTO: <1 MB

For 10 jobs:
├─ Sequential processing: 1 browser at a time = ~200 MB constant
├─ Parallel processing: 3 browsers at a time = ~600 MB peaks

Current: Sequential (safer)
Risk: Very large job descriptions (10MB+ HTML) could cause OOM
Mitigation: Browser auto-closes, Garbage Collection handles cleanup

If system becomes memory-constrained:
├─ Implement browser pool with fixed size
├─ Add memory monitoring/alerts
├─ Reduce max-results to lower concurrent jobs
└─ Process jobs in batches
```

### Logging Strategy

```yaml
logging:
  level:
    root: INFO                  # Default level
    com.joblens: DEBUG          # Detailed JobLens logs
    com.google.api: WARN        # Suppress Gmail API debug spam
    com.microsoft.playwright: WARN  # Suppress Playwright logs

What we log (DEBUG level):
├─ JobLensApplication: Step entry/exit, counts
├─ GmailService: Query used, messages found
├─ JobExtractionService: Title, company, link extracted
├─ JobCrawlerService: URL visited, parse success/fail
├─ JobMatchingService: Score calculated for each job
└─ EmailService: Email sent confirmation

What NOT logged (security):
├─ OAuth credentials
├─ Email content
├─ User profile details
├─ Resume file path
└─ Sensitive skill data

Log destinations:
├─ Console: Real-time monitoring
├─ Rolling file: LastNDays/*.log (if configured)
└─ Future: Centralized logging (ELK, Splunk)
```

---

## Error Handling & Failure Scenarios

### Categorized Failure Points

```
CATEGORY 1: Gmail Authentication Failures
─────────────────────────────────────────
Failure: StoredCredential missing or expired

Behavior:
├─ First run after credential deletion: Browser opens
├─ User accepts OAuth → New token saved
├─ System continues normally

Failure: Gmail API key invalid

Behavior:
├─ OAuth fails immediately
├─ Exception caught in main scheduler
├─ Email sent (optional): "Authentication failed"
├─ Next run retries

Current Error Handling:
└─ Throw exception, log error, exit gracefully

Future Improvement:
├─ Add retry logic with exponential backoff
├─ Add alerting (email/Slack notification)
└─ Add health check endpoint


CATEGORY 2: Email Extraction Failures
──────────────────────────────────────
Failure: Email is not actually about a job

Behavior:
├─ JobExtractionService returns null
├─ Null skipped in extraction loop
├─ Not added to jobs list
└─ No error (just filtered out)

Failure: Email has no apply link

Behavior:
├─ job.link = null
├─ Deduplication skips (can't track without link)
├─ Job might be processed twice
└─ Currently: Not handled

Future: Store jobs without links as-is, compare by title+company


CATEGORY 3: Website Crawling Failures
──────────────────────────────────────
Failure: Website returns 404 (job posting removed)

Behavior:
├─ Playwright gets empty page
├─ JSoup parses but finds no job details
├─ JobPage object has null/empty fields
├─ Fallback to email-extracted data
└─ Job still included (with lower quality)

Failure: Website blocks Playwright (403 Forbidden)

Behavior:
├─ Playwright.navigate() throws exception
├─ Caught in enrichJobWithSemanticData
├─ Returns original JobDTO (email data)
├─ Job still sent, just less detailed
└─ Logged as warning

Failure: Website renders JavaScript but content loads from API

Behavior:
├─ Playwright waits for LoadState.NETWORKIDLE
├─ API requests complete
├─ Content should load
├─ If API fails: Partial content returned
└─ Better than email-only data

Failure: 30-second timeout (network very slow)

Behavior:
├─ Playwright cancels navigation
├─ Exception caught
├─ Original job returned
├─ Logged as warning
└─ Next job continues


CATEGORY 4: Semantic Parsing Failures
─────────────────────────────────────
Failure: OpenAI API is down

Behavior:
├─ HTTP request fails
├─ Exception caught
├─ Fallback to regex parsing
├─ Job still ranked with lower confidence
└─ Logged as warning

Failure: OpenAI misunderstands HTML (weird structure)

Behavior:
├─ Response doesn't match expected JSON format
├─ Exception during JSON parsing
├─ Fallback to regex
├─ Job still ranked
└─ Logged as error


CATEGORY 5: Resume Parsing Failures
────────────────────────────────────
Failure: Resume file not found

Behavior:
├─ ResumeParserService logs warning
├─ Returns null UserProfile
├─ JobMatchingService skips scoring
├─ All jobs returned unsorted
└─ User doesn't see personalized ranking

Failure: Resume file corrupted or unreadable

Behavior:
├─ Tika throws exception
├─ Caught in loadUserProfile
├─ Returns null
└─ System continues (no personalization)

Failure: Resume extracted but OpenAI parsing fails

Behavior:
├─ Fallback to regex heuristics
├─ Profile extracted with lower quality
├─ Scoring still works
└─ System continues


CATEGORY 6: Email Delivery Failures
────────────────────────────────────
Failure: SMTP authentication fails

Behavior:
├─ EmailService throws AuthenticationException
├─ Not caught in main scheduler
├─ Propagates to exception handler
├─ Logged as error
└─ Email NOT sent (user unaware!)

PROBLEM: Silent failure — user doesn't know email wasn't sent

Solution needed:
├─ Send alert email (if possible)
├─ Log to database
├─ Expose health check endpoint
└─ Add retries


Failure: Recipient email address invalid

Behavior:
├─ SMTP rejects email
├─ EmailService throws exception
├─ Not sent
└─ Logged as error


CATEGORY 7: Data Quality Issues
────────────────────────────────
Failure: Job has no title

Behavior:
├─ Title field = null or empty
├─ Fallback: "Job Opportunity"
├─ Scoring affected: Can't match preferred roles
└─ User sees: Relevance score lower than deserved

Failure: Job has no apply link

Behavior:
├─ Link field = null
├─ Deduplication skipped (future issue: possible duplicates)
├─ Email sent but user can't click apply
└─ Problem: Metadata in email, link broken


CATEGORY 8: Concurrency Issues
───────────────────────────────
Failure: Scheduler runs twice simultaneously

Behavior:
├─ Job runs at midnight → Long-running (2+ minutes)
├─ Another trigger at 00:01 → Scheduler invokes again?
├─ Both access same files
└─ Possible: Duplicate emails sent, race conditions

Current Status: NOT HANDLED

Prevention:
├─ Spring @Scheduled is single-threaded by default
├─ But if manually triggered + scheduled trigger overlap
├─ Could cause issues

Solution:
└─ Add @SchedulerLock (if using distributed scheduler)
```

### Graceful Degradation Strategy

```
JobLens is designed to FAIL OPEN (return partial results):

Scenario: Website crawling fails for 5/10 jobs

├─ 5 jobs with full website details ✅
├─ 5 jobs with email-only details ⚠️ (lower quality)
└─ All 10 sent to user (not perfect but usable)

Alternative (FAIL CLOSED): Would be:
├─ All or nothing: Either send email with 10 jobs or send none
└─ If any job failed: Retry entire batch
└─ Risk: No email sent due to 1 failure


Current Philosophy: "Partial results > No results"
├─ Trade-off: Some jobs have lower data quality
├─ Benefit: User always gets something useful
└─ Alternative would be: User gets nothing
```

---

## Performance, Scaling & Limitations

### Current Performance Baseline

```
Measured with: Typical 10-15 job emails

Phase 1: Gmail fetch         ~2s
Phase 2: Email extraction    ~1s (10 emails)
Phase 3: Deduplication       ~100ms
Phase 4: Website crawling    ~60-120s (6-12s per job × 10)
Phase 5: Semantic parsing    ~3-5s (if AI enabled)
Phase 6: Resume loading      ~500ms
Phase 7: Ranking            ~100ms
Phase 8: Email composition  ~1s
─────────────────────────────────────
Total:                       ~70-140s (1-2 minutes)

Bottleneck: Website crawling (80% of time)
└─ Unavoidable: Requires actual browser navigation
```

### Scaling Considerations

```
Current Assumptions:
├─ Single server deployment
├─ 1 run per day
├─ ~10-20 job emails per run
├─ Sequential job crawling (not parallel)
└─ Single user

What changes at scale:

SCALE SCENARIO 1: 50 emails per day
├─ Time: 50 jobs × 8s = 400s = 6.7 minutes
├─ Problem: Might exceed next scheduled run
├─ Solution: Increase scheduling interval OR parallelize

SCALE SCENARIO 2: Multiple users (SaaS)
├─ Multiple scheduler instances needed
├─ Risk: Duplicate credential files
├─ Solution: Move config to database/vault

SCALE SCENARIO 3: Parallel job crawling
├─ Use executor service for thread pool
├─ Memory: 3 concurrent browsers = 600MB
├─ Problem: Website rate-limiting
├─ Solution: Implement backoff/retry logic

SCALE SCENARIO 4: Persistent job database
├─ Currently: No database, only emails
├─ Future need: Store jobs for deduplication across days
├─ Future need: Track user clicks/actions
├─ Future need: Historical job data
```

### Known Limitations

```
LIMITATION 1: Single scheduler instance
├─ Can't run on multiple servers simultaneously
├─ Would cause duplicate emails
├─ Solution: Distributed scheduler (Quartz with distributed lock)

LIMITATION 2: No persistent storage
├─ Jobs not stored
├─ Can't track historical data
├─ Can't deduplicate across multiple runs
├─ Solution: Add PostgreSQL/MongoDB

LIMITATION 3: No user authentication
├─ System assumes single user
├─ All jobs go to same email address
├─ Can't support multiple users
├─ Solution: Add user accounts, multi-tenant architecture

LIMITATION 4: Website parsing fragility
├─ HTML structure changes → Selectors break
├─ No automatic detection/recovery
├─ Solution: Periodic selector updates, use AI for parsing

LIMITATION 5: No learning/feedback loop
├─ Scoring based on static resume profile
├─ Doesn't learn from user interactions
├─ Can't improve relevance over time
├─ Solution: Track clicks, add ML-based ranking

LIMITATION 6: Resume must be manually provided
├─ Can't auto-sync from LinkedIn
├─ Can't parse from email signature
├─ Solution: Add LinkedIn OAuth, email parsing

LIMITATION 7: Rate limiting not handled
├─ Job sites might block our crawler
├─ No backoff or retry logic
├─ Solution: Add exponential backoff, proxy rotation

LIMITATION 8: No API/UI
├─ Only email-based output
├─ Can't view results in browser
├─ Solution: Add REST API + React frontend
```

---

## Maintenance, Monitoring & Troubleshooting

### What to Monitor

```
Health Checks:

1. Scheduler execution
   └─ Did the job run? (Check logs for "Starting scheduled job")

2. Email fetching success rate
   └─ Are emails being fetched? (Count of emails in logs)

3. Website crawling success rate
   └─ Are websites being crawled? (Count of successful crawls vs failures)

4. Email delivery confirmation
   └─ Are emails being sent? (Mail server logs)

5. Error rate
   └─ Exception count in logs

6. Performance metrics
   └─ Total execution time (from log timestamps)

Where to add monitoring (not current implemented):
├─ Prometheus metrics (if using Spring Boot Actuator)
├─ Datadog/New Relic integration
├─ CloudWatch if running on AWS
├─ Custom metrics stored in database
```

### Common Troubleshooting

```
Problem 1: No jobs found in email

Check:
├─ 1. Gmail credentials valid? Try manually accessing Gmail
├─ 2. Email filter correct? Check application.yml query
├─ 3. Subject keywords correct? Adjust subject-terms
├─ 4. Lookback window? Increase lookback-hours temporarily
├─ 5. Logs: Look for "Fetching emails with query: ..."

Fix:
├─ Add more keywords: job, opportunity, vacancy, hiring, recruiter
├─ Increase lookback-hours from 24 to 48
├─ Check if emails are being auto-archived


Problem 2: Emails fetched but jobs not extracted

Check:
├─ 1. Email structure: Are jobs really in email body?
├─ 2. Apply link present? Some emails might not have link
├─ 3. Logs: Look for extraction errors
├─ 4. Sample email: Download and inspect raw HTML

Fix:
├─ Verify email sender is legitimate job source
├─ Add debug logging to extractApplyLink()
├─ Manually test JSoup parsing on sample email


Problem 3: Website crawling failing (all jobs show email data only)

Check:
├─ 1. Network connectivity? Test: curl https://careers.techcorp.com
├─ 2. Playwright installed? Run: npx playwright install
├─ 3. Logs: Look for crawling exceptions
├─ 4. Ports: Is any firewall blocking Chromium?

Fix:
├─ Reinstall Playwright: mvn clean install
├─ Check memory: is Chromium running out of RAM?
├─ Try manual navigation: Use Playwright inspector


Problem 4: Resume not being used for matching

Check:
├─ 1. Resume file path set? Check application.yml: resume.file-path
├─ 2. File exists at that path? ls -la /path/to/resume.pdf
├─ 3. File format supported? (PDF, DOCX, TXT)
├─ 4. Logs: Look for "Resume file not found"

Fix:
├─ Set resume.file-path=/full/path/to/resume.pdf
├─ Try simple TXT file first for debugging
├─ Check file permissions: is app able to read it?


Problem 5: Email send fails

Check:
├─ 1. Gmail app password set? Check spring.mail.password
├─ 2. Credentials correct? Test manually with: telnet smtp.gmail.com 587
├─ 3. Recipient email valid? Check job.email.send-to
├─ 4. SMTP config correct? Check spring.mail.host/port/properties
├─ 5. Logs: Look for "javax.mail.AuthenticationFailedException"

Fix:
├─ Regenerate app password from Google Account
├─ Whitelist sender IP (if on corporate network)
├─ Test with: mvn spring-boot:run + manual trigger


Problem 6: Jobs not ranked correctly (all same score)

Check:
├─ 1. Resume profile loaded? Check logs for "Loading resume profile"
├─ 2. Profile is null? (Resume not provided)
├─ 3. Skills extracted? Check UserProfile object in logs
├─ 4. Job skills parsed? Check JobDTO.skills in logs

Fix:
├─ Provide resume file
├─ Verify resume parsing: Check logs for extracted skills
├─ Run with OpenAI enabled for better parsing


Problem 7: Scheduler not running at configured time

Check:
├─ 1. Is Spring scheduled enabled? Check: @EnableScheduling on main class
├─ 2. Check cron expression: "0 0 * * * *" is midnight UTC
├─ 3. Is app still running? Check: ps aux | grep java
├─ 4. Server timezone: Is it UTC or local?

Fix:
├─ Restart application
├─ Set timezone environment variable: export TZ=UTC
├─ Test cron manually: Debug scheduler logs
└─ Verify time is synced: ntpq -p


Problem 8: Memory leak / Out of memory

Check:
├─ 1. Playwright browser not closing? (Resource leak)
├─ 2. Large job descriptions loaded? (HTML stored in memory)
├─ 3. JVM heap size: java -Xmx512M ...

Fix:
├─ Increase JVM memory: export JAVA_OPTS="-Xmx1G"
├─ Check Playwright cleanup in JobCrawlerService
├─ Profile memory usage: jconsole localhost:9010
└─ Reduce max-results to process fewer jobs
```

### Operational Best Practices

```
BACKUP & RECOVERY

Critical Files:
├─ tokens/StoredCredential (OAuth tokens)
├─ application.yml (configuration)
├─ Resume file (if using local file)
└─ Logs (for debugging)

Backup Strategy:
├─ Automate: Daily backup of config + tokens
├─ Store: Encrypted, off-site
├─ Test: Verify restore process quarterly

Recovery:
├─ If StoredCredential lost: Re-run → OAuth required → User accepts
├─ If config lost: Deploy from version control
├─ If resume lost: User provides new one


SECURITY CHECKLIST

Secrets Management:
├─ Never commit application.yml to git (use .gitignore)
├─ Use environment variables for sensitive values
├─ Rotate Gmail app password regularly
├─ If using OpenAI key: Rotate periodically

Access Control:
├─ Run app as non-root user
├─ Restrict file permissions: 600 for config files
├─ Use VPN if accessing from remote

Logging:
├─ Don't log credentials
├─ Don't log full email content
├─ Don't log resume content
├─ Sanitize PII before logging


DEPLOYMENT BEST PRACTICES

Environment Setup:
├─ Dev: application-dev.yml (local config)
├─ Prod: application-prod.yml (environment variables)
├─ Staging: application-staging.yml (test config)

Configuration Management:
├─ Use Spring profiles: @profile("prod")
├─ Externalize: ConfigMap (Kubernetes) or Systems Manager (AWS)
├─ Validate: Check all required vars are set on startup

Graceful Shutdown:
├─ When app stops: Finish current job before exiting
├─ Close: Playwright browsers, database connections, thread pools
├─ Timeout: Force shutdown after 30s


MAINTENANCE WINDOWS

Planned Maintenance:
├─ Schedule outside of normal job run times
├─ Notify users if disruptive
├─ Have rollback plan ready

Updates:
├─ Test in staging before deploying to prod
├─ Have previous version ready to rollback
├─ Monitor logs for 15 minutes after deployment


ALERTING

What to Alert On:
├─ Scheduler failed to run
├─ Email delivery failed
├─ Website crawling success rate < 50%
├─ Error rate spike
├─ Memory usage > 80%

Alert Channels:
├─ Email (immediate)
├─ Slack (if available)
├─ PagerDuty (if critical)
└─ Logs (always)
```

---

## Technical Debt & Future Roadmap

### Known Technical Debt

```
DEBT ITEM 1: No database layer
─────────────────────────────────
What's borrowed:
├─ No persistent storage of jobs
├─ Can't deduplicate across days
├─ Can't track user actions
├─ No historical data

Cleanup effort: Medium (2-3 days)

Implementation:
├─ Add PostgreSQL schema: jobs, users, actions
├─ Migrate from files to database
├─ Add repository layer

Benefits:
├─ Deduplication across runs
├─ Trend analysis
├─ Multi-user support


DEBT ITEM 2: Website parsing fragility
─────────────────────────────────────
What's borrowed:
├─ Hard-coded CSS selectors
├─ Breaks when website changes layout
├─ Manual selector maintenance

Cleanup effort: High (1 week)

Implementation:
├─ Switch to AI-based parsing (OpenAI + LangChain)
├─ Use visual element detection instead of selectors
├─ Add selector versioning/validation

Benefits:
├─ Robust to website changes
├─ Supports more job sites
├─ Less maintenance


DEBT ITEM 3: No API/UI
──────────────────────
What's borrowed:
├─ Email-only output
├─ Can't browse results
├─ Can't provide feedback

Cleanup effort: High (1-2 weeks)

Implementation:
├─ Add REST API (Spring REST)
├─ Add React frontend
├─ Add user authentication

Benefits:
├─ Better UX
├─ Feedback loop
├─ Extensible


DEBT ITEM 4: Single-threaded processing
────────────────────────────────────────
What's borrowed:
├─ Website crawling is sequential
├─ Slow for 50+ jobs
├─ Resources underutilized

Cleanup effort: Medium (3-4 days)

Implementation:
├─ Add ExecutorService for job crawling
├─ Implement browser pool (max 3 concurrent)
├─ Add rate limiting

Benefits:
├─ 3x faster processing
├─ Better resource utilization


DEBT ITEM 5: No learning/improvement
──────────────────────────────────────
What's borrowed:
├─ Scoring is static
├─ Doesn't learn from behavior
├─ No A/B testing

Cleanup effort: High (2-3 weeks)

Implementation:
├─ Track user clicks (which jobs applied to)
├─ Log feedback (good/bad match)
├─ Train ML model on feedback
├─ Improve scoring over time

Benefits:
├─ Better recommendations
├─ Personalized ranking
├─ Adaptive system


DEBT ITEM 6: Resume required for ranking
─────────────────────────────────────────
What's borrowed:
├─ No ranking if resume not provided
├─ Can't use alternative profile sources

Cleanup effort: Medium (1 week)

Implementation:
├─ Add fallback: Extract profile from emails
├─ Add: LinkedIn OAuth integration
├─ Add: Manual profile configuration

Benefits:
├─ Works without resume
├─ More profile sources
├─ Better UX


DEBT ITEM 7: No rate limiting/backoff
──────────────────────────────────────
What's borrowed:
├─ Can get blocked by job sites
├─ No automatic recovery
├─ Fails silently

Cleanup effort: Low (1-2 days)

Implementation:
├─ Add exponential backoff on errors
├─ Add User-Agent randomization
├─ Add proxy rotation (if needed)
├─ Respect robots.txt

Benefits:
├─ More resilient
├─ Fewer blocked requests
├─ Longer availability
```

### Future Roadmap (Prioritized)

```
PHASE 1: Stability & Reliability (Q3 2026)
──────────────────────────────────────────
1. Add basic database (PostgreSQL)
   └─ Job storage for deduplication
   └─ Estimate: 3 days

2. Add retry/backoff logic
   └─ Crawling failures
   └─ Email delivery failures
   └─ Estimate: 2 days

3. Add health check endpoint
   └─ Kubernetes/ECS integration
   └─ Estimate: 1 day

4. Add monitoring/alerting
   └─ Prometheus metrics
   └─ Slack alerts
   └─ Estimate: 2 days

Total: ~1 week


PHASE 2: Scale & Performance (Q4 2026)
──────────────────────────────────────
1. Parallel job crawling
   └─ Browser pool (max 3)
   └─ Estimate: 3 days

2. Caching strategy
   └─ Cache website scrapes
   └─ Cache AI parsing results
   └─ Estimate: 2 days

3. Multi-user support
   └─ User authentication
   └─ Isolated profiles
   └─ Estimate: 5 days

Total: ~2 weeks


PHASE 3: Intelligence & UX (Q1 2027)
────────────────────────────────────
1. Web UI/Dashboard
   └─ Browse jobs
   └─ View scores
   └─ Track applications
   └─ Estimate: 1 week

2. REST API
   └─ Get jobs, scores, recommendations
   └─ Provide feedback
   └─ Estimate: 3 days

3. Learning/feedback loop
   └─ Track clicks
   └─ Improve scoring
   └─ Estimate: 1 week

Total: ~3 weeks


PHASE 4: Advanced Features (Q2 2027)
───────────────────────────────────────
1. Semantic matching (embeddings)
   └─ Vector similarity for skills
   └─ Estimate: 1 week

2. Multi-source ingestion
   └─ LinkedIn integration
   └─ Twitter job posts
   └─ API integrations
   └─ Estimate: 2 weeks

3. Advanced filtering
   └─ Salary ranges
   └─ Visa requirements
   └─ Deal-breakers
   └─ Estimate: 1 week

Total: ~4 weeks
```

---

## Appendix: Configuration Reference

### Critical Environment Variables

```bash
# OAuth
GMAIL_OAUTH_CLIENT_ID="xxxx.apps.googleusercontent.com"
GMAIL_OAUTH_CLIENT_SECRET="GOCSPX-xxxx"

# SMTP (for email sending)
GMAIL_SMTP_USERNAME="your-email@gmail.com"
GMAIL_SMTP_PASSWORD="xxxx xxxx xxxx xxxx"  # App password with spaces

# Job Filtering
JOB_KEYWORDS="java,spring,backend,docker"
JOB_EMAIL_SEND_TO="recipient@gmail.com"

# Scheduling
SCHEDULING_CRON="0 0 * * * *"  # Midnight UTC

# Resume (optional)
RESUME_FILE_PATH="/home/user/resume.pdf"

# OpenAI (optional)
OPENAI_API_KEY="sk-xxxx"
OPENAI_MODEL="gpt-4"

# Server
SERVER_PORT=8080
JAVA_OPTS="-Xmx512M -Xms256M"
```

### Key Configuration Parameters

```yaml
gmail:
  lookback-hours: 24          # How far back to search emails
  max-emails-per-run: 20      # Prevent timeout on large mailbox

job:
  filter:
    min-experience: 1         # Years (if resume not available)
    max-experience: 5
  max-results: 10             # Top N jobs to send

scheduler:
  timeout: 300                # 5-minute max execution time

browser:
  timeout: 30                 # 30 seconds per job crawl
  headless: true              # Always true (no GUI)
```

---

## End of Knowledge Transfer Document

**Ownership**: New maintainer as of [DATE]  
**Last Reviewed**: May 2026  
**Next Review**: August 2026

For questions or issues, refer to troubleshooting section or escalate to senior engineer.
