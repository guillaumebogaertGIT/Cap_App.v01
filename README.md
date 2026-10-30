# CAP Fitness App

A Java learning project building toward a coaching platform for CAP in Gistel, Belgium.

Staff RBAC implementation is staged: [roles, testing, backup and approval workflow](docs/staff-rbac.md).
Do not apply V13 or replace the running CAP image until the real account-ID mapping and migration plan are approved.
Existing accounts default to unassigned after this migration; explicit reviewed activation is required before staff can log in.

Docker Desktop option: `.\scripts\start-docker.cmd`, then open **http://127.0.0.1:8081/app/index.html**.
It uses your existing Windows PostgreSQL database. Stop native CAP first. See [Docker start, stop and rebuild guide](docs/docker.md).

Local real-account launch: `.\scripts\start-accounts.cmd` from the project root.
Open **http://127.0.0.1:8081/app/index.html**. Save frontend files and refresh;
saved backend Java compiles in VS Code and DevTools restarts automatically.
Development passwords/Stripe test keys are entered once and reused with Windows
encryption. Payment forwarding: `.\scripts\stripe-listen.cmd`; whole app with
payments: `.\scripts\start-stripe-test.cmd`. See the guide to update/forget saved keys.
See [local development: start, refresh and restart rules](docs/development.md).
See [verified behavior, pgAdmin and manual checks](docs/accounts-verification.md).

Stripe billing is **TEST MODE only** and disabled until configured. Shop now has
five memberships and four credit packs. Follow [memberships and credits: start and test](docs/memberships-test.md).
The catalog test launcher enables membership/credit checks for group and Open gym bookings;
ordinary accounts mode leaves them disabled. Existing login/workout features remain available.
The [earlier EUR10 test results](docs/stripe-test-setup.md) remain separate from
the new catalog, whose prices/portal are configured but paid provider flows still need testing.

## Current status

Local web prototype with a responsive frontend and a Java 25 / Spring Boot backend.
Workout creation, example-athlete assignment, timed completion, saved history and
rep-specific PRs are connected. Coaches can prescribe fixed kg or percentages of
a matching rep record. The older design preview retains browser-local club data.

The **original app with real accounts** at `http://127.0.0.1:8081/app/index.html`
keeps the same sidebar, Home, calendar, popups and workout editor, and connects Planning/bookings, workout creation/assignment, exercise management and
athlete training/history/records to PostgreSQL. It uses coach invitations,
cookie sessions and server-side roles/ownership. The preview remains separate;
its data is not imported automatically. See [account setup](docs/accounts-setup.md)
for the `accounts` profile and full review flow. Saved nutrition targets now also use PostgreSQL. Food diaries, payments and health
measurement history are not shared yet.

Read the [development overview](docs/SESSION-2026-09-27.md) for the architecture,
test results, limitations and next steps. Open [the printable overview](docs/CAP-overview.html)
in a browser and use Print → Save as PDF for a handoff document.

## Run the web application

Use JDK 25 and run from `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `frontend/index.html` with VS Code Live Server at
`http://127.0.0.1:5500/frontend/index.html`. Use the preview-entry button.
The API runs locally on port 8080. Persistent prototype files in `backend/data`
are ignored by Git; back them up separately. Do not expose the preview publicly.

Run backend checks with `./mvnw clean test` (Windows: `.\mvnw.cmd clean test`).

Coaches can open **Oefeningen** from the sidebar/mobile menu to search the shared
catalog, add or edit definitions, and archive/restore entries. Active definitions
appear in the workout picker. Existing workout snapshots and name-based PRs keep
their saved names after catalog edits; renaming does not migrate old records.
Coach changes persist in `backend/data/exercises.json` (`cap.exercises.file` can
override the path). The 1,078 imported definitions are bundled in the backend;
only additions/overrides are written to disk. Search/filtering runs in the browser
against a session cache, with 24 rows initially displayed. No new service or
runtime dependency is required. Hosting must retain/back up this data directory;
real server authorization remains required before public deployment.

After updating the original Excel import, run `node tools/export_exercise_seed.mjs`
to regenerate the bundled backend seed. Existing override IDs refer to the current
import's row IDs; review overrides before replacing/reordering the source workbook.
Restart Spring Boot after backend changes, then refresh the browser.

For Windows VS Code, workspace settings select JDK 25 from
`%USERPROFILE%/.jdks/openjdk-25/jdk-25.0.2` for terminal `JAVA_HOME` and prepend
its `bin` directory to `PATH`. Adjust that location if your JDK is installed
elsewhere. Close existing terminals and open a new terminal after changes;
other projects keep their existing Java defaults.
Register JDK 25 as `JavaSE-25` in **user** `java.configuration.runtimes` settings,
retaining valid entries for older JDKs. Maven takes the backend language level
from `backend/pom.xml`. The workspace path uses USERPROFILE rather than a fixed username.

In a PowerShell terminal outside VS Code, select Java 25 for that session before
running Maven (this does not change Windows' default Java):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE/.jdks/openjdk-25/jdk-25.0.2"
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
java -version
.\mvnw.cmd -version
```

## Earlier console learning project

Console functionality:

- Create workouts with exercise names, sets, reps, and tempo.
- List, find (case-insensitive), and remove workouts.
- Load workouts at startup, save on creation, and save when choosing **5. Exit**.
- Stop without saving if loading fails, preserving the existing file.

## Structure

~~~text
src/                    Java source files
backend/                Spring Boot web API and tests
frontend/               Responsive browser application
bin/                    Compiled classes (generated, ignored)
lib/                    Optional dependencies (currently empty)
docs/PROJECT_PLAN.md     Canonical CAP master project plan
.vscode/settings.json   Shared Java source/output configuration
workouts.txt            Current prototype workout data
backups/                Local data backups (ignored)
~~~

## Compile and run

Install a JDK with javac and java on PATH; verified with JDK 11.
From the repository root in PowerShell:

~~~powershell
New-Item -ItemType Directory -Force bin | Out-Null
javac -d bin src/*.java
java -cp bin App
~~~

Always run from the repository root: workouts.txt is relative to the working directory.
Recompile after changing Java code. Choose **5** to save and exit normally.

The text format is:

~~~text
WORKOUT: upper
EXERCISE: bench, 3, 5, 4011
~~~

Back up workout data before manually editing it. Older display-format files require
conversion; they are rejected without saving. The separator comma followed by a space
is reserved, so do not use it inside exercise names or tempo. Numeric console input
is not yet validated, and saving is not protected against a disk failure midway
through writing. The tracked workout file is prototype data; do not put private
client data in Git.

## Roadmap

The core training flow is now demonstrated locally. Next: browser/device checks,
real accounts and permissions, database storage, then shared planning and bookings.
Nutrition and AI must not delay that work.

See [the canonical master project plan](docs/PROJECT_PLAN.md) for scope and learning
principles. Its original review-only task section is retained as historical context;
current work follows the latest agreed task.
