# Recorded Decisions

Reviewed: September 29, 2026. Explicit rules and implemented choices only; undocumented motives are not inferred.

## Product and working rules

- Prioritize workouts and coach/client use; nutrition and AI must not delay the core. Basketball/RAW Hoops is outside the MVP. Sources: master plan, README.
- One responsive browser application, phone usability first-class, desktop sidebar retained. Final mobile navigation remains open. Source: `AGENTS.md`.
- Incremental tutor/pair-programmer collaboration; discuss larger changes first. English identifiers and Dutch interface support. Source: `AGENTS.md`.

## Implemented choices

- October 19 visual direction: user approved compact styling, then requested CAP orange across shared accents instead of teal/blue-green. Current palette uses CAP orange actions/navigation/highlights with neutral charcoal dark surfaces and warm off-white light surfaces. Reduced card/header gaps and type sizes retain phone touch targets.

- October 19: user requested phone bottom navigation. First version uses existing routes: Home, Planning, Workouts and Voortgang (training history/PRs); coach shortcuts use Home, Planning, Workout maken and Sporters. Desktop sidebar and mobile Menu remain; Social/AI wait for working screens.

- Plain HTML/CSS/JavaScript and separate Java 25 / Spring Boot 4.1.1 Maven backend; retain the earlier console project. Sources: frontend, `backend/pom.xml`, `src/`.
- CAP's Windows VS Code terminals select JDK 25 through `JAVA25_HOME`. Machine-specific JDK paths belong in user environment/runtime settings, preserving other projects' Java defaults.
- Local JSON with atomic replacement; completed sessions retain plan snapshots. Browser storage holds drafts and club previews. Sources: store services, frontend scripts.
- One example athlete. Assignments use today in Europe/Brussels; a new same-day assignment clears the old assignment but keeps its workout in the library. Source: `WorkoutStore`.
- Records use normalized exercise names and exact reps, not estimated one-rep maxes. Only completed sets count; equal weights do not improve a record. Zero kg means no added weight. Sources: `TrainingStore`, training UI.
- Percentage loads require assignment and a matching record. Java freezes reference/target at creation, rounded to two decimals, so later records do not rewrite prescriptions. Source: `TrainingStore`.
- Completion retries reuse a UUID and return the existing session, preventing duplicates. Timer duration uses timestamps; browser drafts survive refresh. Sources: `TrainingStore`, `training.js`.
- Login/signup are simulations, not security. Coach workout creation requires the preview coach role, independent of a paid plan. Sources: auth/navigation/editor scripts.

## Provisional, not finalized

- October 19: session programmes should be visible in Planning before signup, with booked sessions appearing in the sporter's Workouts. Implemented as a browser-local text preview; subscription-based client grouping and shared/backend session workout tracking remain future work. Broader booking rules still await the coach's input.

Public athlete signup, administrator-granted staff roles and separate subscription entitlements await CAP agreement. Packages/prices are examples. Athlete-created workouts, legal terms, booking/payment rules and providers remain unresolved (`docs/account-access-plan.md`). PostgreSQL is only a likely future direction in the master plan, not an implemented database choice.
