# Recorded Decisions

Reviewed: September 29, 2026. Explicit rules and implemented choices only; undocumented motives are not inferred.

## Product and working rules

- October 30: User explicitly approved creating six confirmed coach accounts now with temporary passwords for later handover/change, rather than invitations. Keep others pending. Unique Windows-encrypted handover passwords, existing local PostgreSQL, no shared password or existing-account reset. Changing one's password terminates that account's sessions. No automatic email and no forced first-login change/expiry in this version.

- October 30: User requested a Financiën page for Glenn after asking about revenue. First version reads monthly Stripe TEST payments on demand, including renewals, rather than estimating receipts from active memberships. Separate single-email COACH finance permission, denied by default. Refunds are attributed to original payment month; fees/VAT/dispute adjustments/external payments are explicitly outside these totals. No staff accounts or live billing created.

- Prioritize workouts and coach/client use; nutrition and AI must not delay the core. Basketball/RAW Hoops is outside the MVP. Sources: master plan, README.
- One responsive browser application, phone usability first-class, desktop sidebar retained. Final mobile navigation remains open. Source: `AGENTS.md`.
- Incremental tutor/pair-programmer collaboration; discuss larger changes first. English identifiers and Dutch interface support. Source: `AGENTS.md`.

## Implemented choices

- October 26: user prioritizes easy use, performance and low hosting cost for coach exercise management. Keep one shared CAP catalog in the existing Java service with sparse JSON overrides and no new service/dependency. Offer archive/restore rather than permanent deletion. Retain existing workout/history/PR names after catalog edits. Search locally against a session cache. This does not introduce real coach authentication or a production database.

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
- October 27: Coach sidebar order is Home, Planning, Groepslessen, Boekingen, Workout maken, Oefeningen, Opgeslagen workouts, Sporters, Voeding. Coach Home tiles follow the same task order (without a self-link); existing Gezondheid & Progress and Shop follow afterward. Athlete navigation retains its original order.
- October 27: User approved calendar-based group planning in small reviewable steps. First step replaces Groepslessen placeholders with a monthly calendar and prefilled session dialog; Planning retains its day/week/list overview. Reuse existing repeat/workout handoffs now; custom recurrence/end dates and editing future series are later work.
- October 27: User requires dd/mm/yyyy dates, Belgium's timezone (Europe/Brussels) and 24-hour clocks throughout the app. Keep ISO date strings/UTC instants internally; explicitly format displays and enhance native inputs so US device settings cannot change the visible field format. Calendar month headings and day-number grids remain natural calendar labels.
- October 27 refinement: user removed the redundant Groepslessen entry. Planning → Les toevoegen now opens the monthly calendar as a dialog for all session types, including Open gym and Privé. Remove the old Home tile/route too; retain one scheduling entry. Calendar cancellation returns to Planning; session-form cancellation returns to the calendar, while saving returns to Planning. This supersedes the separate group-calendar page above.

October 27: User requested direct CAP lesson choices and private appointments for one or more athletes. Use one activity dropdown and searchable checkbox roster. Private assignment creates bookings automatically; only coach and selected athletes see the appointment in the preview. Real authenticated athlete ownership remains future backend work.

October 27: User approved month-calendar-first Planning for athletes and coaches. Clicking a date lists lessons instead of opening the editor. Reuse selected-day bookings/edit actions and open the coach form only through Les toevoegen. Remove the extra calendar popup and old day/week/list controls.

October 27: User approved real accounts/shared server storage using PostgreSQL, Spring Security cookie sessions and coach invitations initially. Implement in small stages; preserve preview separately and deny legacy example-athlete APIs in real-account mode until ownership migration. First coach is bootstrapped from local environment, invitations create ATHLETE only. User confirmed neither Docker nor PostgreSQL installed; native Windows PostgreSQL setup documented, Docker optional.

October 27: Connect real account UI incrementally through a backend-served account page using existing CAP styles. Keep main preview data separate until ownership migration. Coaches copy invitation links manually; no automatic emails or additional-coach signup yet. Localhost links only work on this computer. User has installed PostgreSQL and started accounts mode; prior missing-database setup task is superseded.

October 27: User requested connecting the whole core after successful real login. Implement shared Planning/bookings, workouts/assignments, exercises and training/history/records in the real-account workspace, preserving the old preview separately. Keep domain validation/PR/percentage rules shared through persistence callbacks. Use a transaction-locked shared workout state row initially, relational booking rows for capacity, and versioned per-athlete training drafts. Do not infer demo-recipient ownership or silently import preview data. Commercial membership/payment rules remain undecided; invited athletes can book public sessions and their assigned private sessions. Shared session workouts require fixed weights; percentages resolve against a specific athlete.

