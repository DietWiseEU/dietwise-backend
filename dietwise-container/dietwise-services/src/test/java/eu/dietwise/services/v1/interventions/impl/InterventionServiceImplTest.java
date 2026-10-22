package eu.dietwise.services.v1.interventions.impl;

import static eu.dietwise.v1.types.UiLanguage.EL;
import static eu.dietwise.v1.types.UiLanguage.EN;
import static eu.dietwise.v1.types.UiLanguage.LT;
import static eu.dietwise.v1.types.UiLanguage.NL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory.MockReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.interventions.InterventionAssignmentDao;
import eu.dietwise.dao.interventions.InterventionDao;
import eu.dietwise.services.model.interventions.ImmutableInterventionAssignment;
import eu.dietwise.services.model.interventions.ImmutableTranslatedIntervention;
import eu.dietwise.services.model.interventions.InterventionAssignment;
import eu.dietwise.services.model.interventions.TranslatedIntervention;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.interventions.AssignedIntervention;
import eu.dietwise.services.v1.types.Intervention;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.UiLanguage;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterventionServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final UUID USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final HasUserId USER = () -> new UserIdImpl(USER_UUID.toString());
	private static final String APPLICATION_ID = "recipewatch";
	private static final String RECIPE_URL = "https://example.test/recipe";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 30, 15);
	private static final SuggestionTemplateId SUGGESTION_ID_1 = new GenericSuggestionTemplateId("00000000-2222-3333-4444-555555555555");
	private static final SuggestionTemplateId SUGGESTION_ID_2 = new GenericSuggestionTemplateId("00000001-2222-3333-4444-555555555555");
	private static final HasSuggestionTemplateIds SUGGESTIONS = () -> Set.of(SUGGESTION_ID_1, SUGGESTION_ID_2);
	private static final HasSuggestionTemplateIds NO_SUGGESTIONS = Set::of;

	private static final String GREEK_TEXT = "Εγγυημένη υγιεινή επιλογή.";
	private static final String ENGLISH_TEXT = "Healthiness guaranteed.";
	private static final TranslatedIntervention NO_INTERVENTION = intervention(0, Map.of());
	private static final TranslatedIntervention INTERVENTION_1 = intervention(1, Map.of(EN, "First", EL, "Πρώτη"));
	private static final TranslatedIntervention INTERVENTION_2 = intervention(2, Map.of(EN, ENGLISH_TEXT, EL, GREEK_TEXT));
	private static final TranslatedIntervention INTERVENTION_ONLY_IN_ENGLISH = intervention(6, Map.of(EN, ENGLISH_TEXT));

	private final MockReactivePersistenceTxContext tx = new MockReactivePersistenceTxContext();

	@Mock
	private InterventionDao interventionDao;

	@Mock
	private InterventionAssignmentDao interventionAssignmentDao;

	@Mock
	private RandomGenerator random;

	@Mock
	private DateTimeService dateTimeService;

	private InterventionServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new InterventionServiceImpl(interventionDao, interventionAssignmentDao, random, dateTimeService);
	}

	@Test
	void assignsTheInterventionPickedUniformlyAmongTheActiveOnesAndRecordsTheAssignment() {
		when(interventionDao.findActive(tx)).thenReturn(Uni.createFrom().item(List.of(NO_INTERVENTION, INTERVENTION_1, INTERVENTION_2)));
		when(random.nextInt(3)).thenReturn(2);
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(interventionAssignmentDao.insert(any(), any())).thenReturn(Uni.createFrom().voidItem());

		Optional<AssignedIntervention> result = assign(EL, SUGGESTIONS);

		InterventionAssignment inserted = captureInsertedAssignment();
		assertThat(inserted).isEqualTo(expectedAssignment(inserted.getAssessmentId(), 2, EL));
		assertThat(result).contains(new AssignedIntervention(inserted.getAssessmentId(), new Intervention(2, GREEK_TEXT)));
	}

	@Test
	void sendsAndRecordsTheEnglishTextWhenTheInterventionHasNoTextInTheUiLanguage() {
		when(interventionDao.findActive(tx)).thenReturn(Uni.createFrom().item(List.of(INTERVENTION_ONLY_IN_ENGLISH)));
		when(random.nextInt(1)).thenReturn(0);
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(interventionAssignmentDao.insert(any(), any())).thenReturn(Uni.createFrom().voidItem());

		Optional<AssignedIntervention> result = assign(LT, SUGGESTIONS);

		InterventionAssignment inserted = captureInsertedAssignment();
		assertThat(inserted).isEqualTo(expectedAssignment(inserted.getAssessmentId(), 6, EN));
		assertThat(result).contains(new AssignedIntervention(inserted.getAssessmentId(), new Intervention(6, ENGLISH_TEXT)));
	}

	@Test
	void theNoInterventionArmHasNoTextAndRecordsTheUiLanguage() {
		when(interventionDao.findActive(tx)).thenReturn(Uni.createFrom().item(List.of(NO_INTERVENTION, INTERVENTION_1)));
		when(random.nextInt(2)).thenReturn(0);
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(interventionAssignmentDao.insert(any(), any())).thenReturn(Uni.createFrom().voidItem());

		Optional<AssignedIntervention> result = assign(NL, SUGGESTIONS);

		InterventionAssignment inserted = captureInsertedAssignment();
		assertThat(inserted).isEqualTo(expectedAssignment(inserted.getAssessmentId(), 0, NL));
		assertThat(result).contains(new AssignedIntervention(inserted.getAssessmentId(), new Intervention(0, null)));
	}

	@Test
	void assignsNothingWithoutUiLanguage() {
		Optional<AssignedIntervention> result = assign(null, SUGGESTIONS);

		assertThat(result).isEmpty();
		verifyNoInteractions(interventionDao, interventionAssignmentDao, random);
	}

	@Test
	void assignsNothingWithoutSuggestions() {
		Optional<AssignedIntervention> result = assign(EN, NO_SUGGESTIONS);

		assertThat(result).isEmpty();
		verifyNoInteractions(interventionDao, interventionAssignmentDao, random);
	}

	@Test
	void assignsNothingWhenNoInterventionIsActive() {
		when(interventionDao.findActive(tx)).thenReturn(Uni.createFrom().item(List.of()));

		Optional<AssignedIntervention> result = assign(EN, SUGGESTIONS);

		assertThat(result).isEmpty();
		verifyNoInteractions(interventionAssignmentDao, random);
	}

	private Optional<AssignedIntervention> assign(UiLanguage uiLang, HasSuggestionTemplateIds suggestions) {
		return sut.assign(tx, USER, APPLICATION_ID, uiLang, RECIPE_URL, suggestions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private InterventionAssignment captureInsertedAssignment() {
		var captor = ArgumentCaptor.forClass(InterventionAssignment.class);
		verify(interventionAssignmentDao).insert(eq(tx), captor.capture());
		return captor.getValue();
	}

	private static InterventionAssignment expectedAssignment(UUID assessmentId, int interventionId, UiLanguage uiLang) {
		return ImmutableInterventionAssignment.builder()
				.assessmentId(assessmentId)
				.userId(USER_UUID)
				.applicationId(APPLICATION_ID)
				.interventionId(interventionId)
				.uiLang(uiLang)
				.recipeUrl(RECIPE_URL)
				.createdAt(NOW)
				.suggestionTemplateIds(Set.of(SUGGESTION_ID_1, SUGGESTION_ID_2))
				.build();
	}

	private static TranslatedIntervention intervention(int id, Map<UiLanguage, String> texts) {
		return ImmutableTranslatedIntervention.builder().id(id).texts(texts).build();
	}
}
