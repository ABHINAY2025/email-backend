# ApplyFlow — API Contract (source of truth)

Backend and frontend MUST both follow this document exactly. All JSON is camelCase.
All timestamps are ISO-8601 UTC strings (Java `Instant`, e.g. `"2026-10-05T10:21:00Z"`).
Plain dates (no time) are `"YYYY-MM-DD"`. IDs are numbers (Java `Long`).
Nullable fields are always present in the JSON with value `null` (never omitted).

Base path: `/api`. Backend runs on `:8080`. Frontend dev server (Vite, `:5173`) proxies `/api` → `http://localhost:8080`.

---------------------------------------------------------------------
## 0. Conventions

### Error body (every non-2xx response from `/api/**`)
```ts
interface ApiError {
  status: number;          // HTTP status
  error: string;           // short code, e.g. "VALIDATION_FAILED", "NOT_FOUND", "IMAP_AUTH_FAILED"
  message: string;         // user-friendly, never a stack trace
  timestamp: string;
  fieldErrors: Record<string, string> | null;
}
```
Error codes used: `VALIDATION_FAILED` (400), `BAD_REQUEST` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403),
`NOT_FOUND` (404), `CONFLICT` (409), `IMAP_AUTH_FAILED` (400), `IMAP_CONNECTION_FAILED` (400/502),
`IMAP_TIMEOUT` (504), `MAILBOX_UNAVAILABLE` (502), `RATE_LIMITED` (429), `SYNC_IN_PROGRESS` (409),
`DATABASE_ERROR` (503), `INTERNAL_ERROR` (500).

### Page wrapper
```ts
interface Page<T> {
  content: T[];
  page: number;          // 0-based
  size: number;
  totalElements: number;
  totalPages: number;
}
```

