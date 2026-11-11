# Athlete workout timeline

November 11, 2026: local implementation, not pushed or deployed.

## Flow and files

- `workouts-api.js`: assigned-day previous/next navigation and compact timeline, including completed assignments. Selecting an exercise starts/resumes the owned workout and opens that index. Another active session remains protected; completed workouts link to Progress.
- `training-player.js`: presentation component for the shared connected timeline and compact actual-set table. Mobile uses overview/detail navigation; desktop shows both columns. Block colors supplement explicit labels; completion uses checkmarks and text. Coach order and original exercise indexes are preserved.
- `training.js`: retains owned API calls, local recovery, serialized saves, explicit completion flags and same-ID finish retry. Set completion immediately triggers progress save. Local completion is distinguished from confirmed CAP storage by the live save status. Unsaved input warns on page departure.
- `workout-player.css`: CAP theme tokens, phone-friendly numeric inputs and 44px actions, tablet adaptation and split desktop layout. Existing bottom navigation is retained; training hides unrelated fixed controls.
- `AccountWorkoutService.UiState`: optional paused-rest seconds in existing JSON, validated 0–3600 and mutually exclusive with a running deadline. Old three-argument constructor and old JSON remain compatible; no migration or new endpoint.

## Logging behavior

Latest real history supplies example placeholders. Explicit copying fills only empty values, never completed or already entered performance. Suggestions are not counted as completed sets. Blank RIR is accepted; 0 kg is valid. Every row retains its prescription, including varying per-set targets. Complete/correct, add/remove extra sets, prior/next exercise, rest pause/resume/skip and +15 seconds use the same draft.

Saved rest uses a deadline while running and bounded seconds while paused. Browser suspension does not decrement a paused timer. Refresh preserves completed actuals and the selected index; mobile returns to the timeline with the detail accessible by tapping.

## Verification

- PostgreSQL: 26 workout, history and authorization tests passed, including paused-state persistence and invalid paused-state rejection.
- Node: existing 5 training-state tests passed.
- Mocked browser: exercise selection, 0kg/blank RIR, immediate completed-save, correcting actual weight, failed request/local status/retry, exercise switching, refresh and paused rest, completed-history summary and one finish receipt. Previous values copied correctly from real-shaped history. No paid AI or real client writes.
- Visual inspection and reference comparison: 360/390/430/768/1440, no horizontal overflow; desktop split timeline/table and mobile detail. Fixed excess paragraph padding, numeric field styling and compact media controls. Supplied IMG_5176 reference informed round connected icons, overlapping block badges and simple rep summaries, retaining CAP amber. Moved secondary retry/coach comments under Workoutopties to keep the list higher. Synthetic screenshots in `.local/cap-timeline-phone.png`, `.local/cap-table-phone.png` and `.local/cap-timeline-laptop.png` (before final header compaction).

## Limits

The existing schema supports integer repetitions, per-set loads, RIR, tempo, rest and superset group labels. Dedicated AMRAP/per-side/circuit-round fields do not exist; no prescriptions are invented or converted. History matching uses the exact saved exercise name because completed snapshots have no canonical exercise ID. No offline launch/completion guarantee, OS timer notification or physical iPhone verification. Nutrition, billing and scheduling remain unchanged in this redesign.

## Full-screen execution follow-up

Local, not pushed: compact full-screen phone execution with fixed info/previous/close/next/history toolbar; split desktop remains. Superset position dots and round heading use saved group membership. Complete-and-next advances within a round, then returns to the first unfinished group member, including unequal set counts. RIR is an optional table column. Skip navigates onward while preserving incomplete input; it never counts skipped sets as completed. Information displays actual public instructions, coach description and existing external video links; history shows actual prior sets and optional RIR. There is no new athlete-journal storage or embedded video player.

The supplied 14.9-second file decoded as Control Centre at inspected opening, middle and closing timestamps (including 0, 0.1, 0.5, 3.9, 11 and 14.8 seconds). Layout/interaction choices therefore follow the explicit written brief and IMG_5176 screenshot, not claimed observation of unseen transitions. Frame extraction is local only.

Verification this pass: 6 Node state tests including unequal superset round progression; mocked browser completes Squat then Row then returns to Squat round two, with real-shaped draft persistence. Instructions/history dialogs work; optional RIR toggles, 0kg/blank RIR accepted. Sustained failed PUTs leave local status and visible retry; recovery confirms CAP save. One finish receipt contains correct [2,1] set counts. Footer spacing fix verified by clicking the formerly obstructed finish action. No horizontal overflow at360/390/430/768/1440. JavaScript syntax, scoped diff and detector pass. No backend changes in this follow-up; prior26 PostgreSQL checks remain applicable. Screenshots `.local/execution-final-390.png` and `.local/execution-final-1440.png` use synthetic data. Native iPhone keyboard/suspension still needs device testing.
