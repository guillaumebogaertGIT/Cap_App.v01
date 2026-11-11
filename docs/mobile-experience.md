# Mobile experience and focused training

Implemented locally November 11, 2026. Not pushed or deployed in this session.

## Changes

- One responsive application: compact phone header reuses existing account/navigation controls; existing bottom navigation respects safe areas. Training hides unrelated fixed controls. Keyboard-aware layout keeps AI typing usable.
- Workout discovery prioritizes the active session or today's/next assigned workout independently of the selected week. Other assignments and week searching remain available. Home offers resume/next workout before secondary content.
- Compact exercise previews preserve order, groups, repetitions, load, rest, tempo and RIR. Public catalogue instructions and saved videos are available when present; no invented media or durations.
- Focused player shows one exercise, prescribed targets, real previous results and actual weight/reps/RIR. Completing a set validates values, marks it complete and starts adjustable/skippable timestamp-based rest. Completed sets can be corrected.
- Client nutrition presents meal name, calories and protein first; ingredients, instructions and full macros are expandable. Existing server-side idempotent meal registration is retained.

## Persistence and authorization

Existing owned training draft APIs remain authoritative. Optional `ui` in draft JSON stores exercise cursor, completed-set flags and rest deadline; no database migration. Backend validates shape, bounds and completed actual values. Existing account ownership, membership checks and optimistic version checks remain enforced.

Input is immediately mirrored under a per-account browser key, then debounced to CAP. Status distinguishes local input from confirmed server storage. A different server version requires an explicit recovery choice. Local copies never restore another draft ID. Sign-out warns about unconfirmed input and clears this account's mirror.

Finish submits only explicitly completed sets, confirms unfinished work and retains the same session ID on retry. An uncertain completion skips another draft update and retries the existing idempotent finish endpoint. Refresh checks history for the matching completion receipt before clearing the recovery copy. A missing receipt remains unconfirmed; no success is invented.

## Verification

- PostgreSQL: AccountWorkoutServiceTests, TrainingStoreTests, StaffRbacTests and AthleteMembershipAccessTests: 26 passed. Covers durable UI state, stale/invalid progress rejection, owned completion, idempotence and existing role/access boundaries.
- Node training-state tests: 5 passed. Covers rest after backgrounding, completed-only payload, local/server reconciliation, session isolation and actual-value validation.
- Mocked browser API: start, actual entry, complete/correct, navigation, refresh, failed progress save/local recovery, retry, rest deadline restoration, real-shaped prior history, uncertain finish with one completion, and refresh receipt recovery.
- Client meal disclosure and single registered entry; AI composer within a reduced phone viewport. No paid AI calls or real client writes.
- Phone widths 360/390/430 and desktop 1440: no horizontal page overflow across dashboard, workouts, nutrition, planning, progress, community, messages and settings. Light/dark player review; enabled training buttons meet 44px targets. JavaScript syntax, scoped diff and design detector checks passed.
- Local screenshots: `.local/cap-player-final-390.png`, `.local/cap-player-final-1440.png` (synthetic athlete).

## Remaining practical checks

Test on a physical iPhone with its keyboard, browser chrome, screen lock and reconnect. Browser tests simulate background timing and viewport shrink, not iOS suspension. Initial app loading and final completion still require connectivity; this is not an offline app. Browser storage can be removed by the browser/user, so only confirmed CAP saves are durable across devices. No deployment was performed.
