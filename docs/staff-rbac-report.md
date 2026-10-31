# Staff RBAC implementation report — 30/10/2026

Subsequent clarification: both Glenn and Dieter are proposed as OWNER (display label Admin), with the same full access. Their real records are still unchanged. An athlete dossier popup now opens from Sporters, links to existing athlete-specific screens, and provides audited coach linking/unlinking. This follow-up passed 19 focused PostgreSQL tests and browser checks at 390/768/1440 in both themes. See the athlete-first section in staff-rbac.md.

## Delivery status

Implemented in source and verified using fake accounts on isolated PostgreSQL. **Not activated on the ordinary CAP database and not deployed to the running Docker app.** No real passwords, staff roles, client records, subscriptions or Stripe configuration were changed. The ordinary database remains V12; V13 and the exact account-ID mapping require approval first.

The mapping proposal is in ignored `.local/staff-role-mapping.json`. Its IDs/emails were read from the existing local database and verified with the packaged read-only provisioning tool. Maxim/Vic were not created. The mapping is a proposal, not a runtime email allowlist.

## Existing functionality reused

The original COACH/ATHLETE accounts, BCrypt, CSRF/session login, activation/password flows, profile, SQL workout snapshots, assignment API, training history/PRs, planning/calendar/bookings, exercises, nutrition calculator/targets, food diary, measurements and Stripe TEST modules remain. The frontend remains plain HTML/CSS/JavaScript with the existing CAP design and themes.

## New behavior

- Staff roles: OWNER, MANAGER, INSTRUCTOR, FINANCE; DEVELOPMENT and UNASSIGNED have no operational permissions. Existing account type remains COACH/ATHLETE.
- Central capabilities and current database grants replace the three email-based permission policies. Backend request authorization and service-level client checks both apply.
- OWNER/MANAGER operational management; manager creation/role/activation operations restricted to INSTRUCTOR/FINANCE targets. No self-escalation or self-deactivation, last-owner protection, no DEVELOPMENT promotion through HTTP.
- Explicit instructor/client grants through Rollen & klanten. Instructors see only linked client assignments/history/records and own teaching sessions. Attendance creates no grant; instructors cannot administer bookings/attendance or read client nutrition/measurements.
- Scoped initial HTML: finance gets no teaching people/sessions/bookings; instructors get only appropriate data. Athlete self-service retains existing ownership checks.
- Separate append-only private notes with author/time/client/optional-assignment and idempotent request ID. No athlete or finance access. Legacy note text preserved read-only and redacted from athlete assignment responses.
- Existing dated/weekly assignment snapshots retained. New assignments leave library templates unchanged, record assigning staff/time, and retain notes/attribution on retries. Instructor prescriptions come from the saved server template. Creation-plus-assignment uses the same protected path.
- Finance roles reuse the existing monthly TEST charge report and membership/credit panel. Financial client picker contains only ID/name, separate from training roster data. No staff checkout/cancellation/refund writes or invented accounting/export screens.
- Durable staff audit for provisioning, roles/activation, client grants, assignments and notes. Role changes expire sessions; sensitive note/assignment saves recheck grants under row locks shared with access changes.
- DEVELOPMENT default login denied; explicit local development profile/flag allows self-service only. Production profiles still deny DEVELOPMENT login. Preview refuses public binding/production startup. No role switching/impersonation tool.

## Files changed

| Area | Files |
| --- | --- |
| New schema | `backend/src/main/resources/db/migration/V13__staff_permissions_and_notes.sql` |
| New authorization/operations | `accounts/StaffPolicy.java`, `StaffRequestAuthorization.java`, `StaffController.java`, `StaffNoteController.java`, `StaffRoleProvisioner.java` |
| Existing authentication/authorization | `accounts/AccountSecurity.java`, `AccountService.java`, `RealAppController.java`, `StaffManagementService.java`, `FinanceAccess.java`, `MembershipReviewAccess.java`, `StaffManagementAccess.java`; `PreviewSecurity.java` |
| Existing protected features | `accounts/AccountWorkoutService.java`, `AccountWorkoutController.java`, `PlanningService.java`, `AccountExerciseService.java`, `AccountExerciseController.java`, `NutritionController.java`, `FoodDiaryController.java`, `MeasurementController.java`; `WorkoutStore.java` |
| Financial picker | `accounts/billing/BillingService.java`, `BillingController.java`; existing webhook/checkout fulfillment logic retained |
| Frontend | `frontend/index.html`, new `js/staff-ui.js`; navigation, real-app, real-init, club, workouts-api, colleagues, coach-health, nutrition, food-diary, finance, coach-membership modules |
| Activation helper | `scripts/staff-roles.ps1`, `staff-roles.cmd` |
| New tests | `StaffRbacTests.java`, `StaffMigrationTests.java`, `DevelopmentAccountTests.java`, `PreviewSafetyTests.java` |
| Regression adaptations | AccountApi, AccountWorkoutService, PlanningService, BillingApi, CatalogBillingApi, CoachDirectory, CoachSetup, FinanceApi, MembershipReviewAccess, PostgresRestart, WorkoutStore tests |
| Build/CI | `backend/pom.xml` isolated verification output; `.github/workflows/backend-tests.yml` requires a PostgreSQL test service |
| Documentation | README, staff-rbac guide/report, coach-accounts, finance, notes and brain files |

