# 01 — Prefactor: one path for suggestion statistics transitions

**What to build:** The four suggestion statistics calls (increase/decrease times accepted/rejected) reach the
SuggestionStats through a single service operation that takes the transition as a value (`INCREASE_ACCEPTED`,
`DECREASE_ACCEPTED`, `INCREASE_REJECTED`, `DECREASE_REJECTED`), instead of four parallel methods. The endpoints, their
request and response shapes, and the counter behavior stay exactly as they are. This makes ticket 04 add the
`assessmentId` handling and the Suggestion Event once instead of four times.

Context: spec `dietwise-architecture/src/site/markdown/specs/intervention-logging.md`, ADR 0001.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] The transition is a named type in the service-interface types, with the four values above.
- [ ] The statistics service exposes one operation taking the user, the SuggestionTemplate id and the transition; the
      four per-transition service methods are gone.
- [ ] The four endpoints keep their paths, request body and response, and delegate to the single operation.
- [ ] Counter semantics are unchanged: the `accepted + rejected ≤ suggested` cap, the floor at 0, login and
      application id required.
- [ ] Existing tests of the statistics resource, service and DAO pass, adapted only where they call the removed
      methods; no behavior assertion is weakened.
- [ ] The RCA's and MyRecipeWatch's statistics requests are unchanged.
