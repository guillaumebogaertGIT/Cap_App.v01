# Client-first nutrition flow

## Navigation

- Athletes open Voeding with three choices: Mijn dagdoelen, Voedingsschema, Mijn voeding. The existing scan/search/diary remains inside Mijn voeding. Saved dietary preferences and AI approval remain with the goals.
- Authorized nutrition coaches open Voeding with Mijn voeding or Klantvoeding. Klantvoeding requires an explicit client selection before showing diary, saved schemas/history, schema creation and goal calculation.
- Creation offers manual or AI. The AI path pins the selected client ID and editor week in the authorized nutrition task API, reads saved needs, and places the validated proposal in the existing editable editor. Extra instructions are optional. It no longer uses the older independent patch generator or requires retyping stored targets.

## Save and assignment

The editor saves a private version for the selected client. V33 records every saved revision of existing `meal_plan` records in `meal_plan_revision`, seeding current records during migration. History is scoped and paginated in groups of20. Opening an older version prepares an editable draft; saving creates a further revision rather than deleting history.

Assignments use `/api/meal-plans/{id}/assignments` with an idempotent reference, expected saved version, inclusive start/end dates and explicit overlap approval. The end must be on/after start, at most365 days later. A saved content snapshot is stored independently in `meal_plan_assignment`; later draft edits cannot change it. Overlapping assignments require confirmation; the latest snapshot takes precedence only on the dates it covers. The week pattern follows actual weekdays and repeats over the selected range. Existing legacy weekly publications remain visible outside overriding assignments.

`/api/meal-plans/self/day` returns only the authenticated athlete's applicable day. New meal logging uses the selected assignment/date/meal as an idempotency key, delegates actual nutrient/ingredient snapshots to the existing food diary, rejects future dates and detects changed assignments. The legacy published-plan logging path remains available for older records.

Saving is separate from assigning. Success/error messages stay beside the relevant controls. Hidden required fields no longer stop the save handler before it can reveal invalid meal inputs. Chat save receipts provide an explicit link to open the saved client schema, avoiding an older browser draft hiding it.

## Permissions and scope

Coach reads/writes retain `NUTRITION_MANAGE` and client scope. AI additionally retains health permission, coach settings, tester/budget checks and current athlete AI approval. Selected IDs do not bypass scope or approval. Athletes cannot read schema history, assign or alter another client's plan. Blank allergy text means not provided/not checked; no detection is claimed.

## Verification and remaining limits

53 isolated PostgreSQL regressions passed, followed by13 focused AI tests after the pinned-client ambiguity guard. These cover flexible dates, snapshot isolation, history, precedence, roles/CSRF, idempotent logging, existing weekly publication, and saved-target generation with a selected client whose name is ambiguous. Browser tests use fake API responses or the browser-local preview, covering manual creation/save, AI generation/save, reopening/editing history, explicit date assignment, athlete day display, return navigation, and390/768/1440px widths. JavaScript syntax checks and the scoped design detector passed. No real client changes or paid OpenAI calls were made.

The schema is still a seven-weekday pattern, not a different custom menu for every date of a long range. The underlying editable draft remains unique per client/base week; all saved versions are retained, and assignments use independent snapshots. Reference-food breadth and clinical/allergy review limits from `ai-nutrition-tasks.md` still apply. Existing assignment snapshots have no withdrawal/edit interface in this first flow; overlapping replacement is explicit. Live AI generation and Render deployment remain to be verified after release.
