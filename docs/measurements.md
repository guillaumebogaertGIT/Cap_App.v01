# Shared account measurements

In real account mode, saving a changed profile height or weight also adds a measurement. Saving the same values again does not add another row. The existing weight log and body-measurement form save to the same PostgreSQL history; a new height or weight entered there updates the profile too.

Athletes see their own data in **Gezondheid & Progress**. Coaches have **Mijn gezondheid** for themselves and **Gezondheid sporters** for selected athletes. Colleague details never include health measurements. Existing profile values without measurement history appear with an unknown date; CAP does not invent when they were measured.

Migration V12 adds `public.account_measurement` in the existing `cap` database. It does not delete or backfill profiles. The interface loads the latest 200 entries; older rows remain in PostgreSQL. Browser preview mode stays separate and is not imported automatically.

Read-only inspection in pgAdmin, choosing database **cap**, schema **public**:

```sql
SELECT id, display_name, role FROM cap_user ORDER BY display_name;
SELECT user_id, recorded_at, source, values_json
FROM account_measurement ORDER BY recorded_at DESC LIMIT 20;
```

Manual check:

1. Save a changed height/weight in your profile; open your health page and measurement history.
2. Save the same profile again: no duplicate measurement should appear.
3. Add a weight using the weight log; reopen the profile and refresh the page.
4. As coach, select the test athlete in Gezondheid sporters, add a measurement, then refresh the athlete's health page in their separate browser session.
5. Restart CAP: saved measurements should remain. Existing undated profile values should stay undated until a new measurement is saved.

Automated checks use fake accounts in isolated PostgreSQL, including role/ownership denial, CSRF, validation, logout/new login, profile/history synchronization and unchanged saves. Responsive browser checks cover 390, 768 and 1440 pixel widths in the existing themes. No real user's measurements were changed for verification.
