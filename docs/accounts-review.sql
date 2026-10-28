-- pgAdmin: connect to localhost:5432 -> Databases -> cap -> Query Tool.
-- These statements only read data. No passwords, password hashes or invitation tokens.
SELECT current_database(), current_user, current_schema();

SELECT version, description, installed_on, success
FROM public.flyway_schema_history
ORDER BY installed_rank;

SELECT id, email, display_name, role, created_at
FROM public.cap_user ORDER BY created_at DESC;

SELECT email, expires_at,
       CASE WHEN accepted_at IS NOT NULL THEN 'used'
            WHEN expires_at <= CURRENT_TIMESTAMP THEN 'expired'
            ELSE 'pending' END AS invitation_status
FROM public.athlete_invitation ORDER BY expires_at DESC;

-- Workouts/assignments currently live as JSON snapshots, not in a table named workout.
SELECT jsonb_array_length(payload::jsonb -> 'workouts') AS saved_workouts,
       jsonb_array_length(payload::jsonb -> 'assignments') AS assignments
FROM public.workout_state;

SELECT workout ->> 'id' AS id, workout ->> 'name' AS name
FROM public.workout_state,
     LATERAL jsonb_array_elements(payload::jsonb -> 'workouts') AS workout;

SELECT s.title, s.session_date, s.start_time, u.display_name AS athlete,
       b.cancelled, b.attendance
FROM public.session_booking b
JOIN public.club_session s ON s.id = b.session_id
JOIN public.cap_user u ON u.id = b.athlete_id
ORDER BY s.session_date, s.start_time;

SELECT u.display_name, p.photo IS NOT NULL AS has_photo
FROM public.account_profile p JOIN public.cap_user u ON u.id = p.user_id;

-- Available after restarting with V6. Saved targets only; no food diary data.
SELECT u.display_name, n.calories, n.protein_g, n.carbs_g, n.fat_g, n.water_ml, n.saved_at
FROM public.nutrition_target n JOIN public.cap_user u ON u.id=n.athlete_id;
