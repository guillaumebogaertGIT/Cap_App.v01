# Session Log

## October 30, 2026 — Glenn staff administration

- Added separate Glenn-only staff-manager capability/config and coach creation/deactivate/reactivate in Collega’s. Temporary password chosen by manager, BCrypt hash only; no emails or plaintext response/cache. V11 defaults existing accounts active, disables new login and revokes existing sessions. Indexed private-request activation check catches stale/racing auth; inactive coaches hidden from ordinary directory/new coach selections but historical lessons retained. Own deactivation blocked; titles do not grant access.
- PostgreSQL: catalogue23 and directory7 passed; staff class8 passed in clean rerun after correcting a test lesson category. Covers role/CSRF denial, duplicate/athlete preservation, retained sessions, self protection, old sessions remaining expired after reactivation and direct stale-session DB flag check. No real staff mutations in QA.
- Actual browser fake-manager add/login/deactivate401/reactivate/relogin passed; password fields cleared, own action hidden, ordinary coach controls absent. Both themes 390/768/1440 fit. Saved account + roster-refresh failure is reported clearly with retry. Final Docker rebuilt/restarted with actual local Glenn manager config; existing data fingerprints verified unchanged.

## October 30, 2026 — Coach colleague directory

- Added Collega’s sidebar route, full-row name/title buttons, contact dialog with mail/tel links and missing-phone state. Coach profile includes optional phone with sharing notice. V10 additive coach_title column labels six existing staff emails from supplied screenshots; security roles/reviewer rules remain independent.
- GET list/detail restricted to COACH; DTOs exclude private profile/health/password/payment data. Phone validated; profile edits cannot alter title. Only actual coach accounts listed. No new dependency, accounts or invented phone contacts.
- CoachDirectoryTests: 7 passed on isolated PostgreSQL (includes inherited account/profile checks), covering guest/athlete denial, non-coach404, no-store, restricted DTOs, own phone update/removal, invalid phone, no title editing and logout. Browser actual fake profile save/contact fetch passed; 390/768/1440 both themes fit, missing-phone/error/retry safe and athlete link/route hidden with zero directory calls. Stale hash-only browser navigation required reload during QA, no app fix needed.
- Rebuilt/restarted Docker to apply V10; verified account/password/role and workout fingerprints unchanged. No real coach phone/profile values changed during QA. Instructions in docs/coach-accounts.md.

## October 30, 2026 — Free coach access

- User requested no coach purchasing and permanent free coaching access. Removed coach Shop/Home/payment options, prevented billing dialog/self-status requests, added Home notice. COACH self summary is free/no expiry/no offers with no SQL/Stripe work; Spring Security and service checks deny four personal payment actions. Athlete billing and restricted review permissions retained; no existing Stripe records changed.
- Isolated PostgreSQL catalogue+finance run: 31/32 initially passed; one old assertion expected coach access false and was updated for the new requirement. Both lifecycle/new coach tests reran successfully. Coach test verifies 403 actions/no gateway interactions/no billing-row creation; athlete checkout 200. Browser fake coach at 390/768/1440: no Shop/payment/dialog, zero billing calls, no overflow; athlete still sees Shop/payment/dialog. Docker image rebuilt and local app updated.

## October 30, 2026 — Docker app with native PostgreSQL

- User installed WSL/Docker Desktop and authorized implementation. Added source-allowlisted multi-stage Java 25 image, Compose app-only localhost:8081 service, non-root/limited-memory runtime, HTTP health probe and start/stop/restart/status commands. Existing PostgreSQL 18.6 cap DB retained; no database/network authentication settings changed. See docs/docker.md.
- Reused DPAPI database/Stripe test credentials and reviewer config through environment-backed Compose runtime secret; no plaintext checkout config, container secret env or build args. Fixed fresh-install credential-helper PATH issue. Compose rejected environment-backed secrets with read-only root filesystem; removed read_only while retaining non-root/capability protections and documented limitation. Stop removes app container/runtime secret copy; start forces recreation.
- Built successfully; Flyway validated existing V1-V9, no migration needed. Actual existing Dieter login 204, membership permission true, finance false/direct403, guest401, legacy demo403, logout401, test billing configured. Removed/recreated container; in-memory account/password/workout fingerprints and counts matched. No new payment or real data mutation for verification.
- Audited packaged original assets byte-for-byte, absence of login/local/Git/devtools/H2, no secret container env, loopback port and occupied-port guard. Native development launcher preserved; Docker source changes require rebuild. App left healthy/running for user. Actual sandbox checkout through Docker remains manual.
- Actual scratch context export caught backend/target re-included through parent allow rules; tightened per-directory exclusions and fresh export passed for private/build/test/unrelated paths. Final app rebuild passed (cached). Removed only the temporary audit exports afterward.

## October 30, 2026 — Coach accounts and password changes

- User approved six confirmed coaches with individual temporary passwords for later handover. Read-only preflight found no email collisions, then explicitly provisioned six COACH rows in existing local cap DB. Actual sign-ins all 204/COACH; logged out verification sessions. No existing records reset or staff emails sent.
- Temporary values saved with Windows DPAPI/user-only ACL in ignored separate coach-secrets.xml. coach-login.cmd copies selected password privately and opens local login. Existing main server reported reviewer flags false; manual restart needed for config/new API.
- Added own-password POST + CSRF/current-password checks, BCrypt update and registry expiration of matching sessions; original account menu dialog. Isolated PostgreSQL CoachSetupTests 8 passed (including inherited account checks); FinanceApiTests 9 passed with updated session config. Initial test fixture password mismatch corrected. Provisioning idempotence, role-conflict rollback, both-session expiry and old/new login behavior tested with fake data.
- Browser dialog fits 390/768/1440; mismatch, wrong-password and success/cleared fields checked with fake responses. No actual staff password changed in tests. See docs/coach-accounts.md; Docker remains pending.

## October 30, 2026 — Glenn financial overview

- Added Financiën in existing sidebar/design, separate Glenn-only email + COACH permission, read-only monthly Stripe TEST captured/refunded EUR report for linked CAP customers. No payment mutations or migrations. Configured ignored local reviewer email; no real staff accounts created.
- FinanceApiTests: 9 tests passed against isolated PostgreSQL (includes inherited account checks). Existing catalog/reviewer tests: 24 passed in combined run; initial finance route allowlist omission caught and fixed, then 9 finance tests rerun successfully. No real Stripe purchases or user database changes.
- Browser fake-response checks at 390/768/1440, light/dark: no horizontal overflow; totals, Brussels dates, empty/error/retry, unauthorized hidden route/link and zero automatic finance requests passed. Actual Stripe report comparison on Glenn's future account remains manual. See docs/finance.md.

## October 20, 2026 — Compact athlete workouts

### Coach athletes grouped by subscription

- Added compact Credit/Groep/All-in dropdown sections, with first-name-sorted fictional examples. All-in describes both credit and group access. Nine display-only examples plus existing Guillaume; his assignment controls remain functional. No real member accounts, credit balance or entitlement changes implied.
- HTML structure/count checks passed. Phone sections stack and desktop shows three columns; actual browser verification remains outstanding.

