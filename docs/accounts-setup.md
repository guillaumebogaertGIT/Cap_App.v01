# Real accounts and shared CAP data

The `accounts` Spring profile uses PostgreSQL for accounts, lessons, private
recipients, bookings, workouts, exercise-library changes and athlete training.
The existing design preview still runs separately on port 8080 with its old data.

The original app is served at `http://127.0.0.1:8081/app/index.html`.
Guests go to `/accounts/index.html` to log in, then return to the original app.
It supports real login/logout, coach invitations, Planning, bookings, workouts,
exercise management, training entry, history, PRs and saved nutrition targets. Existing browser/JSON
preview data is not imported automatically or assigned to real people.

## Use the account page

Recommended from the project root: `.\scripts\start-accounts.cmd`. This privately
prompts for the database-role password and preserves existing accounts. Stop the
old backend first if 8081 is occupied. See [verification and pgAdmin guide](accounts-verification.md)
and [read-only SQL](accounts-review.sql).


After updating the code, stop the account backend with Ctrl+C and restart it in
the same terminal (which retains your database environment variables):

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=accounts'
```

Open `http://127.0.0.1:8081/accounts/index.html` and log in using your coach email
and coach password, not the database password. The preview entry screen also has
an **Inloggen met je echte CAP-account** link. Use the backend-served page, not
Live Server, for real account operations.

As coach, open **Sporters**, enter an athlete email and choose **Uitnodigingslink maken**. Copy the
link and open it in a private browser window to test accepting it as that athlete.
The athlete chooses their own password, then logs in. Coaches cannot choose an
athlete's password or grant coach rights through this form.

Real login now opens the original sidebar and screens; there is no separate task workspace:

- **Planning / Boekingen:** month calendar, CAP group/private/open-gym lessons,
  one/4/8-week creation, per-lesson editing/removal, booking/cancellation and
  attendance. Private recipients come from real accepted athlete accounts.
- **Workout maken / Opgeslagen workouts / Workouts:** coach workout library/editor, sets with individual
  reps/weights/percentages, rest/tempo/supersets, assignments to a real athlete
  on a fixed day or flexible week, and snapshots attached to booked sessions.
  Athletes start their own assigned or booked workout, log results and view
  their own history/rep-specific records. Coaches select an athlete to review.
- **Oefeningen:** coaches add/edit/archive/restore shared definitions. Existing
  workout names and completed history remain snapshots when definitions change.
- **Sporters:** coaches create athlete accounts through invitations.

To review the complete flow, create an athlete invitation and accept it in a
private browser window. In the coach window, create a future group lesson, save
a workout and choose **Toewijzen → Geplande les**. In the athlete window, refresh
Planning, book the lesson and open Workouts. Start, enter results, wait for the
short typing pause (about one second), refresh, then complete.
Verify history/PRs and that another athlete cannot see a private appointment.

Training starts with a server-frozen workout snapshot. Progress is saved after a
short typing pause and can resume after refresh/login on another browser.
Concurrent edits from another device are rejected by version checks rather than
silently overwriting results. Completion retries reuse the same server ID.
Changing a template does not rewrite a training already in progress or completed.

Each booking locks its lesson while checking capacity. Roles, private visibility
and athlete ownership are enforced on the server, not by the browser selector.
Membership billing/paid-package entitlement rules are not implemented; invited
athletes can book public lessons and their explicitly assigned private lessons.
Food diaries, health measurements/history, shop/payments and messaging remain outside
this connected core. Their original screens remain visible; browser-only tools
do not become shared storage. Only the core features listed above use PostgreSQL.

For now these localhost links work only on this PC, not on an athlete's phone or
another computer. Email delivery and public hosting are separate later steps.
No password or session token is stored in browser localStorage. Invitation tokens
use a URL fragment and are removed from the visible address after reading.

## Start a local persistent database

Requires JDK 25 and either Docker Desktop with Compose or an existing PostgreSQL
database. Docker was not available on the development machine during this step.
No paid service or hosting account is required for local work.

### Windows without Docker

