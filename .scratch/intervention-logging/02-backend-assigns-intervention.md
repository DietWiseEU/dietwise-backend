# 02 — Backend assigns an Intervention to each assessment

**What to build:** When MyRecipeWatch assesses a recipe and sends its UI language (`uiLang`), the backend picks an
Intervention uniformly among the active ones (`0`, "no Intervention", included), records the Intervention Assignment
with the Suggestions shown under it and the recipe URL, and returns an `assessmentId` and the Intervention's id and
text (in `uiLang`, falling back to `en`) with the Suggestions. Clients that send no `uiLang` (the RCA, outdated app
builds) get exactly today's response and nothing is recorded. Deleting an account removes its Assignments.

See the spec `dietwise-architecture/src/site/markdown/specs/intervention-logging.md` (sections Data model, Assessment,
Account deletion) and ADR 0001 for the decisions; do not re-open them.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Liquibase creates `DW_INTERVENTION` (manual integer id, `active`, `text_en/nl/el/lt`,
      `CHECK (id = 0 OR text_en IS NOT NULL)`), seeded with `0` (no texts) and `1..5` copied verbatim from
      MyRecipeWatch's `en/nl/el/lt.json` `interventions` blocks, all active. No Working Copy, publish-tool registry
      unchanged.
- [ ] Liquibase creates `DW_INTERVENTION_ASSIGNMENT` (`assessment_id` UUID PK, `user_id`, `application_id`,
      `intervention_id`, `ui_lang` actually used, `recipe_url`, `created_at`) and
      `DW_INTERVENTION_ASSIGNMENT_SUGGESTION` (`assessment_id`, `suggestion_template_id`), with foreign keys.
- [ ] Both assessment requests (URL and markdown) accept an optional `uiLang`.
- [ ] With `uiLang`, active Interventions and at least one Suggestion: one Assignment and one row per Suggestion shown
      are written in the same transaction as the times-suggested counters, and the `SUGGESTIONS` message carries
      `assessmentId` and `intervention {id, text}` (`text` null for `0`).
- [ ] Without `uiLang`, with no active Intervention (kill switch), or with no Suggestions: no rows, and the
      `SUGGESTIONS` message has no `assessmentId`/`intervention`.
- [ ] A `uiLang` without a text column yields the `en` text and records `en` as `ui_lang`.
- [ ] The random pick is injectable; tests fix it. Picking is uniform over the active rows, read on every assessment.
- [ ] Deleting an account deletes the user's Assignment Suggestions and Assignments, in the existing deletion
      transaction.
- [ ] The RCA's assessment requests and response stream are unchanged (verified against its request shape/tests).
- [ ] `CONTEXT.md` terms (Intervention, Intervention Assignment) are used in names.
