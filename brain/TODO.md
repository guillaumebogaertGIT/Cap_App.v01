# Current Gaps and Documented Plans

Reviewed: September 29, 2026. This is not a new roadmap.

## Obviously incomplete in the current project

- No real accounts, password recovery, server roles/entitlements or per-user ownership; training belongs to one example athlete. Profile/messages/chat/notifications/payments are explanatory dialogs.
- Planning, bookings, attendance and shop are browser-local previews without shared backend storage or checkout.
- Exercise search is a hardcoded starter catalog plus custom names, not a managed library. Web API lists/creates workouts; editing/deletion, templates and multi-workout programs are absent.
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
