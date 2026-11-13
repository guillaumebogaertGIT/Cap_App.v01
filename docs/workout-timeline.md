# Athlete workout timeline

November 13 IMG_5210 execution refinement (local, not pushed): larger centered title; exercise-position superset pill across horizontal rule with block icon; Overslaan/Logboek/Media action row; default three-column Set/Gewicht/Reps table with large rounded fields and amber active row. RIR remains optional. Tap a set number to select it or reopen a completed set for correction; Voltooien validates and completes the selected set, then follows existing superset progression. Prescribed reps prefill actual fields for convenience but explicit completed flags still control finish/history. Required weight remains explicit, including0kg.

Mobile app header and redundant inline details hidden in execution; prescriptions/instructions/video remain in Media and input titles. Floating previous/close/next and separate history action match reference placement. Finish/discard remain on overview, reached by closing exercise. Desktop retains split view with the same execution composition. Logboek means actual prior performance; no new athlete note editor or media upload is claimed. Existing external exercise videos remain links.

This pass:7 Node tests passed, including prefilled-but-uncompleted exclusion; mocked browser verified0kg completion, superset advancement, correction, explicit set4 selection saved as set4, Logboek/Media dialogs and six-set display. No overflow360/390/430/768/1440. Syntax/diff passed; detector's one thick-row-accent warning is intentional to match the explicit reference. Synthetic screenshots `.local/reference-exercise-final-390.png` and `.local/reference-exercise-final-1440.png`. No backend/data migration, real client writes or push.

November 13 self-planning follow-up (local): empty personal days offer Workout toevoegen. Picker reads only the authenticated athlete's existing unfinished, unscheduled weekly assignments and sorts by block/week end ascending. Shows deadline and allowed week; choosing outside the assigned week or an active workout is disabled. Reuses owned PUT /workout-assignments/{id}/schedule; successful save reloads the selected day, errors remain in the dialog. No duplication or copying of coach templates. Current blocks are the existing weekStart-to-six-days-later model; arbitrary multiweek deadlines are not added.

Booked session rendering now follows the same selected date through cap:workout-day; actual owned non-cancelled bookings display existing session name/time/program and training action. Other dates are excluded, including their empty-state effects. cap:workout-bookings suppresses the contradictory no-workouts message on a booked-session day. Existing private booked sessions remain supported alongside groups. Browser checked schedule persistence, earliest-first ordering, out-of-week disabled choice, today/tomorrow/no-booking filtering and five widths without page/dialog overflow. Existing6 Node tests, syntax/diff and detector passed. No backend changes, real account writes or push.

November 13 local follow-up: Workouts now opens on the current Brussels day on phone and laptop, without falling forward/back to another assignment. Day arrows step one calendar day; date/calendar control opens a date chooser and assigned-day list. Multiple assignments on a day are all shown; completed ones remain visible. Empty today says "Geen workouts vandaag". Weekly undated assignments remain under "Nog te plannen" with existing day-planning action. Reentering Workouts resets to today; in-page refresh retains chosen date. Old athlete week browser hidden; coach library unchanged. Browser checked empty tomorrow, only-tomorrow/no-today, assigned-day selection, custom date and route reentry, plus no overflow360/390/430/768/1440. JavaScript syntax, scoped diff and detector passed. No backend/data changes or push.

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
