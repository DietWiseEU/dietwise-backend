# 03 — MyRecipeWatch shows the Intervention chosen by the server

**What to build:** MyRecipeWatch (repo `recipe-watch`) sends its current UI language as `uiLang` with every recipe
assessment and shows, above the Suggestions, the Intervention text the backend returned with them. When the backend
returns no Intervention, or the "no Intervention" arm (`0`, no text), no banner is shown. The app no longer picks or
stores Intervention texts itself.

See the spec `dietwise-architecture/src/site/markdown/specs/intervention-logging.md` (section MyRecipeWatch).

**Blocked by:** 02 — Backend assigns an Intervention to each assessment.

**Status:** DONE

- [x] The assessment request carries `uiLang` = the app's active i18n language.
- [x] The `SUGGESTIONS` message's optional `assessmentId` and `intervention` are parsed and kept with the assessment
      state; they are replaced on every new assessment and untouched by accept/reject.
- [x] The banner shows if and only if the current assessment has an Intervention with a non-null text, and shows
      that text verbatim.
- [x] The local random pick, its test, and the `interventions` blocks of all i18n files are removed.
- [x] Cooking mode reset on a new assessment still works as before.
- [x] Verified in the running app against a backend with ticket 02, in at least `en` and one other language,
      including the `0` arm (no banner).
