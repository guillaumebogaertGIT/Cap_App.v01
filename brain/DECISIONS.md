# Recorded Decisions

Reviewed: September 29, 2026. Explicit rules and implemented choices only; undocumented motives are not inferred.

## Product and working rules

- Prioritize workouts and coach/client use; nutrition and AI must not delay the core. Basketball/RAW Hoops is outside the MVP. Sources: master plan, README.
- One responsive browser application, phone usability first-class, desktop sidebar retained. Final mobile navigation remains open. Source: `AGENTS.md`.
- Incremental tutor/pair-programmer collaboration; discuss larger changes first. English identifiers and Dutch interface support. Source: `AGENTS.md`.

## Implemented choices

- Plain HTML/CSS/JavaScript and separate Java 25 / Spring Boot 4.1.1 Maven backend; retain the earlier console project. Sources: frontend, `backend/pom.xml`, `src/`.
- CAP's Windows VS Code terminals select JDK 25 through `JAVA25_HOME`. Machine-specific JDK paths belong in user environment/runtime settings, preserving other projects' Java defaults.
- Local JSON with atomic replacement; completed sessions retain plan snapshots. Browser storage holds drafts and club previews. Sources: store services, frontend scripts.
- One example athlete. Assignments use today in Europe/Brussels; a new same-day assignment clears the old assignment but keeps its workout in the library. Source: `WorkoutStore`.
- Records use normalized exercise names and exact reps, not estimated one-rep maxes. Only completed sets count; equal weights do not improve a record. Zero kg means no added weight. Sources: `TrainingStore`, training UI.
- Percentage loads require assignment and a matching record. Java freezes reference/target at creation, rounded to two decimals, so later records do not rewrite prescriptions. Source: `TrainingStore`.
- Completion retries reuse a UUID and return the existing session, preventing duplicates. Timer duration uses timestamps; browser drafts survive refresh. Sources: `TrainingStore`, `training.js`.
- Login/signup are simulations, not security. Coach workout creation requires the preview coach role, independent of a paid plan. Sources: auth/navigation/editor scripts.

## Provisional, not finalized

Public athlete signup, administrator-granted staff roles and separate subscription entitlements await CAP agreement. Packages/prices are examples. Athlete-created workouts, legal terms, booking/payment rules and providers remain unresolved (`docs/account-access-plan.md`). PostgreSQL is only a likely future direction in the master plan, not an implemented database choice.
