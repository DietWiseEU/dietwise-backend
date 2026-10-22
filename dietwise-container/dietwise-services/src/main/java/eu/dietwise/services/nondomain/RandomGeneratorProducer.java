package eu.dietwise.services.nondomain;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class RandomGeneratorProducer {
	/**
	 * The source of randomness of the services; shared between concurrent requests, hence a thread-safe generator.
	 */
	@Produces
	@ApplicationScoped
	RandomGenerator randomGenerator() {
		return new SecureRandom();
	}
}
