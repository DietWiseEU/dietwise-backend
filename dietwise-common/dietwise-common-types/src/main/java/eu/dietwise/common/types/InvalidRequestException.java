package eu.dietwise.common.types;

/**
 * The request of a client is well-formed but contradicts the stored data, so it cannot be carried out.
 */
public class InvalidRequestException extends RuntimeException {
	public InvalidRequestException(String message) {
		super(message);
	}
}