### Compact coach bookings

- Replaced coach per-participant booking cards with seven date dropdowns from a date picker. Each date contains compact session dropdowns (including zero-booking sessions), then participants, attendance and cancellation controls. Athlete view remains unchanged. Expanded sections and attendance focus survive updates.
- Syntax and actual-renderer simulation passed for seven days, 18 sessions/day, nested participants and preserved open state. Actual browser/device visual verification remains outstanding.

### Exercise-first builder and save window revision

- User revised flow: compact initial exercise, small + for next exercise, + Superset beside it; title/type/destination/category/description moved into save dialog. Private/Open gym/Athlete select athletes by first-name letter (only Guillaume exists). Group saves into a CAP category without requiring scheduling; optional session linking remains explicit.
- Added persisted workoutType and coach library Full body/Lower body/Upper body/Conditioning/Overig filters; retained group dropdown sections. Category storage now also supports Private/Open gym/Athlete. Existing null types display under Overig.
- All 16 backend tests passed, including type/audience persistence through resolve/assignment/reload. Four JS syntax, HTML structure and actual type-filter simulations passed. Restarted agent-managed backend; browser/device visual verification remains outstanding.

### Categories and dated assignments

- Grouped coach library into CAP HYROX/Hybrid/Move/Lift & HIT plus general/personal. Added category to group-session editor and category/date filters for explicit session selection. Saved-workout Toewijzen supports a personal date or selected group sessions; no automatic category-wide assignment.
- Added persisted dated personal assignment snapshots and athlete name/date overview with expandable blocks/start and previous-date filter. Same template can be scheduled on multiple dates; template edits preserve scheduled snapshots. Completion references assignmentId. Local group snapshots preserve structured superset blocks.
- Old workout arrays load automatically with backup before first format upgrade write. All 15 backend tests passed, covering migration/backup, multiple dates, categories and completion from a scheduled snapshot after template edits. JS/load-order and selected-session-only simulations passed. Restarted agent-managed backend and verified GET assignments/health. Browser/device verification remains outstanding. No commit/push.

### Athlete Home tiles

- Replaced large assigned-workout/support panels with six compact linked tiles. Small assignment-ready caption on Workouts; booking count on Planning. Full assigned workout remains accessible through Workouts. Added explicit Nutrition placeholder route; no nutrition implementation.
- Reused existing routes, two-column phone/three-column wider layout and CAP orange icons. Removed obsolete Home selection redirect nodes/references. Three script syntax checks and Home tile/markup checks passed; browser visual review remains outstanding.

### Saved library and athlete superset display

- Added dedicated coach Opgeslagen workouts navigation/view with existing compact dropdown rows and edit/assign/delete actions; saves navigate there. Home retains a library shortcut.
- Replaced athlete selection redirect (hidden by the assigned Home panel) with Bekijk workout modal. Shared display helper groups superset A/B/C sections in previews and training entry, preserving original exercise indexes.
- Five JavaScript syntax checks and A/B/C grouping/index tests passed; actual browser/device verification remains outstanding. No backend/data changes.

### Assign a saved workout

- Added coach-library Toewijzen dialog and dedicated assignment endpoint. Assigns the existing workout ID to example athlete Guillaume for today; clears the previous same-day assignment without deleting templates or creating copies. Resolves percentage targets for the athlete and preserves session/history snapshots.
- UI shows the replacement rule, loading/error feedback and blocks assigning a workout active in this browser. Actual browser/mobile verification remains outstanding.

### Supersets and optional RIR

- Revised on user feedback: removed visible superset-letter selector. Bottom-right + Superset creates a linked exercise box, grouped side-by-side on wider screens and stacked on phones. Grouped fields are more compact; removing one of a pair clears the leftover group. Existing saved supersets load into these wrappers. Backend format unchanged. Syntax and actual grouping-function simulations passed; visual review remains outstanding.

- Added optional coach superset letters A–F, paired-exercise validation and compact group labels/accent borders. Saved workouts, editing/copies, prescriptions, session programme text and training snapshots preserve groups.
- Added narrow optional RIR field beside performed reps/kg (0–10) and display in completion history. No target RIR prescription or change to PR calculations. Old JSON fields default null and existing Java constructors remain supported.
- All 12 backend tests passed, including superset persistence/singleton rejection and RIR persistence/bounds. Four JavaScript syntax checks passed. Actual browser/mobile layout verification remains outstanding.

### Busy timetable and Open gym

- Added user-requested busy preview: one-time local seed of three future days, 18 slots/day (6 group, 6 private, 6 Open gym), varied fictitious occupancy and labelled example sessions. Opens first future preview day. Existing sessions/bookings retained; no Guillaume bookings invented. Athlete booking list now excludes other example participants.
- Syntax and seed simulations verify 54 sessions, mix, preservation and no duplicate inserts. Browser visual review remains outstanding.

- Changed planning cards to expandable time/title/type/capacity rows and day-first navigation; retained week/list modes. Added Open gym with seven-place editable default, no coach/program requirement, no workout assignment, and bookings separate from personal/group workouts.
- Provisional open-gym entitlement uses Uitgebreid preview plan. No automatic daily 18-slot seeding or business hours imposed; coach can create the actual slots.
- Syntax checks and actual rendering simulation passed for an 18-slot day and absent Open gym programme. Actual phone/tablet/desktop browser verification remains outstanding.

### Current/future planning and compact date picker

- Planning now clamps its earliest date to today (Brussels), disables backward navigation at that boundary, and omits cancelled/ended sessions while retaining in-progress ones. Lesson creation rejects past dates. Booking history is preserved.
- Uses a compact native date picker with showPicker enhancement/fallback; activity/trainer filters collapsed into Filters. Syntax and actual filter/navigation simulations passed. Native picker/phone/tablet/desktop visual verification remains outstanding.

- Coach session action changed from cancel/restore to Les verwijderen with named/date confirmation. Confirming removes the selected local session and related bookings; workout library is preserved. Dismiss/confirm callback simulations and syntax checks passed; browser review remains outstanding.

- Follow-up: HYROX remained visible because athlete library included every coach template. Restricted athlete list to today's personal assignment and renamed it Mijn persoonlijke workouts. Booked group sessions remain separate; full coach library unchanged. Syntax/filter checks passed; browser review remains outstanding.

- Replaced large athlete workout cards with expandable rows, collapsed/searchable library and six-at-a-time disclosure. Upcoming booked sessions appear above the library as compact rows with their own six-at-a-time control. Existing ended/cancelled booking exclusion retained; shared library templates remain available after cancellation.
- Syntax checks and simulated actual library filtering checks passed (limits/search/more/empty). Browser/device layout review remains outstanding. No backend changes, commit or push.

## October 19, 2026 — Lesson save fix

### Shared visual polish

- User approved compact styling and requested CAP orange for sidebar and shared accents. Replaced blue-green shared surfaces with neutral charcoal/warm off-white; retained small blue/lilac health metric accents. User requested committing and pushing all current changes to GitHub.

