# Real accounts: first backend step

The `accounts` Spring profile adds PostgreSQL-backed accounts and athlete invitations.
The existing design preview still runs normally on port 8080. Account mode runs
separately on port 8081 and deliberately denies the old workout/training/exercise
APIs, which still use example data without real athlete ownership.

This step is an API foundation, not the finished real-account frontend. The app's
current login screen remains a preview. Planning, bookings and workout ownership
will be migrated next; existing browser/JSON data is not imported automatically.

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
inviting coach receives the raw token from the API. Email delivery and the
invitation acceptance page are not implemented yet.

## Checks and remaining work

`./mvnw.cmd test` includes real HTTP tests for coach login, CSRF rejection,
guest/athlete restrictions, invitation acceptance/reuse/expiry/reissue, hashed
password storage, logout and denial of the legacy data APIs in account mode.
Tests apply the actual migration to a temporary H2 database in PostgreSQL mode;
this is not a substitute for checking against PostgreSQL itself.

Before real use: connect the frontend, migrate sessions/bookings/workout ownership,
test capacity transactions on PostgreSQL, add email delivery/password recovery and
login/invitation abuse limits, and configure HTTPS, secure cookies, backups and
deployment monitoring. Keep this profile on localhost during that work. Set
`CAP_COOKIE_SECURE=true` behind HTTPS. Login sessions currently live in server
memory, so restarting requires login again; accounts remain in PostgreSQL.

Framework references: [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html),
[Spring CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
[Flyway initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
