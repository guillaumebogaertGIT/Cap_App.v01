# CAP memberships and credits — local test guide

Updated 29/10/2026. All payments stay in Stripe **sandbox/test mode**.
The nine prices and a restricted customer portal have already been prepared in
the existing CAP sandbox on this PC. Their IDs are saved in the ignored file
`.local/stripe-test-catalog.json`. That file contains no passwords or API keys.

## Start and open CAP

1. Stop the old CAP backend terminal with **Ctrl+C**, then **Y** if asked.
2. Keep the Stripe forwarding terminal open. If it is closed, open a terminal
   in the project folder and run:

   ```powershell
   stripe.cmd listen --events checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,invoice.paid,invoice.payment_failed,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,customer.subscription.paused,customer.subscription.resumed --forward-to http://127.0.0.1:8081/api/billing/webhook
   ```

3. In a second terminal, from the project folder, run:

   ```powershell
   .\scripts\start-stripe-test.cmd
   ```

   Enter the **test** secret key and the forwarding terminal's signing secret
   privately. The launcher now loads the nine price IDs and portal automatically.
   The optional old EUR10 price prompt is only for retaining the old test
   subscription in the status display; press Enter to skip it. Enter the
   PostgreSQL `cap` role password when prompted. Never paste secrets into chat.

4. Wait for **Started BackendApplication**, then open
   **http://127.0.0.1:8081/app/index.html#shop** and sign in with a fake test athlete.
   Keep both terminals open. This launcher runs the **whole backend**.

The first restart applies migration V9 without resetting existing records.
Later HTML/CSS/JavaScript edits need a browser refresh only. Changes to the
catalog mapping or environment require a backend restart.

## Offers and rules

| Offer | Payment | End |
| --- | --- | --- |
| Full access — 1 month | EUR90 once | After 1 calendar month |
| Full access — 3 months | EUR225 once | After 3 calendar months |
| Full access — 1 year | EUR750 once | After 12 calendar months |
| Full access — cancellable | EUR75/month | After 12 months at the latest; cancellation stops payments after the paid month |
| Full access — fixed | EUR65/month | After 12 months; no self-service early cancellation |
| 1 / 5 / 10 / 20 credits | EUR18 / EUR80 / EUR140 / EUR240 once | No expiry for unused credits |

Prepaid memberships start at the confirmed payment time, with calendar-month
expiry calculated in Europe/Brussels. Monthly subscriptions have a maximum term
of 12 months from Stripe's original billing anchor. The webhook/reconciliation
sets Stripe's `cancel_at` date, with no proration. CAP also checks the term locally.
An active subscription needs a paid current invoice; failed payments don't grant
access. No grace or trial is introduced. A fixed plan lasts 12 calendar months;
failed instalments do not extend the term.

The €75 plan has **Opzeggen na betaalde maand** inside CAP. The new Stripe portal
handles invoices/payment methods, with cancellation, plan changes and pausing
disabled. This protects the €65 commitment and the maximum term. The old EUR10
test subscription remains a separate legacy test record; it is not converted.
Use a fresh fake athlete to try a new membership if that account still has an
active legacy subscription. CAP refuses overlapping membership purchases.

The catalog launcher enables `CAP_TEST_PAID_BOOKINGS=true`:

- A **group lesson** uses membership if its date is within the paid access period;
  otherwise it consumes one credit. Future unpaid renewal months are not assumed.
- Retrying the same booking does not spend another credit. The last credit cannot
  be spent on two simultaneous bookings.
- Cancelling **before the lesson starts** returns the credit. A coach cancelling
  the booking or deleting the upcoming lesson also returns it.
- Cancellation after the start is refused. Unused credits never expire.
- Open gym and coach-assigned private appointments keep their existing behavior;
  their payment/access rules still need definition. Login, workouts and other
  existing features are not blocked by a membership.

Ordinary accounts mode leaves this group-booking gate disabled by default. The
design preview remains separate from real account storage.

## Short manual test

