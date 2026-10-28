# CAP Stripe TEST MODE

**One actual Stripe sandbox checkout is now verified (October 28, 2026).**
The EUR10 test payment completed and the same CAP athlete account showed active
membership after a verified subscription-update webhook. No live payment was used.
Automated tests remain separate: they mock Stripe and use isolated PostgreSQL.
Portal access and scheduled period-end cancellation are also user-verified.
Declines, 3-D Secure, actual period expiry and renewals still need provider testing.

## What the app does

Open the account menu -> **Betalingen**. The original app design, sidebar and
authentication remain. Both coach and athlete accounts can test their own
subscription. Role and membership are independent: paying never makes an athlete
a coach, and no existing CAP feature is restricted by this new membership flag.
The same test membership also appears in **Shop**, under Alles/Abonnement, with
its server-provided price and the signed-in account's status. The card opens the
existing billing panel; it is not a separate product or payment flow. Shop status
refreshes on opening the page and returning to its tab. Other category filters
exclude the membership. Demo Shop remains separate.

Stripe-hosted Checkout collects card details outside CAP. Your signed-in session
determines the CAP account and server configuration determines the only allowed
price. The success URL only displays local status; visiting it cannot activate
membership. A signed webhook synchronizes Stripe's current subscription state.
Checkout and the customer portal open in a new tab, keeping the original CAP tab
open. After finishing, close the extra tab or switch back to CAP; its open billing
panel reloads local status on return. This does not reconcile or grant membership
by itself. If the browser blocks the tab, allow pop-ups for CAP and try again;
the blocked attempt does not send a billing request. Failed link requests close
the temporary blank tab and show an error in CAP.
**Status bijwerken** explicitly reconciles the signed-in account with Stripe if
an event was missed. The customer portal manages that account's own subscription.

Temporary rules live in `MembershipPolicy.java`: **CAP Test Membership**, EUR
10/month, no trial, card-only test Checkout, cancellation at period-end. Only an
active subscription with a paid current invoice and an unexpired billing period
grants the test entitlement. Incomplete, incomplete_expired, unpaid, past_due,
trialing, paused, canceled, missing or expired-period records do not. Scheduled
cancellation retains access until the paid period ends. Unsupported prices or
paused collection fail closed. These rules are placeholders, not actual CAP offers.

## Current PC setup (October 28, 2026)

Stripe CLI 1.55.3 is installed; use stripe.cmd on this Restricted-policy Windows
PC. PostgreSQL18 and the project JDK25 are available. The user configured CAP
sandbox, an existing EUR10 monthly test price, a saved period-end cancellation
portal and private runtime credentials. Credentials/Stripe object IDs are not
recorded here. CLI account matching, a completed paid test Checkout and its CAP
account reference were verified without displaying secrets.

The initial payment did not reach CAP because no forwarding listener was running.
Recovery: start the test listener, restart CAP using that listener's private
whsec secret, then update only a harmless metadata marker on the existing paid
test subscription. This emitted a real customer.subscription.updated webhook;
the user confirmed Status Actief and Testlidmaatschap Actief without pressing
Status bijwerken. No second Checkout/payment was created. Original checkout
completion delivery was missed; subscription-update delivery/recovery is verified.
User also verified the portal opens the matching test subscription in a separate
tab, then confirmed scheduled cancellation. CAP retained active status and showed
planned cancellation/access until 28/11/2026. Actual period expiry is not yet tested.
Keep the listener running while testing.

## Set up Stripe once

