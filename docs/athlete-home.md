# Athlete Home — local first version

Athlete Home replaces the shortcut-tile grid with four useful summaries:

- **Jouw trainingen:** Vandaag / Deze week, personal fixed-day and flexible weekly assignments, booked lessons and saved lesson workouts. Completed personal/lesson workouts remain visible with an Afgerond label. Flexible unplanned workouts are reachable from the Today empty state. Long lists scroll inside the panel.
- **Voeding vandaag:** logged calories and protein versus the coach's saved goals, or logged totals without invented goals. The count refers to saved food entries, not measured actual consumption. Missing targets and empty diaries have explicit empty states.
- **Ongelezen berichten:** up to three recent unread direct/group messages with a link to the existing conversation. The inbox remains the full view.
- **CAP Community · Algemeen:** latest three undeleted general-channel posts, optional saved author photo and link to Algemeen. Previewing Home does not mark messages/posts read.

## Integration

athlete-home.js/css uses existing authenticated self-service APIs: workout-assignments, training history, session-workouts, food-diary for the current Brussels date, own nutrition-targets, general community and social notifications. Upcoming/ongoing own bookings reuse capClub.athleteSessions/refreshSessions. No selected-client ID is sent for diaries, training or targets. Only signed-in ATHLETE accounts load these widgets; coach Home remains unchanged. No schema or backend changes.

Workout previews use the existing capWorkoutView renderer. Starting uses capTraining.start; group starts re-fetch and revalidate the current own booking/snapshot before passing session:id. The existing server remains authoritative. Start is not offered for completed items or other fixed days, while current-week flexible workouts can start. A current active training has a resume link. The prior home-training.js next-session component remains on disk but is no longer loaded, avoiding duplicated Home cards.

Each panel has independent loading/error recovery. Refresh runs on Home entry, focus, becoming visible, training/booking changes, manual refresh and once per minute while visible. Navigation/role changes invalidate pending results. New Home is read-only until the user explicitly opens/starts/logs something through existing flows.

## Validation and review

Browser fixture checks covered today/week filters, flexible and completed workouts, saved lesson completion visibility, personal/group start wiring, nutrition totals/goals, message/post teasers and author photo, failure isolation/recovery, coach-role no-read guard and unchanged coach Home. Layouts at 390/768/1440 had no horizontal overflow. Empty browser-local previews remain honest empty states. Fixtures did not change real accounts or records; start handlers were stubbed for wiring checks. Full authenticated browser/live-device review remains pending. Existing static preview may still report its unrelated localhost backend probes.

Open http://127.0.0.1:5501/frontend/index.html#dashboard and choose Sporter preview. Real API data appears only in the authenticated app. No live deployment or GitHub push has been performed for this Home change.
# Compact dashboard follow-up — November 13

Local, not pushed: user selected a compact all-in-one athlete dashboard based on IMG_5207, rather than swipe sections. One primary today/resume workout, seven-day completed-training chart, four compact nutrition bars and one-preview inbox/announcement sections. All-workout browsing stays in Workouts; full messages/community stay in their routes. No duplicate resume banner or large Home week toggle. No step counts, calories burned or invented goal percentages. Actual self training history supplies weekly counts; existing authenticated APIs/role checks remain unchanged. Coach dashboard remains unchanged.

Browser checks: empty summaries, active resume link, nutrition route, four nutrient bars and two real-shaped history rows producing two weekly completions. No overflow360/390/430/768/1440; compact phone host roughly668px compared with1433px before, with sample content above bottom navigation at390×844. Longer real content/shorter screens can still scroll; no text is forcibly cut to a viewport. Syntax/diff/detector passed. Synthetic screenshots `.local/home-compact-final-390.png` / `1440.png` precede the last small spacing reduction. No real data writes or push.

