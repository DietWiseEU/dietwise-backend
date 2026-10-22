package eu.dietwise.dao.impl.interventions;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.interventions.InterventionDao;
import eu.dietwise.dao.jpa.interventions.InterventionEntity;
import eu.dietwise.dao.jpa.interventions.InterventionEntity_;
import eu.dietwise.services.model.interventions.ImmutableTranslatedIntervention;
import eu.dietwise.services.model.interventions.TranslatedIntervention;
import eu.dietwise.v1.types.UiLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class InterventionDaoImpl implements InterventionDao {
	@Override
	public Uni<List<TranslatedIntervention>> findActive(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(InterventionEntity.class);
		var root = q.from(InterventionEntity.class);
		q.where(cb.isTrue(root.get(InterventionEntity_.active)));
		q.orderBy(cb.asc(root.get(InterventionEntity_.id)));
		return em.createQuery(q).getResultList().map(entities -> entities.stream().map(this::toTranslatedIntervention).toList());
	}

	private TranslatedIntervention toTranslatedIntervention(InterventionEntity entity) {
		Map<UiLanguage, String> texts = new EnumMap<>(UiLanguage.class);
		putIfPresent(texts, UiLanguage.EN, entity.getTextEn());
		putIfPresent(texts, UiLanguage.NL, entity.getTextNl());
		putIfPresent(texts, UiLanguage.EL, entity.getTextEl());
		putIfPresent(texts, UiLanguage.LT, entity.getTextLt());
		return ImmutableTranslatedIntervention.builder().id(entity.getId()).texts(texts).build();
	}

	private void putIfPresent(Map<UiLanguage, String> texts, UiLanguage lang, String text) {
		if (text != null) {
			texts.put(lang, text);
		}
	}
}
