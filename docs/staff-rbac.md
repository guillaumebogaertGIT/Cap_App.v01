# Staff access control

Product clarification: Glenn and Dieter both receive OWNER, displayed as **Admin**, with identical permissions. MANAGER remains a narrower optional role for future use; it is no longer Dieter's proposed role. The revised local UUID mapping is still pending activation approval.

## Athlete-first coaching

Open **Sporters**, click an athlete's name, then use the dossier popup. It links to that athlete's existing workouts, assignment action, measurements/PRs/training history, nutrition targets, private notes and membership information according to permissions.

Admins can choose an instructor under **Aan coach koppelen**. Existing links are listed with **Ontkoppelen**. Multiple instructors are supported; removing one does not remove the others. Admins already have club-wide access. The popup uses the same audited instructor-client grants as Rollen & klanten; it does not create a second assignment system. Instructor popups omit nutrition/measurement/finance and coach-link controls. `GET /api/staff/clients/{id}/instructors` is staff-management-only, no-store, and returns only linked account IDs.

## Implementation and activation are separate

The new source extends the existing COACH/ATHLETE accounts, BCrypt passwords, cookie sessions, CSRF protection, PostgreSQL and original frontend. It does not replace authentication or billing. Migration V13 **has not been approved for the ordinary CAP database**. Do not rebuild/restart the ordinary CAP app with this source until its migration and account-ID mapping are approved.

The existing Docker image can keep running the previous version while this work is reviewed. Automated tests and browser verification use isolated PostgreSQL schemas and fake users.

## Role matrix

| Capability | OWNER | MANAGER | INSTRUCTOR | FINANCE |
| --- | --- | --- | --- | --- |
| Workout library view | Yes | Yes | Yes | No |
| Templates/exercises create/edit/delete | Yes | Yes | No | No |
| Existing-template assignment/history/records | All clients | All clients | Explicitly linked clients | No |
| Teaching classes/schedules | All | All | Own teaching lessons only | No |
| Class, booking and attendance administration | Yes | Yes | No | No |
| Private staff notes | All clients | All clients | Explicitly linked clients | No |
| Nutrition/food diaries/client measurements | Yes | Yes | No | No |
| Financial reports/membership and credit review | Yes | Yes | No | Read only |
| Staff/client-scope management | Yes | Ordinary instructors/finance | No | No |
| Grant OWNER/MANAGER or administer them | Yes, except self | No | No | No |

ATHLETE remains the existing account type and retains own workouts, training, bookings, profile, nutrition, food logging and payments. The displayed CLIENT label does not rename stored ATHLETE records.

`StaffPolicy` maps roles to named capabilities and reads current grants by account UUID. `StaffRequestAuthorization` protects the API surface; services enforce operation and client scope. There is no fallback from COACH, display title, first name or the old email allowlists. FinanceAccess/MembershipReviewAccess/StaffManagementAccess are compatibility facades over this policy.

The client association is explicit: class attendance never creates a grant. OWNER/MANAGER use **Rollen & klanten** to link/unlink clients. Instructors receive only their linked client roster and their own classes; booking identities and unrelated private class recipients are not returned. Revoked scope is checked again on subsequent requests. Sensitive nutrition/measurement access is not granted to instructors even for a linked client.

Role changes end registered sessions. Authorization also reads the current role, rather than trusting stale session role grants. Existing account deactivation checks and session invalidation remain. Managers cannot promote themselves or administer OWNER/MANAGER/DEVELOPMENT targets; self-role changes, self-deactivation and loss of the last active owner are blocked. Ordinary creation requires an explicit permitted role or defaults to INSTRUCTOR, never OWNER from a display title.

## Notes and assignments

`staff_note` is separate from the old assignment `note`. Notes record client, optional assignment, authenticated author, server creation time, content and retry UUID. They are append-only: no staff-note edit/delete routes exist. Duplicate retries return the same note; conflicting reuse is rejected. Neither notes nor their contents enter athlete responses or initial HTML. Author names are displayed with Brussels timestamps.

Legacy notes stay in the original workout JSON, without invented authors/times. They remain readable in authorized staff assignment records, but are redacted in athlete assignment responses. Legacy notes are read-only in real account mode; new private notes use Notities and the old write endpoint is denied. The assignment view links directly to an attributed private note for that client/workout.

Dated and weekly assignments use existing snapshots. Assigning and retrying no longer modifies the library template or erases assignment notes. New assignments have `assignment_attribution` (workout, client, assigning staff, timestamp); historical entries remain unattributed. Creating a template and assigning it goes through the same assignment path. Instructor requests cannot supply a different prescription: the backend reads the saved template. Owner/manager prescription editing remains available separately.

## Financial boundaries

OWNER/MANAGER/FINANCE can read the existing monthly Stripe TEST charge report. FINANCE has a financial client picker containing only account ID/name, loaded through a separate financial endpoint; no teaching roster, health details or schedules are embedded in its app bootstrap. The existing membership/credit panel is reused on the finance page.

This is not complete CAP turnover/accounting: TEST data, linked customers, EUR captured payments, refunds on their original payment month, before fees; disputes flagged, no cash/off-Stripe income or VAT accounting. No financial exports or staff refunds were added. All staff self-service checkout/portal/reconcile/cancel writes remain denied. Athlete self-service and signature-verified webhook processing remain intact. No live Stripe configuration or transactions are part of this implementation.

