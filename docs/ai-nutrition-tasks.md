# Coach Glenn: saved-client nutrition drafts

The coach chat now routes nutrition-plan requests through `/api/ai-coach/nutrition-chat`. This supplements the existing manual nutrition editor and its patch-based AI panel; it does not replace either.

## Data and authorization

`AiNutritionTasks` performs a scoped client-name lookup against active `cap_user` athletes. An exact full name takes priority; multiple matches require clarification. It reads the actual saved `nutrition_target` row (including calculation origin and timestamp), whitelisted `account_profile` fields, and the athlete's approved `nutrition_preference` information. A browser-only unsaved calculator result is unavailable: the coach must save the calculation first.

Every read requires `NUTRITION_MANAGE`, `CLIENT_HEALTH`, client scope and current athlete AI approval. Each additional provider call rechecks authorization, coach settings and the athlete's approval version. Neither payment/password records nor private messages are queried. Athletes and instructors receive no new action permissions.

OpenAI Responses function tools expose scoped client lookup, selected-client nutrition context, canonical food records, a complete-plan proposal, and focused clarification. Fresh backend reads are supplied before generation so known values need not be requested again. Model tool arguments cannot switch the selected client. No model text constitutes evidence of a successful save.

## Task state and dates

V32 stores an actor-owned nutrition task and idempotent turn receipts. The state retains client, Monday–Sunday dates, effective targets, explicit per-plan overrides, draft content and revision, proposal/plan IDs and optimistic versions. It does not store a full transcript. Recent user messages may identify the initial client; subsequent turns use durable state. Changing clients clears the previous client's overrides.

Relative dates use a Brussels clock. Explicit valid Monday–Sunday ranges win; the current week may include earlier days. Without a supplied week, the next Monday is used. Invalid ranges ask for a correction while retaining other task fields. Explicit targets never update `nutrition_target`; missing macros may be derived using 4/4/9 energy arithmetic. Missing calories prompt one focused question.

State expires 24 hours after the last completed turn. Expired rows/receipts are deleted by the minute cleanup and on a new request. The session holds only the task ID for reload recovery. Clearing the conversation deletes its state and cancels its pending proposal. A short processing lease and revision checks prevent simultaneous turns from overwriting one another.

## Validation, review and save

The model chooses complete meals for all seven days using the existing 18 generic CAP food records. CAP ignores model-supplied nutrient values, uses canonical food preparation/unit/nutrition, and adjusts underlying portions with bounded optimization. Every day must be within 5% of calories and 10g protein (configuration properties `cap.ai-nutrition.calorie-tolerance` and `cap.ai-nutrition.protein-tolerance-g`). Explicit carbohydrate/fat overrides are also checked. At least four different daily ingredient compositions are required by default; “Meer variatie” must change at least three days. This is a composition check, not clinical assessment of a diet.

A successfully received invalid plan allows one repair proposal; the total tool loop is capped at four provider calls. Each call reserves the existing conservative budget unit. Uncertain provider failures are never retried automatically. Failure keeps the task and does not save a plan. A failed request may still retain its reservation.

A validated proposal uses `save_nutrition_draft` in the existing actor-owned action confirmation mechanism. Older pending proposals are invalidated on the next task turn. Confirmation rechecks approval, role, canonical nutrition, daily targets and the current meal-plan version. It creates/updates only the draft, and returns an idempotent saved receipt only after the transaction succeeds. Existing published snapshots remain unchanged. Publication remains a separate explicit operation in Voeding.

## Verification and limits

PostgreSQL integration tests use fake clients and a mocked provider to cover lookup ambiguity, saved calculations, missing/overridden goals, food-derived daily totals, follow-ups/reload, tool dispatch, bounded repair, failed requests, idempotency, stale proposals, roles, revocation, and publication separation. Clock tests cover 10/11 November 2026 and the 16–22 November week. Browser fixtures exercise the chat review and composer at 390/768/1440px. No paid provider requests or real client changes are required by these checks.

Remaining limits: generation quality still needs a live provider test after deployment; the catalogue has only 18 generic references. Allergy filtering covers common groups and exact reference names, not every ingredient, cross-contamination risk or medical restriction. The coach must review suitability, preparation and labels before publishing. The assistant may decline an infeasible target/catalogue combination rather than invent food data. The standalone editor's older patch-generation flow retains its existing behavior; the durable, daily-target validation described here applies to nutrition plans created through chat.
