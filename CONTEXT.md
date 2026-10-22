# DietWise

Backend for assessing recipes from a web page and suggesting healthier, more sustainable ingredient alternatives. Serves the MyRecipeWatch mobile app and the Responsible Cooking Alliance (RCA) browser extension.

## Language

### Applications

**MyRecipeWatch** (MyRW):
The mobile app. Used to assess recipes a user cooks for themselves and their household.

**RCA** (Responsible Cooking Alliance):
The browser extension. Used by a content provider to assess recipes they *publish*.

_The same person may use both from one account, but the intent differs (cook-for-self vs. publish), so user-scoped data is kept separate per application._

### Domain

**Recipe**:
A set of ingredients and instructions extracted from a web page. Extracted fresh on each assessment and not persisted; located by its source URL.

**Ingredient**:
An item in a recipe's ingredient list. Not stably identifiable across assessments — its in-recipe id is regenerated every extraction.

**TriggerIngredient**:
Master data naming an ingredient (or class of ingredients) whose presence in a recipe can trigger a substitution Rule. It also carries the Components a recipe ingredient matched to it contributes to the score of the recipe, curated by the scientific partners.
_Avoid_: trigger

**Scoring-only TriggerIngredient**:
A TriggerIngredient without active Rules. It suggests nothing, but its Components still count in the score of the recipe. A supported convention, not an error.

**Component**:
A GBD Recommendation as used for scoring, named by its component for scoring and either ENCOURAGED (raises the score) or LIMITED (lowers it). Identified by the Recommendation's master name, which is language-independent; the component name itself is localized.
_Avoid_: dietary component, recommendation component

**Primary component**:
For one ingredient of one assessed recipe, the Component of the Recommendation of the Rule selected for it. Always among the ingredient's Components; absent when no Rule was selected.

**AlternativeIngredient**:
Master data naming a healthier or more sustainable substitute that a Suggestion proposes in place of a recipe ingredient.
_Avoid_: replacement, substitute

**Rule**:
Master data tying a TriggerIngredient (and optional role/technique) to a health Recommendation and the alternatives that satisfy it.

**SuggestionTemplate**:
Master data pairing a Rule with one AlternativeIngredient (plus restriction, equivalence, and technique notes) — a reusable, recipe-independent "replace X with Y" definition.

**Suggestion**:
A SuggestionTemplate instantiated against a specific recipe during assessment: it knows what it applies to (an ingredient or the whole recipe) and carries rationale, seasonality, cost, and stats. Generated fresh per assessment; never persisted.

**SuggestionDecision**:
A user's standing accept/reject verdict on one Suggestion, for one recipe. Current state (last-write-wins), not a history of actions.
_Avoid_: SavedDecision, saved suggestion, suggestion feedback, choice

**SuggestionStats**:
Aggregate counts of accept/reject *events* for a SuggestionTemplate. Answers "how often", whereas a SuggestionDecision answers "what is this user's verdict now, for this recipe".

**Intervention**:
A fixed motivational message shown in MyRecipeWatch above the Suggestions, curated by the marketing partners. Id `0` is the "no Intervention" arm of the experiment. An id names one wording forever: a change of meaning is a new id. Switched on and off by its `active` flag directly in master data, not through the Working Copy.
_Avoid_: banner, nudge, motivational message

**Intervention Assignment**:
The Intervention the backend picked for one assessment, with the Suggestions shown under it; identified by the `assessmentId`. Records what was assigned, not what was displayed.
_Avoid_: impression, exposure

**Suggestion Event**:
One accept/reject transition a user made on one Suggestion of an Intervention Assignment. Append-only history, unlike a SuggestionDecision; recorded alongside, not instead of, the SuggestionStats.

### Backoffice

**Working Copy**:
The single shared staging area where backoffice editors' changes to master data accumulate before going live. System-wide, not per-editor; invisible to recipe assessment until published.
_Avoid_: draft, sandbox, staging

**Staged Change**:
One not-yet-published edit to master data, held in the Working Copy.
_Avoid_: pending edit, delta, diff

**Publish**:
Applying the entire Working Copy to the live master data as a single batch, after which the changes take effect in assessments.
_Avoid_: apply, commit, save, deploy

**Deactivate / Activate**:
Marking a *published* Rule or SuggestionTemplate so recipe assessment stops (resp. resumes) using it. Reversible, staged like any other change, and the entity stays visible while deactivated. Deactivating a SuggestionTemplate is how an editor "removes" it from a Rule without losing it or its history.
_Avoid_: delete, remove, disable, archive

**Discard**:
Dropping a new Rule or SuggestionTemplate that exists only in the Working Copy and was never published. Distinct from Deactivate, which applies to already-published entities.
_Avoid_: delete, cancel, undo

**Revert**:
Restoring a single changed field of an already-published entity in the Working Copy back to its live master value. Per-field; distinct from Discard, which drops an unpublished new Rule wholesale.
_Avoid_: undo, reset, rollback, cancel