October 27: User explicitly requires the exact existing CAP app after real login. Preserve original HTML/CSS, sidebar, Home, month calendar, exercise picker and workout editor; connect data inside those components. Retire the separate account task workspace. Invitations belong in Sporters. Real roster has no inferred demo subscriptions/ownership.

October 27: User flagged add-exercise text regression and missing profile editing/photo. Restore accessible + button in shared original HTML. Preserve original profile fields; store self-service profile and a small sanitized JPEG in PostgreSQL rather than add an image-hosting service. Email stays read-only because login-email change requires a separate verification flow.

October 27: User approved connecting saved nutrition targets using existing coach/athlete screens. Persist only the five published targets plus origin/save metadata; leave calculator inputs and food diary local. No new service/dependency or layout changes. Do not auto-import/publish existing browser targets. Athletes read own values; coaches publish updates.

October 27: User approved sharing food diaries through PostgreSQL while retaining existing screens. Athletes edit only their own entries; coaches read diaries. No automatic import of browser logs. Saved meal templates remain local and are separated by account. No new dependency or external service.

October 27: User requested automatic local frontend refresh and Java rebuild/restart. Keep existing accounts launcher; opt into DevTools only via Maven dev profile. Use VS Code Standard-mode Maven import and Java auto-build (not a second compiler watcher). Both app/login assets serve uncached live files only with local-frontend. Disable DevTools verbose defaults/LiveReload and keep sessions in memory. No production dependency or database changes.

October 27: User authorized Stripe TEST MODE subscription workflow, one clearly labeled temporary CAP Test Membership EUR10/month; no actual pricing/billing rules confirmed. No trials, cancellation at paid-period end, no grace for incomplete/unpaid/past-due/canceled/expired; login and current features remain available. Keep membership separate from roles and server-approved price configurable. Preserve UI/navigation. User confirmed Stripe/CLI not set up; complete local/mock PostgreSQL checks and document exact remaining setup, never claim actual Stripe payments tested. No live mode switch or automatic products/prices.

October 29: User confirmed full-access prepaid prices EUR90/1 month, EUR225/3 months, EUR750/1 year; 12-month monthly options EUR75 cancellable anytime and EUR65 non-cancellable. Memberships stop after their duration, without automatic renewal. Credit packs 1/EUR18, 5/EUR80, 10/EUR140, 20/EUR240; one credit covers one lesson, unused credits never expire. Cancellation effective access end date and booking credit-return rules remain undecided. Recorded requirements only: current Stripe EUR10 TEST placeholder unchanged. Individual coach emails still needed.

October 29 clarification: cancellation of the EUR75/month plan keeps access until the end of the paid month. This resolves the effective access end date above; implementation remains pending.

October 29 implementation: user confirmed booking cancellation before the start returns its credit, and authorized implementing the catalog. Prepaid plans start at confirmed payment, no overlapping memberships; monthly term capped at 12 calendar months. Initial paid-booking tests cover group lessons only, using membership valid on the lesson date or one credit. Open gym/private payment rules remain pending. New portal cannot cancel plans; CAP allows paid-month-end cancellation for EUR75 only. No dependency added; original design retained. All Stripe activity stays sandbox-only.

October 29 refinement: user requires athlete Planning to display available credits, and one credit to cover either a group lesson or Open gym session. Both use existing paid-period membership coverage first; private appointments remain separate. Session edits between group/Open gym retain the credit reservation; conversion to private releases it. Reuse the billing summary GET on Planning navigation, confirmed booking changes and focus rather than polling.

October 29 development workflow: user requested avoiding repeated key/password entry on backend restarts. Cache development credentials as Windows DPAPI SecureStrings in ignored .local/dev-secrets.xml, with user-only file ACL; no plaintext/non-Windows fallback. Same start commands reuse values; -RefreshCredentials replaces all, -UpdateWebhook changes the listener secret, -ForgetCredentials removes the cache only. No secrets in VS Code tasks, Git, CLI arguments or browser. Old EUR10 prompt removed from normal catalog startup (optional -LegacyPriceId).

October 29: User approved read-only coach access to athlete membership, paid-until date and credits in Sporters; then explicitly requested Docker as next task. Use existing club-wide coach roster authorization. Athlete payment actions remain self-service; no coach credit adjustments/cancellation added. Docker architecture/setup remains next, not part of this feature.

October 29 permission correction: user restricts reviewing athlete memberships/credits to Glenn Vlamynck and Dieter Vlamynck, not every coach. Match immutable login email plus COACH role, never editable display name. At most two configured reviewers; empty configuration denies all until actual emails are supplied. No automatic grant to bootstrap/test coach. Existing self-service billing and ordinary coaching access remain. This supersedes the earlier club-wide coach membership-review grant.
