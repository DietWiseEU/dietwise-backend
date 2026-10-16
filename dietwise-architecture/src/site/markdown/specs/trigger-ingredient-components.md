# Components for scoring per Trigger Ingredient

## Problem

The GBD-based rating needs to know which GBD components (rows of `DW_RECOMMENDATION`, identified by
`component_for_scoring`) each recipe ingredient contributes. Today, an LLM call
(`IngredientMatchInRecommendationsAiService.matchIngredientsWithRecommendations`) guesses the components for every
ingredient, from the ingredient name alone. The result is not vetted by the scientific partners, is non-deterministic,
and is lost entirely whenever the ingredient's Trigger Ingredient has no active Rules.

The scientific partners will instead provide a fixed mapping from each Trigger Ingredient to its components, the same
way `AlternativeIngredientEntity.componentsForScoring` maps Alternative Ingredients to components.

## Terms

- **Component**: a GBD recommendation used for scoring; a row of `DW_RECOMMENDATION`, named by its
  `component_for_scoring` and typed `ENCOURAGED` or `LIMITED`.
- **Components of a Trigger Ingredient**: the fixed set of components curated by the partners for a Trigger Ingredient.
- **Primary component**: for one ingredient of one assessed recipe, the Recommendation of the Rule selected for that
  ingredient. It is always part of the ingredient's components. An ingredient has no primary component when no Rule
  was selected for it (no Trigger Ingredient matched, the Trigger Ingredient has no active Rules, or Rule selection
  failed).
- **Scoring-only Trigger Ingredient**: a Trigger Ingredient with no active Rules. It produces no Suggestions, but its
  components count toward the rating. This is a supported convention, not an error.

## Decisions

1. Each ingredient is matched to a Trigger Ingredient as now. A matched ingredient's components are the components of
   its Trigger Ingredient, plus its primary component.
2. Components no longer depend on Rules. A Trigger Ingredient with zero *active* Rules still contributes its components.
3. An ingredient that matches no Trigger Ingredient (the trigger AI answers `unknown`, or a name not in the database)
   gets its components from the existing `IngredientMatchInRecommendations*AiService`, repurposed as a fallback. It has
   no primary component.
4. The primary component is explicit in the data sent to the clients. The score calculation that uses it is out of
   scope.
5. The components of every existing Trigger Ingredient are seeded from the Recommendations of its Rules, as they are.
6. The trigger-matching prompt is not changed.

## Data model

New master link table, mirroring `DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING`:

- `DW_TRIGGER_INGREDIENT_COMPONENTS_FOR_SCORING (trigger_ingredient_id, recommendation_id)`, composite PK, foreign
  keys to `DW_TRIGGER_INGREDIENT.id` and `DW_RECOMMENDATION.id`.
- JPA: `TriggerIngredientEntity.componentsForScoring`, `@ManyToMany Set<RecommendationEntity>`.

New Working Copy table, mirroring `DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC`:

- `DW_TRIGGER_INGREDIENT_COMPONENTS_FOR_SCORING_WC (trigger_ingredient_id, recommendation_id, present)`, sparse,
  no foreign keys; `present` is the absolute staged presence.
- JPA: `TriggerIngredientComponentForScoringWcEntity`.

Seed (Liquibase, in the same changelog): `INSERT ... SELECT DISTINCT trigger_ingredient_id, recommendation_id FROM
DW_RULE`, so that each environment is seeded from its own data. All Rules are included, active or not, so that a
Trigger Ingredient whose only Rule is inactive (e.g. "Cream") keeps a sensible starting point.

## Publish tool

Add a `LINK_DELTA` mirror at order 1 to `eu.dietwise.tools.publish.schema.Tables`, with
`ParentRef("trigger_ingredient_id", "DW_TRIGGER_INGREDIENT")` and `ParentRef("recommendation_id", "DW_RECOMMENDATION")`
so that a staged link to a Trigger Ingredient that will not exist in master is dropped. No planner change is needed.

## Backoffice API

New resource `TriggerIngredientsResource`, `@Path("trigger-ingredients")`, mirroring `AlternativeIngredientsResource`:

- `GET trigger-ingredients/recommendations`: the grid.
	- Columns: `RecommendationColumn(id, componentForScoring, typeOfRecommendation)`, as for Alternative Ingredients.
	- Rows (`triggerIngredients`): every Trigger Ingredient in master ∪ Working Copy, with `id`, `name`, `published`,
	  `version`, `translations`, `linkedRecommendationIds` (master), `stagedRecommendationIds` (Working Copy delta),
	  and `ruleRecommendationIds`: the Recommendations of the Trigger Ingredient's effective active Rules (master
	  overlaid with the Working Copy). These cells are the possible primary components.
