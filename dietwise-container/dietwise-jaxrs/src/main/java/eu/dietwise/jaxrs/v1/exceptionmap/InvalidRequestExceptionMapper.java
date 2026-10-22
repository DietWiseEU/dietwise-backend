package eu.dietwise.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.types.InvalidRequestException;

/**
 * Map an {@link InvalidRequestException} to HTTP 400 (BAD REQUEST): the request contradicts the stored data, so the
 * client must not repeat it unchanged.
 */
@Provider
public class InvalidRequestExceptionMapper implements ExceptionMapper<InvalidRequestException> {
	@Override
	public Response toResponse(InvalidRequestException exception) {
		return Response.status(Response.Status.BAD_REQUEST).build();
	}
}
