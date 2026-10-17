package eu.dietwise.jaxrs.v1.backoffice;

import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.jaxrs.v1.backoffice.types.ToggleRecommendationRequest;
import eu.dietwise.jaxrs.v1.backoffice.types.TriggerIngredientGridResponse;
import eu.dietwise.services.v1.BackofficeTriggerIngredientsService;
import io.smallrye.mutiny.Uni;

@Path("trigger-ingredients")
public class TriggerIngredientsResource {
	@Inject
	BackofficeTriggerIngredientsService backofficeTriggerIngredientsService;

	@GET
	@Path("recommendations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<TriggerIngredientGridResponse> recommendationGrid(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeTriggerIngredientsService.recommendationGrid(user).map(TriggerIngredientGridResponse::from);
	}

	@PUT
	@Path("{id}/recommendations/{recommendationId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> toggleRecommendation(@PathParam("id") String id, @PathParam("recommendationId") String recommendationId, ToggleRecommendationRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeTriggerIngredientsService.toggleRecommendation(user, UUID.fromString(id), UUID.fromString(recommendationId), request.present());
	}
}