Paths beginning `accounts/` are under `backend/src/main/java/be/cap/backend/`. Tests are under `backend/src/test/java/be/cap/backend/`.

## Database and API changes

V13 adds `cap_user.staff_role` default UNASSIGNED and four tables: `instructor_client`, `staff_note`, `staff_audit`, `assignment_attribution`. No destructive migration, automatic staff mapping or historical author/time backfill.

New endpoints:

- `GET /api/staff/members`, `PUT /api/staff/{id}/role`.
- `GET /api/staff/{id}/clients`, `PUT/DELETE /api/staff/{id}/clients/{client}`.
- `GET /api/staff/audit` (latest 100).
- `GET/POST /api/staff-notes` (client-scoped, no edit/delete).
- `GET /api/billing/athletes` (financial ID/name picker).

Existing workout/assignment/training/record/exercise, class/booking/attendance, nutrition, food diary, measurement, staff and financial endpoints now pass through the capability policy. Legacy account-mode assignment-note writes are denied; existing text stays readable to scoped staff. Unknown API actions remain denied; unauthenticated requests use the existing login/401 handling and CSRF protections.

## Verification results

All integration runs used native PostgreSQL 18 on isolated port 55433 with random test schemas, `CAP_REQUIRE_POSTGRES_TESTS=true`, and fake data. No H2 fallback was used for these checks.

| Check | Result |
| --- | --- |
| Full backend regression/package | 146 tests, 0 failures, 0 errors, 0 skipped |
| Final focused role/migration/development/preview/workout verification after final guards | 37 tests, 0 failures, 0 errors, 0 skipped |
| Additional final legacy-note redaction/retry/attribution check | 10 tests, 0 failures, 0 errors, 0 skipped |
| Executable JAR packaging | Passed |
| Frontend JavaScript syntax and Git whitespace checks | Passed |
| Packaged account-ID activation dry run against ordinary local cap | Passed; no migration or role write |

These counts overlap; they are separate runs, not a claim of that many unique tests. Local evidence logs are in ignored `backend/target/rbac-release.log`, `rbac-final-targeted.log`, and `rbac-note-final.log`.

Coverage includes authentication/invitations/password/logout/CSRF, direct API role denials, manager restrictions, instructor scope grant/revoke, own schedule, finance bootstrap privacy/report access/no writes, notes author/retry/privacy, immutable templates/attribution, DEVELOPMENT default denial/local isolation, last-owner/self protection, deactivation, V12→V13 preservation, exact-ID provisioning rollback/idempotence, planning concurrency, nutrition/measurements and Stripe fake-provider webhook/subscription/credit regressions. Restart tests passed. No real Stripe transaction was executed.

Browser verification used a separate CAP server on 55434 with fake OWNER/MANAGER/INSTRUCTOR/FINANCE/ATHLETE fixtures. Confirmed instructor-only client picker, assignment from existing template with no edit/delete buttons, successful private note, author/time attribution, note persistence across test-server restart, manager/owner access screens, scope grant/revoke UI, finance-only navigation/bootstrap and membership viewing. Checked phone/tablet/desktop widths 390/768/1440 and light/dark layouts; no horizontal overflow in checked screens. These are desktop browser emulations, not physical iPhone testing. Screenshots are under ignored `.playwright-mcp/rbac-*`.

GitHub's test workflow now specifies PostgreSQL; remote CI results are separate from the verified local results above.

## Remaining approval and configuration

1. Approve the exact UUID/email/role mapping in `.local/staff-role-mapping.json` and the V13 plan in `staff-rbac.md`.
2. Take and restore-test a backup before migration. No ordinary CAP backup/restore or schema activation was performed by this implementation.
3. Activate reviewed roles during a maintenance window, then separately approve replacing the running app. Until then, the current app keeps its old permission behavior.
4. Confirm actual login details for Maxim/Vic before provisioning; no accounts or passwords invented.
5. Assign real instructor/client grants explicitly; none inferred from class bookings.
6. Perform the manual staff QA checklist in `staff-rbac.md` with approved accounts. No real staff password was changed or used to test the new roles.

No live Stripe, refunds, staff financial writes, financial exports, comprehensive accounting, password recovery, custom permission editor, or production deployment was added. Actual device QA and remote CI remain separate. The financial report retains its documented TEST-only limitations.

## Java/Spring concepts

**Role** describes the staff job; **permission** describes an action; **scope** determines which client/session it applies to. Checking only one of these is insufficient.

Spring Security keeps the existing login, session and CSRF boundary. The request permission map blocks forbidden actions; services independently validate client/teaching scope. Bootstrap filtering prevents leaks before JavaScript runs. Database transactions keep grants/notes/audit and assignments/attribution consistent; row locks serialize access changes against sensitive saves. Author IDs/times come from the authenticated account and server, not browser input.

See `staff-rbac.md` for the full role matrix, backup/recovery considerations, activation commands and OWNER/MANAGER/INSTRUCTOR/FINANCE/ATHLETE manual checklist.
