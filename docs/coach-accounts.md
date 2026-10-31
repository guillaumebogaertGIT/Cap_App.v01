# Coach accounts (local development)

The approved local app uses OWNER/MANAGER/INSTRUCTOR/FINANCE staff roles. V13 and the verified account-ID mapping were explicitly approved and activated; see [staff RBAC](staff-rbac.md). Existing passwords and historical records were preserved.

Created on 30/10/2026 in the existing local PostgreSQL `cap` database:

| Coach | Login email | Access |
| --- | --- | --- |
| Glenn Vlamynck | hello@cap-gistel.be | Admin (OWNER), full access |
| Dieter Vlamynck | dieter.vlamynck@gmail.com | Admin (OWNER), identical full access |
| Mathias Marchand | mmarchand@hotmail.be | INSTRUCTOR, linked clients and own lessons |
| Marlies Vermeersch | marliesvermeesch@hotmail.com | INSTRUCTOR, linked clients and own lessons |
| Robin Van geluwe | robinvgeluwe@gmail.com | INSTRUCTOR, linked clients and own lessons |
| Vince Decraemer | vince.decraemer@hotmail.com | INSTRUCTOR, linked clients and own lessons |

Maxim, Vic and the marketing contact remain pending. All six retain the COACH account type; staff_role determines permissions. Display titles do not grant access. No messages or invitations have been sent. Original Coach Guillaume is DEVELOPMENT and cannot log in by default; his separate ATHLETE account is unchanged.

## Collega’s

### Glenn: coaches toevoegen en verwijderen

Glenn's configured account (`hello@cap-gistel.be`) sees **Coach toevoegen**. Enter name, email, a display title and a temporary password. Share that password privately; the coach can change it through **Wachtwoord**. New accounts always receive COACH access, never staff management or finance access from the selected title. Passwords are hashed and are not returned, logged, emailed or automatically added to `login.txt`.

Click a colleague to choose **Coach deactiveren**. This is the removal operation: it disables login, expires sessions and removes the coach from ordinary colleague lists/new lesson choices, while retaining accounts, profiles, existing lessons, bookings and history. Existing future lessons are not canceled or reassigned automatically; Glenn can edit their coach if needed. Glenn can still see inactive entries and choose **Opnieuw activeren**, retaining the current password. Old sessions do not regain access; the coach signs in again. Glenn cannot deactivate his own account.

This is a separate permission with one configured login email, independent of displayed Admin/Manager labels, finance access or membership review. The local launchers read ignored `.local/staff-manager.json` (`email` field); other environments use `CAP_STAFF_MANAGER_EMAIL`. Empty configuration denies staff administration to everyone. Other coaches keep read-only access to active colleagues; athletes/guests cannot access staff management. Existing athlete accounts are never silently promoted when an email collides.

V11 adds `cap_user.active` (existing accounts default active). Spring login rejects disabled accounts, existing session registration is expired on activation changes, and an indexed activation check on private requests also covers stale sessions/concurrent login. Static assets and public login/health pages do not perform this extra lookup. Staff changes log actor/target IDs and action, not credentials.

Endpoints: `POST /api/coaches` creates a COACH; `PUT /api/coaches/{id}/active` sets activation with `{ "active": false }` or `true`. Both require the configured manager and CSRF. There is no hard-delete endpoint. Password recovery/reset and editing existing titles remain separate future work.

### Contactgegevens bekijken

Every signed-in coach can open **Collega’s** in the sidebar (Menu on phones), then click a colleague to view their title, email and optional phone. The directory includes only existing COACH accounts; pending staff are not added automatically. Own entry is marked “jij”. Contact details are not available to athletes or guests.

V10 adds `cap_user.coach_title` and labels the six existing supplied coach emails from the screenshots. Other/new coaches default to “Coach”. These labels are display information, not authorization; profile editing cannot change titles or privileges. Title administration remains future work.

Coaches can add or remove their optional phone through their name → **Profiel** → **Telefoonnummer** → **Profiel bewaren**. The field explains that the number is shared with colleagues. No phone numbers were invented or imported. Colleague responses include only name, title, email and phone, never date of birth, measurements, health details, password hashes or payment data.

Read-only, no-store endpoints: `GET /api/coaches` (IDs/names/titles) and `GET /api/coaches/{id}` (contact details). Guests receive 401, athletes 403, non-coach targets 404. No automatic emails or calls are sent by CAP; contact links open the device's mail/phone app when clicked.

## Sign in locally

Active COACH accounts have coaching access without payment or expiry. Their Home shows this explicitly; Shop and personal Betalingen are absent. Self-service checkout/portal/reconcile/cancel APIs reject coaches. This is role-based access, not a Stripe lifetime subscription. Athlete subscriptions are unchanged, and membership/finance review permissions remain restricted as listed above. Existing Stripe records are not deleted or automatically canceled.

1. Stop the old backend with Ctrl+C (Y if asked), then from the project root run `.\scripts\start-stripe-test.cmd`. Keep Stripe forwarding running separately if testing payments. This restart loads the current code and local permission configuration.
2. In another terminal run `.\scripts\coach-login.cmd`.
3. The command opens CAP's login page. Enter `hello@cap-gistel.be` and paste the temporary password with Ctrl+V. The password is copied privately to your clipboard, never printed here. Clear clipboard/history when finished; clipboard sync/history may retain copied text.

For another coach: `.\scripts\coach-login.cmd -Email 'dieter.vlamynck@gmail.com'` (substitute their email). The command does not change the password or sign in automatically. If that coach already changed it, the saved temporary password no longer works.

## Password handover and changing it

Each coach has a different cryptographically generated temporary password. PostgreSQL stores only BCrypt hashes. Temporary handover values are encrypted with Windows DPAPI in ignored `.local/coach-secrets.xml`, readable only by the same Windows user on this PC, with a user-only file ACL. They are separate from resettable Stripe/database development credentials. Do not commit or send these files. No passwords are hardcoded in source or shown in logs.

Give each coach only their own login details privately. Once signed in, open their name at the top right → **Wachtwoord**. Enter the current password and the new password twice. Minimum 12 characters, maximum 72 UTF-8 bytes. A successful change ends that account's registered sessions, including the current session; sign in again with the new password. It does not affect other users. Temporary passwords do not expire automatically or force a first-login change in this version.

These accounts currently exist only on your local development server. Coaches cannot open your `127.0.0.1` link from their devices; deployment/shared access is still required.

## Setup tool

`.\scripts\create-coach-accounts.cmd` explicitly creates the six listed coaches through a local JDBC CLI, not a web endpoint or automatic startup seed. It reads the existing encrypted database credential, or privately prompts if missing. It saves each temporary password encrypted before inserting the batch. It requires the existing schema; it never starts a temporary database.

Re-running keeps existing coach hashes/names and does not reset passwords. A collision with an athlete email rolls back the entire batch. Setup does not grant arbitrary staff financial access, promote athletes, delete accounts or send email. Recovery/reset and staff administration UI remain future work.

Verification: actual login for all six succeeded against local CAP. That older running process still had both review permissions false; restart is required to load ignored reviewer configuration. Password change, old-password rejection, CSRF, ownership/session termination, provisioning rollback/idempotence tested separately with fake accounts in isolated PostgreSQL. No real coach password was changed during testing.
