# Accounts and access — provisional design

Public signup is the current proposal, pending CAP approval. Signup creates an
athlete account, never a coach or administrator. Club administrators grant staff
roles separately. Role and subscription are independent concepts.

## Current frontend preview

- Login and registration simulate entry to the existing Guillaume demo.
- Form names, email addresses, passwords and checkbox values are not saved or sent.
- No credential verification, account creation, email verification or password reset occurs.
- Logout hides the app; it does not clear the existing local club example data.
- Each entry starts with no subscription. Test controls can change roles and plans.
- None: no workout creation or bookings. Group example: group bookings only.
  Extended example: workout creation and group/private bookings. These are test
  combinations, not commercial products, pricing or final CAP entitlements.
- Coach tools require the coach role, never a subscription. Subscription test
  controls are hidden in coach mode; athlete booking restrictions remain active.
- Workout creation currently requires the coach preview role. Whether an
  athlete package should allow personal workout creation remains a CAP decision.
- Cancellations remain available without a current booking entitlement.

## Backend work before real use

Agree on framework/database first. Store users, securely hashed passwords,
staff roles, subscription status/expiry, configurable entitlements and assignments.
Enforce roles and entitlements on every protected API operation. Never trust the
preview selector or browser storage. Check booking capacity atomically.
Implement secure sessions, logout, email verification, password reset, rate limits
and appropriate handling of account deletion and personal data.

## Legal review before launch

The signup panels are draft scaffolds, not approved terms or a complete privacy
notice. CAP must supply its legal entity/contact details, packages, cancellation
and renewal rules, consumer-rights arrangements, minor-user policy, processors,
retention periods and actual data-processing purposes/legal bases.

Have the final documents reviewed for the actual service and jurisdiction.
Record accepted terms version and timestamp server-side. Keep optional marketing
separate and unchecked, with withdrawal available. Do not use acceptance of terms
as blanket consent for all personal-data processing.

Reference reviewed for the design:
https://www.edpb.europa.eu/sme/be-compliant/process-personal-data-lawfully_en

## Verification

JavaScript syntax and diff checks passed. Browser/device interaction testing is
still required, including registration validation, legal-dialog focus, logout,
plan restrictions, phone keyboards and both themes.