- Applied compact spacing/type/card treatment throughout existing screens via polish.css; shared teal navigation, amber actions and coordinated light/dark surfaces. Kept mobile input sizing, minimum 44px action targets, safe-area bottom spacing and desktop sidebar.
- CSS structure/whitespace checks passed; seven primary palette text/background pairs meet 4.5:1 calculated contrast. Browser tools report no connected browsers, so no actual phone/tablet/desktop visual verification was possible. User visual review remains next step.

### Sporter health and progress

- Visual iteration: six main measurements now use small square tiles (three columns on phones, six on wider screens). Health-only teal/amber/blue/lilac accents adapt to light/dark themes. User prefers compact tiles and more colour; visual review remains outstanding. Syntax/markup checks passed.

- Compact-layout follow-up: removed Uit je profiel; merged main composition values into Jouw metingen with latest available value per metric (dated title). Additional/segmental values, entry and histories expand on demand. Reduced health card padding and value type size; phone metrics remain two columns. Syntax/structural checks passed; browser visual review remains outstanding. User prefers a denser overview over large cards.

- Follow-up: optional manual body-composition entry and dated history, based on the published Onyx Pro metric list (Neo Health product page and Shop2Fitness detailed table). Includes fat/muscle/water/bone, fat-free mass, visceral fat, BMI/BMR/metabolic age and arm/leg/trunk details. No device connection or automatic estimates added.
- Syntax and simulated partial/segmental entry, weight sync, bounds, rejected-input atomicity and defensive-copy checks passed. Actual browser visual verification remains outstanding.

- Replaced sporter Oefeningen/Basketball/Voeding/Recovery menu entries with Gezondheid & Progress; phone Progress now opens health. Added profile height/weight, manual weight logging/history, edit-profile shortcut and training/PR link. All health data remains in memory and clears on refresh/signout.
- Apple Health explicitly unavailable; direct browser HealthKit access is not implemented and would require a separate iPhone integration decision.
- Three JavaScript syntax checks and simulated weight/profile sync, bounds and defensive-copy checks passed. Actual phone/tablet/desktop browser verification remains outstanding.

### Saved workout editing

- Added coach-library Bewerken and editor update/copy/cancel controls. PUT updates validated workout fields while preserving identity, existing athlete/date and library position. POST still creates copies; session programme snapshots and completed histories remain independent. UI blocks updates of a workout active in this browser.
- All 10 backend tests passed, covering update/reload, no duplication, assignment preservation, invalid/missing targets and HTTP update with history/PR preservation. Changed JavaScript syntax and whitespace checks passed. Browser/device review remains outstanding.

### Short signup profile preview

- Follow-up: account-menu Profiel now opens the same fields for editing. Signup and edits share in-memory values; close discards unsaved edits, save retains them until refresh/signout. No credentials or profile values are persisted to browser storage/server. Both changed scripts passed syntax checks; browser verification remains outstanding.

- Added one optional profile form after simulated registration: birth date, sex, height (cm), weight (kg), goal and experience. Includes continue, skip and back; login/direct demo still bypass it. Native bounds validation includes no future birth dates.
- Profile data is explicitly fictitious, never stored or sent, and cleared when leaving the step. Single-column phone layout and two-column wider layout. Syntax check passed; browser/device verification remains outstanding.

### Mobile bottom navigation

- Added requested phone bottom shortcuts using existing routes: Home/Planning/Workouts/Voortgang for athletes and Home/Planning/Workout maken/Sporters for coaches. Icons have text labels and active page indicators. Desktop sidebar and mobile Menu retained; future Social/AI deferred.
- Added safe-area support, content bottom spacing, theme styling and signed-out hiding. JavaScript syntax passed; actual browser/device visual verification remains outstanding.

### Lesson-to-workout shortcut

- Added Workout toevoegen in lesson editor: validates/saves the lesson, then opens the shared workout editor with its dates selected. Added saved-workout picker and explicit copy loading, with draft replacement confirmation; exercises, rest, tempo and load values remain editable. Original workouts are preserved.
- Syntax checks passed; actual lesson submit handler simulated successfully for four weekly dates and editor handoff. Real browser/device verification remains outstanding.

- Fixed hidden input name=id masking form.id: use getAttribute('id') to route session saves correctly instead of creating malformed shop products. Saving a lesson now clears planning filters and opens the chosen date.
- Verified the actual handler in a simulated DOM environment: new lesson, edit, four weekly repeats, storage handoff, filter reset and invalid times. JavaScript syntax passed; real browser verification remains outstanding. Existing browser data was not altered.

## October 19, 2026 — Rest and tempo

- Added optional rest-between-sets seconds and free-text tempo per exercise to the coach editor, Java model/validation, saved prescriptions, training displays and session programme copies. No rest timer added.
- Preserved existing exercise constructors and optional null defaults for old JSON. Target resolution carries the fields across no/fixed/percentage load modes.
- All nine backend tests passed, including persistence and HTTP snapshot checks; four JavaScript syntax checks passed. Browser/device verification remains outstanding.

## October 19, 2026 — Unified coach workout editor

- Added coach sidebar Workout maken link and one destination selector: library, personal athlete assignment, or multiple scheduled sessions. Sporters and Groepslessen shortcuts select the matching destination.
- Reused Java workout creation; capClub supplies local upcoming session choices and persists copied programme text to chosen dates. Session selection validation and personal-PR-only percentage validation precede saving. Local assignment failures report that the Java workout was saved without claiming session success.
- Responsive checkbox rows reuse touch sizing; no navigation redesign or backend changes. JavaScript syntax and whitespace checks passed; browser/device verification remains outstanding.

## October 19, 2026 — Session programme preview

- Added optional coach-editable programme text to local sessions, expandable previews in Planning/Bookings and booked session cards in Workouts through session end. Cancellation removes the workout card; refresh uses existing browser storage. Older sessions show an empty-programme message, with example content for the demo circuit.
- This is text preview only, without Java completion/set tracking. Subscription grouping and shared session persistence remain future work.
- Validation: JavaScript syntax and diff whitespace checks passed; actual browser/mobile layout and interactive flow remain to be verified by visual review.

## October 19, 2026 — Saved workout deletion

- Added coach-library Dutch delete buttons with confirmation, pending/error feedback and library/assignment refresh. Buttons use existing touch sizing, full width on phones and content width on larger screens.
- Added DELETE endpoint and shared atomic JSON persistence in WorkoutStore. Completed training snapshots and PRs remain intact. UI blocks deleting the workout active in this browser; this local preview guard does not cover other devices.
- Validation: all eight backend tests passed, including persisted deletion and HTTP history/PR preservation; both changed JavaScript files passed syntax checks. Browser/device verification remains outstanding. No commit or push.

## September 30, 2026 — Java 25 environment repair

