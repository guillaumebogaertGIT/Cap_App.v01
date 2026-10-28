# Account and PostgreSQL verification — 27 October 2026

## What the inspection established

- PostgreSQL service `postgresql-x64-18` is running. Port 5432 accepts connections.
- The developer's launcher output confirms database `cap`, role `cap`, PostgreSQL
  18.6, and all five migrations validated; schema `public` is at version 5.
- A second application startup failed because an existing CAP Java process was
  already listening on 8081. Its public health endpoint returned 200. This is a
  port conflict, not a database/password/migration failure.
- The agent has not received or stored the developer's database password. No
  account rows or workouts in the ordinary `cap` database were edited by this audit.
- Unrelated `notes.txt` and `docs/SESSION-2026-09-27.md` changes were preserved.

## Changes

The original sidebar, Home, calendar, exercise picker, compact + button and workout
editor remain shared between preview and accounts mode. Real login now also uses
the original branded story/card layout, with real authentication handlers instead
of demo sign-in. Preview still explicitly uses fake/browser-local data on Live Server;
real mode uses `/app/index.html` on the account backend.

Added local launchers, safe Dutch security/validation/connection errors, error
reference IDs and backend log events without passwords, tokens or SQL values.
Returning through browser back/forward cache reloads the authenticated page.
Remaining nutrition/measurement browser tools are marked as local; their athlete
keys no longer pretend the signed-in account is `preview-guillaume`.

## Launch the persistent development app

From the project root:

```powershell
.\scripts\start-accounts.cmd
```

This launches a child PowerShell process with a per-launch execution-policy bypass;
it does not change Windows' permanent policy. It checks 8081 before asking for a
secret, prompts privately for the PostgreSQL **cap-role** password, checks the real
local database, preserves existing coaches and starts accounts mode. A first coach
is requested only when none exists. No database is created/substituted silently.
Pending migrations are applied by Flyway; existing migrations are validated.

If 8081 is occupied, use Ctrl+C in the existing CAP backend terminal, then rerun.
Do not stop PostgreSQL. Open `http://127.0.0.1:8081/app/index.html` and use your CAP
login, not the database password. Keep that backend terminal running.

If credentials are wrong, confirm the password for role `cap` in pgAdmin; the
PostgreSQL administrator password and CAP login password are separate credentials.
The launcher deliberately refuses to continue after a connection failure.

## Automated evidence

The complete Maven run passed **35 tests, zero failures/errors/skips** with
`CAP_REQUIRE_POSTGRES_TESTS=true` and real PostgreSQL on isolated port 55433,
database `cap_test`. Database-backed account/planning/workout tests use fresh
`cap_test_*` schemas. Pure domain/preview tests remain their existing tests;
none were represented as production database checks.

Coverage includes:

- Public login/assets; guests denied private profile, workout, training, records,
  booking and exercise APIs.
- Real coach/athlete login, CSRF, forbidden athlete invitations/workout/catalog
  changes, and ignoring a requested COACH role at invitation acceptance.
- Direct HTTP requests cannot read another athlete's workouts, history or records,
  reschedule their assignment, start their workout, or write coach-only notes.
- Used, expired and replaced invitations fail; no accounts are created by them.
- Preview controllers are absent in accounts mode; `preview-guillaume` is rejected
  as a real recipient. Shared URL names resolve to authenticated account controllers.
- Logout blocks subsequent private requests; profile/photo access is self-only.
- PostgreSQL migrations V1–V5, capacity races, private recipients, frozen workout
  snapshots, stale draft versions, exercise changes, history and PR rules.
- `PostgresRestartTests` closes and rebuilds the complete backend context against
  one PostgreSQL schema: accounts, workouts and assignments remain; old sessions
  fail and login works again. No truncation or schema reset is used.

Browser verification on PostgreSQL used fake accounts in schema
`cap_review_20261027_193027`: invitation, athlete registration/login, original
workout editor save, assignment and athlete workout display. Login layouts were
checked at 390, 768 and 1440px without horizontal overflow. The separate test JVM
was also stopped/restarted against that same schema with coach bootstrap disabled:
coach/athlete accounts, the workout and its assignment remained; the old session
returned 401, re-login succeeded, and logout again blocked access.
Never use the ordinary `cap` database for automated fixture writes.

To repeat on a dedicated local test database, set `CAP_TEST_POSTGRES_URL`,
`CAP_TEST_POSTGRES_USER` and, when needed, `CAP_TEST_POSTGRES_PASSWORD` privately,
then run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-postgres.ps1
```

The wrapper requires a local database named `cap_test` (or `cap_test_*`) and refuses
an unset URL or the ordinary `cap` database. It does not install/start a substitute
database. The audit's isolated cluster is stopped after verification; test schemas
are retained, not dropped. Maven reports are in `backend/target/surefire-reports`.

## pgAdmin and safe inspection

Your app data: **Servers → local PostgreSQL 18 → Databases → cap → Schemas → public → Tables**.
Register a localhost server on port 5432 if pgAdmin has no server entry. Connect
using your own locally entered PostgreSQL credentials; never share them in chat.

| Table | Purpose |
|---|---|
| `cap_user` | Accounts, roles and password hashes (do not export the hashes) |
| `athlete_invitation` | Invitation expiry/use, hashed tokens |
| `account_profile` | Profile values and small JPEG avatar |
| `club_session` | Planned group/private/open-gym lessons |
| `session_recipient` | Athletes allowed to see a private lesson |
| `session_booking` | Reservations, cancellations and attendance |
| `workout_state` | One JSON snapshot containing workouts and assignments |
| `athlete_training` | Completed sessions per athlete, stored as JSON snapshots |
| `training_draft` | One versioned active workout per athlete |
| `exercise_state` | Shared exercise additions/overrides |
| `flyway_schema_history` | Which database migrations ran successfully |

Use [accounts-review.sql](accounts-review.sql) in Query Tool. It contains only
SELECT statements and excludes password/token hashes and photo contents.
An SQL migration is a numbered database change. Flyway records it so the app can
restart without recreating tables. A backend login session is temporary; the
account/workout records are persistent rows in PostgreSQL.

## Short manual checklist

1. Stop the old backend, launch once, log in as coach; confirm original navigation.
2. Invite a fake athlete and accept in a private window. Reopen the used link:
   acceptance must fail. Expiry is covered automatically; don't alter real rows.
3. Create and assign a workout. Return to the coach window: the new athlete should
   appear without losing an unsaved workout draft.
4. In athlete view, see only that athlete's assignments, log sets, wait a second,
   refresh, complete. Check history and PRs. Test a lesson booking/cancellation.
5. Log out, then refresh a private page: login is required. Restart the backend:
   login again and verify the saved workout still exists.
6. Optionally create a second fake athlete to verify private lessons and personal
   assignments do not appear in their account.

## Limits

Your local database connection/migration is confirmed by your supplied startup
log; the newly built app still needs its final restart on 8081 after this audit.
Automated and browser data writes used isolated PostgreSQL, not your real account.
Health measurement history, shared food diaries, payments, messaging, recovery/email
delivery and hosting remain incomplete. The restored login/layout does not claim
these features are connected. Extra browser testing used Chromium; physical iPhone
and Safari behavior is unverified. No production deployment was performed.

Subsequent nutrition update: migration V6 now stores shared coach targets.
The expanded suite has 36 passing PostgreSQL tests, including nutrition ownership
and restart persistence; the coach/athlete nutrition browser flow also passed.