- `PUT trigger-ingredients/{id}/recommendations/{recommendationId}`, body `{present}`: stage a link change. Unversioned,
  like the Alternative Ingredient toggle. Removing a link whose Recommendation is in `ruleRecommendationIds` is
  refused with `EntityInUseException` (`409`), since the primary component is always included anyway.

The staging logic shared with Alternative Ingredients (master XOR staged grid; a toggle that collapses back to master
deletes the Working Copy row) is extracted rather than duplicated.

## Backoffice UI (`dietwise-backoffice`)

- Extract the toggle grid from `SubstitutionValuePage` into a generic component under `src/components/` (it must not
  import from any feature folder), parameterized by the row data, the toggle action, and the i18n keys.
- New page `src/triggerIngredients/TriggerIngredientComponentsPage.tsx`, route `trigger-ingredient-components`, nav
  item. It is also the list of all Trigger Ingredients, including those without Rules, so each row has the existing
  `ReferenceEditDialog` and `ReferenceTranslationsDialog`.
- Cells in `ruleRecommendationIds` are shown present and locked, marked as coming from a Rule.

## Recipe assessment

Per ingredient, in `RecipeSuggestionsServiceImpl`:

1. Role, then Trigger Ingredient, as now.
2. If no Trigger Ingredient matched: components from the AI fallback; no Suggestions; no primary component.
3. Otherwise load the active Rules.
	- No active Rules: components = components of the Trigger Ingredient; no Suggestions; no primary component.
	- Rules exist: select the best Rule as now, with the Trigger Ingredient's components as the `dietaryComponents` of
	  the `findBestRule` prompt. Components = components of the Trigger Ingredient ∪ the primary component.
	  If Rule selection fails, components = components of the Trigger Ingredient, without a primary component.

`TriggerIngredient` (services model) gains its components, loaded by `TriggerIngredientDao.findAll` and cached per
language as now. Components are identified by their Recommendation's master name, which is language-independent: a
`RecommendationComponent` carries it, so the components of a Trigger Ingredient and the Recommendation of the selected
Rule resolve to the localized components of the assessment in every language. `NoRulesForTriggerIngredientException` is removed; zero Rules is a normal outcome and no longer logs
`"Failed to process ingredient"`.

AI fallback fixes: raise `num-predict` for the `ingredientMatchInRecommendations*` models so that multi-line answers
are not truncated, and parse the answer tolerantly (ignore blank lines and list markers, collapse duplicates instead
of throwing from `Set.of`).

## Data sent to the clients

`ScoringData` gains `primaryComponentPerIngredient: Map<IngredientId, RecommendationComponentName>`, serialized as
`"primaryComponentPerIngredient": {"<ingredientId>": "<componentForScoring>"}`. An ingredient without a primary
component has no entry. `recommendationsPerIngredient` keeps containing the primary component. The field is additive;
the clients ignore it until the score calculation is updated (out of scope).

## Known limitations, for the scientific partners

- The trigger-matching prompt sends any vegetable, olive oil, herb, water and tomato paste/passata to `unknown`. A
  scoring-only Trigger Ingredient for these (e.g. "leafy vegetables") will never be matched; such ingredients get
  their components from the AI fallback. Curating them requires reworking the trigger prompt, as a separate change.
- There is no component for olive oil or monounsaturated fat among the 15 GBD components.
- Seeded pairings that may need review: poultry and game → "seafood omega-3 fatty acids"; ketchup, BBQ sauce, sweet
  chili sauce, sugar → "sugar-sweetened beverages"; eggs, butter and cheeses → "trans fatty acids"; bechamel →
  "vegetables".
- Trigger Ingredients and their components are cached for the application's lifetime; restart after publishing.

## Delivery order

1. Backend: changelog (tables + seed), JPA entities, publish registry. Backoffice API.
2. Backoffice UI.
3. Recipe assessment: components from the Trigger Ingredient, decoupling from Rules, primary component in
   `ScoringData`.
4. Repurpose the AI service as the fallback, in its own commit.
5. `CONTEXT.md`: Trigger Ingredient definition, Component, Primary component, Scoring-only Trigger Ingredient.