- Switched from `main` to the requested `appmod/java-upgrade-20260930170153` branch; its POM already targets Java 25. Preserved the existing interactive build-update preference; backed up local settings under ignored `backups/java-configuration/`.
- Confirmed the upgrade-installed Microsoft JDK 25.0.2 exists. Removed the stale workspace Java 21 path, registered valid JDK 11/17/21/25 locations in VS Code user settings, and configured CAP terminals through user `JAVA25_HOME`. Windows JAVA_HOME/PATH and the Java 21 default for unmanaged projects remain unchanged.
- Left optional null analysis unchanged and separate from runtime selection. Updated README setup instructions. VS Code must fully restart and open a fresh terminal to inherit the new variable; editor diagnostics were not visually rechecked.
- Validation: Maven Wrapper 3.9.16 used Java 25.0.2; `backend/mvnw.cmd clean test` passed all 7 tests. `spring-boot:run` started on port 8080 and `/api/health` returned `status: ok`. Stopped the verification process tree and confirmed no listener remained on port 8080. No commit, push, or merge.

## September 29, 2026 — Initial project-memory baseline

- Inspected frontend HTML/CSS/JavaScript, Spring Boot code/config/tests, earlier `src/`, README, AGENTS.md, documentation and existing brain files.
- Current state: local prototype with API-backed workouts, example-athlete assignment, timed completion, saved history and exact-rep records. Planning/bookings/shop remain browser previews; real accounts, payments and database storage are absent.
- Corrected PROJECT.md scope: basketball is excluded by the master plan despite residual frontend entries. Distinguished working features from future nutrition/recovery/AI ideas.
- Recorded architecture, evidenced decisions, technologies/concepts and separate current-gap/documented-plan lists. No user-mastery claims or invented rationales.
- Verification: source inspection only. Seven backend tests exist; the September 27 handoff reports passing tests and JavaScript syntax checks. Tests were not rerun here; browser/device behavior was not verified.
- Changed only the six brain files. Pre-existing `backend/.gitignore` changes and untracked `.obsidian/` and `docs/TODO.md` were left untouched. No application code changed.

## October 20, 2026 — Coach Home tiles
- Matched athlete Home with seven existing coach destinations using shared responsive tile styles and CAP heading logo. Removed large coach cards and dynamically injected overview/duplicate quick links.
- JavaScript syntax, supported-route and whitespace checks passed. Actual phone/tablet/desktop browser rendering remains for visual review. No backend changes.

- Coach Home icon fix: auth-preview.js used link.textContent during access/role updates, deleting the tile SVG and caption. Both update paths now share label-only updates that preserve nested markup. Syntax and simulated allowed/locked/repeated label checks passed; live browser verification unavailable.

## October 20, 2026 — Category-first workout creation
- Added seven category tiles before the existing editor, with Categorie wijzigen and existing saved-library header link retained. Uses shared two/three-column responsive tiles. Draft exercises survive category changes; session links reset. Editing and athlete/lesson shortcuts retain direct editor access.
- Syntax, whitespace and simulated seven-category prefills, chooser/editor transitions and cleared session-link checks passed. Actual phone/tablet/desktop browser verification remains outstanding. No backend/category-schema changes.

## October 20, 2026 — Saved library category tiles
- Replaced category filter buttons with Home-style tiles/counts, followed by searchable compact lists and retained workout-type filters. Added Alle workouts and Alle categorieën navigation. Existing edit/assign/delete handlers remain attached to rows.
- Syntax, whitespace and simulated category/search/type combinations, empty state, counts and chooser-return checks passed. Actual browser/device visual verification remains outstanding.

## October 20, 2026 — Coach health and progress
- Added coach-only page/Home tile/sidebar entry with ten-name searchable roster, selected-athlete identity, six compact metrics with dates/neutral changes, full body/segmental measurements, manual entry and expandable histories. Training section shows actual Guillaume sessions/sets/PRs; other athletes use unconnected empty states. Session-only measurement isolation and shared Guillaume capProfile, cleared on signout.
- Syntax/DOM references/whitespace and simulated ten-athlete roster/search, measurement isolation, invalid-input atomicity, stale-response protection, shared-profile writes, training/PR rendering, offline feedback and signout reset passed. Actual browser/device visual verification unavailable; user review remains next. No backend changes.

## October 22, 2026 — Coach exercise workbook integration
- Imported all 1,100 exercise rows and 14 source columns, unchanged names, overview cells and source provenance through a repeatable standard-library Python importer. Replaced starter catalog with 1,078 distinct picker choices while preserving every original row. Added token search, dependent collapsed region/category/subcategory filters, eight-at-a-time paging and collapsed source details. Kept custom entries, existing prescriptions, supersets and backend format; nutrition workbooks untouched.
- Independent workbook comparison passed for every imported cell. All frontend syntax/load-order checks and simulated all-row reachability, multiword/symbol search, combined filters, paging, exact selection, metadata, custom names and keyboard checks passed. Phone/tablet CSS uses in-flow results with touch-sized controls; actual visual verification blocked by computer-use URL verification. No commit/push.

### Exercise library and simpler workout picker
- Added coach-only Oefeningen sidebar route, full alphabetical catalog with search/24-row disclosure and optional source details. Add buttons append exact catalog names to the current draft without navigating or saving; toolbar shows current title/edit mode/count and opens the editor. Added library shortcut inside Create Workout and removed the nested picker filters.
- Frontend syntax/whitespace and simulated library browsing/search/paging/empty states, draft feedback, blank-row reuse, preservation of existing supersets/title/edit identity, invalid-ID/role/100-row guards and coach-route checks passed. Actual phone/tablet/desktop browser verification remains outstanding. No backend changes, commit or push.

### Body-focus-first workout creation
- Added Lower/Upper/Full body first-step tiles and change-type controls before category/editor flow. Existing workoutType is prefilled, required and saved without a second selection; copy loading preserves the chosen type, saved edits retain their stored type. Saved library exposes matching sections/counts across groups, with old types still accessible.
- All frontend syntax/whitespace and simulated first-step/prefill/category retention/type changes/library guard/legacy edit, three saved-section counts/filtering/search/legacy access checks passed. All 10 WorkoutStoreTests passed, including workoutType persistence. Actual phone/tablet/desktop visual verification remains outstanding.

### Empty editor, compact cards and body-scoped search
- Replaced automatic first exercise row with a + tile; last row is removable. Cards expose selection/sets/reps, with weight/rest/tempo under details. Shared roomy dialog replaces per-card dropdowns; includes one search, body-specific query shortcuts, 12-result disclosure and custom names. Editor and library search/additions obey workbook region labels for upper/lower; Full body includes both. Changed-focus drafts retain rows and show mismatch notes.
- All frontend syntax/whitespace and simulated body scoping/no cross-region results, dialog selection/custom-name/mismatch checks, empty/unnamed submit guards and original prescription payload checks passed. Actual browser/device layout review remains outstanding. No backend changes or push.

### Planning readability and controls
- Found older desktop agenda grid targeted article placement while current lesson rows are details; this squeezed alternating rows into an 11rem column. Scoped planning day sections now stack full-width rows. Week columns auto-fit readable widths; improved date/control layout, explicit Dag/Week/Lijst, day/total counts and capacity labels. Booking/storage rules unchanged.
- JavaScript syntax/whitespace and simulated 18-slot day, seven-day week/list, cancelled/ended exclusions, trainer/activity filters, selected-view states, counts and date boundaries passed. Actual phone/tablet/desktop browser visual verification remains outstanding. No dependency added; optional CSS parser unavailable.

