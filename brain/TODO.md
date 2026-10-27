# Current Gaps and Documented Plans

Reviewed: September 29, 2026. This is not a new roadmap.

## Obviously incomplete in the current project

- No real accounts, password recovery, server roles/entitlements or per-user ownership; training belongs to one example athlete. Profile/messages/chat/notifications/payments are explanatory dialogs.
- Planning, bookings, attendance and shop are browser-local previews without shared backend storage or checkout.
- Exercise management now supports the imported 1,078-definition catalog plus persistent coach additions/edits/archive/restore (October 26). User review of Oefeningen and picker remains next; real server coach authorization is still absent. Saved workouts support editing/copies; multi-workout programs remain absent.
- Sidebar exercises, Basketball, nutrition, recovery, coaches and AI links point to `#`, without screens. Basketball placeholders/catalog entries conflict with documented MVP scope. Some group-overview cards remain static despite the separate planning preview.
- JSON persistence has no database layer; no production deployment setup or automated frontend tests are present.
- Earlier console app: invalid numeric input can throw; saves overwrite directly; removal is persisted on normal exit. Sources: `src/`, README.

## Planned work already documented

- **Verification:** phone/tablet/desktop, both themes, keyboard navigation, iPhone inputs/dropdowns, registration/legal dialogs, refresh and offline-save recovery. Prior docs leave these outstanding; this inspection did not perform them. Sources: September 27 handoff, account-access plan.
- **Real use:** authentication, athlete ownership, coach authorization, server entitlements; agree CAP membership/booking/minors/privacy/cancellation/payment requirements. Sources: same documents.
- **Storage/integration/deployment:** agree database, add migrations/backups/transactions, connect planning/bookings/products to Java; payments after provider/business decisions. HTTPS, secrets, monitoring and restart management before deployment. Source: September 27 handoff.
- **Master-plan scope still absent:** managed exercises, workout editing/templates/programs, broader client management and basic coach-provided nutrition plans. AI is conditional on a stable core; “Coach Glenn” is provisional. Source: `docs/PROJECT_PLAN.md`.
- **Open choices:** athlete workout-creation entitlement and eventual mobile navigation. Do not build the complete mobile navigation system without a request. Sources: account plan, `AGENTS.md`.

Possible post-MVP integrations/wearables and other ideas are not commitments. `docs/TODO.md` is empty. History and PRs already exist, despite being described as future work in older master-plan sections.

- Coach Gezondheid & Progress preview added October 20: visually review phone/tablet/desktop picker, measurement form and histories. Real roster/health persistence and per-athlete training/record API support remain absent; current backend training data is Guillaume-only.

- October 22 exercise catalog: visually review phone/tablet/desktop search, collapsed filters, eight-result paging, long names, supersets and save/reopen. Browser inspection was blocked because computer-use could not confidently verify the browser URL. Difficulty/series/media fields in the supplied workbook remain unfilled.

- Review new Oefeningen page: search, 24-row browsing, add multiple exercises and Workout bekijken. Check draft/superset preservation and phone button wrapping. Nested editor filters have been removed.

- Review type-first creation and saved sections: Lower body -> CAP group -> save -> Lower body section; repeat Upper/Full, save/reopen, template copies, athlete/lesson/library shortcuts and phone layouts.

- Review revised empty editor/search dialog on phone/tablet/desktop: Upper body hides lower-body catalog choices, Lower body hides upper-body choices, Full body includes both; +/last-row removal, quick search, long names, optional weight/rest/tempo, supersets and saved-workout reopening.

- Review revised Planning at phone/tablet/desktop widths: readable first lesson rows, date controls, Dag/Week/Lijst, filters, expanded programme/actions and empty days. Actual browser layout verification remains outstanding.

- Review Voeding coach calculator/athlete picker, manual targets, stale-input feedback and Guillaume athlete view on phone/tablet/desktop. Targets clear on refresh/signout. Next nutrition step is food/portion-based meal planning using Voedingsschema; multi-athlete server persistence remains absent. Food diary preview now supports workbook/custom foods, portions, dates and coach viewing; review on phone/tablet/desktop.


- Review compact daily nutrition bars and saved meals on phone/tablet/desktop: log ingredients, name/save a meal, inspect ingredients, reuse on another date and delete template. Recipes and logs are still session-only; permanent athlete-owned storage remains pending.

- Review weekly workout agenda on phone/tablet/desktop: coach saved-library and creation paths, fixed day versus flexible week, group session selection, athlete week navigation/day choice/clear, refresh and completed state. Browser tool was unavailable during implementation. Personal backend still represents Guillaume only; no real open-gym membership enforcement added.

- Visually review optional per-set workout targets on phone/tablet/desktop, including editor resizing, save/reopen, assignments and athlete actual-weight logging.
