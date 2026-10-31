# Current Gaps and Documented Plans

Reviewed: September 29, 2026. This is not a new roadmap.

## Obviously incomplete in the current project

- October 30 Docker update: App-only Docker setup implemented and running healthy against existing native PostgreSQL; Docker start/restart/rebuild/status guide in docs/docker.md. Existing coach login, membership/finance denial, guest/demo/logout boundaries and data persistence across container recreation verified. Native database containerization, production hosting/backups/TLS, and an actual Docker-served Stripe test checkout remain separate/unverified; no new purchase created for Docker QA.

- October 30 update: Six confirmed coach accounts created and actual logins verified locally; remaining staff pending. Restart user backend for password UI/API and reviewer config. Glenn sandbox finance comparison still pending after restart. Staff password recovery, forced first-login change and deployment remain future work; handover described in docs/coach-accounts.md.

- October 30: Glenn-only Financiën implemented; actual Glenn account provisioning and sandbox totals comparison remain. Finance HTTP tests passed on isolated PostgreSQL; mobile/tablet/desktop themes and error/empty/unauthorized UI verified with fake responses. Docker remains next after account/permission work.

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
- October 27: User review next for Planning → Les toevoegen → month calendar → dated form for Groep/Privé/Open gym. Groepslessen navigation/page removed. Browser checks cover all types, Open gym repeats/hidden workout fields, cancel/back/save focus and four-session workout handoff; phone/tablet/desktop layouts verified. Future steps: custom recurrence/end date, direct saved-workout choice in session form, and single/future-series editing. Session persistence remains browser-local.

Review Planning activity dropdown and private athlete selection: create/repeat/edit recipients, switch to Guillaume preview, verify unassigned private sessions are hidden. Implement server authentication and session ownership before real private appointments.

Review calendar-first Planning: month arrows/Today, date selection, athlete booking/cancellation, private-session counts, coach add/edit and workout handoff. Phone/tablet list below calendar; desktop list beside it. Local preview storage and real authorization limitations remain.

Accounts next: install/start PostgreSQL locally and verify Flyway/persistence on PostgreSQL (current 26 backend tests pass with H2 for account flows). Connect Dutch coach invitation/athlete acceptance/login UI, then migrate real sessions/bookings and workouts/results with server-side ownership and capacity transactions. Add password recovery/email delivery, abuse limits, HTTPS/secure cookies and backups before deployment. Current frontend remains preview; do not imply accounts protect its browser-local data.

Next: review real account page on 8081 after backend restart, then migrate shared sessions/bookings and workout ownership into the real app. Native PostgreSQL installed and connection/migration confirmed in user logs. Real login/invitation/accept/logout UI now implemented; email delivery, recovery, abuse limits, deployment and capacity transaction work remain.

Connected core completed October 27: real accounts now support SQL Planning/private recipients/bookings/attendance, workout CRUD/assignment, exercise CRUD/archive, athlete training drafts/completion/history/PRs. User review after restarting accounts backend: invite athlete in private window, create lesson/workout, attach/book/start/log/refresh/finish, check ownership and privacy. 32 tests passed on isolated PostgreSQL 18 and H2; browser full flow passed. Next: reconcile real workspace navigation with the original full app design, explicitly review/import wanted preview templates, commercial membership rules, email delivery/recovery/abuse limits, HTTPS/backups/deployment. Nutrition/health/shop/messaging remain outside the connected core.

Original design restored for real accounts: review /app/index.html after restarting accounts backend in the same terminal. Same original navigation/screens now drive real lessons/bookings, workout creation/assignment and athlete training. Browser verified invitation/login, repeated lesson creation, booking, original picker/editor save/assignment, draft refresh and completion; 32 backend tests pass including app redirect/no-store/asset assertions. Layouts checked at 390/768/1440px. Nutrition/health/profile/shop/messaging still need separate shared-storage work.

Audit completed: user database confirmed connected/migrated by supplied startup log; stop duplicate backend before launching scripts/start-accounts.cmd. Read docs/accounts-verification.md and accounts-review.sql for table inspection/manual checks.35 tests pass on isolated PostgreSQL; native JVM restart and browser original-UI account/workout flow verified. Shared nutrition/measurement history and deployment/email/recovery remain separate work.

Nutrition targets connected: review after accounts restart applies V6. Coach saves manual/calculated targets for real test athlete; athlete checks Voeding in private window. 36 tests passed on isolated PostgreSQL, including new ownership/validation/update and restart checks; browser cross-session save/reload/update/calculated flows and 390/768/1440px layouts passed. Food diaries and measurement history remain local/unconnected.

- Stripe TEST setup and first paid sandbox Checkout verified Oct28. Matching CAP athlete became active through an actual subscription-update webhook after forwarding recovery; no second payment/manual reconcile. Remaining provider tests: decline/3DS, portal operations, renewal/cancellation and this subscription's restart persistence. See docs/stripe-test-setup.md. Live billing and feature gates remain unapproved.

October 29: Catalog implemented and nine Stripe sandbox prices/restricted portal configured. User verified the 5-credit paid sandbox purchase and shared group booking/cancellation balance 5 -> 4 -> 5. Next provider checks: new membership purchase/cancellation, other credit packs, renewals/expiry/declines and this actual purchase's restart persistence. Define open gym/private payment access, refunds/disputes and failed-instalment policy before live work. Coach emails still required; names alone did not create accounts. See docs/memberships-test.md.

October 29 refinement completed: athlete Planning balance and group/Open gym credit bookings implemented and tested (23 PostgreSQL checks, real browser balance updates). User reported EUR75 purchase/cancellation success; user-side Open gym check after backend reload remains next. Private-session pricing, other plans, renewals/expiry and refund/dispute handling remain pending.

Next priority explicitly requested October 29: Docker setup, after the now-completed coach membership/credits overview. Discuss a backend/PostgreSQL development setup and persistent data before implementing. Docker CLI not found in current PATH; installation/status needs checking. Coach emails still unavailable.

Membership review now restricted to Glenn/Dieter only. Activate later with their actual COACH login emails; default denial is intentional until then. Two-account policy and UI/API checks complete (24 checks). Docker remains the next requested task.

October 30 update: Staff contact emails and source roles are now in notes.txt; Glenn/Dieter local reviewer configuration populated. Actual coach/staff account provisioning still pending, along with mapping non-coach source roles to CAP permissions. Marketing contact name is cropped and unconfirmed. Docker remains next; emails are no longer the blocker for known coaches.

October 30 update: Real profile/weight/body-measurement persistence now implemented and tested; supersedes earlier health-history placeholder notes. Actual iPhone hardware verification remains manual.

October 30 RBAC: source/tests/UI/docs implemented. Approval gate next: exact UUID mapping (.local/staff-role-mapping.json), verified backup, V13 application and real role activation; then separately approve app replacement. Ordinary cap remains V12/old permissions. Maxim/Vic not provisioned; confirm login identifiers. Assign real instructor/client grants after activation. Remote CI and physical iPhone QA remain.

RBAC activation gate resolved by explicit user approval; local cap V13 and approved roles/Docker now active. Next user check: Glenn/Dieter login, Sporters dossier and desired instructor/client links. No instructor clients were guessed or auto-granted. Maxim/Vic require confirmed account details; external hosting/live billing remain separate.

Maxim/Vic provisioning completed at user request. Remaining: admins choose Maxim's client links; staff handover/password changes; marketing contact remains unprovisioned.
