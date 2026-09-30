# CAP App — Project Memory

Reviewed: September 29, 2026.

## Purpose and scope

CAP is a fitness and coaching application for CAP in Gistel, Belgium, and a software-engineering learning project. The documented target is a usable MVP/demonstration around December 2026, prioritizing workouts and the coach/client experience.

Basketball belongs to the separate RAW Hoops scope and is explicitly excluded from the CAP MVP in `docs/PROJECT_PLAN.md`. Residual frontend placeholders and basketball exercise names do not establish a requirement.

## Current state

- One responsive HTML/CSS/JavaScript application with Dutch interface text, CAP images, light/dark/system themes and athlete/coach previews.
- Java 25 / Spring Boot 4.1.1 API: workout creation, assignment to example athlete Guillaume, timed completion, saved history and exact-rep personal records. Prescriptions support no target, fixed kg or a percentage of a matching record.
- Planning, bookings, attendance and shop use browser-local example data. Login, registration, roles and subscriptions are simulations; no real accounts or payments exist.
- Backend storage is local JSON, not a database. The earlier Java console application remains separately in `src/`.
- Nutrition, recovery, coaches and AI are not implemented feature areas. “Coach Glenn” is a provisional future AI name.

## Working requirements

Follow `AGENTS.md`: small teaching steps, explain changes and verification, allow visual review, and discuss larger features/dependencies/architecture first. Keep identifiers English and support Dutch user-facing text. Do not infer the user's understanding from existing code.

Maintain one responsive codebase for phones (especially iPhones), tablets and desktops. Retain the desktop sidebar; broader mobile navigation remains a product decision.

## References and local run

- `README.md`: run instructions/current overview.
- `docs/PROJECT_PLAN.md`: canonical scope and learning principles. Its original review-only task is historical, as clarified by README.
- `docs/SESSION-2026-09-27.md`: prior handoff; `docs/CAP-overview.html`: printable companion.
- `docs/account-access-plan.md`: provisional access design and unresolved CAP decisions.
- From `backend/`, with JDK 25: `.\mvnw.cmd spring-boot:run`. Windows VS Code terminals use the user `JAVA25_HOME` variable; see README for setup. Open `http://127.0.0.1:5500/frontend/index.html` through Live Server and enter the design preview. API port: 8080. Current access controls are not production security; use example data.
