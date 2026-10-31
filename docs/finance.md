# Financiën (Stripe TEST)

RBAC update: approved V13 is active locally. OWNER, MANAGER and FINANCE replace the single-email permission below; Glenn and Dieter are both OWNER/Admin and can view reports. The old reviewer settings no longer grant access. FINANCE reuses the membership/credit panel on the finance page, with a separate minimal financial client picker. See [staff RBAC](staff-rbac.md). Report calculations and limitations below are unchanged; the email-only instructions describe the previous version.

The Financiën sidebar page is available only to the configured finance reviewer with a CAP COACH account. Glenn's supplied login email is configured locally. Dieter's membership-review permission does not grant financial access. No staff accounts were created by this change.

## Start and inspect

1. From the project root: `.\scripts\start-stripe-test.cmd`.
2. Open http://127.0.0.1:8081/app/index.html and log in as the configured coach.
3. Choose **Financiën**, select a month/year and choose **Overzicht laden** to refresh.

Restart the launcher after changing reviewer configuration. It reads ignored `.local/finance-reviewer.json`, containing one `email` field. Other environments use `CAP_FINANCE_REVIEWER_EMAIL`. Empty configuration denies everyone; editable display names never grant permission.

## Meaning of the figures

- Read-only TEST report, not real turnover, profit, VAT accounting or bank payouts.
- Reads Stripe charges on demand: successful, captured EUR payments whose Stripe customer is linked to a CAP account. Monthly renewals count individually; active memberships are not multiplied by their price.
- Month boundaries use Europe/Brussels including daylight saving. The period uses charge creation time; current CAP Checkout automatically captures cards. Future delayed/manual capture would require another reporting basis.
- Received is `amount_captured`. Refunded is current `amount_refunded` on those same charges, including later-month refunds. Historical figures can change. Remaining is received minus refunds, before Stripe fees, without VAT breakdown.
- Disputes are marked but not subtracted. Other currencies are excluded with a visible count. Cash, external payments and unlinked Stripe customers are excluded.
- Follows pagination with duplicate protection; malformed/live data or over 2,000 returned charges fail rather than show partial totals. No polling or additional recurring service.
- Returns no raw Stripe objects, customer/payment IDs, card details or secrets. No payments/refunds/membership changes, new financial storage, or migrations.

API: `GET /api/finance?month=2026-10`, no-store. Guests 401; athletes and unauthorized coaches 403. Server authorization is independent of UI visibility.

## Manual checks

- Configured coach: compare the selected month's linked test charges in Stripe.
- Empty month: explicit empty result, no stale values.
- Other coach/athlete: no navigation entry; direct API GET 403. After logout: 401.
- Stripe unavailable or disabled: safe error, cleared totals, retry.
- Phone and desktop, light/dark themes.

Automated tests use fake Stripe and isolated PostgreSQL; browser layout checks use labeled fake payment responses. Glenn's local account now exists (see docs/coach-accounts.md); actual sandbox comparison remains manual after restarting the backend to load reviewer configuration.

Stripe field definitions: [charges](https://docs.stripe.com/api/charges/object) and [listing/pagination](https://docs.stripe.com/api/charges/list).