Install PostgreSQL using the installer linked from the
[official Windows downloads page](https://www.postgresql.org/download/windows/).
Keep the administrator password you choose during installation. Open SQL Shell
(psql), connect as the administrator to the local `postgres` database, and create
a separate development role/database:

```sql
CREATE ROLE cap LOGIN;
\password cap
CREATE DATABASE cap OWNER cap;
```

The `\password` command prompts for a password without putting it in SQL history.
In PowerShell set:

```powershell
$env:CAP_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5432/cap'
$env:CAP_DATABASE_USER = 'cap'
$databaseSecret = Read-Host 'Password chosen for the cap database role' -AsSecureString
$env:CAP_DATABASE_PASSWORD = [System.Net.NetworkCredential]::new('', $databaseSecret).Password
```

Then continue with **Create the first coach** below. Use your installation's port
if you changed it from 5432. Do not use the PostgreSQL administrator account as
the application's database user.

### Optional Docker alternative

From the project root in PowerShell:

```powershell
$env:CAP_DATABASE_USER = 'cap'
$databaseSecret = Read-Host 'Choose a local database password' -AsSecureString
$env:CAP_DATABASE_PASSWORD = [System.Net.NetworkCredential]::new('', $databaseSecret).Password
docker compose -f compose.accounts.yaml up -d --wait
$env:CAP_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55432/cap'
```

The named Docker volume retains accounts when the container stops. Changing the
environment password later does not change an already initialized database's
password. Do not delete the volume to fix a connection problem; investigate the
database credentials. A volume is persistence, not a backup.

For an existing database, set the same three `CAP_DATABASE_*` variables to its
connection details and skip Docker. Use a dedicated database/user.

## Create the first coach and start account mode

In the same terminal:

```powershell
$env:CAP_COACH_EMAIL = Read-Host 'First coach email'
$coachSecret = Read-Host 'First coach password (12+ characters, max 72 UTF-8 bytes)' -AsSecureString
$env:CAP_COACH_PASSWORD = [System.Net.NetworkCredential]::new('', $coachSecret).Password
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=accounts'
```

The first startup creates one coach with a BCrypt password hash. Later startups
do not change that coach's password; remove the bootstrap environment variables
after creation. They are not a password reset mechanism. No public endpoint can
create a coach, and invited users always become athletes.

Flyway applies versioned SQL migrations at startup. It does not replace the old
JSON files. Missing database configuration fails startup instead of silently
using an in-memory database.

## Exercise the API without changing the frontend

In a second PowerShell terminal, use a cookie-preserving web session. Commands
below intentionally avoid printing passwords or invitation tokens.

```powershell
$accountBase = 'http://127.0.0.1:8081/api/accounts'
$csrf = Invoke-RestMethod "$accountBase/csrf" -SessionVariable coachSession
$loginSecret = Read-Host 'Coach password' -AsSecureString
$loginBody = @{
    email = (Read-Host 'Coach email')
    password = [System.Net.NetworkCredential]::new('', $loginSecret).Password
}
Invoke-WebRequest "$accountBase/login" -Method Post -WebSession $coachSession -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body $loginBody -UseBasicParsing | Out-Null
$loginBody.Clear()
Invoke-RestMethod "$accountBase/me" -WebSession $coachSession

# Login rotates the session; fetch a fresh CSRF token after login.
$csrf = Invoke-RestMethod "$accountBase/csrf" -WebSession $coachSession
$inviteBody = @{email=(Read-Host 'Athlete email')} | ConvertTo-Json
$invitation = Invoke-RestMethod "$accountBase/invitations" -Method Post -WebSession $coachSession -Headers @{'X-CSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body $inviteBody

# For this local test, accept the invitation in a separate anonymous session.
$athleteCsrf = Invoke-RestMethod "$accountBase/csrf" -SessionVariable athleteSession
$athleteSecret = Read-Host 'Athlete password' -AsSecureString
$acceptBody = @{
    token=$invitation.token
    name=(Read-Host 'Athlete name')
    password=[System.Net.NetworkCredential]::new('', $athleteSecret).Password
} | ConvertTo-Json
Invoke-RestMethod "$accountBase/accept" -Method Post -WebSession $athleteSession -Headers @{'X-CSRF-TOKEN'=$athleteCsrf.token} -ContentType 'application/json' -Body $acceptBody
Remove-Variable acceptBody, invitation

# Acceptance creates the athlete account; login separately using the same flow.
# Logout requires the current CSRF token and invalidates the session.
$csrf = Invoke-RestMethod "$accountBase/csrf" -WebSession $coachSession
Invoke-WebRequest "$accountBase/logout" -Method Post -WebSession $coachSession -Headers @{'X-CSRF-TOKEN'=$csrf.token} -UseBasicParsing | Out-Null
```

Invitation tokens expire after seven days, work once and are stored only as
SHA-256 hashes. Reissuing an invitation invalidates the earlier token. Only the
inviting coach receives the raw token from the API. Email delivery is not
implemented yet; the account page provides a copyable link for local testing.

## Checks and remaining work

`./mvnw.cmd test` includes real HTTP tests for coach login, CSRF rejection,
guest/athlete restrictions, invitation acceptance/reuse/expiry/reissue, hashed
password storage and logout. Service tests also cover booking capacity races,
private-recipient edits, training ownership, frozen snapshots, version conflicts,
percentage targets and shared exercise persistence. The default suite uses H2.
The original 32 backend tests have also passed with account/club tests configured against
an isolated PostgreSQL 18 cluster; V1–V4 migrations and restart persistence were
checked without accessing the developer's CAP database. For repeat PostgreSQL
tests, set `CAP_TEST_POSTGRES_URL` to a dedicated test database (never production),
plus `CAP_TEST_POSTGRES_USER`/`CAP_TEST_POSTGRES_PASSWORD`; tests create fresh schemas.

Before deployment: add email delivery/password recovery and
login/invitation abuse limits, and configure HTTPS, secure cookies, backups and
deployment monitoring. Keep this profile on localhost during that work. Set
`CAP_COOKIE_SECURE=true` behind HTTPS. Login sessions currently live in server
memory, so restarting requires login again; accounts remain in PostgreSQL.

Framework references: [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html),
[Spring CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
[Flyway initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Frontend changes during local development

Start with .\scripts\start-accounts.cmd from the project root, or use VS Code
Terminal -> Run Task -> CAP: Start development. The launcher enables development-only
DevTools plus direct uncached frontend serving for both /app/ and /accounts/.
Save frontend changes and refresh. Saved Java is compiled by VS Code, then
DevTools restarts the backend automatically; login may be required afterward.
See [the full development guide](development.md) for startup, troubleshooting,
production separation and changes requiring a manual restart.

## Profile editing

Open the account menu ? Profiel to edit name, birth date, sex, height, weight,
goal and experience. Email is read-only. Profile fields and photo persist in
PostgreSQL through migration V5. Choose a photo up to 8 MB; the browser crops
and compresses it to 256px JPEG, and the server validates/re-encodes it. Choose
Profiel bewaren to save a photo selection or removal. Profiles/photos are
available only to their signed-in owner. Measurement history remains separate.

## Shared nutrition targets

Restart the accounts backend to apply migration V6 automatically. Existing data
is preserved. Coach: Voeding -> select the real athlete -> calculate or enter
all five targets -> Dagdoelen bewaren. Athlete: open Voeding in a private browser
window with their own login; the saved calorie/protein/carbohydrate/fat/water
targets load from PostgreSQL. Reopening Voeding or returning to that window
refreshes them. Athletes cannot change targets or query another athlete.

The table is public.nutrition_target in the cap database. Each athlete has one
current row, with the last coach and server save time. Calculator drafts and
food diaries remain browser-local; old browser drafts are not auto-published.
Tests cover permissions, validation, update-in-place and PostgreSQL restart
persistence. Manual and calculated targets were verified through both original
coach/athlete screens in separate browser sessions. All 36 tests passed.

## Shared food diary

For the separate, opt-in **Stripe TEST MODE** billing flow, see
[Stripe test setup](stripe-test-setup.md). Ordinary account startup still works
without Stripe. Membership does not restrict the existing features.

Restart the accounts backend once to apply V7. New entries in athlete Voeding are saved in PostgreSQL; the coach selects that athlete in Voeding and the same date to read them. Returning to the coach window or reopening Voeding refreshes the diary. There is no background polling. Only the athlete can add/delete; Verwijderen and undo both save on the server. Failed saves keep the entered form.

Existing browser-only entries are not imported or deleted. Saved meal templates remain local to that account/browser; logging a saved meal saves the resulting portion to PostgreSQL. The existing layout and demo remain separate.

In pgAdmin open cap -> Schemas -> public -> Tables -> food_diary_entry. Read-only:

```sql
SELECT u.display_name, d.entry_date, d.id, d.saved_at
FROM public.food_diary_entry d
JOIN public.cap_user u ON u.id = d.athlete_id
ORDER BY d.saved_at DESC;

SELECT entry_date, payload::jsonb ->> 'name' AS food,
       payload::jsonb ->> 'meal' AS meal,
       payload::jsonb ->> 'calories' AS calories
FROM public.food_diary_entry
ORDER BY saved_at DESC;
```

Manual check: log food as a test athlete, refresh, then select the same athlete and date as coach in another browser/private window. Check the meal/totals; delete and undo as athlete, returning to the coach window each time. A second athlete must see only their own diary. Restart and verify entries remain.
