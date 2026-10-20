# Session Log

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
