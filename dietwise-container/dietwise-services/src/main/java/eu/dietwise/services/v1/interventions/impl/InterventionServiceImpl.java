package eu.dietwise.services.v1.interventions.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.interventions.InterventionAssignmentDao;
import eu.dietwise.dao.interventions.InterventionDao;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.services.model.interventions.TranslatedIntervention;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.interventions.AssignedIntervention;
import eu.dietwise.services.v1.interventions.InterventionService;
import eu.dietwise.services.v1.types.Intervention;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.UiLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class InterventionServiceImpl implements InterventionService {
	private final InterventionDao interventionDao;
	private final InterventionAssignmentDao interventionAssignmentDao;
	private final RandomGenerator random;
	private final DateTimeService dateTimeService;

	public InterventionServiceImpl(
			InterventionDao interventionDao,
			InterventionAssignmentDao interventionAssignmentDao,
			RandomGenerator random,
			DateTimeService dateTimeService
	) {
		this.interventionDao = interventionDao;
		this.interventionAssignmentDao = interventionAssignmentDao;
		this.random = random;
		this.dateTimeService = dateTimeService;
	}

	@Override
	public Uni<Optional<AssignedIntervention>> assign(
			ReactivePersistenceTxContext tx,
			HasUserId hasUserId,
			String applicationId,
			UiLanguage uiLang,
			String recipeUrl,
			HasSuggestionTemplateIds suggestions
	) {
		if (uiLang == null || suggestions.getSuggestionTemplateIds().isEmpty()) {
			return Uni.createFrom().item(Optional.empty());
		}
		return interventionDao.findActive(tx).flatMap(active -> {
			if (active.isEmpty()) {
				return Uni.createFrom().item(Optional.empty());
			}
			TranslatedIntervention picked = pick(active);
			UiLanguage textLang = textLanguage(picked, uiLang);
			InterventionAssignment assignment = ImmutableInterventionAssignment.builder()
					.assessmentId(UUID.randomUUID())
					.userId(UUID.fromString(hasUserId.getId().asString()))
					.applicationId(applicationId)
					.interventionId(picked.getId())
					.uiLang(textLang)
					.recipeUrl(recipeUrl)
					.createdAt(dateTimeService.getNow())
					.suggestionTemplateIds(suggestions.getSuggestionTemplateIds())
					.build();
			var intervention = new Intervention(picked.getId(), picked.getTexts().get(textLang));
			return interventionAssignmentDao.insert(tx, assignment)
					.replaceWith(Optional.of(new AssignedIntervention(assignment.getAssessmentId(), intervention)));
		});
	}

	private TranslatedIntervention pick(List<TranslatedIntervention> active) {
		return active.get(random.nextInt(active.size()));
	}

	/**
	 * The language of the text sent: {@code uiLang}, unless the Intervention has texts but none in {@code uiLang}.
	 */
	private UiLanguage textLanguage(TranslatedIntervention intervention, UiLanguage uiLang) {
		Map<UiLanguage, String> texts = intervention.getTexts();
		return texts.isEmpty() || texts.containsKey(uiLang) ? uiLang : UiLanguage.EN;
	}
}
