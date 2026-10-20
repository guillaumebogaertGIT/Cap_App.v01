# Session Log

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
