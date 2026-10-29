# CAP App — Project Memory

Reviewed: October 27, 2026.

## Purpose and scope

CAP is a fitness and coaching application for CAP in Gistel, Belgium, and a software-engineering learning project. The documented target is a usable MVP/demonstration around December 2026, prioritizing workouts and the coach/client experience.

Basketball belongs to the separate RAW Hoops scope and is explicitly excluded from the CAP MVP in `docs/PROJECT_PLAN.md`. Residual frontend placeholders and basketball exercise names do not establish a requirement.

## Current state

- October 29: Shop has the five confirmed memberships and four non-expiring credit packs in Stripe TEST mode. V9 stores orders/credits and finite monthly terms. The catalog launcher enables membership-or-credit checks for group bookings, with credit return before the lesson starts; open gym/private payment rules remain undefined. User verified a 5-credit sandbox purchase, shared coach-created lesson and booking/cancellation balance 5 -> 4 -> 5. New membership payments/renewals/expiry remain unverified. See docs/memberships-test.md. Individual coach emails remain needed.

- One responsive HTML/CSS/JavaScript application with Dutch interface text, CAP images, light/dark/system themes and athlete/coach previews.
- Java 25 / Spring Boot 4.1.1 API: real-account workout creation, assignment, timed completion, saved history and exact-rep personal records; the separate preview retains its example athlete. Prescriptions support no target, fixed kg or a percentage of a matching record.
- The accounts profile now uses PostgreSQL and real cookie login for Planning/bookings, private recipients, attendance, workout creation/assignment, shared exercises and athlete training/history/PRs. Real login opens the original app design at /app/index.html; the Live Server design preview remains separately browser-local. Stripe TEST-only billing has one verified paid sandbox Checkout and webhook-driven activation in the matching CAP athlete account; no existing features are payment-gated. Provider portal/decline/3DS/renewal testing remains.
- Real-account storage uses PostgreSQL (Flyway V1-V8); the original preview still uses local JSON/browser data. The earlier Java console application remains separately in `src/`.
- October 26: shared coach exercise management persists additions/edits/archive/restore in Java JSON. Workout popup uses active definitions; existing workouts/history/PR names are preserved. The real-account profile now also uses PostgreSQL overrides and coach authorization.
- Nutrition now saves coach targets to PostgreSQL and loads only the signed-in athlete's targets in the existing view; calculator drafts remain local; athlete food logs now persist in PostgreSQL and coaches read the selected athlete's diary; saved meal templates remain browser-local; meal planning is not implemented. Recovery, coaches and AI remain unimplemented. “Coach Glenn” is a provisional future AI name.

## Working requirements

Follow `AGENTS.md`: small teaching steps, explain changes and verification, allow visual review, and discuss larger features/dependencies/architecture first. Keep identifiers English and support Dutch user-facing text. Do not infer the user's understanding from existing code.

Maintain one responsive codebase for phones (especially iPhones), tablets and desktops. Retain the desktop sidebar; broader mobile navigation remains a product decision.

All displayed full dates and date fields use dd/mm/yyyy, times use 24-hour HH:mm, and timestamps/today use Europe/Brussels (including daylight saving). API/storage dates remain ISO and timestamps remain UTC instants. Shared helpers live in date-time.js/date-inputs.js.

## References and local run

- `README.md`: run instructions/current overview.
- `docs/PROJECT_PLAN.md`: canonical scope and learning principles. Its original review-only task is historical, as clarified by README.
- `docs/SESSION-2026-09-27.md`: prior handoff; `docs/CAP-overview.html`: printable companion.
- `docs/account-access-plan.md`: provisional access design and unresolved CAP decisions.
- From `backend/`, with JDK 25: `.\mvnw.cmd spring-boot:run`. Windows VS Code terminals select the JDK under USERPROFILE/.jdks/openjdk-25/jdk-25.0.2; see README for setup. Open `http://127.0.0.1:5500/frontend/index.html` through Live Server and enter the design preview. API port: 8080. Current access controls are not production security; use example data.

October 23 persistence update: preview login/role/plan, profile/measurements, nutrition targets/drafts, food diary and saved meals now survive reloads in this browser. Logout ends the session without deleting records. Local preview only; no real accounts or cross-device sync. Live Server ignores backend writes. This supersedes session-only descriptions above.

October 23 weekly planning: personal workouts support one flexible Monday–Sunday assignment or a fixed day. Coach library shortcuts/save dialog share a week/day agenda; group assignments still select actual dated sessions. Athletes browse workouts by week and can choose/change/clear a day for flexible assignments. Personal schedules persist in Java; group sessions remain browser-local.
