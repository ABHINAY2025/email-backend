# ApplyFlow

Multi-user job-application tracker powered by your own mailboxes. Anyone can create an account (self-service
registration, can be switched off); every user only ever sees their own mailboxes, emails and applications.
ApplyFlow reads your email over IMAP (Gmail App Passwords), keeps **only** job-application emails,
groups them into applications, tracks status changes automatically, and shows everything in a
Jira/Linear-style workspace: overview dashboard, applications, job inbox, calendar, Kanban, companies and analytics.

```
CONNECT EMAIL → SYNC → DETECT JOB EMAIL → CREATE / MATCH APPLICATION → UPDATE STATUS → TIMELINE → DASHBOARD
```

## Quick start

Requires Java 21, Node 20+ and a MongoDB **replica set** (MongoDB Atlas, including the free tier, works out of
the box; a local `mongod` must run as a single-node replica set because ApplyFlow uses multi-document transactions).

1. Put the connection string in a `.env` file in the project root (git-ignored, loaded automatically by the backend):

   ```properties
   MONGO_URI=mongodb+srv://<user>:<password>@<cluster>/?retryWrites=true&w=majority
   MONGO_DATABASE=applyflow
   ```

   ApplyFlow only ever uses the database named by `MONGO_DATABASE` (it wins over any database in the URI).

2. Run backend and frontend:

   ```bash
   cd backend && ./mvnw spring-boot:run        # :8080, creates collections + indexes on startup
   cd frontend && npm install && npm run dev   # :5173, proxies /api → :8080
   ```

3. Open http://localhost:5173 and either create an account (**Sign up**) or log in as the built-in admin with
   `APP_USERNAME` / `APP_PASSWORD` (defaults `admin` / `applyflow`).

Without `MONGO_URI` the backend defaults to `mongodb://localhost:27017/applyflow` (see [.env.example](.env.example)).
Ids stay numeric (`AF-42`): each collection has its own sequence in the `counters` collection.
On first start the backend seeds realistic demo data (29 applications, 60 emails) for the admin user so the UI is populated.
Remove it any time in **Settings → General → Privacy & data → Remove demo data**, or disable with `SEED_DATA=false`.

Tests: `cd backend && ./mvnw test`. Frontend type-check + build: `cd frontend && npm run build`.

## Connecting Gmail

1. Enable 2-Step Verification on the Google account.
2. Create an App Password at https://myaccount.google.com/apppasswords.
3. In ApplyFlow: **Email Accounts → Connect account**, enter the address and the 16-character app password,
   pick an initial sync window (30 / 90 / 180 / 365 days / all).

The connection is tested before anything is saved. Outlook, Yahoo, iCloud and any custom IMAP server
work too (expand *Advanced* for host / port / SSL / folder). After the initial import only new messages
(by IMAP UID) are processed; the scheduler runs every 5 minutes (configurable in Settings).

## How detection works

All intelligence lives behind `EmailIntelligenceService` (`backend/.../intelligence`). The shipped
implementation, `RuleBasedEmailIntelligenceService`, is deterministic:

- **Classification** – weighted signals from sender domain (Greenhouse, Lever, Workday, Ashby, HackerRank, Codility, …),
  sender address, subject and body phrases, with strong negative signals for job alerts, newsletters, shopping, banking,
  OTPs, GitHub/AWS notifications and promos. Produces category, confidence (0–1) and a human-readable reason.
- **Extraction** – company, job title, requisition/reference ID, job URL, location, salary, employment type,
  interview/assessment dates and deadlines.
- **Matching** – thread (Gmail thread id or References/In-Reply-To) → reference ID → job URL → company + title similarity
  → recruiter → temporal proximity. High scores link automatically; medium scores land in **Inbox → Needs Review**
  as *Possible application match* with **Merge / Create new application / Ignore**.
