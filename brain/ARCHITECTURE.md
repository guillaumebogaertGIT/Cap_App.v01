# Current Architecture

Reviewed: September 29, 2026.

## Browser application

`frontend/index.html` is the static shell; scripts also create screens/dialogs. There is no frontend framework or build pipeline.

- CSS loads as `style.css` (base components/themes/responsive rules), `polish.css` (refinements/overrides), then `auth.css` (preview entry).
- `navigation.js`: hash routes, section visibility, preview roles, desktop sidebar and mobile Menu/bottom shortcuts (800px breakpoint). Bottom navigation switches links by role, shares active-route styling and hides on signed-out screens. CSS reserves content space and iPhone safe-area padding.
- `auth-preview.js` / `account-menu.js`: simulated entry, plan checks and account dialogs; credentials are not transmitted or saved. Signup opens a short optional profile form (birth date, sex, height, weight, goal, experience). Submitted profile values stay in memory for account-menu Profiel editing; refreshing/signing out clears them. No browser storage or server profile persistence. The profile dialog clones the signup fields to share validation/layout.
- `coach-workouts.js`: shared editor and workout POST; exercise-data.js and exercise-picker.js supply the imported coach catalog and compact picker. Destinations: library only, example athlete today (Java), or multiple scheduled sessions (local preview through `capClub`).
- `workouts-api.js`: workout GET, libraries and today's Brussels-date assignment.
- Athlete Home is a compact six-tile menu (Workouts, Planning, Boekingen, Gezondheid & Progress, Nutrition, Shop). Assignment availability is a tile caption; full workouts remain in Workouts. Nutrition is an explicit placeholder route, not implemented food tracking. Club preview updates Home booking count without inserting the previous overview panels.
- Coach `saved-workouts` route hosts compact expandable library entries (edit/assign/delete); saving opens this view. Athlete Bekijk workout opens a modal instead of redirecting to an assignment-hidden Home selection.
- `workout-view.js` shares index-preserving superset blocks across workout previews and training entry. Blocks A/B/C render as sections instead of inline labels; logged exercise indexes still refer to the original plan.
- Athlete Workouts prioritizes upcoming booked sessions in expandable rows (six initially). Mijn persoonlijke workouts lists only today's assignment to the example athlete; the shared library remains in coach view. Cancellation removes booked-session entries without deleting coach templates. No data is deleted by filtering.
- `training.js`: draft/timer, actual sets, completion POST, history and records.
- `health.js`: sporter Gezondheid & Progress screen; profile height/current weight, manual timestamped weight history, profile editing and training history link. Shares session-only `capProfile` values with signup/profile. Apple Health is an unavailable integration placeholder, not a working connector.

Health also supports optional manual body-composition snapshots: Onyx Pro total metrics and segmental fat/fat-free/muscle metrics for arms, legs and trunk. Metadata defines fields, units and input bounds. `capProfile.logMeasurement` validates before inserting; included weight updates the profile/weight log. Body history is session-only and cleared on signout, without scale syncing or clinical interpretation.
- `club.js`: local scheduling, editable session programme text/previews, bookings, attendance, products and dashboard summaries. Active upcoming/in-progress bookings derive a session list in Workouts; cancellation removes entries without changing Java workouts.
- `theme.js` / `backend-status.js`: theme preference and health check.

Scripts communicate through globals (`previewRole`, `capAccess`, `capTraining`), DOM and custom events (`cap:viewchange`, `cap:workouts-saved`). HTML script order matters.

`capClub.sessions()` supplies upcoming session choices; `capClub.assignWorkout()` copies a saved workout into session programme text and persists local planning before updating the UI. Session programmes are independent snapshots, without backend session assignment or set tracking.

Planning defaults to one day of expandable slot rows, with day-aware previous/next arrows and optional week/list modes. Open gym is a separate local session type, excluded from workout assignment choices and booked workout previews; capacity bookings/attendance remain in Planning/Boekingen. Coach/program inputs are hidden for this type.

The lesson editor's Workout toevoegen saves the lesson and dispatches `cap:workout-for-sessions` with its IDs (all new repeat dates included). The shared workout editor preselects those sessions and can load saved workouts as editable copies; saving creates a new library entry without editing the original.

## API and persistence

Exercise prescriptions also carry optional `restSeconds` (0–3600) and `tempo` (up to 40 characters). Both persist through load-target resolution and training snapshots, and display in workout/training/session previews. Missing values in older JSON are null.

Optional `supersetGroup` (A–F) pairs exercises, with at least two exercises per used letter. Group labels persist through editing/copying, target resolution and snapshots, and appear in workout/session/training views. Performed sets carry optional integer `rir` (0–10); missing values remain null and do not affect PR calculations. Existing Java constructors remain available for older call sites.

