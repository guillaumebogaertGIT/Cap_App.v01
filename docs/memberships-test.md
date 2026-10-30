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
   .\scripts\stripe-listen.cmd
   ```

3. In a second terminal, from the project folder, run:

   ```powershell
   .\scripts\start-stripe-test.cmd
   ```

   On the first start, enter the **test** secret key, the forwarding terminal's
   signing secret and the PostgreSQL `cap` password privately. Windows encrypts
   them in ignored `.local/dev-secrets.xml`, restricted to your Windows user.
   Later starts reuse them automatically. The nine price IDs/portal load from
   the catalog file. The old EUR10 prompt has been removed; optionally pass
   `-LegacyPriceId price_...` if you still need it. Never paste secrets into chat.

4. Wait for **Started BackendApplication**, then open
   **http://127.0.0.1:8081/app/index.html#shop** and sign in with a fake test athlete.
   Keep both terminals open. This launcher runs the **whole backend**.

The first restart applies migration V9 without resetting existing records.
Later HTML/CSS/JavaScript edits need a browser refresh only. Changes to the
catalog mapping or environment require a backend restart.

For normal restarts, keep forwarding open, stop CAP with Ctrl+C, then run the
same `start-stripe-test.cmd` again. No repeated credential entry is needed.
VS Code also has **Terminal → Run Task → CAP: Start payments development** and
**CAP: Stripe forwarding**.

To replace all saved credentials:

```powershell
.\scripts\start-stripe-test.cmd -RefreshCredentials
```

If a new forwarding listener prints a different `whsec_...`, replace only that:

```powershell
.\scripts\start-stripe-test.cmd -UpdateWebhook
```

To forget the encrypted local credentials (without starting/stopping CAP or
changing any account/database records):

```powershell
.\scripts\start-stripe-test.cmd -ForgetCredentials
```

The file uses Windows DPAPI and only decrypts for the same Windows user on the
same computer. It has no plaintext fallback. On another PC, enter credentials
again; copying the encrypted file will not work. Java DevTools restarts reuse
the existing process environment and never ask for keys.

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

- A **group lesson or Open gym session** uses membership if its date is within the paid access period;
  otherwise it consumes one credit. Future unpaid renewal months are not assumed.
- Retrying the same booking does not spend another credit. The last credit cannot
  be spent on two simultaneous bookings.
- Cancelling **before the lesson starts** returns the credit. A coach cancelling
  the booking or deleting the upcoming lesson also returns it.
- Cancellation after the start is refused. Unused credits never expire.
- Coach-assigned private appointments keep their existing behavior;
  their payment/access rules still need definition. Login, workouts and other
  existing features are not blocked by a membership.

Planning shows the signed-in athlete's available credits, a Credits kopen link,
and refreshes the balance after booking/cancellation, page navigation or returning
to the tab. Loading failures offer Opnieuw laden instead of showing an incorrect zero.

Ordinary accounts mode leaves these paid-booking checks disabled by default. The
design preview remains separate from real account storage.

## Short manual test

1. Sign in as a fresh fake athlete, open Shop, choose **5 lescredits**, and complete
   test Checkout using `4242 4242 4242 4242`, a future expiry and any three-digit
   CVC. Verify the forwarding terminal reports a successful HTTP response.
2. Return to CAP. Shop and Planning should show **5 available credits**. Book a
   future group lesson or Open gym session: 4 remain. Cancel it before the start:
   5 remain. Planning updates the number without a page refresh.
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

Only the two designated membership reviewers can use
**Sporters → Lidmaatschap & credits** and choose an athlete.
The panel shows plan, status, paid-until date, available credits, maximum term
and scheduled cancellation. It refreshes on returning to the page/tab, or with
Opnieuw laden. Switching athletes clears old details immediately. This is a
read-only view of saved PostgreSQL records; it makes no Stripe requests and
cannot cancel subscriptions or change credit balances.

The permission-protected `GET /api/billing/athletes/{athleteId}` returns a small summary
without Stripe customer IDs, prices, portal URLs or payment controls. Direct
guest requests return 401; athletes and non-designated coaches return 403.
Unknown athletes/coach targets return 404. All responses disable caching.

Verification: 21 focused PostgreSQL billing/account tests passed after this
addition. Browser checks covered real fake-account records, empty accounts,
late-response isolation, loading failures/retry and 390/768/1440px in both themes.
To review manually, log in as coach, choose two different athletes and compare
their paid-until date/credits against each athlete's own Shop/Planning.

### Designated reviewers: Glenn and Dieter

The user selected **Glenn Vlamynck and Dieter Vlamynck**. On 30/10/2026 the user
supplied their emails, and the local reviewer file was populated from that source.
Actual CAP COACH accounts with those login emails are still required. Account
creation is separate; the configuration alone does not create or promote users.
On an installation without reviewer configuration, the default remains **no access**.
No display-name matching or automatic permission for the first/test coach is used.
Other coaches keep their roster, planning and workout tools. Everyone can still
view their own payments through their own account.

The local administrator sets
`CAP_MEMBERSHIP_REVIEWER_EMAILS` to those two comma-separated emails. The launcher
can alternatively read ignored `.local/membership-reviewers.json`, with an
`emails` array of at most two addresses. An empty example is:

```json
{"emails": []}
```

Do not guess addresses. This configuration does not create coach accounts. Email
must match the authenticated account and its COACH role; changing a profile name
does not grant permission. Restart after changing the configuration. More than
two unique reviewers or invalid emails cause startup to fail instead of widening
access. This setting also applies directly to the backend, including future
Docker environments. The browser receives only its own permission boolean.

Permission refinement verified with 24 focused checks: two allowed test coaches,
a third denied coach (even with Glenn's display name), default denial, maximum
two/invalid configuration rejection, guest/athlete denials and retained ordinary
coaching access. Browser verified the allowed panel, hidden denied panel with
zero automatic membership requests, and direct API denial. No real coach was
granted access during these tests.

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
User verified the new **5-credit sandbox purchase** on 29/10/2026: CAP showed
5 credits, a future group booking reduced the balance to 4, and cancellation
before the start restored 5. The coach-created shared lesson appeared for the
athlete. These are user-confirmed manual results, separate from mocked tests.
The user also confirmed the EUR75 membership purchase/cancellation flow.
The Open gym extension passed 23 focused billing/planning tests on isolated
PostgreSQL, including no-credit rejection, duplicate bookings, credit return,
membership coverage, concurrent group/Open gym spending and session-type edits.
An actual browser booking on the isolated database showed Planning 5 -> 4 -> 5;
no Stripe calls were made for that fake credit fixture. Both themes at
390/768/1440px and balance-load failure/retry were checked.
Other paid membership checkouts, other credit packs, renewals and actual expiry still
need provider testing. The previously verified EUR10 sandbox checkout remains
documented in `stripe-test-setup.md`.

This is test development. Refunds/disputes and automatic entitlement reversal,
production webhook delivery/reconciliation, VAT, failed-payment collection rules,
private-session pricing and deployment remain separate work. The €65 restriction
prevents early cancellation in CAP; it cannot guarantee collection of 12 payments.
Coach accounts also still need individual email addresses; no shared password or
automatic accounts were created from names alone.

Implementation references: [Stripe subscription updates and proration](https://docs.stripe.com/api/subscriptions/update?api-version=2025-06-30.basil),
[canonical Checkout sessions](https://docs.stripe.com/api/checkout/sessions/retrieve?api-version=2025-06-30.basil).