1. Sign in as a fresh fake athlete, open Shop, choose **5 lescredits**, and complete
   test Checkout using `4242 4242 4242 4242`, a future expiry and any three-digit
   CVC. Verify the forwarding terminal reports a successful HTTP response.
2. Return to CAP. The Shop should show **5 available credits**. Book a future group
   lesson: 4 remain. Cancel it before the start: 5 remain.
3. In a different fake athlete account, buy the **EUR75/month** plan. Check the
   paid access date and maximum end date. Cancel in CAP: access stays active until
   the paid month ends. Another athlete must not see this membership or credits.
4. Test the EUR65/month plan with a separate account. It must not offer early
   cancellation, including through a direct `/api/billing/cancel` request.
5. Restart CAP, log in again, and check saved balances/status. If forwarding was
   missed, use **Status bijwerken**. Never buy again just to recover a missing
   status. Unknown/lost Checkout attempts retain the existing safe retry checks.

## Setting up another development PC

Install/login to Stripe CLI, select the same CAP sandbox, then run:

```powershell
.\scripts\setup-stripe-catalog.cmd
```

Enter the sandbox account ID from the Dashboard URL (`acct_...`, not a secret).
The script checks the account, reuses prices by stable lookup keys and refuses
unexpected amounts or recurrence. It creates only missing sandbox catalog
objects and a separate restricted portal; it does not create customers/payments
or alter existing subscriptions. Re-running it was verified to keep the same IDs.

## PostgreSQL inspection

In pgAdmin open **cap → Schemas → public → Tables**. V9 adds `billing_order` and
`lesson_credit`, and extends `billing_subscription` with `offer_code`/`term_end`.
Orders record the server-selected purchase. Each credit has one row: a null
`session_id` means available; a session ID means used for that booking.

```sql
SELECT u.display_name, o.offer_code, o.status, o.paid_at, o.period_end
FROM public.billing_order o JOIN public.cap_user u ON u.id = o.user_id
ORDER BY o.created_at DESC;

SELECT u.display_name,
       COUNT(*) FILTER (WHERE c.session_id IS NULL) AS available,
       COUNT(*) FILTER (WHERE c.session_id IS NOT NULL) AS used
FROM public.lesson_credit c JOIN public.cap_user u ON u.id = c.user_id
GROUP BY u.id, u.display_name;

SELECT u.display_name, s.offer_code, s.status, s.payment_status,
       s.period_end AS paid_until, s.term_end, s.cancel_at
FROM public.billing_subscription s JOIN public.cap_user u ON u.id = s.user_id;
```

## Verification and limits

**74 backend tests passed** against isolated PostgreSQL `cap_test` on
55433, with Stripe mocked. The suite covers the existing accounts/core flow and the new
catalog, immutable server prices, unpaid/expired rejection, owner isolation,
duplicate events, lost Checkout response recovery, calendar expiry, finite
monthly terms, flexible/fixed cancellation, credit return, concurrent last-credit
bookings and restart retention. Test fixtures never reset the development `cap` DB.

Browser checks use the real app and CSS with mocked billing responses. They cover
Shop filters (five memberships/four credit packs), offer selection, detached
Checkout tabs, flexible cancellation display, 390/768/1440px layouts and both
light/dark themes. JavaScript syntax and PowerShell parsing checks also passed.
Sandbox catalog configuration is real; payments for these **new nine offers**,
renewals and actual expiry still need provider testing. The previously verified
EUR10 sandbox checkout remains documented in `stripe-test-setup.md`.

This is test development. Refunds/disputes and automatic entitlement reversal,
production webhook delivery/reconciliation, VAT, failed-payment collection rules,
open-gym/private pricing and deployment remain separate work. The €65 restriction
prevents early cancellation in CAP; it cannot guarantee collection of 12 payments.
Coach accounts also still need individual email addresses; no shared password or
automatic accounts were created from names alone.

Implementation references: [Stripe subscription updates and proration](https://docs.stripe.com/api/subscriptions/update?api-version=2025-06-30.basil),
[canonical Checkout sessions](https://docs.stripe.com/api/checkout/sessions/retrieve?api-version=2025-06-30.basil).