- **Status engine** – automatic changes only move forward through the pipeline (or to Rejected / Withdrawn), and only when
  confidence ≥ the configured threshold; otherwise the email is flagged *Needs Review*. Manual changes (incl. Kanban drag)
  can set any status. Every change is recorded in the audit history with actor, reason, source email and confidence.

An AI provider can be added later as another `EmailIntelligenceService` implementation; it would only ever see
emails that already passed the job-related pre-filter.

## Privacy & security

- Non-job emails are never stored — only counted.
- IMAP app passwords are encrypted at rest with AES-256-GCM (key from `APP_ENCRYPTION_KEY`), never returned by the API and never logged.
  Keep the key stable; changing it means reconnecting accounts.
- Session-cookie auth (Spring Security) with CSRF protection; login is rate-limited.
- Delete individual emails, applications, account connections (optionally with their mail), clear all imported mail, or delete all data.
- Use HTTPS (e.g. a reverse proxy) if you expose it beyond localhost.

## Accounts (multi-user)

- `POST /api/auth/register` creates an account (display name, email, password ≥ 8 chars) and signs the user in;
  the email (lower-cased) is the username. Passwords are hashed with bcrypt. Registration is limited to 5 per IP per
  hour and can be disabled with `REGISTRATION_ENABLED=false` (`GET /api/auth/config` tells the UI).
- The admin from `APP_USERNAME` / `APP_PASSWORD` is still created/updated on every start. Data from the earlier
  single-user version (documents without an owner) is assigned to this admin automatically at startup.
- Every document carries its owner's `userId`; all endpoints, the mail pipeline, matching, notifications and SSE
  events are scoped to that user (other users' ids answer 404). Settings and the onboarding checklist
  (`/api/onboarding`) are per user. The scheduler syncs every user's mailboxes on that user's interval.
- **Privacy note for operators:** all users' job-related emails (subject, sender, body of job mail) are stored in the
  one shared MongoDB database, and their IMAP app passwords are encrypted with the single server key
  (`APP_ENCRYPTION_KEY`). Whoever runs the server and its database can therefore read every user's job mail — only
  invite people who trust the operator.

## Environment variables

See [.env.example](.env.example). Key ones: `MONGO_URI`, `MONGO_DATABASE`,
`APP_ENCRYPTION_KEY`, `SESSION_SECRET`, `APP_USERNAME`, `APP_PASSWORD`, `APP_DISPLAY_NAME`, `REGISTRATION_ENABLED`
(default `true`), `SEED_DATA`, `SYNC_ENABLED`,
`APP_TIMEZONE` (default `Asia/Kolkata`; used for date parsing in emails and for week/day boundaries).

## Project layout

```
backend/    Spring Boot 3.3 / Java 21 modular monolith (com.applyflow: controller, service, mail.{imap,parser,classifier,matcher},
            application.{service,status,timeline}, analytics, scheduler, intelligence, sse, seed, security,
            persistence (MongoDB: id sequences, schema/index setup, cascades), …)
frontend/   React 18 + TypeScript + Vite, Tailwind, shadcn-style Radix components, TanStack Query, Recharts, dnd-kit, cmdk
docs/       API_CONTRACT.md — REST + SSE contract shared by both sides
```

Real-time updates use Server-Sent Events at `/api/events` (application/status/email/sync/notification events).

## Known limitations

- Detection is rule-based: some emails will have no detected title or location ("Not detected") and go to the review queue rather than being guessed.
- IMAP sync was tested against a local test mail server (GreenMail) and Gmail's login-failure path, but not yet against a real Gmail mailbox.
- Sessions are in memory — restarting the backend logs you out. `SESSION_SECRET` is read but currently unused (no remember-me).
- Kanban cards are ordered by last activity; manual ordering within a column isn't persisted.
- MongoDB transactions fail fast on write conflicts: mail processing retries automatically, but a UI edit that collides
  with a sync writing the same application at that instant may need to be repeated.
