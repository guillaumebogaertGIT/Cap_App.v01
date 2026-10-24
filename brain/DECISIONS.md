# Recorded Decisions

Reviewed: September 29, 2026. Explicit rules and implemented choices only; undocumented motives are not inferred.

## Product and working rules

- Prioritize workouts and coach/client use; nutrition and AI must not delay the core. Basketball/RAW Hoops is outside the MVP. Sources: master plan, README.
- One responsive browser application, phone usability first-class, desktop sidebar retained. Final mobile navigation remains open. Source: `AGENTS.md`.
- Incremental tutor/pair-programmer collaboration; discuss larger changes first. English identifiers and Dutch interface support. Source: `AGENTS.md`.

## Implemented choices

- October 20: categories organise saved workouts (CAP HYROX/Hybrid/Move/Lift & HIT), not automatic assignments to all matching sessions. Coach chooses dates and specific sessions. Personal workouts support independent dated snapshots; athlete list shows name/date, expandable blocks and Start training, with earlier dates separately selectable. This supersedes today's-single-workout-only display/storage assumptions.

- October 20: busy Planning starts in compact day view (expandable time/title/capacity rows), with week/list views retained. Open gym represents a booked place, not a session workout: no programme/assignment entry and no booked-workout card. Seven places is an editable preview default, not a business limit. Open gym access is provisionally in the Uitgebreid preview plan; real entitlements await CAP.

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

- October 20: User confirmed CAP Lift & HIT remains a single category. Coach creation starts with a Home-style category grid before the shared editor; category selection does not automatically assign sessions.

- October 20: First coach Gezondheid & Progress page uses the existing example roster and a searchable athlete picker. Manual measurements are per-athlete, session-only; Guillaume shares the athlete preview. No invented health/training measurements or automatic health interpretations. Multi-athlete backend integration remains separate work.

- October 22: Use coach-supplied KILO exercise names in workout creation. Preserve original rows/empty metadata; collapse exact duplicates only in picker display. Ship a static generated catalog with no new runtime dependencies or backend changes. Optional filters/source details keep rows compact; handwritten custom names remain allowed. Nutrition files are not part of this step.

- October 22 refinement: user found the inline exercise filters excessive. Use a dedicated coach Oefeningen sidebar page with search and add-to-draft actions; simplify the Create Workout picker to search only. This supersedes the nested region/category/subcategory filter UI.

- October 22: User requested body focus as the first creation choice. Lower/upper/full use the existing workoutType field, independent of CAP categories and recipients; new saved-workout sections filter by that stored value. No automatic guessing or relabelling of older workouts.

- October 22 refinement: user requested no automatic first exercise card and body-focus-aware search. Start with +; use one wider search window instead of inline dropdowns; collapse optional prescription controls. Use source region labels for matching; preserve existing draft exercises and flag mismatches when the type changes.

- October 22: Planning visual revision addresses stacked/squeezed letters by giving lesson rows their own full-width line; week columns adapt to available width. Direct Day/Week/List controls replace the cycling view toggle.

- October 22 Nutrition first implementation: follow Smart Macro Planning Tool for calculations; keep Voedingsschema and food data for the meal-planning step. Expose basic inputs first with advanced strategy/scaling collapsed. Coach reviews/adjusts/saves targets; athlete preview reads Guillaume's targets. Session-only data, default current-weight scaling/maintenance, explicit missing-data handling; no automatic diet assignment or clinical interpretation.


October 22: user requested food logging. Keep the first diary compact under athlete Voeding with searchable workbook products, amount/unit, meal and optional custom portion values. Coach has a read-only selected-athlete diary. Historical dates compare against explicitly labelled current targets. Retain session-only preview storage pending real athlete ownership/backend work.


October 22: user requested smaller daily bars and repeatable meals. First implementation saves a logged meal group under an athlete-chosen name, then reuses its fixed portion with one Add button. Uses existing session-only preview storage; permanent recipe persistence remains future work.

- October 23: User requested saved changes and no unexpected login resets. Persist preview session and formerly session-only profile/measurement/nutrition/food data in this browser; logout retains records. Exclude backend writes from Live Server. No credentials, real authentication or cross-device sync introduced.

- October 23: Athlete review actions target one dated assignment, preserving the saved template, other dates and completed training history. Reuse the shared editor; keep coach notes per assignment in backend JSON.

- October 23: User requested both flexible weekly training (especially open gym) and coach-selected training days. Use one flexible workout per Monday–Sunday week, with optional chosen day; do not create seven copies. Coach can instead assign a fixed day. Athletes can plan their flexible workout within its week. Group workouts remain attached to specific scheduled sessions; agenda filters a day/week and requires session selection. This supersedes automatic assignment for today from the athlete shortcut.



- October 24 clarified by user: Exercises belongs under Create Workout as a button opening a separate selection page, with Excel category filters and a dropdown of matching exercises. Preserve current workout draft and return action.

- October 24: Body-focus selection (lower/upper/full) opens the workout editor directly. Category/recipient choices remain accessible through the editor and save dialog.

- October 24: User requested naming the workout before choosing/adding exercises. Keep name above the exercise-page button and add catalog selections to that named draft; saving remains explicit.

- October 24: User approved different reps/weights/percentages per set, retaining percentages based on matching rep-count PRs. Coach targets and athlete actuals remain separate. Old shared-target workouts remain supported.