### Nutrition calculator and per-athlete day targets
- Added coach Voeding sidebar/Home page, searchable example-athlete selector, six main calculator fields and advanced strategy/scaling options; editable calorie/protein/carbohydrate/fat/water targets and calculation details. Pure calculation module follows the supplied Smart Macro Planning Tool. Defaults avoid copying its personal example inputs. Session-only drafts/targets are separate by athlete; stale calculations cannot be saved without recalculation or explicit manual mode. Athlete Voeding displays only Guillaume's saved targets. Meal planner/food diary and backend changes deferred.
- Workbook cached-reference comparison, 60 sex/activity/strategy energy reconciliations, invalid/missing/negative cases and all frontend syntax passed. Repeatable tools/test_nutrition.mjs added. Simulated ten-athlete draft/save isolation, Guillaume-only athlete view, manual mode, stale-input protection, signout reset, DOM IDs/references and role routing passed. Actual browser/device visual verification remains outstanding. No commit/push.


### Athlete food logging
- Added workbook food importer/catalog, compact add-food/custom-portion forms, per-day meal entries and calorie/macro totals compared to current coach targets. Added removal/undo and coach selected-athlete read view. Incomplete source food values remain unselectable rather than assumed zero.
- Repeatable test_food_diary.mjs passed portion/date validation, add/delete/undo, date and athlete isolation, role guard and signout reset; all frontend JavaScript syntax and git whitespace checks passed. Responsive CSS uses two/four metric columns, wrapping rows and 44px controls. Actual browser/device visual verification remains outstanding. Entries/targets remain session-only.


### Compact intake bars and reusable meals
- Reduced daily bar thickness, padding, font sizes and spacing. Added saved-meal panel and per-meal save form; recipe snapshots preserve amounts and nutrition for reuse on another date. Ingredient inspection and template deletion included.
- Simulated save/reuse on another date, diary totals, delete/undo, athlete isolation, role guards and signout reset passed in tools/test_food_diary.mjs. Whitespace checks passed. Actual phone/tablet/desktop visual review remains outstanding.
# October 23 — Coach athlete actions

Guillaume's Sporters card now offers Resultaten bekijken, Workouts bekijken (dated assignment previews in a dialog), and Workout toewijzen (opens the existing saved library and assignment flow). Actions wrap with phone-sized touch targets. Other roster entries remain examples; real multi-athlete assignment is still future work. Browser layout verification remains pending.


October 23 refinement: Athlete Workout toewijzen opens all saved workouts in direct-selection mode. Clicking a workout name posts today's Brussels-date assignment to Guillaume and returns to Sporters with confirmation. Regular library browsing retains preview/edit/date assignment controls. Browser verification pending.

## October 23, 2026 — Persistence and preview session

Fixed reload resetting preview login/role; added browser-local profile, measurements, nutrition, food diary and meal persistence. Explicit logout retains records. Added visible storage-error reporting and Live Server backend/build ignores. Updated Dutch storage descriptions. Syntax and tools/test_preview_storage.mjs checks pass. Actual browser assignment/refresh verification pending; restart Live Server for ignore settings.

## October 23 — Athlete workout management

Added compact edit/delete/note controls in Guillaume workout review. Assignment-specific backend endpoints persist snapshot edits, deletion and notes; editor returns to athlete review. All 17 backend tests pass including store persistence/isolation and HTTP edit/note/delete/history preservation; frontend scripts parse. Browser layout/interaction verification pending.

October 23 screenshot correction: workout review modal now has a separate whole-workout removal panel above exercises, with explicit scope. Each exercise has its own small removal action using the assignment PUT; lone remaining superset members become standalone. Last exercise cannot silently delete a workout; it directs to whole-workout removal. JavaScript syntax and index-preserving renderer check pass; visual review pending.

## October 23 — Weekly and daily workout planning

Replaced automatic-today athlete shortcut with shared agenda; coach can assign a flexible week or fixed day. Creation uses the same picker; group assignment filters actual sessions by day/week. Athlete Workouts browses weeks and supports choosing/changing/clearing a flexible workout day. Backend persists weekly metadata, stable assignment IDs and completion reference; completed assignments cannot be rescheduled. Existing daily assignments retained. All 18 backend tests, agenda unit checks and JavaScript syntax checks pass. Browser UI unavailable (no registered browsers; in-app browser unavailable), so visual verification remains pending. Restarted Spring Boot successfully.




October 24: User requested undoing all three exercise UI steps. Restored the standalone Oefeningen sidebar page and original searchable catalog/add-to-draft flow; removed embedded panel, new filters and dropdown. JavaScript syntax checks passed; browser verification pending.

October 24 final implementation: Replaced exercise sidebar entry with a Create Workout button linking to the separate exercise page. Added dependent Excel filters and matching dropdown while retaining searchable detail list and add-to-draft behavior. Node VM interaction checks passed for Lower Body/Squat (62 choices), selecting/adding and empty-result handling; JavaScript syntax and whitespace checks passed. Responsive controls stack below 650px. Actual phone/tablet/desktop browser review remains pending.

October 24 autocomplete: Added filtered clickable suggestions below exercise search. Node VM checks passed for typing, suggestion selection and adding to draft; syntax and whitespace checks passed. Actual browser/mobile review pending.

October 24 creation flow fix: Changed initial and default body-focus destination from category chooser to editor after user reported being unable to reach creation. Explicit category changes remain supported. JavaScript syntax and VM click checks pass for all three body types; actual browser verification pending.

October 24 editor startup repair: Removed an obsolete event binding to the absent athlete Create Workout link (athlete assignment now uses Saved Workouts). The null dereference stopped initialization before +, save and draft API setup. Full-script Node VM DOM harness confirms initialization with the retired link absent, + creates a row/opens picker, and submit/draft handlers exist. Syntax and whitespace checks pass; actual browser verification pending.

October 24 named workout flow: Moved title out of save dialog to above exercise-page button. Added shared nonblank-name guard for page links, +, submit and catalog additions. Exercise page Add controls explain missing names and retain the same named draft. Syntax/whitespace and VM name-validation checks passed; actual browser verification pending.

October 24 per-set progression: Added backward-compatible setPlan model, exact-rep target resolution, persistence and optional coach set editor. Updated workout, training/history and group previews; athletes still enter actual kg/reps. All 19 backend tests pass, including new mixed per-set percentage/fixed/free targets, frozen references, persistence and independent actual weights. Node VM checks cover edit/load/serialization and set-count growth; modified scripts parse and whitespace checks pass. Browser layout review remains pending.

## October 26 — Workout navigation cleanup
- Replaced duplicate exercise-library links and + entry with one labelled Oefening toevoegen button opening the existing exercise page. Return link says Terug naar mijn workout; draft summary shows name, body type, count and unsaved status. Existing row picker/superset behavior retained.
- Existing save button stays above bottom navigation at <=800px with content clearance. Desktop save placement unchanged.
- Browser checks passed for add/return draft preservation and save dialog open/cancel. Layout geometry checked at 390/768/1440px, with phone/desktop screenshots inspected; no horizontal overflow or save/nav overlap. JavaScript syntax and whitespace checks passed. No workout saved during verification. User visual review remains next.

