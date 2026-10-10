package eu.dietwise.services.v1.suggestions.impl;

abstract class NonFatalIngredientProcessingException extends RuntimeException {
	protected NonFatalIngredientProcessingException(String message) {
		super(message);
	}
}
