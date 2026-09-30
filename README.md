# CAP Fitness App

A Java learning project building toward a coaching platform for CAP in Gistel, Belgium.

## Current status

Local web prototype with a responsive frontend and a Java 25 / Spring Boot backend.
Workout creation, example-athlete assignment, timed completion, saved history and
rep-specific PRs are connected. Coaches can prescribe fixed kg or percentages of
a matching rep record. Real authentication, a database and payments are not yet
implemented. Planning, bookings and shop still use browser-local preview data.

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

For Windows VS Code, set the user environment variable `JAVA25_HOME` to your
installed JDK 25 directory, then fully restart VS Code and create a new terminal.
CAP's workspace settings use this variable for terminal `JAVA_HOME` and prepend
its `bin` directory to `PATH`; other projects keep their existing Java defaults.
Register JDK 25 as `JavaSE-25` in **user** `java.configuration.runtimes` settings,
retaining valid entries for older JDKs. Maven takes the backend language level
from `backend/pom.xml`. Keep machine-specific JDK paths out of workspace settings.

In a PowerShell terminal outside VS Code, select Java 25 for that session before
running Maven (this does not change Windows' default Java):

```powershell
$env:JAVA_HOME = $env:JAVA25_HOME
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