UI event → `fetch()` to `http://localhost:8080` → Spring controller → store service → JSON response → DOM update.

`backend/` is a separate Maven project targeting Java 25. `BackendApplication` starts Spring Boot. Constructor injection connects controllers to `WorkoutStore`/`TrainingStore`; these services combine validation, domain logic and file persistence. Java records model requests/results; Jackson serializes JSON. `ApiErrors` maps validation/storage failures to HTTP messages. No database/repository layer exists.

| Endpoint | Operation |
| --- | --- |
| `GET /api/health` | Connection status |
| `GET`, `POST /api/workouts` | List/create, optionally assign for today |
| `DELETE /api/workouts/{id}` | Delete saved workout and assignment; completed session snapshots remain |
| `PUT /api/workouts/{id}` | Edit workout in place, preserving ID and assignment; history and copied session programmes remain |
| `POST /api/workouts/{id}/assignment` | Schedule a snapshot for athlete/date; repeated template/date replaces that snapshot |
| `GET /api/workout-assignments` | List dated personal assignment snapshots |
| `GET`, `POST /api/training` | History/save completed session |
| `GET /api/records` | Best weight per exercise and exact reps |

Backend binds to `127.0.0.1`; CORS permits `http://127.0.0.1:5500`. CORS is not authentication. Training uses `preview-guillaume`; API operations have no real user authorization.

| Data | Location |
| --- | --- |
| Workouts/assignments | `data/workouts.json` |
| Completed sessions/plan snapshots | `data/training.json` |
| Records | Derived from completed sessions |
| Active training | localStorage `cap-active-training-v1` |
| Club examples | localStorage `cap-club-preview-v1` |
| Theme | localStorage `cap-theme` |

JSON paths are relative to the process working directory (normally `backend/`); properties `cap.workouts.file`/`cap.training.file` override them. `backend/data/` is Git-ignored. Stores load into memory, synchronize methods, and write temporary files with atomic replacement before updating memory. This is local prototype storage, not a transactional multi-user database.

## Earlier project and checks

October 20 update: Workouts JSON uses an atomic `{workouts, assignments}` state. Old arrays load automatically with existing assignments converted in memory; first write preserves the old file as `.legacy-backup`. Dated assignments hold planned snapshots with deterministic IDs; the same template can be scheduled on multiple dates. Template edits do not rewrite them. Completion optionally references assignmentId for the correct plan. Deleting a template removes its schedule entries but keeps completed history.

Library categories: CAP HYROX, CAP Hybrid, CAP Move, CAP Lift & HIT, or general/personal. Group sessions share the category, but assignment requires explicit session/date selection. Group scheduling/structured snapshots remain browser-local; personal schedules persist in Java.

Builder revision: compact exercise-first layout; + below adds a standalone exercise and + Superset adds a linked box. Save opens a dialog with form-associated metadata/assignment controls. Group can save a category template without linking a session; optional linking still uses explicit session IDs. Private/Open gym/Athlete lead to the first-name grouped athlete chooser. Library categories also accept Private/Open gym/Athlete. Optional `workoutType` (other/full-body/lower-body/upper-body/conditioning) persists through templates and assignment snapshots; coach filter buttons combine with category dropdowns.

`src/App.java` → `UserInterface` → `WorkoutLibrary`/`Workout`/`Exercise`; `WorkoutFileManager` reads/writes root `workouts.txt`. Both console and web exercises support tempo; the web model also supports optional rest seconds. `Athlete` is a small standalone model. `src/ApiServer.java` is a bare server experiment on port 8080: do not run it alongside Spring Boot. Console data/classes are not automatically imported into the web backend.

Four backend test classes contain seven JUnit tests covering startup, stores and an HTTP training flow. Store/API tests use temporary files. Run `.\mvnw.cmd test` from `backend/`. No automated frontend test suite is present.

October 20: Coach Home reuses the athlete home-tiles component for Workout maken, Opgeslagen workouts, Sporters, Groepslessen, Planning, Boekingen and Shop. Removed the injected coach quick links/overview; assignment status remains in the Sporters tile caption. Shared CSS supplies two columns on phones and three from 650px.

October 20: Workout maken starts with category tiles (CAP HYROX, combined CAP Lift & HIT, CAP Hybrid, CAP Move, specific athlete, private, other). coach-workouts.js toggles chooser/editor and prefills existing category/destination controls; save dialog still chooses explicit dates/recipients. Category changes preserve exercise drafts and clear session links. Saved editing and lesson/athlete shortcuts open the editor directly.