October 26 clarification: user prefers the shared exercise-picker popup for initial and subsequent additions, matching supersets. Oefening toevoegen now calls the existing addExercise() flow; updated helper text. Browser verified initial/additional picker, selection and superset picker; no backend changes.

## October 26 — Persistent coach exercise library
- Added shared Oefeningen management with independent add/edit forms, active/archive browsing and archive/restore. Uses the same live catalog as the existing workout popup. Saved workout snapshots, history and name-based PRs remain unchanged; renamed definitions do not migrate old PR names.
- Java ExerciseStore/Controller load 1,078 bundled definitions and persist only additions/overrides atomically in data/exercises.json. Stable IDs, duplicate validation, bounded fields, recoverable error feedback. Existing preview authorization limitations remain. No new dependency or hosting service.
- Added reproducible export_exercise_seed.mjs. Browser searches use one session cache and paged rows; verified typing makes zero API calls. Manager refreshes on entry; successful mutations update the popup immediately.
- Validation: all 24 backend tests passed; store/API checks cover persistence, archive/restore, duplicates, missing/invalid region, failed-write atomicity and saved-workout-name preservation. Browser checks covered create/edit/reload, archive exclusion/restore, popup selection, duplicate/offline input preservation. Layout measured at 390/768/1440px; inspected light desktop and dark phone screenshots, with 16px inputs and 44px targets. Syntax/whitespace checks passed. Temporary port-8081 backend used separate target-directory data and was stopped; user's data unchanged. User review and backend restart on port 8080 remain next.

## October 27 — Group month calendar, first review step
- Replaced Groepslessen placeholders with a responsive Monday-first monthly calendar, month arrows/current-month reset, day counts, date-prefilled group-session dialog and selected-day details using existing booking/session controls. UTC arithmetic and Brussels today; keyboard arrows supported. Past sessions are view-only in this screen.
- Reused existing 1/4/8-week repetition and workout-editor handoff. Removed obsolete group-card editor binding; reset blank agenda dates and retain all repeat-session targets across week filters. Custom end-date/series editing and direct in-dialog saved-workout selection remain future steps.
- Browser verified creation/reload, four weekly dates across months, isolated single-session editing, Jan/Dec navigation, leap February, keyboard arrows, past-date restrictions and invalid end times. Exercised editor selection and all-four-session workout snapshot attachment with a mocked workout POST (no backend workout writes); removed browser test sessions afterward. Geometry checked at 390/768/1440px, inspected desktop light/phone dark screenshots. Initial handoff errors corrected and fresh browser reported no console errors. Agenda/storage unit checks, modified JS syntax and whitespace checks passed. No backend change; user's unrelated docs edit left untouched.

October 27 cleanup: removed unreferenced old inline exercise-dropdown/help/results CSS, retired coach-card styles and selector branches, and the unreachable afterTypeChoice path. Kept live category navigation, legacy console project and Java compatibility constructors. Browser checks passed for all body choices/category changes, exercise/superset popups and calendar; no console errors or overflow at 390/768/1440px. JavaScript syntax/reference/whitespace checks passed; no backend or data changes. Unrelated docs edit preserved.

## October 27 — Belgian date/time presentation
- Added shared date-time.js formatters/parsers and date-inputs.js controls. Full dates display dd/mm/yyyy; timestamp times use Europe/Brussels and HH:mm with DST. Replaced local-device history/health formatting and raw ISO assignment labels. ISO dates/UTC timestamps in storage/API remain unchanged.
- Visible date/time fields wrap native ISO-valued originals, retain FormData/validation and synchronize programmatic updates, constraints, disabled states and resets; new inputs are enhanced automatically. Native calendar button retained; HH:mm text avoids locale-dependent AM/PM fields. No dependencies or backend changes.
- Unit checks cover numeric dates, invalid dates/times, midnight and both Belgian DST transitions. Browser checks in en-US/America-Los_Angeles verified displayed dates/times, ISO session saves, max-date/optional-invalid validation, programmatic updates and disabled controls. Dynamic external-form inputs/serialization/reset passed. Geometry at 390/768/1440px showed no overflow; inspected phone/desktop screenshots (16px fields, 44px calendar buttons). Initial stale food-diary test lacked current capStorage and expected signout deletion; updated harness to actual storage/retention behavior, now passes. Other agenda/storage/nutrition checks and all JS syntax passed. Browser test data used an isolated context, then closed. User's unrelated docs edit preserved.

## October 27 — One calendar entry in Planning
- Removed the redundant Groepslessen sidebar/Home tile/route. Planning Les toevoegen now opens the renamed planning-calendar.js month dialog, showing all session types and reusing the dated form for Groep, Privé and Open gym.
- Swaps dialogs instead of stacking: cancelling/X/Escape in the form returns to the calendar, saving returns to Planning, and closing the calendar restores Les toevoegen focus. Preserved full-cell clicks, existing session details, Belgian date/time fields, Open gym restrictions and cross-week workout handoffs. Retired group-only API names/selectors.
- Browser checks in an isolated context passed creation of all three types, four repeated Open gym slots, hidden coach/workout fields/capacity 7, correct return after saving, and four selected workout targets. X/Escape/full-cell checks passed; phone/tablet/desktop geometry and light phone/dark desktop screenshots inspected. Phone day targets exceed 44px after native-dialog max-width adjustment; no horizontal overflow. All frontend syntax and date/agenda/storage/diary/nutrition checks passed. No backend changes or user data writes; unrelated docs edit preserved.

October 27: Added single CAP activity dropdown and searchable private-session recipients with automatic bookings. Shared preview visibility filters all session surfaces; no new service/dependency. Policy and existing frontend checks pass; browser save flow and overflow checked at 390/768/1440px. Real cross-account privacy still requires backend authorization.

October 27: Promoted the existing monthly calendar to main Planning for both roles. Removed calendar popup, obsolete day/week/list controls and their JS. Reused private-aware session queries and booking/edit forms. Syntax/frontend regression checks passed; browser verified initial Today, selected-date creation/save, month navigation, private exclusion from counts/details and no overflow at 390/768/1440px. Desktop and phone visually inspected.

October 27: Added isolated PostgreSQL account foundation (Security/JDBC/Flyway), schema, environment-based first coach, expiring/reissuable single-use invitation tokens, password hashing, session login/logout and CSRF/role enforcement. Legacy APIs denied in accounts profile, preview preserved. Added HTTP tests for authentication/CSRF/roles/expiry/reuse/reissue; 26 backend tests pass. Added native PostgreSQL and optional Compose setup docs; user confirmed neither database nor Docker installed, so actual PostgreSQL verification and frontend connection remain next. No credentials committed; notes.txt and existing user session document left untouched.

