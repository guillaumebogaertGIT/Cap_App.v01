# CAP Fitness App

A Java learning project building toward a coaching platform for CAP in Gistel, Belgium.

## Current status

Early console prototype. No web interface, database, authentication, client assignments,
workout completion history, nutrition system, or AI coach is implemented yet.

Current functionality:

- Create workouts with exercise names, sets, reps, and tempo.
- List, find (case-insensitive), and remove workouts.
- Load workouts at startup and save when choosing **5. Exit**.
- Stop without saving if loading fails, preserving the existing file.

## Structure

~~~text
src/                    Java source files
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

## December 2026 goal

Aim for a focused demonstration of coach workout creation and assignment, client
performance logging, stored history, and a simple CAP interface. These are planned
features, not current functionality. Nutrition and AI must not delay the core workflow.

See [the canonical master project plan](docs/PROJECT_PLAN.md) for scope and learning
principles. Its original review-only task section is retained as historical context;
current work follows the latest agreed task.
