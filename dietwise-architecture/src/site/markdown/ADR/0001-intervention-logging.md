# ADR 0001: Log Intervention Assignments and Suggestion Events in the backend

## Status

Accepted, 2026-10-22.

## Context

MyRecipeWatch shows a randomly picked motivational message (an Intervention) above the Suggestions. The research
partners need to test the Interventions statistically (Grant Agreement KPI-26), including a "no Intervention" arm, and
relate each accept/reject decision to the Intervention shown at the time. Until now the pick and the texts lived only
in the app, and the backend kept only aggregate counters (`DW_USER_SUGGESTION_STATS`). The RCA shares the statistics
endpoints and must not be affected. Details are in [the spec](../specs/intervention-logging.md).

## Decision

- **The backend picks the Intervention**, per assessment, uniformly among the active ones, and logs the Assignment
  together with the Suggestions shown. It sends the text and an `assessmentId` to the app.
- **The database is the single source of the Intervention texts.** An Intervention id names one wording forever:
  changing the meaning means a new id.
- **Decisions are logged as an append-only event log**, one row per statistics call, alongside the unchanged counters
  and in the same transaction.
- **Only clients that send their UI language get an Intervention.** That excludes the RCA and outdated app builds, so
  the log never claims an Intervention the client did not display from the server.

## Alternatives rejected

- **Aggregate counters per Intervention**: cannot answer questions about time or order, and cannot be turned into
  events later.
- **Merging events within a time window**: erases undo/hesitation, makes results depend on an arbitrary threshold, and
  needs read-modify-write under concurrency. The volume does not justify it.
- **The app picks the Intervention**: the impression would need a separate, losable call, and the assignment policy
  would change only with app releases.
- **Texts in both the database and the app**: the copies drift, and a new Intervention would reach outdated apps
  without a text, silently landing their users in the "no Intervention" arm.
- **Per-user (sticky) assignment**: cleaner between-subjects comparison, but needs far more users for six arms.

## Consequences

- What is logged is the assignment, not the display (intention-to-treat).
- The same user sees different Interventions; the analysis must account for repeated measures.
- Adding an Intervention or switching one off is a database change, without an app release. Deactivating all of them
  turns the experiment off.
- Account deletion removes the user's Assignments and events, as for the other statistics.