October 20: Saved workouts starts with category tiles and full-library counts, including Alle workouts, combined Lift & HIT, Open gym and Overig. Selecting a tile reveals the existing expandable rows with combined category/type/text search. Back returns to categories; load status/retry stay outside the hidden list. Counts update after successful loads/deletions/saves.

October 20: coach-health route (Home tile/sidebar) adds a searchable picker from the ten example Sporters roster entries. coach-health.js keeps manual body metrics in session-only maps per example athlete; Guillaume reads/writes existing capProfile so athlete/coach measurements stay in sync. Shows latest values/dates/differences, full/segmental metrics and history. Only Guillaume has GET training/records integration; other athletes show explicit unconnected states. Aborted/request-identity guarded loads prevent responses appearing under another athlete. Signout clears measurement maps. No server roster/health persistence added.

October 22: KILO workbook imported through standard-library tools/import_exercises.py into static exercise-data.js (all 1,100 rows/14 columns, original names, source rows/checksum and Overzicht cells). exercise-picker.js groups identical rows into 1,078 visible choices, token-searches source names/classification, and offers dependent region/category/subcategory filters, eight-at-a-time results, collapsed source metadata and custom names. Original workbook is not changed. New scripts load before coach-workouts.js; saved workout/backend schemas stay unchanged. Dropdown becomes in-flow at <=800px.

October 22 follow-up: coach exercises route replaces the placeholder sidebar link. exercise-library.js browses the shared catalog alphabetically (24 at a time) with one search field, expandable metadata and Add buttons. capWorkoutDraft adds to the existing draft, reuses an empty standalone row, preserves supersets/edit identity and caps 100 rows. Workout bekijken opens the existing editor; adding alone does not persist. Nested picker filters removed at user request; compact search/custom entries remain.

October 22: Create Workout starts with Lower body/Upper body/Full body tiles, then the existing audience/category step. The existing required workoutType control carries the choice into POST/PUT; loading a template copy retains an already-chosen type, while editing restores the saved type. Lesson/athlete/library handoffs require the type if missing; drafts/exercises are retained. Saved workouts adds three top-level body-type tiles/counts using the existing type filter across all categories. Old other/conditioning entries remain in Alle workouts/group views.

October 22: Editor now starts with zero exercise rows and a + tile; last row can be removed. Cards keep exercise selection/sets/reps visible and collapse weight/rest/tempo. exercise-picker.js owns one shared responsive dialog (12-at-a-time search, quick query shortcuts, optional custom name), storing selected names in hidden inputs. Upper/lower selections scope search/library to the workbook's Upper Body/Lower Body labels; Full body includes both. Library insertion also guards scope. Type changes refresh mismatch notices without deleting existing rows. Submit explicitly blocks zero/unnamed exercises; save payload is unchanged.

October 22 Planning layout fix: planning-scoped flex day sections override the old desktop 11rem/1fr agenda grid (which placed newer details rows into narrow alternating columns). Agenda slots are full width; week view auto-fits readable 20rem day columns and stacks on smaller screens. Responsive date/control toolbar now has explicit Dag/Week/Lijst buttons, date/day counts and accessible capacity badges. Existing booking/session/date/filter logic remains in club.js.

October 22 Nutrition first step: coach-nutrition sidebar/Home route has searchable example-athlete selection, macro calculator and editable day targets. nutrition-calculator.js translates Smart Macro Planning Tool arithmetic/lookups (scaling, strategies, activity, muscularity, hydration); missing/negative outputs are rejected rather than hidden as zero. Blank optional goal uses current weight explicitly. nutrition.js isolates inputs/targets in per-athlete in-memory maps, protects against saving stale calculations, supports manual mode, and clears on signout. Athlete nutrition route reads only Guillaume's saved targets. No backend/account ownership/storage, meal planning or food logging added. tools/test_nutrition.mjs checks the workbook reference and energy/input boundaries.


October 22 food diary: import_foods.py preserves all 55 Calorietabel rows in food-data.js; 53 complete foods selectable, two incomplete rows disabled. food-diary.js scales source portions, snapshots logged nutrition, groups meals by date, supports custom entries and delete/undo. Session-only entries clear on signout; athlete preview logs Guillaume, coach reads selected athlete. capNutritionTargets exposes copied targets and selection plus change events. No server persistence added.


October 22 nutrition refinement: daily intake uses compact stacked target bars; food-entry preview owns the portion-only macro ring. Saved meal templates snapshot ingredients/amounts/nutrients from a logged meal group, support names, ingredient inspection, adding one meal portion to the chosen date/meal and deletion. Session-only preview templates clear on signout. No backend/dependencies added.