October 27: Added Dutch backend-served account page for cookie login/logout, coach invite links and athlete-owned password creation; linked from preview entry. Reuses CSS/theme sources via Maven resources. Fixed same-page invitation hash navigation found in browser testing. 26 backend tests pass including public account assets. Isolated browser tests verify wrong login, invite acceptance, role visibility, session reload and 390/768/1440px overflow; desktop and phone visually inspected. Main app data migration remains next; user must restart existing account backend to load security/resources.

October 27: Connected real-account core through V2?V4 migrations, account-aware controllers/services and same-origin Planning/workouts/exercise modules. Preserved preview constructors/profile while sharing validation, percentage resolution and PR rules through StateWriter. Added private isolation, last-slot race, actor-owned assignments/history, frozen/versioned drafts and catalog tests. All 32 backend tests passed both H2 and isolated native PostgreSQL 18; restarted test cluster and verified retained account rows. Browser end-to-end coach invitation/athlete signup/lesson+workout creation/booking/training autosave/reload/completion/PR passed; phone/tablet/desktop no overflow and visual review. Test cluster created only under target on 55433, not user CAP database. Old notes/session doc left untouched; no credentials committed.

October 27: Replaced separate real-account workspace with original CAP app at /app/index.html. Preserved existing visual design/assets; real identity/roster/invitations, club mutations, workout assignment and server drafts now feed original UI. Deleted superseded account task JS, retained login/acceptance. Verified 32 tests and browser coach/athlete flow including saved set recovery and history/PR completion; no layout overflow at phone/tablet/desktop. User database/server untouched; used isolated test server8082. User must restart8081 to load changes.

October 27 roster refresh fix: Coach lists previously stayed at login-time bootstrap data after invitation acceptance. Refresh actual athletes on window focus, relevant navigation and before assignment/save dialogs; update Sporters, assignment/editor dropdowns and private-session checkboxes in place. Preserve selected athletes and unsaved workout fields; coalesce concurrent reads, no polling. Browser reproduced account creation while coach editor stayed open, verified draft preserved and new athlete selectable/assignable.

October 27: Restored + exercise button with accessible label and existing picker. Replaced read-only real profile with original editable fields plus photo add/remove and top-right close. V5 profile persistence, self-only endpoints, validated image limits/re-encoding; no dependencies. 33 backend tests pass; profile account/ownership/photo tests also checked against isolated PostgreSQL. Browser verified field/photo save/reload/remove, avatar update, + picker and 390/768/1440px layouts. User database/server untouched.

October 27 end-to-end audit: preserved unrelated notes/session document. User startup output confirms PostgreSQL18.6 localhost5432/cap rolecap, public schema V5; failure was duplicate CAP process on8081, not database. Added per-launch .cmd/.ps1 launcher, safe errors/logging, original branded login layout, PostgreSQL-only test wrapper and pgAdmin/SELECT/manual guide. Full35 tests passed on isolated PostgreSQL55433 including direct API ownership/invitations/logout/demo isolation and backend-context restart. Separate actual JVM restart retained fake coach/athlete, workout and assignment; old sessions401, re-login works. PostgreSQL browser invite/create/assign flow passed; login390/768/1440 checked. Existing local data untouched by audit; final user8081 restart remains manual.

October 27: Implemented shared nutrition targets in PostgreSQL V6 with coach-only writes and self-only athlete reads; preserved existing calculator/layout and local preview/drafts. Server timestamps/real roster, async load/save status, no automatic draft publication. Fixed overly broad club form-submit listener causing false unconnected banners on nutrition saves. Full36 tests passed against isolated PostgreSQL; browser verified manual and calculated saves, separate athlete context, updates, reload and responsive layouts. User database/backend untouched; restart needed for V6.

October 27 nutrition diagnosis: user coach confirmation explicitly said in deze preview, confirming browser-local preview save while checking a real athlete account. Directed coach to 8081/app/index.html#coach-nutrition and explicit re-save. Made preview nutrition notice/confirmation unambiguous and added direct real-account link. No automatic demo-to-real ownership/data import.

October 27: Local accounts launcher now enables local-frontend, serving app assets and the authenticated index directly from the checkout without caching. Frontend edits need browser refresh only; Java/config and separate accounts login assets still require restart. Packaged accounts mode retains bundled files. Eleven HTTP/account checks passed using isolated H2 test data, including edits without restart, account injection, guest redirect and existing permission checks. No PostgreSQL changes; user backend left running.

October 27: Connected original food diary to PostgreSQL V7; athlete-only add/delete/undo and self reads, coach-selected read-only day view. Confirmed server saves, load/error statuses and stale response guards; account-scoped local recipes and separate preview. All 44 backend tests passed against isolated PostgreSQL, including direct API privacy/CSRF/logout/idempotency and diary restart persistence; preview and real-mode Node diary checks passed. Separate real browser sessions verified add/reload/coach view/delete/undo and saved-meal logging; no horizontal overflow at 390/768/1440px. User database/backend untouched; one restart needed for V7.

October 27 development workflow: Added opt-in Maven dev DevTools dependency, VS Code auto-build/Standard import, CAP start task and Java extension recommendation. Launcher includes -Pdev; app plus login/shared assets served directly with no-store in local-frontend. Verified source-only health edit compiled automatically by the running VS Code Java language server and DevTools restarted; API changed, restoration restarted again, no manual compile. Temporary HTML titles changed/restored on refresh without additional backend start; cache headers verified. Thirteen account/frontend tests passed on isolated PostgreSQL with DevTools present; normal Maven dependency tree and packaged jar both exclude DevTools. Docs/development.md explains commands, restart/session behavior and troubleshooting. User backend/DB untouched.

October 27 Stripe TEST billing: implemented V8 customer/subscription/event ledger, session-owned Checkout/portal/reconcile and raw HMAC webhook verification. Durable attempt keys/owner locks, duplicate/out-of-order event handling via canonical Stripe reads, centralized placeholder EUR10 monthly paid-active rules; no existing feature gates or role changes. Added responsive original-account-menu billing panel, success/cancel/pending/error states, private-prompt test launcher and setup/SQL/business-decision guide. Final full55 tests passed on isolated PostgreSQL with StripeGateway mocked; restart ledger fixtures retained. Browser checked original real login/unconfigured state, mock success/pending/cancel-at-end/error responses and 390/768/1440 layouts, light/dark screenshots, Escape/focus return and reload retry. No Stripe account/CLI available (user confirmed); no provider objects/requests/payments performed, actual Checkout/portal/3DS/renewals remain blocked until setup. Initial fake customer ID collision fixed; final suite has no failures. User notes/session document preserved.

Stripe final review: status reconciliation before any purchase no longer creates an unused customer attempt/retry window. Added regression assertion; all11 focused billing/account PostgreSQL tests passed again.

October 28 Stripe setup: read guide and checked local state without displaying credentials. PostgreSQL18 running, project JDK25 present, CAP8081 stopped; no inspected Stripe CLI login files or CAP Stripe process/user variables. Installed official @stripe/cli globally and verified stripe.cmd version1.55.3 plus forwarding/portal options. PowerShell policy Restricted resolves bare stripe to blocked stripe.ps1; updated Windows guide to stripe.cmd without changing policy. Asked user to sign in/create account and report dashboard/onboarding state. No Stripe authentication, objects or payments performed yet.