### Authentication / CSRF
- Session cookie auth (Spring Security, `JSESSIONID`, HttpOnly, SameSite=Lax).
- CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` → cookie `XSRF-TOKEN`. Frontend sends header
  `X-XSRF-TOKEN: <cookie value>` on every POST/PUT/PATCH/DELETE. Use the Spring Security 6 SPA pattern
  (plain `CsrfTokenRequestAttributeHandler` + a filter that touches the token so the cookie is always issued).
- Unauthenticated access to `/api/**` (except the auth endpoints below) → `401` JSON `ApiError` (no redirect, no HTML).
- `/api/auth/login` is CSRF-exempt.

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/auth/csrf` | – | `204` (sets XSRF-TOKEN cookie), public |
| POST | `/api/auth/login` | `{ username, password }` | `200 CurrentUser` / `401` |
| POST | `/api/auth/logout` | – | `204` |
| GET | `/api/auth/me` | – | `200 CurrentUser` / `401` |

```ts
interface CurrentUser { username: string; displayName: string; }
```
Single user. Credentials from env `APP_USERNAME` / `APP_PASSWORD` (bcrypt hash stored in `users` on startup).

---------------------------------------------------------------------
## 1. Enums

```ts
type ApplicationStatus =
  | "APPLIED" | "UNDER_REVIEW" | "ASSESSMENT" | "RECRUITER_CONTACT" | "INTERVIEW"
  | "OFFER" | "REJECTED" | "WITHDRAWN" | "CLOSED";
// Pipeline order: APPLIED < UNDER_REVIEW < ASSESSMENT < RECRUITER_CONTACT < INTERVIEW < OFFER
// Terminal: REJECTED, WITHDRAWN, CLOSED
// "Active" = not terminal.  "Waiting" = APPLIED or UNDER_REVIEW.

type EmailClassification =
  | "APPLICATION_CONFIRMATION" | "APPLICATION_UPDATE" | "RECRUITER_CONTACT" | "ASSESSMENT"
  | "INTERVIEW_INVITATION" | "INTERVIEW_UPDATE" | "OFFER" | "REJECTION" | "WITHDRAWAL"
  | "FOLLOW_UP" | "OTHER_JOB_RELATED" | "NOT_JOB_RELATED";

type EventType =
  | "APPLICATION_SUBMITTED" | "APPLICATION_CONFIRMATION" | "APPLICATION_UPDATE"
  | "RECRUITER_CONTACT" | "ASSESSMENT" | "INTERVIEW_INVITATION" | "INTERVIEW_UPDATE"
  | "OFFER" | "REJECTION" | "WITHDRAWAL" | "FOLLOW_UP" | "STATUS_CHANGE" | "NOTE_ADDED" | "EMAIL_RECEIVED";

type Actor = "SYSTEM" | "USER";

type EmailProvider = "GMAIL" | "OUTLOOK" | "YAHOO" | "ICLOUD" | "IMAP" | "DEMO";

type SyncStatus = "CONNECTED" | "SYNCING" | "ERROR" | "DISCONNECTED";

type SyncJobStatus = "RUNNING" | "COMPLETED" | "FAILED";

type CalendarEventType =
  | "APPLICATION" | "ASSESSMENT" | "INTERVIEW" | "RECRUITER_CALL" | "FOLLOW_UP" | "OFFER" | "DEADLINE" | "REJECTION";

type NotificationType =
  | "NEW_INTERVIEW" | "RECRUITER_RESPONSE" | "NEW_OFFER" | "NEW_REJECTION" | "ASSESSMENT_DEADLINE"
  | "STATUS_CHANGE" | "SYNC_FAILURE" | "POSSIBLE_DUPLICATE";

type AttentionKind =
  | "INTERVIEW" | "ASSESSMENT" | "RECRUITER_RESPONSE" | "OFFER" | "FOLLOW_UP" | "POSSIBLE_MATCH" | "NEEDS_REVIEW";

type InboxTab =
  | "all" | "attention" | "applications" | "recruiters" | "interviews"
  | "assessments" | "offers" | "rejected" | "review";
// Tab → classification mapping:
//  applications → APPLICATION_CONFIRMATION, APPLICATION_UPDATE, FOLLOW_UP, OTHER_JOB_RELATED, WITHDRAWAL
//  recruiters   → RECRUITER_CONTACT
//  interviews   → INTERVIEW_INVITATION, INTERVIEW_UPDATE
//  assessments  → ASSESSMENT
//  offers       → OFFER
//  rejected     → REJECTION
//  attention    → actionRequired = true
//  review       → needsReview = true (low confidence classification or possible application match)
//  all          → every job-related email (isJobRelated = true)

type ApplicationBucket = "all" | "active" | "interviews" | "offers" | "rejected" | "waiting";
// active = non-terminal; interviews = INTERVIEW; offers = OFFER; rejected = REJECTED; waiting = APPLIED|UNDER_REVIEW
```

---------------------------------------------------------------------
## 2. Applications

```ts
interface ApplicationSummary {
  id: number;
  displayId: string;               // "AF-" + id, e.g. "AF-123"
  companyId: number;
  companyName: string;
  companyDomain: string | null;
  jobTitle: string;
  location: string | null;
  source: string | null;           // "LinkedIn", "Company site", "Greenhouse", "Lever", "Workday", "Manual", ...
  status: ApplicationStatus;
  appliedAt: string | null;
  lastActivityAt: string | null;
  emailCount: number;
  emailAccountId: number | null;
  emailAccountEmail: string | null;
  provider: EmailProvider | null;
  needsReview: boolean;
  archived: boolean;
}

interface Note { id: number; applicationId: number; content: string; createdAt: string; updatedAt: string; }

interface StatusHistoryEntry {
  id: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  actor: Actor;
  reason: string | null;
  emailId: number | null;
  confidence: number | null;       // 0..1
  changedAt: string;
}

interface ApplicationDetail extends ApplicationSummary {
  jobUrl: string | null;
  employmentType: string | null;   // "Full-time", "Contract", "Internship", ...
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  recruiterName: string | null;
  recruiterEmail: string | null;
  applicationRef: string | null;   // reference id extracted from email (e.g. "REQ-48213")
  currentStage: string | null;     // free text, e.g. "Waiting for interview"
  confidence: number | null;       // 0..1, confidence of last automatic update
  createdAt: string;
  updatedAt: string;
  notes: Note[];
  statusHistory: StatusHistoryEntry[];   // newest first
}

interface TimelineEvent {
  id: number;
  applicationId: number;
  eventType: EventType;
  title: string;
  description: string | null;      // short summary
  eventDate: string;
  previousStatus: ApplicationStatus | null;
  newStatus: ApplicationStatus | null;
  confidence: number | null;
  actor: Actor;
  emailId: number | null;
  emailSubject: string | null;
  scheduledAt: string | null;      // detected interview/assessment date or deadline, if any
}
```

| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/api/applications` | query below | `Page<ApplicationSummary>` |
| GET | `/api/applications/{id}` | – | `ApplicationDetail` |
| POST | `/api/applications` | `CreateApplicationRequest` | `201 ApplicationDetail` |
| PATCH | `/api/applications/{id}` | `UpdateApplicationRequest` | `ApplicationDetail` |
| DELETE | `/api/applications/{id}` | – | `204` (also deletes its events, notes, history; its emails are unlinked AND deleted) |
| PATCH | `/api/applications/{id}/status` | `{ status: ApplicationStatus, reason?: string }` | `ApplicationDetail` (records USER history entry + STATUS_CHANGE event) |
| GET | `/api/applications/{id}/emails` | – | `EmailDetail[]` (oldest first) |
| GET | `/api/applications/{id}/timeline` | – | `TimelineEvent[]` (oldest first) |
| GET | `/api/applications/{id}/events` | – | `TimelineEvent[]` (newest first) |
| GET | `/api/applications/{id}/history` | – | `StatusHistoryEntry[]` (newest first) |
| POST | `/api/applications/{id}/notes` | `{ content: string }` | `201 Note` |
| PATCH | `/api/applications/{id}/notes/{noteId}` | `{ content: string }` | `Note` |
| DELETE | `/api/applications/{id}/notes/{noteId}` | – | `204` |
| POST | `/api/applications/{id}/merge` | `{ sourceApplicationId: number }` | `ApplicationDetail` (moves emails/events/notes from source into {id}, deletes source) |

GET `/api/applications` query params (all optional):
`q` (matches company, job title, location, recruiter, displayId/ id, email subject), `bucket` (ApplicationBucket),
`status` (comma-separated ApplicationStatus list), `companyId`, `from` & `to` (YYYY-MM-DD, applied date range, inclusive),
`location` (contains, case-insensitive), `source` (exact, case-insensitive), `emailAccountId`, `jobTitle` (contains),
`archived` (`false` default; `true` = only archived), `sort` (`appliedAt` | `lastActivityAt` | `company` | `jobTitle` | `status`; default `lastActivityAt`),
`dir` (`asc` | `desc`; default `desc`), `page` (default 0), `size` (default 25, max 200).

```ts
interface CreateApplicationRequest {
  companyName: string;            // required; company is found case-insensitively or created
  jobTitle: string;               // required
  jobUrl?: string | null;
  location?: string | null;
  appliedAt?: string | null;      // YYYY-MM-DD; default today
  source?: string | null;         // default "Manual"
  status?: ApplicationStatus;     // default APPLIED
  employmentType?: string | null;
  notes?: string | null;          // if present creates a Note
}
interface UpdateApplicationRequest {   // all optional; only non-undefined fields applied
  companyName?: string; jobTitle?: string; jobUrl?: string | null; location?: string | null;
  appliedAt?: string | null; source?: string | null; employmentType?: string | null;
  salaryMin?: number | null; salaryMax?: number | null; salaryCurrency?: string | null;
  recruiterName?: string | null; recruiterEmail?: string | null; currentStage?: string | null;
  archived?: boolean;
}
```

GET `/api/applications/facets` → filter dropdown values:
```ts
interface ApplicationFacets {
  companies: { id: number; name: string }[];
  locations: string[];
  sources: string[];
  emailAccounts: { id: number; email: string }[];
}
```

---------------------------------------------------------------------
## 3. Emails / Inbox (only job-related emails are ever returned)

```ts
interface InboxItem {
  id: number;
  emailAccountId: number | null;
  emailAccountEmail: string | null;
  companyName: string | null;        // from linked application or detected company
  senderName: string | null;
  senderEmail: string;
  subject: string;
  snippet: string | null;
  summary: string | null;
  applicationId: number | null;
  applicationJobTitle: string | null;
  classification: EmailClassification;
  detectedStatus: ApplicationStatus | null;
  confidence: number;                 // 0..1
  actionRequired: boolean;
  actionText: string | null;          // e.g. "Reply to schedule interview", "No action required."
  needsReview: boolean;
  isRead: boolean;
  receivedAt: string;
}

interface StatusImpact { from: ApplicationStatus | null; to: ApplicationStatus; applied: boolean; }
// applied=false when below threshold / needs review

interface MatchSuggestion { applicationId: number; companyName: string; jobTitle: string; score: number; reason: string; }

interface EmailDetail extends InboxItem {
  recipient: string | null;
  threadId: string | null;
  bodyText: string | null;
  bodyHtml: string | null;           // SANITIZED server-side with Jsoup Safelist.relaxed() (no scripts, no remote images allowed? images kept but frontend renders inside sandboxed iframe)
  classificationReason: string | null;
  statusImpact: StatusImpact | null;
  matchSuggestions: MatchSuggestion[];  // non-empty only when needsReview and possible matches exist
}

interface InboxCounts {
  all: number; attention: number; applications: number; recruiters: number; interviews: number;
  assessments: number; offers: number; rejected: number; review: number; unread: number;
}
```

| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/api/inbox` | `tab` (InboxTab, default all), `q`, `accountId`, `unreadOnly`, `page`, `size` (default 50) | `Page<InboxItem>` newest first |
| GET | `/api/inbox/counts` | – | `InboxCounts` |
| GET | `/api/inbox/{id}` | – | `EmailDetail` |
| PATCH | `/api/inbox/{id}/read` | `{ read: boolean }` | `InboxItem` |
| POST | `/api/inbox/{id}/merge` | `{ applicationId: number }` | `EmailDetail` (links email to app, applies its status impact, clears needsReview) |
| POST | `/api/inbox/{id}/create-application` | – | `ApplicationDetail` (new app from this email's detected company/title) |
| POST | `/api/inbox/{id}/ignore` | – | `204` (marks NOT_JOB_RELATED → disappears from inbox; body content is purged) |
| POST | `/api/inbox/{id}/reclassify` | `{ classification: EmailClassification }` | `EmailDetail` (manual override, actor USER) |
| DELETE | `/api/inbox/{id}` | – | `204` |

---------------------------------------------------------------------
## 4. Dashboard

```ts
interface DashboardSummary {
  total: number; active: number; interviews: number; offers: number; rejected: number; waiting: number;
  pipeline: { status: ApplicationStatus; count: number }[];   // all 9 statuses, in enum order
  appliedThisWeek: number;
  responseRate: number;          // 0..1
  lastSyncAt: string | null;
}
interface ActivityItem {
  id: number;                     // application_event id
  applicationId: number;
  companyName: string;
  jobTitle: string;
  eventType: EventType;
  title: string;                  // e.g. "Application moved to Interview", "Recruiter email received"
  description: string | null;
  previousStatus: ApplicationStatus | null;
  newStatus: ApplicationStatus | null;
  actor: Actor;
  occurredAt: string;
}
interface AttentionItem {
  id: string;                     // stable key, e.g. "email-42" / "app-7-followup"
  kind: AttentionKind;
  applicationId: number | null;
  emailId: number | null;
  companyName: string;
  jobTitle: string | null;
  reason: string;                 // "Interview invitation — reply to schedule", "Assessment expires tomorrow", "No response in 14 days — follow-up recommended"
  timestamp: string;
  dueAt: string | null;
  actionLabel: string;            // "Open", "Review match", "Follow up", ...
}
```

| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/api/dashboard/summary` | – | `DashboardSummary` |
| GET | `/api/dashboard/activity` | `limit` (default 20) | `ActivityItem[]` newest first |
| GET | `/api/dashboard/attention` | – | `AttentionItem[]` (unread actionRequired emails, needsReview emails, upcoming scheduled interviews/assessments in next 14 days, active APPLIED/UNDER_REVIEW apps with no activity in 14+ days → FOLLOW_UP) |

---------------------------------------------------------------------
## 5. Calendar

```ts
interface CalendarEvent {
  id: string;                     // "app-12-applied", "event-88"
  applicationId: number;
  companyName: string;
  jobTitle: string;
  type: CalendarEventType;
  title: string;                  // e.g. "Interview — Google"
  start: string;                  // instant
  end: string | null;
  allDay: boolean;
  emailId: number | null;
}
```
GET `/api/calendar/events?from=YYYY-MM-DD&to=YYYY-MM-DD` → `CalendarEvent[]`.
Sources: application appliedAt (APPLICATION, allDay), application_events with scheduledAt (INTERVIEW/ASSESSMENT/RECRUITER_CALL/DEADLINE, timed),
OFFER and REJECTION events (allDay on eventDate), follow-up reminders (FOLLOW_UP, allDay, appliedAt+14d for apps still APPLIED/UNDER_REVIEW).

---------------------------------------------------------------------
## 6. Companies

```ts
interface CompanySummary {
  id: number; name: string; domain: string | null;
  applications: number; active: number; interviews: number; offers: number; rejected: number;
  latestActivityAt: string | null;
}
interface Contact { id: number; name: string | null; email: string; role: string | null; lastContactAt: string | null; emailCount: number; }
interface CompanyDetail extends CompanySummary {
  website: string | null;
  statusDistribution: { status: ApplicationStatus; count: number }[];
  applications: ApplicationSummary[];
  recentEmails: InboxItem[];        // up to 25 newest
  contacts: Contact[];
  history: ActivityItem[];          // up to 50 newest events across the company's applications
  responseStats: { responseRate: number; avgResponseDays: number | null; totalEmails: number };
}
```
GET `/api/companies?q=` → `CompanySummary[]` (sorted by latestActivityAt desc). GET `/api/companies/{id}` → `CompanyDetail`.

---------------------------------------------------------------------
## 7. Analytics

```ts
interface AnalyticsOverview {
  totalApplications: number;
  applicationsThisWeek: number;
  applicationsThisMonth: number;
  responseRate: number;            // apps with any non-confirmation response (status beyond APPLIED or any recruiter/interview/assessment/offer/rejection email) / total
  interviewRate: number;           // apps that reached INTERVIEW or OFFER (ever, via history) / total
  offerRate: number;
  rejectionRate: number;
  avgResponseDays: number | null;
  avgDaysToInterview: number | null;
  avgDaysToRejection: number | null;
  funnel: { applied: number; responses: number; interviews: number; offers: number };
}
interface TimeSeriesPoint { periodStart: string; label: string; count: number; }  // periodStart YYYY-MM-DD
interface BreakdownItem { key: string; count: number; }
interface ApplicationsAnalytics {
  weekly: TimeSeriesPoint[];       // last 12 weeks, zero-filled
  monthly: TimeSeriesPoint[];      // last 12 months, zero-filled
  bySource: BreakdownItem[]; byCompany: BreakdownItem[]; byJobTitle: BreakdownItem[]; byLocation: BreakdownItem[]; // top 10 each
}
interface StatusAnalytics { distribution: { status: ApplicationStatus; count: number }[]; }
interface ResponseRateAnalytics {
  overall: number;
  bySource: { key: string; total: number; responded: number; rate: number }[];
  weekly: { periodStart: string; label: string; rate: number; total: number }[];  // last 12 weeks
}
```
GET `/api/analytics/overview` → `AnalyticsOverview`; `/api/analytics/applications` → `ApplicationsAnalytics`;
`/api/analytics/status` → `StatusAnalytics`; `/api/analytics/response-rate` → `ResponseRateAnalytics`.

---------------------------------------------------------------------
## 8. Email accounts

```ts
interface EmailAccount {
  id: number;
  email: string;
  provider: EmailProvider;
  host: string;
  port: number;
  ssl: boolean;
  username: string;
  folder: string;                  // default "INBOX"
  syncStatus: SyncStatus;
  enabled: boolean;
  lastSyncAt: string | null;
  lastError: string | null;        // user-friendly
  emailsProcessed: number;
  jobEmails: number;
  initialSyncDays: number;         // 30 | 90 | 180 | 365 | 0 (= all available)
  hasPassword: boolean;            // password is NEVER returned
  createdAt: string;
}
interface CreateEmailAccountRequest {
  email: string;
  appPassword: string;
  provider?: EmailProvider;        // inferred from domain if omitted (gmail.com/googlemail.com → GMAIL, outlook/hotmail/live → OUTLOOK, yahoo → YAHOO, icloud/me.com → ICLOUD, else IMAP)
  host?: string; port?: number; ssl?: boolean; username?: string; folder?: string;   // defaults per provider (GMAIL: imap.gmail.com:993 ssl)
  initialSyncDays?: number;        // default from settings (90)
}
interface UpdateEmailAccountRequest { appPassword?: string; host?: string; port?: number; ssl?: boolean; username?: string; folder?: string; initialSyncDays?: number; enabled?: boolean; }
interface ConnectionTestResult { success: boolean; message: string; }
```

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/email-accounts` | – | `EmailAccount[]` |
| POST | `/api/email-accounts` | `CreateEmailAccountRequest` | `201 EmailAccount` — tests the IMAP connection FIRST; on failure returns `400 ApiError` (`IMAP_AUTH_FAILED` etc.) and saves nothing. On success starts initial sync in background. `409 CONFLICT` if email already connected. |
| PATCH | `/api/email-accounts/{id}` | `UpdateEmailAccountRequest` | `EmailAccount` |
| POST | `/api/email-accounts/{id}/test` | – | `ConnectionTestResult` (always 200) |
| POST | `/api/email-accounts/{id}/sync` | – | `202 SyncJob` / `409 SYNC_IN_PROGRESS` |
| POST | `/api/email-accounts/{id}/clear` | – | `204` deletes all emails imported from this account and resets its sync cursor |
| DELETE | `/api/email-accounts/{id}` | query `purge=true|false` (default false) | `204`. purge=true also deletes emails imported from it. Applications are kept. |

---------------------------------------------------------------------
## 9. Sync

```ts
interface SyncJob {
  id: number; emailAccountId: number; emailAccountEmail: string; status: SyncJobStatus;
  startedAt: string; finishedAt: string | null;
  messagesFetched: number; messagesProcessed: number; jobEmailsFound: number;
  applicationsCreated: number; applicationsUpdated: number;
  error: string | null;
}
interface SyncStatusResponse {
  state: "IDLE" | "SYNCING" | "ERROR";      // ERROR if any enabled account is in ERROR and none syncing
  lastSyncAt: string | null;
  lastError: string | null;
  intervalMinutes: number;
  accounts: { accountId: number; email: string; syncStatus: SyncStatus; lastSyncAt: string | null; lastError: string | null }[];
  recentJobs: SyncJob[];                     // last 10
}
```
POST `/api/sync` → `202 { started: number }` (starts sync for all enabled, non-DEMO accounts not already syncing).
GET `/api/sync/status` → `SyncStatusResponse`.

---------------------------------------------------------------------
## 10. Notifications

```ts
interface Notification {
  id: number; type: NotificationType; title: string; message: string;
  applicationId: number | null; emailId: number | null; read: boolean; createdAt: string;
}
```
GET `/api/notifications?unreadOnly=false&limit=50` → `Notification[]` newest first.
GET `/api/notifications/unread-count` → `{ count: number }`.
PATCH `/api/notifications/{id}/read` → `Notification`. POST `/api/notifications/read-all` → `204`.

---------------------------------------------------------------------
## 11. Search (command palette / global search)

GET `/api/search?q=` (min 1 char) →
```ts
interface SearchResults {
  applications: ApplicationSummary[];   // up to 8 (company, title, location, status, recruiter, displayId)
  emails: InboxItem[];                  // up to 6 (subject, sender)
  companies: CompanySummary[];          // up to 5
}
```

---------------------------------------------------------------------
## 12. Settings & privacy

```ts
interface AppSettings {
  displayName: string;
  syncIntervalMinutes: number;          // default 5, min 1, max 1440
  defaultInitialSyncDays: number;       // default 90
  confidenceThreshold: number;          // default 0.75 (0.5..0.99)
  autoUpdateStatus: boolean;            // default true
  followUpDays: number;                 // default 14
}
```
GET `/api/settings` → `AppSettings`. PUT `/api/settings` (full object) → `AppSettings`.

Privacy:
- POST `/api/privacy/clear-imported-mail` → `204` (deletes ALL emails + email-derived events; applications kept; resets account cursors)
- POST `/api/privacy/clear-demo-data` → `204` (deletes seeded demo applications/emails/companies/demo account)
- POST `/api/privacy/delete-all-data` → `204` (everything except user, settings, email accounts)

---------------------------------------------------------------------
## 13. Server-Sent Events

GET `/api/events` (authenticated, `text/event-stream`). Each event: SSE `event:` = type name, `data:` = JSON
```ts
interface ServerEvent { type: ServerEventType; payload: any; timestamp: string; }
type ServerEventType = "APPLICATION_CREATED" | "APPLICATION_UPDATED" | "STATUS_CHANGED" | "EMAIL_RECEIVED"
  | "SYNC_STARTED" | "SYNC_COMPLETED" | "SYNC_FAILED" | "NOTIFICATION_CREATED";
```
Payloads: APPLICATION_* → `ApplicationSummary`; STATUS_CHANGED → `{ applicationId, companyName, jobTitle, from, to, actor }`;
EMAIL_RECEIVED → `InboxItem`; SYNC_* → `SyncJob`; NOTIFICATION_CREATED → `Notification`.
Server sends a comment heartbeat (`:ping`) every 25s. An initial `:connected` comment is sent on subscribe.
Frontend: one `EventSource('/api/events', { withCredentials: true })`, invalidates relevant TanStack Query keys, shows toasts for notifications/sync failures, reconnects automatically.

---------------------------------------------------------------------
## 14. Health
GET `/api/health` (public) → `{ status: "UP", database: "UP" | "DOWN", version: string }`.

---------------------------------------------------------------------
## 15. Backend additions

Additive only; nothing above was renamed or removed.

- **`CompanyDetail.applications` is the `ApplicationSummary[]` list.** The section 6 definition of `CompanyDetail`
  overrides `applications: number` from `CompanySummary` with the list, and one JSON key cannot hold both. So the
  detail payload sends the list under `applications` and the count under the extra field **`applicationCount: number`**.
  `GET /api/companies` (the summaries) still sends `applications` as a number.
- **`AttentionItem.dueAt` for `FOLLOW_UP` items** holds the date the follow-up became due
  (last activity + `followUpDays`). Items are sorted with the soonest due interview, assessment or deadline first,
  then everything else newest first.
- **Login rate limiting:** after 10 failed logins from one IP within 15 minutes, `POST /api/auth/login` returns
  `429 RATE_LIMITED`.
- **Demo account rules:** the seeded `DEMO` account (`demo@applyflow.local`, `enabled=false`) cannot be synced
  (`POST /api/email-accounts/{id}/sync` returns `400 BAD_REQUEST`). `POST /api/email-accounts/{id}/test` returns
  `{ success: false, ... }`. `POST /api/email-accounts` with `provider: "DEMO"` returns `400 VALIDATION_FAILED`.
- **Invalid enum values in request bodies** (for example `{"status":"FOO"}`) return `400 VALIDATION_FAILED` with
  `fieldErrors: { status: "must be one of [...]" }`.
- **Gmail app passwords** may be pasted with spaces (`abcd efgh ijkl mnop`). Spaces are removed before use.
- **SSE details:** `GET /api/events` sends `retry: 5000` together with the initial `:connected` comment. It also sets
  `X-Accel-Buffering: no` and `Cache-Control: no-cache`, so nginx does not buffer the stream.
- **`GET /api/health`** always returns HTTP 200. When the database is unreachable it returns
  `database: "DOWN"` (`status` stays `"UP"`).
- **`POST /api/email-accounts/{id}/clear` and `DELETE /api/email-accounts/{id}`** return `409 SYNC_IN_PROGRESS`
  while that account is syncing.
