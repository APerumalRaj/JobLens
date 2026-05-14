# JobLens - Knowledge Transfer & Handover Document

> **Purpose:** This document is a maintenance handover for JobLens. It is written for a junior engineer who will own the project and for a senior engineer reviewing the architecture.

---

## Table of Contents

1. [Purpose and Scope](#purpose-and-scope)
2. [Current System and What Changed](#current-system-and-what-changed)
3. [End-to-End Pipeline](#end-to-end-pipeline)
4. [Component Responsibilities](#component-responsibilities)
5. [Resume Matching and Filtering](#resume-matching-and-filtering)
6. [Operational Assumptions and Caveats](#operational-assumptions-and-caveats)
7. [Failure Modes and Recovery](#failure-modes-and-recovery)
8. [Performance, Scaling, and Technical Debt](#performance-scaling-and-technical-debt)
9. [Deployment and Environment Notes](#deployment-and-environment-notes)
10. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
11. [Future Work and Known Gaps](#future-work-and-known-gaps)
12. [Appendix: Key Files](#appendix-key-files)

---

## Purpose and Scope

This document is intended as an operational handover for JobLens.
It is not a marketing overview.
Instead, it explains the system behavior, architecture, failure modes, assumptions, and maintenance concerns.

It targets:
- a junior engineer taking ownership tomorrow,
- a senior engineer reviewing the current design,
- any contributor who needs to understand where the system is fragile.

---

## Current System and What Changed

### Previous architecture vs current architecture

| Concern | Previous architecture | Current architecture | Reason for change | Benefit | Tradeoff |
|---|---|---|---|---|---|
| job data source | email body | job page from email apply link | email body is incomplete | richer data and more accurate fields | slower, more complex |
| parsing method | JSoup email-only | Playwright render + JSoup parse | many job pages are dynamic | captures rendered content | browser automation fragility |
| resume usage | none | resume profile and ranking | need personalization | better relevance | matching is still rule-based |
| deduplication | email heuristics | apply-link based before crawl | avoid duplicate crawls | saves resources | only exact link duplicates |
| AI enrichment | none | optional OpenAI parse | messy page extraction | better structured job fields | external dependency |
| persistence | none | still none | keep scope small | simple deployment | no historical tracking |

### What was removed or deprecated

- treating email text as the authoritative job posting
- relying on email body alone for all job metadata
- purely keyword-based job filtering without website validation

### What was introduced

- `JobCrawlerService` to visit job URLs in a headless browser
- `SemanticJobParserService` for optional AI-based page parsing
- `ResumeParserService` to extract candidate profile data
- `JobMatchingService` to score jobs against the resume profile
- explicit separation between email ingestion and website scraping

---

## End-to-End Pipeline

### Actual execution flow

1. `JobLensApplication.processJobOpportunities()` starts on schedule.
2. `GmailService.fetchRecentJobEmails()` retrieves job-related Gmail messages.
3. `JobExtractionService.extractJobsFromMessages()` parses emails and extracts apply links.
4. `JobFilterService.removeDuplicates()` removes duplicate job links.
5. `JobCrawlerService.fetchJobPage()` visits each unique link with Playwright.
6. `SemanticJobParserService.parseJob()` parses the rendered page into a structured job.
7. `ResumeParserService.loadUserProfile()` optionally loads the candidate profile.
8. `JobMatchingService.rankJobs()` scores and ranks jobs.
9. `EmailService.sendJobsEmail()` sends the summary email.

### The key architectural distinction

The current system treats the email only as a link carrier, not the final job source.
The actual job metadata comes from the website reached by the email’s apply link.

### Data flow diagram

```
[Scheduler]
   ↓
[GmailService] → [Email messages]
   ↓
[JobExtractionService] → [JobDTOs with links]
   ↓
[JobFilterService] → [Unique jobs]
   ↓
[JobCrawlerService] → [JobPage crawled data]
   ↓
[SemanticJobParserService] → [Enriched JobDTOs]
   ↓
[ResumeParserService] → [UserProfile]
   ↓
[JobMatchingService] → [Ranked JobDTOs]
   ↓
[EmailService] → [Summary email]
```

---

## Component Responsibilities

### `JobLensApplication`

**Responsibility:** schedule and orchestrate the batch pipeline.

**Notes:**
- `@EnableScheduling` enables cron-based execution.
- The scheduled method is `processJobOpportunities()`.
- It logs the start, each step, and a summary.
- It catches exception globally and logs errors.

**Danger:** there is no distributed lock. Multiple instances may run concurrently if deployed that way.

### `GmailService`

**Responsibility:** authenticate with Gmail and fetch relevant messages.

**Details:**
- uses OAuth2 and local token storage
- builds a Gmail query based on recent time and subject terms
- excludes self-generated/app emails
- limits the result set with `max-emails-per-run`

**Important config:**
- `gmail.oauth.client-id`
- `gmail.oauth.client-secret`
- `gmail.oauth.redirect-uri`
- `gmail.oauth.token-file-path`
- `gmail.lookback-hours`
- `gmail.max-emails-per-run`

### `JobExtractionService`

**Responsibility:** parse email content and extract the job link plus basic metadata.

**What it extracts:**
- title from email subject or first heading
- company from sender or body content
- apply link from HTML anchors
- description from paragraph-like text
- experience ranges using regex

**Core assumption:** the email is a pointer to the job page, not the final content source.

**Failure mode:** if no link is found, the job data is incomplete and may not dedupe correctly.

### `JobFilterService`

**Responsibility:** deduplicate the job list by link before crawling.

**Why:** crawling each link is expensive, so duplicates must be removed early.

**Current behavior:** it keeps the first job for each unique link and drops later duplicates.

### `JobCrawlerService`

**Responsibility:** load job page URLs with Playwright and scrape rendered HTML.

**How it works:**
- launches Chromium headless
- opens a new page
- navigates to the link with `LoadState.NETWORKIDLE`
- waits an additional 2 seconds
- reads the rendered HTML via `page.content()`
- parses the HTML using JSoup
- extracts structured fields

**Fields extracted:**
- title
- company
- location
- description
- skills
- seniority
- employment type
- experience text
- apply link
- raw HTML
- source host

**Implementation caution:**
- a new browser is created for each URL
- this is simple but expensive
- if the page is blocked or times out, the method returns null

### `SemanticJobParserService`

**Responsibility:** convert scraped page content into a structured `JobDTO`.

**Behavior:**
- if OpenAI is configured, it uses AI for parsing
- if AI fails, it falls back to heuristics
- it preserves key fields from the original job record

**Important note:** the pipeline remains functional if semantic parsing is unavailable.

### `ResumeParserService`

**Responsibility:** build a candidate profile from a resume file.

**Workflow:**
- load `resume.file-path`
- extract text using Apache Tika
- if OpenAI is available, request JSON profile output
- otherwise, fallback to heuristic parsing

**Parsed fields:**
- skills
- experienceYears
- preferredRoles
- domains
- locations
- seniority
- employmentType

**Fallback heuristics:**
- skill tokens from a preset list
- experience via regex `\d+ years` or `\d+ yrs`
- role keywords like backend, devops, architect
- domain keywords like finance, healthcare, saas
- location keywords like remote, San Francisco
- seniority keywords like senior/junior

**Limitations:**
- no OCR for scanned resumes
- no embeddings or semantic similarity
- exact keyword matching only

### `JobMatchingService`

**Responsibility:** score and rank jobs against the candidate profile.

**Scoring components:**
- skills: up to 40 points
- experience: up to 20 points
- role match: 15 points
- location match: 10 points
- domain relevance: 10 points
- seniority match: 10 points

**How it works:**
- normalize values to lowercase
- compute skill overlap
- compare experience to job min/max
- match preferred roles against title text
- match location terms against job location
- match domains against description
- exact seniority match

**Caveat:** if the profile is null, ranking degrades and jobs may remain unsorted.

### `EmailService`

**Responsibility:** construct and send the summary email.

**What it includes:**
- job title and company
- location and experience
- match score
- match reasons and missing skills
- apply link

**Failure mode:**
- paper is logged and the batch ends
- there is no built-in retries for SMTP failures

---

## Resume Matching and Filtering

### Current implementation

The current resume matching is rule-based and scoring-based.
It does not use embeddings or a vector store.

### Behavior today

- resume text is extracted via Apache Tika
- optional OpenAI parsing is used when configured
- heuristics parse skills, experience, role, domain, location, and seniority
- `JobMatchingService` scores each job against this profile

### What is not implemented

- embeddings / vector search
- semantic similarity beyond normalized substring matching
- historical profile updates
- multi-user profiles

### Future extensibility

The current architecture supports later extension to:
- resume embeddings,
- vector similarity search,
- persistent profile storage,
- multi-user and multi-tenant support.

---

## Operational Assumptions and Caveats

### Scheduler behavior

- uses Spring `@Scheduled`
- cron schedule is defined in `scheduling.email-fetcher.cron`
- no distributed lock exists
- only safe for a single active instance

### Why the schedule is conservative

- job crawling is expensive
- frequent runs raise rate-limit and anti-bot risk
- the system is intended for periodic batch execution, not real-time updates

### Environment expectations

- local Gmail OAuth token storage is available
- resume file is accessible locally
- SMTP credentials are configured correctly
- target job sites are public

### Duplicate prevention

- based on exact job URL
- works for identical postings
- does not catch different URLs for the same role

---

## Failure Modes and Recovery

### Primary failure categories

- Gmail authentication failure
- email extraction failure
- website crawling failure
- semantic parsing failure
- resume parsing failure
- SMTP email failure

### Recovery strategy

The pipeline is built to continue in the face of partial failures.
- failed crawls fall back to email-derived job metadata
- failed resume parsing turns off profile matching
- failed AI parsing falls back to heuristics
- no-jobs condition triggers a no-jobs email

### Retry behavior

- there is no explicit retry logic within a single run
- retries happen on the next scheduled execution

### Rate limiting and anti-bot risk

- currently not handled explicitly
- repeated crawls of the same site may trigger blocks
- there is no backoff or proxy system

### Session and cookie handling

- each crawl starts a fresh browser
- no persistent session is maintained
- gated job pages requiring login are unsupported

---

## Performance, Scaling, and Technical Debt

### Performance profile

- Gmail fetch is fast and bounded by `lookback-hours`
- email extraction is lightweight
- deduplication is inexpensive
- job crawling is the dominant cost
- AI parsing is secondary
- ranking and email sending are quick

### Scaling concerns

- single-instance design
- local token and resume dependencies
- no shared state for deduplication or history
- no scheduler locking for concurrent instances

### Technical debt

- no persistence layer
- brittle page scraping selectors
- no retries/backoff
- no metrics or health endpoint
- limited resume matching sophistication

### Safe refactors

Safe to refactor:
- email link extraction
- resume heuristics
- scoring logic
- email formatting

Risky without tests:
- browser lifecycle management
- scheduler orchestration
- semantic fallback logic

---

## Deployment and Environment Notes

### Runtime requirements

- Java 21
- Maven
- Playwright Java dependencies

### Build and run

```bash
mvn clean package
java -jar target/job-email-filter-1.0.0.jar
```

### Gmail OAuth notes

- the first run may open a browser for consent
- tokens are stored locally under `tokens/`
- deleting tokens forces re-authentication

### Important configuration values

- `spring.mail.username`
- `spring.mail.password`
- `gmail.oauth.client-id`
- `gmail.oauth.client-secret`
- `resume.file-path`
- `job.max-results`
- `scheduling.email-fetcher.cron`
- `openai.api-key` (optional)

---

## Monitoring and Troubleshooting

### Useful log markers

- `Starting scheduled job processing`
- `Fetching emails from Gmail...`
- `Extracting job details from X emails...`
- `Removing duplicate jobs...`
- `Crawling X job pages for full content...`
- `Loading resume profile for semantic matching...`
- `Ranking jobs by semantic relevance...`
- `Sending summary email with X jobs...`

### Common issues

- no jobs found: adjust Gmail query filters and lookback window
- no apply link: inspect raw email HTML
- crawl failures: test URL accessibility and Playwright
- resume parsing failure: verify resume file path and format
- email send failure: verify SMTP credentials

---

## Future Work and Known Gaps

### Short-term improvements

- add persistence for deduplication and history
- add metrics and health endpoints
- add retries/backoff for external failures
- add scheduler locking

### Medium-term improvements

- implement embeddings for semantic matching
- support multiple users and profiles
- add a UI or REST API
- add anti-bot and rate-limit handling

### Long-term improvements

- add feedback loops from user behavior
- support gated job pages with login
- add application tracking

---

## Appendix: Key Files

- `JobLensApplication.java`
- `GmailService.java`
- `JobExtractionService.java`
- `JobFilterService.java`
- `JobCrawlerService.java`
- `SemanticJobParserService.java`
- `ResumeParserService.java`
- `JobMatchingService.java`
- `EmailService.java`
- `ExperienceExtractionUtil.java`