October28 actual Stripe sandbox verification: user created EUR10 monthly test price, saved period-end portal, configured private prompts and completed one test-card Checkout. CLI read-only checks confirmed paid/complete, test mode, active subscription and matching CAP account reference. CAP initially none because forwarding was absent. User started listener/restarted backend with listener whsec; agent changed only a harmless test metadata marker on the existing matching active subscription to emit a real subscription.updated event (no price/terms/new purchase). User confirmed CAP Status Actief/Testlidmaatschap Actief without reconciliation. Provider paid Checkout and webhook recovery now verified; original checkout event missed, declines/3DS/portal/renewals still unverified. No secrets/IDs recorded.

October28: Checkout and portal now open a detached new tab synchronously before their async link request, keeping CAP open and avoiding popup blockers after await. Blocked popups make no server request; errors/foreign URLs close the temporary tab. Focus/visibility return debounces one local billing GET, never automatic reconciliation. Browser fixtures verified desktop and phone-emulated tab behavior, detached opener, blocked/error paths, repeated clicks (one POST) and active-status refresh (one GET). JavaScript syntax/diff checks pass; no provider purchases or backend changes. Refresh CAP to load the frontend edit.

October28: Added the same Stripe test membership to real-account Shop using its existing club-card/grid and category filter. Billing API supplies price/current account status; card opens the shared billing dialog, with no second product or payment path. Club render hook restores one card after list rebuilds; Shop navigation/focus refresh local status. Mock browser checks cover inactive/active/error, category exclusion, no duplicates, shared dialog and 390/768/1440px overflow. JS syntax checks pass; no backend changes or Stripe transactions.

October28 user confirmed Stripe test portal opens the matching EUR10/month subscription in a new tab. User scheduled period-end cancellation and confirmed CAP retains active status, planned cancellation and access until 28/11/2026. Actual expiry/renewals/declines/3DS remain unverified. User requested pushing all remaining files, including notes.txt.

October 29: Implemented confirmed five memberships/four credit packs in existing Shop and Stripe TEST backend. V9 orders/credit rows, paid canonical Checkout fulfilment, 12-month subscription cancel_at, EUR75 paid-month-end cancellation and EUR65 rejection, restricted portal, group-booking membership/credit checks with pre-start refund. Isolated PostgreSQL final regression: 74 tests passed; includes owner/CSRF, duplicate/lost events, prepaid expiry, fixed/flexible terms, last-credit concurrency and restart retention. Browser fixtures checked 390/768/1440 light/dark, filters, selection, separate Checkout tab and cancellation display; corrected native select touch size. Created/reused nine real sandbox prices plus separate restricted portal through CLI, rerun kept identical IDs. No payments/customers created during setup; new-plan paid provider flows still await user tests. Added docs/memberships-test.md and launcher loading ignored local ID map; no secrets saved. Coach emails still missing. Initial test DB start omitted test port, corrected to 55433; initial PowerShell CLI notices/query argument handling fixed; final checks pass.

October 29 manual provider result: user started catalog test mode, completed the 5-credit sandbox purchase and confirmed CAP displayed 5. Coach-created upcoming group lesson appeared in the athlete account. User explicitly confirmed booking spent one credit (5 -> 4) and cancelling before start restored it (4 -> 5). New memberships/other packs/renewals/expiry remain unverified; no additional transactions performed by agent.

October 29: User confirmed EUR75 membership purchase/cancellation worked, then requested Planning balance and Open gym credit use. Implemented athlete-only balance/Shop link, refresh on successful booking/cancellation and navigation/focus, safe loading/retry. Group/Open gym share credit/membership access; edits preserve reservations, private conversion returns them. 23 focused tests passed on isolated PostgreSQL. Real test-browser Open gym booking/cancellation showed 5 -> 4 -> 5 without refresh using fake SQL credit fixture (no provider calls). Phone/tablet/desktop both themes and error/retry checked. User's cap DB/account data untouched by verification.

October 29: Implemented encrypted development credential reuse after user complained about repeated keys. First-run private prompts remember API/webhook/validated DB password locally; subsequent starts reuse, with refresh/update-webhook/forget switches and no legacy-price prompt. Added short forwarding command and VS Code tasks, updated startup docs. Fake-only Windows tests passed: encrypted XML/no plaintext, restricted ACL, cross-process reuse, full Stripe launcher with no prompts and stubbed backend, env restoration, live-key rejection/trim/retry, safe corrupt-file rejection and forgetting. No actual user credentials read/saved by agent; user must enter once on next start. No Java/runtime/backend/database changes.

October 29: Added coach Sporters membership/credits panel and minimal read-only API. 21 focused PostgreSQL tests passed including guest/athlete denials, coach/nonexistent targets, forbidden writes, logout, no Stripe calls/no customer creation and omitted provider identifiers. Browser fake SQL records verified active prepaid plus credits, empty athlete, late-response switching, error/retry, both themes at 390/768/1440. Removed misleading preview copy in real coach mode only. No production/dev cap data touched by checks. Docker is the user's next requested task; docker executable was not found in current PATH.

October 29: User corrected membership review to two coaches and selected Glenn/Dieter. Implemented deny-by-default two-login allowlist and server/UI capability checks. Actual login emails unavailable, so no real account grant configured. 24 focused tests passed (PostgreSQL integration + policy unit tests). Browser fake allowed coach displayed records; ordinary coach with Glenn's display name had no panel/no automatic requests, API403, normal workouts/roster200. Default/max-two/role/identity checks verified. Docker remains next; email linking can wait.

October 30: User supplied IMG_5041/5042 staff roster screenshots. Transcribed displayed names, emails and role labels into notes.txt, preserving Marlies's differently spelled email, correcting Vince's surname, and marking the cropped marketing name as unconfirmed. Configured ignored .local/membership-reviewers.json for Glenn/Dieter using supplied addresses; JSON/exact-two/email validation and Git ignore check passed. No accounts, invitations, passwords, database changes or new role grants created. Source-system Admin/Manager/Assistent/Trainer/Marketing/Accountant labels are reference until CAP permissions are defined. Reviewer permission applies to matching COACH accounts after launcher restart. Docker remains next.

October 30: Implemented persistent shared measurements (V12) and profile synchronization for athlete/coach accounts. Fifteen focused tests passed on isolated PostgreSQL, including authorization/CSRF/validation/logout and duplication boundaries. Fake-account browser checks verified own profile/log/coach cross-account synchronization, own coach values, and layouts at 390/768/1440 in existing themes. Docker image rebuilt/applied; native cap existing account/password/workout/profile fingerprints unchanged, V12 applied with no fabricated historical rows. Actual health records not modified during QA. Documentation: docs/measurements.md.

October 30: Simplified coach sidebar with collapsible Meer containing colleagues, authorized finance, saved workouts and exercises. Native keyboard/touch disclosure, current child highlighted, deep links open the group. Browser preview checks passed at 390/768/1440 without horizontal overflow; athlete role has no Meer group. Existing backend permissions untouched. Local Docker rebuilt for review.
