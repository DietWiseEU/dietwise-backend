# 04 — Backend logs Suggestion Events

**What to build:** A suggestion statistics call that carries an `assessmentId` updates the SuggestionStats exactly as
today and also appends one Suggestion Event (the transition, the SuggestionTemplate, the Intervention Assignment, the
time), in the same transaction. A call with an invalid `assessmentId` is rejected and writes nothing. Calls without
`assessmentId` (the RCA, outdated app builds) behave exactly as today. Deleting an account removes its Suggestion
Events.

See the spec `dietwise-architecture/src/site/markdown/specs/intervention-logging.md` (sections Data model,
Statistics, Account deletion) and ADR 0001.

**Blocked by:** 01 — Prefactor: one path for suggestion statistics transitions; 02 — Backend assigns an Intervention
to each assessment.

**Status:** DONE

- [x] Liquibase creates `DW_SUGGESTION_EVENT` (`id` UUID PK, `intervention_assignment_assessment_id` FK to the
      Assignment, `suggestion_template_id` FK, `action`, `created_at`, index on
      `intervention_assignment_assessment_id`). Publish-tool registry unchanged.
- [x] The statistics request body accepts an optional `assessmentId`.
- [x] With a valid `assessmentId`: the counter is updated as today and one event is appended, in one transaction —
      also when the counter does not move (cap or floor).
- [x] Unknown `assessmentId`, or one belonging to another user or application: `404`, nothing written (counter
      included).
- [x] SuggestionTemplate not among the Assignment's Suggestions: `400`, nothing written.
- [x] Repeated calls append repeated rows; nothing is merged or overwritten.
- [x] Deleting an account deletes the user's Suggestion Events before their Assignments, in the existing deletion
      transaction.
- [x] The RCA's statistics requests and responses are unchanged (verified against its request shape/tests).