1. Create/sign in to a Stripe account and select a **sandbox/test environment**.
   Stay there for every following step. The secret key must start `sk_test_`.
   CAP rejects live keys and restricts return URLs to localhost. No publishable
   key is needed for this hosted Checkout flow. See [Stripe test keys](https://docs.stripe.com/keys).
2. Inspect the test Product catalogue **before creating anything**. Reuse a
   suitable active monthly EUR 10 fixed-price test membership if one exists.
   Otherwise create **CAP Test Membership**, recurring monthly, EUR 10, quantity
   one, no trial. Copy its `price_...` ID. The backend validates currency, amount,
   recurrence and test mode. It never creates products/prices automatically.
3. In the test customer-portal settings, reuse a suitable configuration or save a
   new one only if needed. Enable cancellation **at end of billing period**,
   payment-method updates and invoice history. Disable plan/quantity switching.
   CAP validates period-end cancellation and disabled subscription updates before
   creating a portal session. Obtain the `bpc_...` configuration ID; after CLI
   setup you can inspect existing configurations with:

   ```powershell
   stripe.cmd billing_portal configurations list --active=true --limit=100
   ```

   See [Stripe portal configuration](https://docs.stripe.com/api/customer_portal/configurations/create).
   Do not enable immediate cancellation or create a duplicate default setup.
4. Install the Stripe CLI using [Stripe's installation instructions](https://docs.stripe.com/cli/install).
   With Node/npm already installed, the documented npm option is:

   ```powershell
   npm install --global @stripe/cli
   stripe.cmd version
   stripe.cmd login
   ```

   On Windows use `stripe.cmd`: the npm `stripe.ps1` wrapper is blocked when
   PowerShell execution policy is Restricted. No policy change is needed.
   Complete login yourself in the browser. Use the same test environment as your
   API key, price and portal. Never add `--live` to CAP test commands.

## Start local payment testing

First stop an existing CAP backend with Ctrl+C (Y if asked). Keep PostgreSQL
running. Use fake CAP accounts and development data.

**Terminal 1:** start local forwarding and leave it running:

```powershell
stripe.cmd listen --events checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,invoice.paid,invoice.payment_succeeded,invoice.payment_failed,invoice.payment_action_required,invoice.finalization_failed,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,customer.subscription.paused,customer.subscription.resumed --forward-to http://127.0.0.1:8081/api/billing/webhook
```

The CLI prints a `whsec_...` signing secret. This is **not** your API key, and is
the secret for this forwarding listener, not a Dashboard endpoint's secret.
Keep it private. See [Stripe local webhooks](https://docs.stripe.com/webhooks).
If restarting the listener changes its signing secret, restart CAP with the new
secret too; the running backend cannot read a secret changed in another terminal.

**Terminal 2:** from the CAP project root:

```powershell
.\scripts\start-stripe-test.cmd
```

If already inside `backend`, use `..\scripts\start-stripe-test.cmd`.
The launcher privately prompts for the `sk_test_...` key and CLI `whsec_...`,
then the existing `price_...` and `bpc_...` IDs, then your PostgreSQL cap-role
password. It restores environment variables when stopped. Keys are never saved
to a file, printed by the launcher or sent to frontend code.

Open **http://127.0.0.1:8081/app/index.html**, sign in as a fake account, then open
the account menu -> Betalingen -> Testabonnement starten. Use Stripe test cards
only. The normal `start-accounts.cmd` leaves billing disabled unless explicitly
enabled in your process environment; it remains usable without Stripe configured.

## Configuration

`.env.example` contains placeholders only. It is a reference; CAP does not load
dotenv files automatically. The test launcher supplies these process variables:

| Variable | Meaning |
| --- | --- |
| `CAP_STRIPE_TEST_ENABLED` | `true` to opt in; default `false` |
| `CAP_STRIPE_TEST_SECRET_KEY` | Private `sk_test_...` key |
| `CAP_STRIPE_TEST_WEBHOOK_SECRET` | Private listener `whsec_...` signing secret |
| `CAP_STRIPE_TEST_PRICE_ID` | Approved active monthly EUR 10 `price_...` |
| `CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID` | Test `bpc_...` with period-end cancellation |
| `CAP_STRIPE_TEST_BASE_URL` | `http://127.0.0.1:8081` (no trailing slash/path) |

The existing CAP database variables still apply; there is no temporary-database
fallback. There are no live-mode variables or live-mode switch. Future live
billing needs a separate reviewed implementation/configuration and business
decisions; do not reuse this test database or remove the guards to launch.

## Manual Stripe test checklist (partially verified)

- **Paid sandbox Checkout + webhook recovery verified:** subscribe with Stripe's successful test card, `4242 4242 4242 4242`, a future
  expiry and any three-digit CVC. Check CLI delivery, database row and the correct
  CAP account. The success screen can show pending briefly until webhooks arrive.
- Open `/app/index.html?billing=success` directly as another unpaid account:
  it must remain unpaid. Leave Checkout without paying: no entitlement.
- Use the **decline** and **3-D Secure/authentication-required** test cases in
  [Stripe's current testing table](https://docs.stripe.com/testing). Confirm
  failed/unfinished authentication grants nothing; successful authentication must
  be followed by confirmed paid/active status. CAP never handles card data.
- Click Subscribe repeatedly or from two tabs: at most one open Checkout attempt
  and no duplicate subscription. An open checkout is reused; an existing
  active/incomplete/past-due subscription sends you to status/portal instead.
- In the portal, schedule cancellation: the paid active period remains usable,
  and the end date/cancellation state appears in CAP. Verify ended cancellation
  removes the entitlement.
- Test renewal success/failure using [Stripe Billing tests/test clocks](https://docs.stripe.com/billing/testing).
  Test clocks require a test-clock customer; CAP does not automatically attach a
  clock. For an existing CAP test customer, use Dashboard/API test controls where
  supported, or explicitly arrange a dedicated test-clock fixture before running
  renewal tests. Do not claim a generic synthetic CLI event paid a CAP invoice.
- Stop forwarding, change the test subscription in Stripe, then click Status
  bijwerken. The canonical Stripe state must reconcile. Restore forwarding.
- Restart CAP; sign back in and confirm subscription records remain. Check a
  second athlete/coach sees only their own billing. Existing workouts and login
  must remain available even with no membership.

## Reliability and recovery

Checkout customer/attempt keys are committed before their remote requests. Owner
row locks serialize concurrent calls; Stripe idempotency keys recover a lost
response. Existing nonterminal subscriptions block another purchase, and an open
Checkout session is reused even after the user returns via the cancellation URL.
Only a confirmed expired/completed old session can be replaced after checking
that Stripe has no nonterminal subscription. Browser buttons also prevent
concurrent actions, but the database/server checks are authoritative.

Stripe may prune idempotency keys after 24 hours. CAP refuses uncertain requests
older than 23 hours rather than risking a duplicate. If this message appears,
do not delete database rows or start a second purchase. Inspect the customer's
test requests/Checkout in Stripe Dashboard, compare the read-only billing rows
below and reconcile with Status bijwerken. If the customer/session ID was lost
before persistence and is still unresolved, stop and have the developer recover
the exact Stripe object/attempt before retrying; there is intentionally no unsafe
automatic reset. Do not switch Stripe accounts/keys against an existing billing
database: customer IDs and attempt keys belong to their original test environment.

Only `/api/billing/webhook` bypasses browser CSRF. It checks HMAC-SHA256 over
the exact raw bytes, constant-time signature comparison, five-minute timestamp
tolerance and `livemode=false`. Other billing writes require login plus CSRF.
Processed event IDs and synchronized records commit together. Failures return a
non-2xx response without marking the event processed, so a retry remains possible.
Duplicates are no-ops. Out-of-order payload statuses are ignored: after acquiring
the owner lock, CAP fetches current Stripe subscriptions and expanded latest
invoices. Unrelated customers are ignored. No webhook payloads/card data are stored.

The local implementation handles events synchronously with bounded Stripe API
timeouts. It has no queue/worker cost. Each customer list is bounded to 100
subscriptions and refuses truncated results instead of guessing; this is suitable
for test development, not a production scaling decision. Webhook delivery plus
explicit reconciliation keep it synchronized; merely viewing the success page
does not make Stripe calls or grant access. It polls local status at most five
times. Do not expect CLI forwarding to recover every offline event automatically.

Backend logs report provider HTTP status/connection category and handled event
type only. They omit API keys, signatures, URLs, customer details and raw bodies.
The UI receives safe Dutch errors. Do not enable HTTP wire/header logging or
paste Stripe secrets, portal URLs or complete provider request logs into Git/chat.

## Database inspection (read-only)

In pgAdmin: **cap -> Schemas -> public -> Tables**. Migration V8 adds
`billing_account`, `billing_subscription`, and `billing_event` without changing
existing roles/data. No card details are stored. Test runs use separate schemas
in `cap_test` on port 55433, not your development `cap` on 5432.

```sql
SELECT u.display_name, u.role, a.customer_id, a.synced_at
FROM public.billing_account a JOIN public.cap_user u ON u.id = a.user_id;

SELECT u.display_name, s.subscription_id, s.price_id, s.status,
       s.payment_status, s.period_start, s.period_end,
       s.cancel_at_period_end, s.cancel_at, s.canceled_at, s.ended_at, s.synced_at
FROM public.billing_subscription s JOIN public.cap_user u ON u.id = s.user_id
ORDER BY s.synced_at DESC;

SELECT event_type, processed_at
FROM public.billing_event ORDER BY processed_at DESC LIMIT 30;
```

Use fake records and inspect locally; customer/subscription IDs are identifiers,
not public data. The tables intentionally contain no API or webhook secrets.

## Verification status

- Full regression: **55 tests passed on isolated PostgreSQL**, covering accounts,
  invitations, workouts, planning, nutrition and billing. Billing tests use a fake
  `StripeGateway`, not an actual Stripe account.
- Final review fix (status checks do not start a Checkout retry window): all
  **11 focused billing/account tests passed again on PostgreSQL** afterward.
- Billing HTTP tests cover paid activation after webhook, success URL alone,
  incomplete/declined/authentication-pending/expired states, renewal failure and
  recovery, cancellation, period expiry, duplicate/stale events, atomic retry,
  lost Checkout response and concurrent clicks, login/CSRF/ownership and forbidden
  status writes. These simulate provider states, not actual card challenges.
- PostgreSQL restart test retains customer/subscription/cancellation fixture rows;
  the authenticated summary reads them after a real backend-context restart.
- Browser: real CAP login/account menu/unconfigured state checked. Mocked billing
  responses checked success-pending, period-end cancellation and errors. Phone,
  tablet and desktop widths checked without horizontal overflow; light/dark
  screenshots inspected. These implementation browser checks used mocks; the later
  actual Stripe sandbox result is recorded in Current PC setup above.
- Initial test fixture reused fake customer IDs across tests and failed uniqueness
  checks; fixed with unique IDs and rerun successfully. No unresolved automated
  test failures. The first actual Stripe test Checkout, paid subscription lookup,
  CLI forwarding/signature and canonical subscription sync now work end to end.
  User verified portal access and scheduled cancellation through 28/11/2026,
  with CAP retaining active access and displaying the planned cancellation.
  Provider declines/3-D Secure, actual period expiry, renewals and restart
  persistence of this particular provider-created subscription remain unverified.

Run the automated suite with the existing `scripts/test-postgres.ps1` against a
dedicated local `cap_test...` database; it refuses the ordinary `cap` database.
The tests require no Stripe credentials. Keep real Stripe testing separate.

## Decisions required before any launch

Confirm actual plans/prices, billing frequency, tax/VAT treatment, payment methods,
trials, refunds, failed-payment grace/retry rules, cancellation policy and which
features require membership. Also review production security, abuse/rate limits,
webhook queue/monitoring, reconciliation scheduling, backup/retention and Stripe
account ownership. None of those decisions are implied by this temporary test plan.

Implementation references: [Checkout](https://docs.stripe.com/api/checkout/sessions/create),
[webhook signatures/delivery](https://docs.stripe.com/webhooks),
[idempotency](https://docs.stripe.com/api/idempotent_requests).
The direct Java HTTP adapter pins Stripe API `2025-06-30.basil`; subscription periods
are read from subscription items. No new runtime dependency was added.