## Schema and backup plan — approval required

V13 adds `cap_user.staff_role`, default `UNASSIGNED`, and four tables: `instructor_client`, `staff_note`, `staff_audit`, `assignment_attribution`. It does not update passwords, map emails to roles, rewrite workout JSON, fabricate history, or delete client/payment records.

Before applying to `cap`:

1. Review/approve the exact UUID + email + intended-role mapping and this migration plan. Keep Maxim/Vic absent until their identifiers are supplied.
2. Stop CAP for a short maintenance window. Record the current version/image and migration number. Existing logins will not work after V13 until roles are explicitly activated.
3. Take a PostgreSQL custom-format backup with pgAdmin Backup or `pg_dump --format=custom`; enter credentials privately. Store it outside Git with restricted access. Confirm it opens with `pg_restore --list`, and test restoration into a separate database before relying on it. A listing alone is not a restore test.
4. Build the reviewed JAR from backend with `./mvnw.cmd -Dcap.build.directory=target/rbac-build package`, using the documented isolated test database environment. Do not run integration tests against cap.
5. Only after approval, launch the updated native backend once to let Flyway apply V13; stop it before offline role activation. The existing launcher supplies credentials privately. Do not use clean/reset/repair to bypass a migration problem.
6. Run `scripts/staff-roles.cmd` for the read-only mapping preview. Before V13 it can still verify IDs/emails. It never starts Spring or runs migrations.
7. After explicit mapping approval, run `scripts/staff-roles.cmd -ApplyApproved` while CAP is stopped. It verifies every ID/email/account type, locks staff, updates the whole approved batch transactionally and records offline activation without fabricating an authenticated actor. Conflicts roll back the entire batch; rerunning an identical mapping is safe. It never creates staff or changes passwords.
8. Start the updated app, verify role-specific login/permissions with approved users, then separately approve any deployment/container replacement.

Recovery: prefer a forward correction while keeping unauthorized accounts blocked. V13 is additive, but reverting the binary to the old broad COACH authorization would reintroduce access risks. Do not automatically run a down migration or overwrite a newer database with an old backup. Restore into a separate database first, validate, and explicitly approve recovery if needed. Preserve records created after the backup.

## Guillaume and missing staff

The original coach Guillaume is proposed as DEVELOPMENT, not OWNER/MANAGER. DEVELOPMENT cannot be promoted through staff administration. Production/default login is disabled. Explicit local-only login requires both the `development-account` Spring profile and `cap.accounts.development-access=true`; it remains disabled with prod/production profiles. Even locally it has only self-profile/measurement access and no private client, financial or operational privileges. Use isolated fake role fixtures to exercise staff behavior, not a production impersonation switch. The ordinary account and its historical records are retained.

No accounts/passwords were invented for Maxim or Vic. After verified details are supplied, OWNER or MANAGER can use the existing **Collega's → Coach toevoegen** dialog, selecting INSTRUCTOR or FINANCE. They can change the temporary password through the existing password flow. Titles never grant permissions.

The old provisioning CLI creates unassigned accounts and cannot silently grant staff permissions. `cap.accounts.bootstrap-owner=true` is an explicit empty-database bootstrap option used only by fake test fixtures; it does not promote an existing coach.

The old unauthenticated design preview also refuses a public server binding or a prod/production profile. It remains a loopback-only development surface, not an alternate deployed authentication system.

## Verification and manual QA

Tests use `CAP_TEST_POSTGRES_URL`/USER and `CAP_REQUIRE_POSTGRES_TESTS=true`. Run `./mvnw.cmd -Dcap.build.directory=target/rbac-build package`; the separate output prevents VS Code's auto-compiler from interfering with verification. The test helper creates isolated schemas. Never point it at ordinary cap.

- OWNER: create/edit a template and class, publish targets, grant an instructor a client, inspect audit/notes/report. Self-role change/deactivation must fail.
- MANAGER: perform operational work and manage an instructor/finance account. Promoting self/others to OWNER/MANAGER or administering an owner must fail.
- INSTRUCTOR: see own lessons and linked clients only; assign an existing template, create a private note, confirm attribution and unchanged template. Try an unlinked client ID directly: denied. No class edits, template edits, nutrition, measurements, financial or staff admin access.
- FINANCE: report and membership/credit read access only. No private training/schedule/health/notes payloads, navigation or payment writes. Disabled Stripe produces a safe configuration error, not fabricated totals.
- ATHLETE: existing workouts/bookings/nutrition/food log/profile remain; no staff notes or other clients' data. Separate browser sessions prevent account-cookie confusion.
- Change an instructor role: previous session must stop working. Revoke a client grant: subsequent notes/assignment/history requests must fail. Deactivate/reactivate: old sessions remain invalid.
- Refresh/restart the test backend: accounts, existing snapshots, staff notes and attribution remain. Dates use Europe/Brussels and 24-hour Dutch formatting.

No complete accounting/export subsystem, live payments, configurable permission editor, staff-note deletion, password recovery, or production deployment is added. They are separate work.
