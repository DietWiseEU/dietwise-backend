# 05 — MyRecipeWatch sends the assessmentId with each accept/reject

**What to build:** When a user accepts, rejects or undoes a Suggestion in MyRecipeWatch, every resulting statistics
call carries the `assessmentId` of the assessment that produced the Suggestion. This closes the loop: each decision is
logged as a Suggestion Event tied to the Intervention the user was seeing.

See the spec `dietwise-architecture/src/site/markdown/specs/intervention-logging.md` (section MyRecipeWatch).

**Blocked by:** 03 — MyRecipeWatch shows the Intervention chosen by the server; 04 — Backend logs Suggestion Events.

**Status:** ready-for-agent

- [ ] Every statistics call (including the implicit "un-accept" of a previously accepted Suggestion for the same
      ingredient) includes the current assessment's `assessmentId` when there is one, and omits it otherwise.
- [ ] Existing statistics behavior (sequencing, in-flight locking, timeout, error logging) is unchanged.
- [ ] Verified end to end in the running app: an accept → undo → reject sequence produces the expected event rows,
      joined to the Assignment and its Intervention.
