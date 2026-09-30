# Current Architecture

Reviewed: September 29, 2026.

## Browser application

`frontend/index.html` is the static shell; scripts also create screens/dialogs. There is no frontend framework or build pipeline.

- CSS loads as `style.css` (base components/themes/responsive rules), `polish.css` (refinements/overrides), then `auth.css` (preview entry).
- `navigation.js`: hash routes, section visibility, preview roles, desktop sidebar and mobile Menu (800px breakpoint).
- `auth-preview.js` / `account-menu.js`: simulated entry, plan checks and account dialogs; credentials are not transmitted or saved.
- `coach-workouts.js`: starter exercise catalog, editor and workout POST.
- `workouts-api.js`: workout GET, libraries and today's Brussels-date assignment.
- `training.js`: draft/timer, actual sets, completion POST, history and records.
- `club.js`: local scheduling, bookings, attendance, products and dashboard summaries.
- `theme.js` / `backend-status.js`: theme preference and health check.

Scripts communicate through globals (`previewRole`, `capAccess`, `capTraining`), DOM and custom events (`cap:viewchange`, `cap:workouts-saved`). HTML script order matters.

## API and persistence

UI event → `fetch()` to `http://localhost:8080` → Spring controller → store service → JSON response → DOM update.

`backend/` is a separate Maven project targeting Java 25. `BackendApplication` starts Spring Boot. Constructor injection connects controllers to `WorkoutStore`/`TrainingStore`; these services combine validation, domain logic and file persistence. Java records model requests/results; Jackson serializes JSON. `ApiErrors` maps validation/storage failures to HTTP messages. No database/repository layer exists.

| Endpoint | Operation |
| --- | --- |
| `GET /api/health` | Connection status |
| `GET`, `POST /api/workouts` | List/create, optionally assign for today |
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

`src/App.java` → `UserInterface` → `WorkoutLibrary`/`Workout`/`Exercise`; `WorkoutFileManager` reads/writes root `workouts.txt`. Console exercises include tempo; the web model does not. `Athlete` is a small standalone model. `src/ApiServer.java` is a bare server experiment on port 8080: do not run it alongside Spring Boot. Console data/classes are not automatically imported into the web backend.

Four backend test classes contain seven JUnit tests covering startup, stores and an HTTP training flow. Store/API tests use temporary files. Run `.\mvnw.cmd test` from `backend/`. No automated frontend test suite is present.
